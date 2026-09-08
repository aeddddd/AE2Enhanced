package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.ITaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.specialcrafting.RoundQuotaScheduler;
import com.github.aeddddd.ae2enhanced.specialcrafting.SelfRefOutputGate;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingRuntime;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 特殊合成执行层（自消耗/循环链计划）.
 *
 * <p>实现 {@link ISpecialCpuAccess} 供 SelfRefOutputGate / RoundQuotaScheduler 访问集群内部状态，
 * 并注入 job 提交/完成/取消钩子与超轮配额否决逻辑。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public abstract class MixinCraftingCPUClusterSpecial
        implements ISpecialCpuAccess, com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess {

    /** 特殊标记字段级缓存(由 SpecialCraftingRuntime.tag/untag 同步写入,查询零锁). */
    @org.spongepowered.asm.mixin.Unique
    private boolean ae2e$specialMarked;

    @Override
    public boolean ae2e$isSpecialMarked() {
        return this.ae2e$specialMarked;
    }

    @Override
    public void ae2e$setSpecialMarked(boolean marked) {
        this.ae2e$specialMarked = marked;
    }

    @Shadow
    private Map<ICraftingPatternDetails, Object> tasks;

    @Shadow
    private IItemList<IAEItemStack> waitingFor;

    @Shadow
    private IAEItemStack finalOutput;

    @Shadow
    private appeng.api.networking.crafting.ICraftingLink myLastLink;

    @Shadow
    private void postChange(IAEItemStack diff, appeng.api.networking.security.IActionSource src) {
    }

    @Shadow
    private void postCraftingStatusChange(IAEItemStack diff) {
    }

    @Shadow
    private void updateRemainingItemCount(IAEItemStack is) {
    }

    @Shadow
    private void updateCPU() {
    }

    @Shadow
    private void completeJob() {
    }

    // ==================== ISpecialCpuAccess ====================

    @Override
    public Map<ICraftingPatternDetails, Object> ae2e$tasks() {
        return this.tasks;
    }

    @Override
    public Map<ICraftingPatternDetails, Long> ae2e$remainingSnapshot() {
        return this.ae2e$remainingSnapshot;
    }

    @Override
    public IItemList<IAEItemStack> ae2e$waitingFor() {
        return this.waitingFor;
    }

    @Override
    public IAEItemStack ae2e$finalOutput() {
        return this.finalOutput;
    }

    @Override
    public appeng.api.networking.crafting.ICraftingLink ae2e$myLastLink() {
        return this.myLastLink;
    }

    @Override
    public void ae2e$postChange(IAEItemStack diff, appeng.api.networking.security.IActionSource src) {
        this.postChange(diff, src);
    }

    @Override
    public void ae2e$postCraftingStatusChange(IAEItemStack diff) {
        this.postCraftingStatusChange(diff);
    }

    @Override
    public void ae2e$updateRemainingItemCount(IAEItemStack is) {
        this.updateRemainingItemCount(is);
    }

    @Override
    public void ae2e$markDirtyCluster() {
        TileComputationCore core = ((IComputationCoreAccess) this).ae2enhanced$getComputationCore();
        if (core != null) {
            core.markDirty();
        }
    }

    @Override
    public void ae2e$updateCPU() {
        this.updateCPU();
    }

    @Override
    public void ae2e$completeJob() {
        this.completeJob();
    }

    // ==================== Special Crafting Execution Layer ====================

    /**
     * 特殊计划提交成功:标记集群(启用交付门控)+ 快照 tasks 总次数(供超轮配额推导).
     */
    @Inject(method = "submitJob", at = @At("RETURN"), require = 0)
    private void ae2enhanced$onSpecialJobSubmitted(IGrid g, appeng.api.networking.crafting.ICraftingJob job,
            appeng.api.networking.security.IActionSource src,
            appeng.api.networking.crafting.ICraftingRequester requestingMachine,
            CallbackInfoReturnable<appeng.api.networking.crafting.ICraftingLink> cir) {
        if (cir.getReturnValue() == null || !SpecialPlanMarker.isSpecial(job)) {
            return;
        }
        CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        SpecialCraftingRuntime.tagCluster(self);
        Map<ICraftingPatternDetails, Long> totals = new java.util.LinkedHashMap<>();
        for (Map.Entry<ICraftingPatternDetails, Object> entry : this.tasks.entrySet()) {
            totals.put(entry.getKey(), ((ITaskProgressAccessor) entry.getValue()).ae2e$getValue());
        }
        RoundQuotaScheduler.snapshot(self, totals);
        AE2Enhanced.LOGGER.info("[特殊配方] 特殊计划已提交到计算核心集群: {}", job.getOutput());
    }

    /**
     * job 完成/取消:解除特殊标记并清理配额快照.
     */
    @Inject(method = "completeJob", at = @At("HEAD"), require = 0)
    private void ae2enhanced$onCompleteJob(CallbackInfo ci) {
        CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        if (SpecialCraftingRuntime.isSpecialCluster(self)) {
            SpecialCraftingRuntime.untagCluster(self);
            RoundQuotaScheduler.clear(self);
        }
    }

    @Inject(method = "cancel", at = @At("HEAD"), require = 0)
    private void ae2enhanced$onCancel(CallbackInfo ci) {
        CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        if (SpecialCraftingRuntime.isSpecialCluster(self)) {
            SpecialCraftingRuntime.untagCluster(self);
            RoundQuotaScheduler.clear(self);
        }
    }

    /**
     * 特殊标记随集群 NBT 持久化:重启/集群重组(done→readFromNBT)后门控与配额
     * 调度自愈.快照以 pattern ItemStack + 总次数存储——重建后的
     * ICraftingPatternDetails 是新实例,按键对象身份无法跨重启复用.
     */
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 0)
    private void ae2enhanced$writeSpecialState(NBTTagCompound data, CallbackInfo ci) {
        CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        if (!SpecialCraftingRuntime.isSpecialCluster(self)) {
            return;
        }
        data.setBoolean("ae2eSpecial", true);
        Map<ICraftingPatternDetails, Long> totals = RoundQuotaScheduler.totalsOf(self);
        if (totals == null || totals.isEmpty()) {
            return;
        }
        NBTTagList list = new NBTTagList();
        for (Map.Entry<ICraftingPatternDetails, Long> entry : totals.entrySet()) {
            ItemStack pattern = entry.getKey().getPattern();
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            NBTTagCompound item = new NBTTagCompound();
            pattern.writeToNBT(item);
            item.setLong("total", entry.getValue());
            list.appendTag(item);
        }
        if (!list.isEmpty()) {
            data.setTag("ae2eSpecialTotals", list);
        }
    }

    /**
     * NBT 恢复:重建特殊标记 + 配额快照(按 pattern ItemStack 匹配重建后的 tasks 键;
     * 原生 readFromNBT 在本钩子之前已重建 tasks/finalOutput/waitingFor).
     */
    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 0)
    private void ae2enhanced$readSpecialState(NBTTagCompound data, CallbackInfo ci) {
        if (!data.getBoolean("ae2eSpecial")) {
            return;
        }
        CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
        SpecialCraftingRuntime.tagCluster(self);
        NBTTagList list = data.getTagList("ae2eSpecialTotals", 10);
        if (list.isEmpty()) {
            AE2Enhanced.LOGGER.info("[特殊配方] NBT 恢复特殊集群(无配额快照,退化为原生推送): {}",
                    this.finalOutput);
            return;
        }
        Map<ICraftingPatternDetails, Long> totals = new java.util.LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            ItemStack patternStack = new ItemStack(item);
            long total = item.getLong("total");
            if (patternStack.isEmpty() || total <= 0) {
                continue;
            }
            for (ICraftingPatternDetails details : this.tasks.keySet()) {
                if (ItemStack.areItemStacksEqual(details.getPattern(), patternStack)) {
                    totals.put(details, total);
                    break;
                }
            }
        }
        if (totals.isEmpty()) {
            AE2Enhanced.LOGGER.warn("[特殊配方] NBT 恢复特殊集群:配额快照匹配失败(样板已变更?),退化为原生推送: {}",
                    this.finalOutput);
            return;
        }
        RoundQuotaScheduler.snapshot(self, totals);
        AE2Enhanced.LOGGER.info("[特殊配方] NBT 恢复特殊集群:标记 + 配额快照({} 样板)已重建: {}",
                totals.size(), this.finalOutput);
    }

    /**
     * 自消耗 job（自引用/循环链计划,最终产出仍是任务输入）的交付门控:
     * 最终产出先入 CPU 库存,全部任务收官后一次性交付,防止边产边交付饿死合成链.
     * 仅对被标记的集群生效;普通 job 判定为不接管时零影响.
     */
    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2enhanced$gateSelfConsumingOutput(IAEItemStack input, appeng.api.config.Actionable type,
            appeng.api.networking.security.IActionSource src, CallbackInfoReturnable<IAEItemStack> cir) {
        SelfRefOutputGate.GateResult result = SelfRefOutputGate.handleInsert(
                (CraftingCPUCluster) (Object) this, input, type, src);
        if (result.handled) {
            cir.setReturnValue(result.leftover);
        }
    }

    /**
     * executeCrafting 单趟内有效的 remaining 快照.
     * <p>旧实现逐次 canCraft 调用前都从 tasks 重建整张 LinkedHashMap——N 个
     * pattern 的计划每 tick 产生 O(N²) 次 HashMap.put(spark 采样:占服务器线程
     * 91%,其中 HashMap.put/hash/resize ≈74%),是大计划下单后执行卡顿的主因.
     * 快照口径只会比逐次重建更保守(同趟内已推送量不可见 → 闸门最多多拒一次,
     * 下一趟快照刷新后放行),不会超额推送.</p>
     */
    private Map<ICraftingPatternDetails, Long> ae2e$remainingSnapshot;

    /**
     * 本趟否决集合(惰性,首次否决判定时计算,趟内复用).
     * <p>趟内 remaining 快照/totals/quota 均不变,逐次否决判定结果趟内稳定;
     * 大单(千级 pattern)下逐次重算 round 扫描闭包是 O(N·闭包)/tick 的热点
     * (spark 采样占 tick ~41%),整趟缓存后降为 O(N+闭包).</p>
     */
    private java.util.Set<ICraftingPatternDetails> ae2e$vetoedThisTick;

    /** executeCrafting 入口:趟首复位趟缓存(快照本体改在首次否决判定时惰性构建,
     * 无配额门控的特殊 job——占多数——整趟零开销). */
    @Inject(method = "executeCrafting", at = @At("HEAD"), require = 0)
    private void ae2enhanced$snapshotRemaining(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        this.ae2e$vetoedThisTick = null;
        this.ae2e$remainingSnapshot = null;
    }

    /** executeCrafting 出口:丢弃快照(防滞留引用 + 下一趟重建). */
    @Inject(method = "executeCrafting", at = @At("RETURN"), require = 0)
    private void ae2enhanced$dropRemainingSnapshot(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        this.ae2e$remainingSnapshot = null;
        this.ae2e$vetoedThisTick = null;
    }

    /**
     * 超轮配额调度:逐次推送否决——超配额的闭包 pattern 令 canCraft 返回 false,
     * 原生视同"输入不足"自然跳过,下一拍配额前进后自动恢复.
     * 仅对被标记集群上的自消耗 job 生效.
     */
    @WrapOperation(
        method = "executeCrafting",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;canCraft(Lappeng/api/networking/crafting/ICraftingPatternDetails;[Lappeng/api/storage/data/IAEItemStack;)Z"
        ),
        require = 0
    )
    private boolean ae2enhanced$vetoPushOverQuota(CraftingCPUCluster self, ICraftingPatternDetails details,
            IAEItemStack[] condensedInputs, Operation<Boolean> original) {
        if (SpecialCraftingRuntime.isSpecialCluster(self)) {
            java.util.Set<ICraftingPatternDetails> vetoed = this.ae2e$vetoedThisTick;
            if (vetoed == null) {
                // 趟内首次判定:无配额门控(非自消耗 job,占多数)直接空集短路,
                // 不构建 remaining 快照;有配额才惰性构建快照并算全闭包否决集
                if (!RoundQuotaScheduler.hasQuota(self, this.finalOutput)) {
                    vetoed = java.util.Collections.emptySet();
                } else {
                    Map<ICraftingPatternDetails, Long> remaining = this.ae2e$remainingSnapshot;
                    if (remaining == null) {
                        remaining = new java.util.LinkedHashMap<>();
                        for (Map.Entry<ICraftingPatternDetails, Object> entry : this.tasks.entrySet()) {
                            remaining.put(entry.getKey(),
                                    ((ITaskProgressAccessor) entry.getValue()).ae2e$getValue());
                        }
                        this.ae2e$remainingSnapshot = remaining;
                    }
                    vetoed = RoundQuotaScheduler.vetoedSetForTick(self, remaining, this.finalOutput);
                }
                this.ae2e$vetoedThisTick = vetoed;
            }
            if (vetoed.contains(details)) {
                return false; // 超配额:视同输入不足,本拍跳过该 pattern
            }
        }
        return original.call(self, details, condensedInputs);
    }
}
