package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.ICraftingTreeProcessAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/**
 * 合成树计算：多供应者分支的批量请求.
 *
 * <p>背景：{@code CraftingTreeNode.request} 的多供应者分支（同一物品有 ≥2 个样板
 * 可产出，nodes.size() &gt; 1）以<b>每次 1 份</b>的粒度循环调用
 * {@code pro.request(subInv, 1L, src)}，每迭代分配一个试用 MECraftingInventory
 * 并做全子树模拟——对 N 份订单是 O(N×子树) 的迭代，是大订单"计算中"卡死的
 * 代码级根因（单供应者分支已通过 getTimes 批量，不受影响；自引用配方
 * getTimes 恒 1 维持逐份，本 mixin 不改变其语义）。</p>
 *
 * <p>本 mixin 包装该分支的 pro.request 调用：一次请求 {@code floor(剩余量/单次产出)}
 * 份（向下取整保证产出不超订，尾数由原生循环继续逐份处理）；批量请求抛
 * {@link CraftBranchFailure}（材料不足以支撑整批）时回退为逐份请求，
 * 保持原生"能做多少做多少"的失败语义。试用库存失败即弃不 commit 的行为与原生一致，
 * 因此记账/缺料统计在两种路径下逐字节等价。</p>
 */
@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class MixinCraftingTreeNodeBatch {

    /** 二分定位开关：-Dae2e.disableTreeBatch=true 时计算侧批量请求完全禁用（原生逐份）. */
    private static final boolean AE2E_DISABLE_TREE_BATCH = Boolean.getBoolean("ae2e.disableTreeBatch");

    @Shadow
    private ArrayList<CraftingTreeProcess> nodes;

    @Shadow
    private IAEItemStack what;

    /**
     * 包装 request 内对 CraftingTreeProcess.request 的调用（两个调用点都会被包装，
     * 单供应者调用点以 nodes.size() == 1 判定并原样透传）.
     */
    @WrapOperation(method = "request", require = 0, at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeProcess;request(Lappeng/crafting/MECraftingInventory;JLappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;"))
    private IAEItemStack ae2e$batchedBranchRequest(CraftingTreeProcess pro, MECraftingInventory subInv,
            long times, IActionSource src, Operation<IAEItemStack> original,
            @Local(argsOnly = true) long l) {
        // 二分定位开关：-Dae2e.disableTreeBatch=true 时完全走原生路径
        if (AE2E_DISABLE_TREE_BATCH) {
            return original.call(pro, subInv, times, src);
        }
        // 单供应者分支（含自引用逐份语义）与任何非 1 份调用：原样透传
        if (this.nodes.size() <= 1 || times != 1L) {
            return original.call(pro, subInv, times, src);
        }
        long per = ((ICraftingTreeProcessAccessor) pro).ae2e$invokeGetAmountCrafted(this.what).getStackSize();
        if (per <= 0) {
            return original.call(pro, subInv, times, src);
        }
        // 自引用检测必须复用原生 getTimes 的护栏：自引用配方（产物∈输入，循环链）
        // getTimes 恒返回 1——其每份产出需经 commit 回馈为下一份输入，批量化会破坏
        // 回馈时序导致材料核算错误。非自引用时 getTimes(MAX, per) 必然 > 1。
        if (((ICraftingTreeProcessAccessor) pro).ae2e$invokeGetTimes(Long.MAX_VALUE, per) == 1L) {
            return original.call(pro, subInv, times, src);
        }
        // floor 批量：尾数由原生循环逐份精确收尾，不产生超产
        // （与原生多供应者分支的精确计数语义逐字节一致）
        long batch = l / per;
        if (batch <= 1L) {
            return original.call(pro, subInv, times, src);
        }
        try {
            return original.call(pro, subInv, batch, src);
        } catch (Exception e) {
            // Operation.call 不声明受检异常，CraftBranchFailure 在运行时原样穿过；
            // 整批材料不足（CraftBranchFailure）时回退逐份，保持原生的部分成功/失败语义
            if (!(e instanceof CraftBranchFailure)) {
                throw e;
            }
            return original.call(pro, subInv, 1L, src);
        }
    }
}
