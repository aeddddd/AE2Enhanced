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
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingJob;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingRuntime;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialLog;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialRecipeDetector;

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
        return new HashSet<>(this.craftableItems.keySet());
    }

    @Override
    public NetworkPatternIndex ae2enhanced$patternIndex() {
        NetworkPatternIndex idx = this.ae2enhanced$patternIndex;
        if (idx == null) {
            synchronized (this) {
                idx = this.ae2enhanced$patternIndex;
                if (idx == null) {
                    idx = NetworkPatternIndex.build((ICraftingGrid) (Object) this);
                    this.ae2enhanced$patternIndex = idx;
                }
            }
        }
        return idx;
    }

    /** 样板集重建后索引失效(下一次访问惰性重建). */
    @Inject(method = "recalculateCraftingPatterns", at = @At("TAIL"), require = 0)
    private void ae2enhanced$invalidatePatternIndex(CallbackInfo ci) {
        this.ae2enhanced$patternIndex = null;
        // craftingMethods 已重建,mediums memo 同步失效
        this.ae2enhanced$mediumsMemo.clear();
    }

    @Shadow
    @Final
    private static ExecutorService CRAFTING_POOL;

    // ==================== Special Crafting Routing (Point A: Calculation) ====================

    /**
     * 特殊配方路由（计算请求分流）:detector 命中才提交 {@link SpecialCraftingJob}
     * 并复用原生 CRAFTING_POOL 线程池;未命中/异常时直接放行,原生行为零改动.
     */
    @Inject(method = "beginCraftingJob", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2enhanced$routeSpecialCalculation(World world, IGrid grid, IActionSource actionSrc,
            IAEItemStack slotItem, ICraftingCallback cb, CallbackInfoReturnable<Future<ICraftingJob>> cir) {
        try {
            if (world == null || grid == null || actionSrc == null || slotItem == null) {
                return;
            }
            ICraftingGrid cc = grid.getCache(ICraftingGrid.class);
            // 特殊配方路由:detector 命中才提交专用求解器(O(1) 闭式)
            if (SpecialCraftingRuntime.isEnabled()
                    && SpecialRecipeDetector.mayInvolveSpecialRecipes(cc, slotItem, world)) {
                SpecialLog.info("[特殊配方] 路由命中,提交专用求解器: {}", slotItem);
                SpecialCraftingJob job = new SpecialCraftingJob(world, grid, actionSrc, slotItem, cb);
                cir.setReturnValue(CRAFTING_POOL.submit(job, job));
                return;
            }
            // DAG 引擎路由(阶段 4):其余非特殊请求按配置模式接线——
            // OFF 放行;DEFAULT 直接 DAG;FALLBACK 原生先算、缺料时 DAG 重算.
            AE2EnhancedConfig.DagPlannerMode mode = AE2EnhancedConfig.crafting.dagPlannerMode;
            if (mode == null || mode == AE2EnhancedConfig.DagPlannerMode.OFF) {
                return;
            }
            if (mode == AE2EnhancedConfig.DagPlannerMode.DEFAULT) {
                com.github.aeddddd.ae2enhanced.craftingplan.dag.DagCraftingJob job =
                        new com.github.aeddddd.ae2enhanced.craftingplan.dag.DagCraftingJob(world, grid,
                                actionSrc, slotItem, cb);
                cir.setReturnValue(CRAFTING_POOL.submit(job, job));
                return;
            }
            com.github.aeddddd.ae2enhanced.craftingplan.dag.FallbackDagCraftingJob job =
                    new com.github.aeddddd.ae2enhanced.craftingplan.dag.FallbackDagCraftingJob(world, grid,
                            actionSrc, slotItem, cb);
            cir.setReturnValue(CRAFTING_POOL.submit(job, job));
        } catch (Throwable t) {
            // 宁可漏判不可误判:路由层任何异常都放行原生
            AE2Enhanced.LOGGER.warn("[特殊配方] 路由判定异常,放行原生计算: {}", t.toString());
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
