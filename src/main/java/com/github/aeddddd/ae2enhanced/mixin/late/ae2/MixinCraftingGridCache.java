package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.crafting.CraftingLink;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.specialcrafting.FallbackLpCraftingJob;
import com.github.aeddddd.ae2enhanced.specialcrafting.LpCraftingJob;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;

/**
 * Mixin into {@link CraftingGridCache} to recognise {@link TileComputationCore} virtual CraftingCPUClusters.
 *
 * <p>AE2-UEL stores CPUs in {@code Set<CraftingCPUCluster>} and rebuilds it from physical
 * {@link appeng.tile.crafting.TileCraftingStorageTile} machines. This mixin:</p>
 * <ul>
 *   <li>Tracks {@link TileComputationCore} instances via addNode/removeNode</li>
 *   <li>Re-injects virtual clusters into {@code craftingCPUClusters} after each rebuild</li>
 *   <li>Provides fallback job submission that dynamically spawns new virtual clusters</li>
 * </ul>
 */
@Mixin(value = CraftingGridCache.class, remap = false)
public class MixinCraftingGridCache implements com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess {

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    @Final
    private it.unimi.dsi.fastutil.objects.Object2ObjectMap<IAEItemStack, com.google.common.collect.ImmutableList<appeng.api.networking.crafting.ICraftingPatternDetails>> craftableItems;

    @Shadow
    @Final
    private Set<IAEItemStack> emitableItems;

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    public void updateCPUClusters(MENetworkCraftingCpuChange event) {
        // shadow
    }

    @Shadow
    public void addLink(CraftingLink link) {
        // shadow
    }

    @Unique
    private final Set<TileComputationCore> ae2enhanced$computationCores = new HashSet<>();

    /** 网络内装配中枢控制器节点计数,>0 时合成 CPU 才需要批量结算扫描. */
    @Unique
    private int ae2enhanced$assemblyHubCount;

    @Override
    public boolean ae2enhanced$hasAssemblyHub() {
        return ae2enhanced$assemblyHubCount > 0;
    }

    /** getMediums 结果 memo(按 details 实例身份),recalculateCraftingPatterns 时失效. */
    @Unique
    private final java.util.IdentityHashMap<ICraftingPatternDetails, List<ICraftingMedium>> ae2enhanced$mediumsMemo =
        new java.util.IdentityHashMap<>();

    @Shadow
    public List<ICraftingMedium> getMediums(ICraftingPatternDetails key) {
        // shadow
        return null;
    }

    @Override
    public List<ICraftingMedium> ae2enhanced$getMediumsMemo(ICraftingPatternDetails details) {
        List<ICraftingMedium> list = ae2enhanced$mediumsMemo.get(details);
        if (list == null) {
            list = this.getMediums(details);
            ae2enhanced$mediumsMemo.put(details, list);
        }
        return list;
    }

    /** 网络样板缓存索引(SCC/副产物倒排/detector memo),惰性构建;volatile 保证计算线程可见. */
    @Unique
    private volatile NetworkPatternIndex ae2enhanced$patternIndex;

    @Override
    public Set<IAEItemStack> ae2enhanced$craftableKeys() {
        // craftableItems 由服务器线程就地 clear+重建(fastutil 无锁),计算线程
        // 读取遭遇重建会得到 CME 或静默部分键集——短暂自旋重试取一致快照
        for (int attempt = 0; attempt < 16; attempt++) {
            try {
                return new HashSet<>(this.craftableItems.keySet());
            } catch (java.util.ConcurrentModificationException ignored) {
                Thread.yield();
            }
        }
        return new HashSet<>(this.craftableItems.keySet()); // 兜底:异常照常抛
    }

    /**
     * 一致性样板快照(canon 键 → 样板表),recalc TAIL(服务器线程、重建刚完成)
     * 固化;volatile 发布.计算线程求解期一律走此快照,禁止活读 craftableItems
     * (重建空窗期 map 稳定为空且无并发修改,CME/复核均无法识别).
     */
    @Unique
    private volatile java.util.Map<IAEItemStack, List<ICraftingPatternDetails>> ae2enhanced$craftableSnapshot;

    /** 发射台键快照(canon),与最近一次 recalc 同代;setEmitable 动态增量 copy-on-write. */
    @Unique
    private volatile Set<IAEItemStack> ae2enhanced$emitterSnapshot;

    @Override
    public java.util.Map<IAEItemStack, List<ICraftingPatternDetails>> ae2enhanced$craftableSnapshot() {
        java.util.Map<IAEItemStack, List<ICraftingPatternDetails>> snap = this.ae2enhanced$craftableSnapshot;
        if (snap != null) {
            return snap;
        }
        // 引导路径(首次 recalc 完成前):活读 + CME/空窗等待重试。
        // 重建空窗是毫秒~秒级,yield 自旋必然全落空窗;下单线程是异步线程,
        // 可以真等待——能发起下单说明网络必有样板,空读几乎必然等于"撞上重建"
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                java.util.Map<IAEItemStack, List<ICraftingPatternDetails>> live = new java.util.HashMap<>();
                for (java.util.Map.Entry<IAEItemStack, com.google.common.collect.ImmutableList<ICraftingPatternDetails>> e : this.craftableItems
                        .entrySet()) {
                    live.computeIfAbsent(
                            com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper.canon(
                                    e.getKey()),
                            k -> new java.util.ArrayList<>()).addAll(e.getValue());
                }
                if (!live.isEmpty()) {
                    return live;
                }
                // 空读:可能撞上重建空窗(clear 后 put 前的瞬间),也可能真空网络——
                // 先看 TAIL 是否已在等待期间发布了快照,都没有则等待重试;
                // 真空网络的代价是每次调用最多空等 ~2s,但能发起下单即说明必有样板,
                // 该路径实际不会走到
                snap = this.ae2enhanced$craftableSnapshot;
                if (snap != null) {
                    return snap;
                }
            } catch (java.util.ConcurrentModificationException ignored) {
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return java.util.Collections.emptyMap(); // 兜底:空快照(不缓存,下次调用重试)
    }

    @Override
    public boolean ae2enhanced$canEmit(IAEItemStack canonKey) {
        Set<IAEItemStack> snap = this.ae2enhanced$emitterSnapshot;
        if (snap != null) {
            return snap.contains(canonKey);
        }
        return this.emitableItems.contains(canonKey); // 引导路径:活读(contains 无迭代,相对安全)
    }

    @Override
    public NetworkPatternIndex ae2enhanced$patternIndex() {
        NetworkPatternIndex idx = this.ae2enhanced$patternIndex;
        if (idx == null) {
            synchronized (this) {
                idx = this.ae2enhanced$patternIndex;
                if (idx == null) {
                    // 构建前先钉住代数:引导期(首次 recalc TAIL 前)构建的索引可能
                    // 来自重建空窗的空快照,缓存它会把"空索引"钉死到下一次 recalc——
                    // 期间所有下单误判根键缺料(概率性下单失败的残留机制).
                    // 引导代索引返回临时实例不缓存,下次调用随快照固化自愈
                    boolean bootstrap = this.ae2enhanced$craftableSnapshot == null;
                    idx = NetworkPatternIndex.build((ICraftingGrid) (Object) this);
                    if (!bootstrap) {
                        this.ae2enhanced$patternIndex = idx;
                    }
                }
            }
        }
        return idx;
    }

    /** 样板集重建后:先固化一致性快照(此刻服务器线程、数据完整),再失效索引
     * (下一次访问惰性重建);置空与惰性构建同监视器,防计算线程把竞态期间构建的
     * 陈旧索引回种到缓存(陈旧索引缺样板 → LP 误判缺料仅根键缺失). */
    @Inject(method = "recalculateCraftingPatterns", at = @At("TAIL"), require = 0)
    private void ae2enhanced$invalidatePatternIndex(CallbackInfo ci) {
        java.util.Map<IAEItemStack, List<ICraftingPatternDetails>> snap = new java.util.HashMap<>();
        for (java.util.Map.Entry<IAEItemStack, com.google.common.collect.ImmutableList<ICraftingPatternDetails>> e : this.craftableItems
                .entrySet()) {
            snap.computeIfAbsent(
                    com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper.canon(e.getKey()),
                    k -> new java.util.ArrayList<>()).addAll(e.getValue());
        }
        Set<IAEItemStack> emit = new HashSet<>();
        for (IAEItemStack e : this.emitableItems) {
            emit.add(com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper.canon(e));
        }
        this.ae2enhanced$craftableSnapshot = snap;
        this.ae2enhanced$emitterSnapshot = emit;
        synchronized (this) {
            this.ae2enhanced$patternIndex = null;
        }
        // craftingMethods 已重建,mediums memo 同步失效
        this.ae2enhanced$mediumsMemo.clear();
    }

    /** setEmitable 动态增量(recalc 外由发射台元件调用):copy-on-write 并入快照. */
    @Inject(method = "setEmitable", at = @At("RETURN"), require = 0)
    private void ae2enhanced$onSetEmitable(IAEItemStack someItem, CallbackInfo ci) {
        Set<IAEItemStack> snap = this.ae2enhanced$emitterSnapshot;
        if (snap != null) {
            Set<IAEItemStack> copy = new HashSet<>(snap);
            copy.add(com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper.canon(someItem));
            this.ae2enhanced$emitterSnapshot = copy;
        }
    }

    /**
     * addCraftingOption 会在不重算的情况下向 craftingMethods 动态注册 medium
     * （接口上线/样板插入后）。若 memo 此前缓存了空兜底列表（ImmutableList.of()），
     * 新列表写入 craftingMethods 后 memo 仍返回空——task 发配队列恒空、永不发配。
     * memo 按实例身份键控，而 api 与 task 持有的 details 可能是内容相等但不同实例，
     * 无法精确移除，故整体清空（注册事件低频，重建成本可忽略）。
     */
    @Inject(method = "addCraftingOption", at = @At("HEAD"), require = 0)
    private void ae2enhanced$invalidateMediumsMemoOnOption(ICraftingMedium medium,
            ICraftingPatternDetails api, CallbackInfo ci) {
        this.ae2enhanced$mediumsMemo.clear();
    }

    @Shadow
    @Final
    private static ExecutorService CRAFTING_POOL;

    // ==================== Special Crafting Routing (Point A: Calculation) ====================

    /**
     * 计划器路由（计算请求分流）:按配置模式提交 {@link LpCraftingJob}
     * 并复用原生 CRAFTING_POOL 线程池;OFF/异常时直接放行,原生行为零改动.
     * <p>M7 起 LP 计划器为唯一增强路径（冷凝分层 + 单纯形）,原生仅作兜底.</p>
     */
    @Inject(method = "beginCraftingJob", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2enhanced$routeSpecialCalculation(World world, IGrid grid, IActionSource actionSrc,
            IAEItemStack slotItem, ICraftingCallback cb, CallbackInfoReturnable<Future<ICraftingJob>> cir) {
        try {
            if (world == null || grid == null || actionSrc == null || slotItem == null) {
                return;
            }
            // 功能开关关闭:计算/提交/执行零干预,完全放行原生(类注释承诺口径)
            if (!com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingRuntime.isEnabled()) {
                return;
            }
            // OFF 放行;DEFAULT 直接 LP;FALLBACK 原生先算、缺料时 LP 重算.
            AE2EnhancedConfig.DagPlannerMode mode = AE2EnhancedConfig.crafting.dagPlannerMode;
            if (mode == null || mode == AE2EnhancedConfig.DagPlannerMode.OFF) {
                return;
            }
            if (mode == AE2EnhancedConfig.DagPlannerMode.DEFAULT) {
                LpCraftingJob job = new LpCraftingJob(world, grid, actionSrc, slotItem, cb);
                cir.setReturnValue(CRAFTING_POOL.submit(job, job));
                return;
            }
            FallbackLpCraftingJob job = new FallbackLpCraftingJob(world, grid, actionSrc, slotItem, cb);
            cir.setReturnValue(CRAFTING_POOL.submit(job, job));
        } catch (Throwable t) {
            // 宁可漏判不可误判:路由层任何异常都放行原生
            AE2Enhanced.LOGGER.warn("[LP计划] 路由判定异常,放行原生计算: {}", t.toString());
        }
    }

    // ==================== Special Crafting Routing (Point B: Submission) ====================

    /**
     * 特殊配方路由（任务提交分流）:特殊计划（{@link SpecialPlanMarker} 标记）独占路由到
     * 超因果计算核心的虚拟 CPU 集群,不回落普通 CPU,防止语义错误的执行;
     * 普通计划直接放行（由原生与下方 fallback 处理）.
     */
    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2enhanced$routeSpecialJob(ICraftingJob job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource src,
            CallbackInfoReturnable<ICraftingLink> cir) {
        if (!com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingRuntime.isEnabled()) {
            return; // 功能开关关闭:提交零干预
        }
        if (!SpecialPlanMarker.isSpecial(job)) {
            return;
        }
        // 与原生相同的先序校验:模拟(缺料)计划一律拒绝
        if (job.isSimulation()) {
            cir.setReturnValue(null);
            return;
        }
        // 手动指定 CPU:只接受计算核心集群,否则拒绝
        if (target != null) {
            if (target instanceof CraftingCPUCluster && ae2enhanced$isCoreCluster((CraftingCPUCluster) target)) {
                CraftingCPUCluster cluster = (CraftingCPUCluster) target;
                if (cluster.isActive() && !cluster.isBusy()) {
                    cir.setReturnValue(cluster.submitJob(this.grid, job, src, requestingMachine));
                    return;
                }
            }
            cir.setReturnValue(null);
            return;
        }
        // 自动分配:仅从计算核心集群中选择
        for (TileComputationCore core : ae2enhanced$computationCores) {
            if (!core.isFormed()) {
                continue;
            }
            ICraftingLink link = core.trySpawnAndSubmitJob(this.grid, job, src, requestingMachine);
            if (link != null) {
                cir.setReturnValue(link);
                return;
            }
        }
        AE2Enhanced.LOGGER.warn("[特殊配方] 特殊计划无可用计算核心,提交失败: {}", job.getOutput());
        cir.setReturnValue(null);
    }

    @Unique
    private boolean ae2enhanced$isCoreCluster(CraftingCPUCluster cluster) {
        for (TileComputationCore core : ae2enhanced$computationCores) {
            List<CraftingCPUCluster> pool = core.getCpuPool();
            if (pool != null && pool.contains(cluster)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Node Lifecycle ====================

    @Inject(method = "addNode", at = @At("HEAD"))
    private void ae2enhanced$onAddNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        if (host instanceof TileAssemblyController) {
            ae2enhanced$assemblyHubCount++;
        }
        if (host instanceof TileComputationCore) {
            TileComputationCore core = (TileComputationCore) host;
            ae2enhanced$computationCores.add(core);
            if (core.isFormed()) {
                updateCPUClusters(new MENetworkCraftingCpuChange(node));
            }
        }
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void ae2enhanced$onRemoveNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        if (host instanceof TileAssemblyController && ae2enhanced$assemblyHubCount > 0) {
            ae2enhanced$assemblyHubCount--;
        }
        if (host instanceof TileComputationCore) {
            TileComputationCore core = (TileComputationCore) host;
            ae2enhanced$computationCores.remove(core);
            updateCPUClusters(new MENetworkCraftingCpuChange(node));
        }
    }

    // ==================== CPU Cluster Rebuild ====================

    @Inject(method = "updateCPUClusters()V", at = @At("TAIL"))
    private void ae2enhanced$injectComputationCores(CallbackInfo ci) {
        int injected = 0;
        for (TileComputationCore core : ae2enhanced$computationCores) {
            if (core.isFormed()) {
                List<CraftingCPUCluster> pool = core.getCpuPool();
                if (pool != null) {
                    for (CraftingCPUCluster cpu : pool) {
                        this.craftingCPUClusters.add(cpu);
                        injected++;
                        if (cpu.getLastCraftingLink() != null) {
                            this.addLink((CraftingLink) cpu.getLastCraftingLink());
                        }
                    }
                }
            }
        }
        // virtual CPUs injected silently
    }

    // ==================== Job Submission Fallback ====================

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true)
    private void ae2enhanced$submitJobFallback(ICraftingJob job, ICraftingRequester requestingMachine,
                                                ICraftingCPU target, boolean prioritizePower, IActionSource src,
                                                CallbackInfoReturnable<ICraftingLink> cir) {
        if (cir.getReturnValue() != null) {
            return; // original already succeeded
        }
        if (job == null || job.isSimulation()) {
            return;
        }
        if (target != null) {
            return; // explicit target was busy or invalid; do not spawn behind user's back
        }
        for (TileComputationCore core : ae2enhanced$computationCores) {
            if (!core.isFormed()) continue;
            ICraftingLink link = core.trySpawnAndSubmitJob(grid, job, src, requestingMachine);
            if (link != null) {
                cir.setReturnValue(link);
                return;
            }
        }
    }

    // ==================== hasCpu ====================

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (cpu instanceof CraftingCPUCluster) {
            for (TileComputationCore core : ae2enhanced$computationCores) {
                List<CraftingCPUCluster> pool = core.getCpuPool();
                if (pool != null && pool.contains(cpu)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
