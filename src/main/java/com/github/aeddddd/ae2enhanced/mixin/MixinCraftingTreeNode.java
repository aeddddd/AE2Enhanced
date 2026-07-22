package com.github.aeddddd.ae2enhanced.mixin;

import javax.annotation.Nullable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.CraftingSimulationState;

import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCalculationAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingTreeNodeAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingTreeProcessAccessor;
import com.github.aeddddd.ae2enhanced.util.RecursiveCraftingHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * 递归类合成支持（计算层,第二部分）.
 * <p>对"唯一候选样板为净产出自引用样板"（如 A+2B=2A）的节点整体接管：
 * 抽取库存时预留种子,随后用"贷款法"一次性模拟全部合成——先向模拟库存
 * 借入 (份数-1)×单次种子 的请求物,使 {@code pro.request(inv, crafts)} 可以
 * 整批通过,产出后立即归还借入量并取走净产出、保留种子.相比逐份迭代,
 * 计算耗时从 O(份数) 降为 O(1) 次样板模拟.其余场景完全走原生逻辑.</p>
 */
@Mixin(value = CraftingTreeNode.class, remap = false)
public class MixinCraftingTreeNode {

    @Inject(method = "request", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$handleSelfRefRequest(CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems, CallbackInfo ci) throws CraftBranchFailure, InterruptedException {
        CraftingTreeNodeAccessor acc = (CraftingTreeNodeAccessor) this;
        if (acc.isCanEmit()) {
            return;
        }

        AEKey what = acc.getWhat();
        // 种子子节点（自引用过程自身的输入节点）走原生路径：直接从模拟库存消耗种子
        CraftingTreeProcess parent = acc.getParent();
        if (parent != null && RecursiveCraftingHelper
                .isNetPositiveSelfRef(((CraftingTreeProcessAccessor) parent).getDetails(), what)) {
            return;
        }

        // 廉价预检查（不构建子样板树）：唯一候选样板且为净产出自引用
        var gridNode = ((CraftingCalculationAccessor) acc.getJob()).getSimRequester().getGridNode();
        if (gridNode == null || !RecursiveCraftingHelper
                .isOnlyCandidateSelfRef(gridNode.getGrid().getCraftingService(), what)) {
            return;
        }

        acc.invokeBuildChildPatterns();
        var nodes = acc.getNodes();
        if (nodes == null || nodes.size() != 1) {
            return;
        }
        CraftingTreeProcess pro = nodes.get(0);
        IPatternDetails details = ((CraftingTreeProcessAccessor) pro).getDetails();
        if (!RecursiveCraftingHelper.isNetPositiveSelfRef(details, what)) {
            return;
        }

        ci.cancel();
        ae2e$runSelfRefBatch(acc, (CraftingTreeProcessAccessor) pro, details, inv, requestedAmount, containerItems);
    }

    /**
     * 催化剂（同 key 返还）配方的计算加速——单分支循环（源码先出现的调用点）.
     * <p>原生对 limitQty 过程强制逐份模拟（每次 1 份）,对含催化剂的配方是 O(份数) 的开销.
     * 当过程的自输入全部为"同 key 催化剂"（输出中存在与输入完全相等的 key 且 out >= in）时,
     * 用与递归合成相同的贷款法一次性模拟全部份数.
     * 含 NBT 变化（耐久损耗等 key 不相等）的自引用不接管,保持原生逐份.
     * 注意：禁止使用 MixinExtras @Local 捕获局部变量（曾导致目标类字节码校验失败、无法加载）,
     * 一律通过 @Redirect 附加宿主方法参数获取所需上下文.</p>
     */
    @Redirect(method = "request", remap = false, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/CraftingTreeProcess;request(Lappeng/crafting/inv/CraftingSimulationState;J)V",
                    ordinal = 0))
    private void ae2e$batchCatalystSingle(CraftingTreeProcess pro, CraftingSimulationState invArg, long times,
            CraftingSimulationState inv, long requestedAmount, KeyCounter containerItems)
            throws CraftBranchFailure, InterruptedException {
        ae2e$batchCatalystImpl(pro, invArg, times, requestedAmount * ((CraftingTreeNodeAccessor) this).getAmount());
    }

    /**
     * 催化剂批量——多分支循环（{@code pro.request(child, 1)} 调用点）.
     */
    @Redirect(method = "request", remap = false, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/CraftingTreeProcess;request(Lappeng/crafting/inv/CraftingSimulationState;J)V",
                    ordinal = 1))
    private void ae2e$batchCatalystMulti(CraftingTreeProcess pro, CraftingSimulationState child, long times,
            CraftingSimulationState inv, long requestedAmount, KeyCounter containerItems)
            throws CraftBranchFailure, InterruptedException {
        ae2e$batchCatalystImpl(pro, child, times, requestedAmount * ((CraftingTreeNodeAccessor) this).getAmount());
    }

    @Unique
    private void ae2e$batchCatalystImpl(CraftingTreeProcess pro, CraftingSimulationState inv, long times,
            long totalRequestedItems) throws CraftBranchFailure, InterruptedException {
        CraftingTreeProcessAccessor proAcc = (CraftingTreeProcessAccessor) pro;
        if (!proAcc.invokeLimitsQuantity() || times != 1 || totalRequestedItems <= 0) {
            proAcc.invokeRequest(inv, times); // 非逐份分支（原生已整批）不干预
            return;
        }

        AEKey what = ((CraftingTreeNodeAccessor) this).getWhat();
        IPatternDetails details = proAcc.getDetails();
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(details, what);
        if (outPer <= 0) {
            proAcc.invokeRequest(inv, times);
            return;
        }

        // 校验所有自输入均为同 key 催化剂,并计算每种催化剂的单次占用量
        Map<AEKey, Long> catalystInPer = new HashMap<>();
        for (var input : details.getInputs()) {
            var primary = input.getPossibleInputs()[0];
            long inPer = primary.amount() * input.getMultiplier();
            if (inPer <= 0) {
                continue;
            }
            long outExact = 0;
            boolean fuzzySelfRef = false;
            for (var output : details.getOutputs()) {
                if (output.what().equals(primary.what())) {
                    outExact += output.amount();
                } else if (output.what().matches(primary)) {
                    fuzzySelfRef = true; // 同物品不同 NBT（如耐久损耗）→ 不接管
                }
            }
            if (fuzzySelfRef) {
                proAcc.invokeRequest(inv, times);
                return;
            }
            if (outExact >= inPer) {
                catalystInPer.put(primary.what(), inPer);
            }
        }
        if (catalystInPer.isEmpty()) {
            proAcc.invokeRequest(inv, times);
            return;
        }

        long batchTimes = (totalRequestedItems + outPer - 1) / outPer;
        if (batchTimes <= 1) {
            proAcc.invokeRequest(inv, times); // 单份无需加速
            return;
        }
        Map<AEKey, Long> loans = new HashMap<>();
        for (var entry : catalystInPer.entrySet()) {
            if (batchTimes > Long.MAX_VALUE / entry.getValue()) {
                proAcc.invokeRequest(inv, times); // 天文数字订单不做批量
                return;
            }
            loans.put(entry.getKey(), entry.getValue() * (batchTimes - 1));
        }

        loans.forEach((key, value) -> inv.insert(key, value, Actionable.MODULATE));
        try {
            proAcc.invokeRequest(inv, batchTimes);
        } finally {
            loans.forEach((key, value) -> inv.extract(key, value, Actionable.MODULATE));
        }
    }

    @Unique
    private void ae2e$runSelfRefBatch(CraftingTreeNodeAccessor acc, CraftingTreeProcessAccessor pro,
            IPatternDetails details, CraftingSimulationState inv, long requestedAmount,
            @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        CraftingCalculationAccessor job = (CraftingCalculationAccessor) acc.getJob();
        AEKey what = acc.getWhat();
        long inPer = RecursiveCraftingHelper.selfInputPerCraft(details, what);
        long outPer = RecursiveCraftingHelper.selfOutputPerCraft(details, what);
        long gain = outPer - inPer;

        job.invokeHandlePausing();
        inv.addStackBytes(what, acc.getAmount(), requestedAmount);

        // 1) 从库存抽取（与原生一致,但预留 inPer 个种子在模拟库存中）
        for (var template : acc.invokeGetValidItemTemplates(inv)) {
            if (requestedAmount <= 0) {
                break;
            }
            long stockItems = inv.extract(template.key(), Long.MAX_VALUE, Actionable.SIMULATE);
            long takeableItems = Math.max(0, stockItems - inPer);
            long takeTemplates = Math.min(requestedAmount, takeableItems / template.amount());
            if (takeTemplates > 0) {
                long extracted = CraftingCpuHelper.extractTemplates(inv, template, takeTemplates);
                if (extracted > 0) {
                    requestedAmount -= extracted;
                    acc.invokeAddContainerItems(template.key(), extracted, containerItems);
                }
            }
        }
        if (requestedAmount == 0) {
            return;
        }
        acc.invokeAddContainerItems(what, requestedAmount, containerItems);

        long totalRequestedItems = requestedAmount * acc.getAmount();

        // 2) 种子校验：无种子则与原生一致报缺料/失败
        long seed = inv.extract(what, inPer, Actionable.SIMULATE);
        if (seed < inPer) {
            ae2e$failShortage(acc, job, what, totalRequestedItems);
            return;
        }

        long crafts = (totalRequestedItems + gain - 1) / gain;
        if (crafts <= 0 || crafts > Long.MAX_VALUE / inPer) {
            // 天文数字订单（贷款量将溢出）时不做接管式计算,按缺料处理
            ae2e$failShortage(acc, job, what, totalRequestedItems);
            return;
        }

        // 3) 贷款法：借入 (crafts-1)×inPer 使整批 request 通过,产出后立即归还.
        // 归还后库存 = 种子 + crafts×gain,取走 totalRequestedItems 并保留种子.
        long loan = inPer * (crafts - 1);
        if (loan > 0) {
            inv.insert(what, loan, Actionable.MODULATE);
        }
        try {
            pro.invokeRequest(inv, crafts);
        } finally {
            if (loan > 0) {
                inv.extract(what, loan, Actionable.MODULATE);
            }
        }

        long avail = inv.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
        long keep = avail > totalRequestedItems ? inPer : 0;
        long drain = inv.extract(what, Math.min(totalRequestedItems, Math.max(0, avail - keep)),
                Actionable.MODULATE);
        totalRequestedItems -= drain;

        if (totalRequestedItems > 0) {
            ae2e$failShortage(acc, job, what, totalRequestedItems);
        }
    }

    @Unique
    private void ae2e$failShortage(CraftingTreeNodeAccessor acc, CraftingCalculationAccessor job,
            AEKey what, long missing) throws CraftBranchFailure {
        if (acc.getJob().isSimulation()) {
            job.invokeAddMissing(what, missing);
        } else {
            throw new CraftBranchFailure(what, missing);
        }
    }
}
