package com.github.aeddddd.ae2enhanced.gridcrafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.craftingapi.SpecialCraftingPorts;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** CraftingGridCache 的快照、索引与特殊/虚拟 CPU 路由。 */
public final class CraftingGridService {

    private CraftingGridService() {
    }

    public static List<ICraftingMedium> mediums(CraftingGridExecutionContext context,
            ICraftingPatternDetails details, Supplier<List<ICraftingMedium>> original) {
        List<ICraftingMedium> result = context.mediumsMemo().get(details);
        if (result == null) {
            result = original.get();
            context.mediumsMemo().put(details, result);
        }
        return result;
    }

    public static Set<IAEItemStack> craftableKeys(
            Object2ObjectMap<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItems) {
        for (int attempt = 0; attempt < 16; attempt++) {
            try {
                return new HashSet<>(craftableItems.keySet());
            } catch (java.util.ConcurrentModificationException ignored) {
                Thread.yield();
            }
        }
        return new HashSet<>(craftableItems.keySet());
    }

    public static Map<IAEItemStack, List<ICraftingPatternDetails>> craftableSnapshot(
            CraftingGridExecutionContext context,
            Object2ObjectMap<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItems) {
        Map<IAEItemStack, List<ICraftingPatternDetails>> snapshot = context.craftableSnapshot();
        if (snapshot != null) {
            return snapshot;
        }
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                Map<IAEItemStack, List<ICraftingPatternDetails>> live = new HashMap<>();
                for (Map.Entry<IAEItemStack, ImmutableList<ICraftingPatternDetails>> entry
                        : craftableItems.entrySet()) {
                    live.computeIfAbsent(SpecialCraftingPorts.get().canon(entry.getKey()),
                            key -> new ArrayList<>()).addAll(entry.getValue());
                }
                if (!live.isEmpty()) {
                    return live;
                }
                snapshot = context.craftableSnapshot();
                if (snapshot != null) {
                    return snapshot;
                }
            } catch (java.util.ConcurrentModificationException ignored) {
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Collections.emptyMap();
    }

    public static boolean canEmit(CraftingGridExecutionContext context, Set<IAEItemStack> live,
            IAEItemStack canonKey) {
        Set<IAEItemStack> snapshot = context.emitterSnapshot();
        return snapshot != null ? snapshot.contains(canonKey) : live.contains(canonKey);
    }

    public static Object patternIndex(CraftingGridExecutionContext context, ICraftingGrid grid) {
        Object index = context.patternIndex();
        if (index == null) {
            synchronized (context) {
                index = context.patternIndex();
                if (index == null) {
                    boolean bootstrap = context.craftableSnapshot() == null;
                    index = SpecialCraftingPorts.get().buildPatternIndex(grid);
                    if (!bootstrap) {
                        context.patternIndex(index);
                    }
                }
            }
        }
        return index;
    }

    public static void recalculated(CraftingGridExecutionContext context,
            Object2ObjectMap<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItems,
            Set<IAEItemStack> emitableItems) {
        Map<IAEItemStack, List<ICraftingPatternDetails>> snapshot = new HashMap<>();
        for (Map.Entry<IAEItemStack, ImmutableList<ICraftingPatternDetails>> entry : craftableItems.entrySet()) {
            snapshot.computeIfAbsent(SpecialCraftingPorts.get().canon(entry.getKey()),
                    key -> new ArrayList<>()).addAll(entry.getValue());
        }
        Set<IAEItemStack> emitters = new HashSet<>();
        for (IAEItemStack item : emitableItems) {
            emitters.add(SpecialCraftingPorts.get().canon(item));
        }
        context.craftableSnapshot(snapshot);
        context.emitterSnapshot(emitters);
        synchronized (context) {
            context.patternIndex(null);
        }
        context.mediumsMemo().clear();
    }

    public static void emitableAdded(CraftingGridExecutionContext context, IAEItemStack item) {
        Set<IAEItemStack> snapshot = context.emitterSnapshot();
        if (snapshot != null) {
            Set<IAEItemStack> copy = new HashSet<>(snapshot);
            copy.add(SpecialCraftingPorts.get().canon(item));
            context.emitterSnapshot(copy);
        }
    }

    public static void craftingOptionAdded(CraftingGridExecutionContext context) {
        context.mediumsMemo().clear();
    }

    public static Future<ICraftingJob> beginJob(ExecutorService pool, World world, IGrid grid,
            IActionSource source, IAEItemStack slotItem, ICraftingCallback callback) {
        try {
            if (world == null || grid == null || source == null || slotItem == null
                    || !SpecialCraftingPorts.get().isEnabled()) {
                return null;
            }
            com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.DagPlannerMode mode = ServerConfig.current().crafting.dagPlannerMode;
            if (mode == null || mode == com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.DagPlannerMode.OFF) {
                return null;
            }
            boolean fallback = mode != com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.DagPlannerMode.DEFAULT;
            return SpecialCraftingPorts.get().beginJob(pool, world, grid, source, slotItem, callback, fallback);
        } catch (Throwable throwable) {
            AE2Enhanced.LOGGER.warn("[LP计划] 路由判定异常,放行原生计算: {}", throwable.toString());
            return null;
        }
    }

    public static RouteResult routeSpecial(CraftingGridExecutionContext context, IGrid grid, ICraftingJob job,
            ICraftingRequester requester, ICraftingCPU target, IActionSource source) {
        if (!SpecialCraftingPorts.get().isEnabled() || !SpecialCraftingPorts.get().isSpecial(job)) {
            return RouteResult.pass();
        }
        if (job.isSimulation()) {
            return RouteResult.handled(null);
        }
        if (target != null) {
            if (target instanceof CraftingCPUCluster && isCoreCluster(context, (CraftingCPUCluster) target)) {
                CraftingCPUCluster cluster = (CraftingCPUCluster) target;
                if (cluster.isActive() && !cluster.isBusy()) {
                    return RouteResult.handled(cluster.submitJob(grid, job, source, requester));
                }
            }
            return RouteResult.handled(null);
        }
        for (TileComputationCore core : context.computationCores()) {
            if (core.isFormed()) {
                ICraftingLink link = core.trySpawnAndSubmitJob(grid, job, source, requester);
                if (link != null) {
                    return RouteResult.handled(link);
                }
            }
        }
        AE2Enhanced.LOGGER.warn("[特殊配方] 特殊计划无可用计算核心,提交失败: {}", job.getOutput());
        return RouteResult.handled(null);
    }

    public static RouteResult submitFallback(CraftingGridExecutionContext context, IGrid grid,
            ICraftingLink current, ICraftingJob job, ICraftingRequester requester, ICraftingCPU target,
            IActionSource source) {
        if (current != null || job == null || job.isSimulation() || target != null) {
            return RouteResult.pass();
        }
        for (TileComputationCore core : context.computationCores()) {
            if (core.isFormed()) {
                ICraftingLink link = core.trySpawnAndSubmitJob(grid, job, source, requester);
                if (link != null) {
                    return RouteResult.handled(link);
                }
            }
        }
        return RouteResult.pass();
    }

    public static void addNode(CraftingGridExecutionContext context, IGridNode node, IGridHost host,
            Consumer<MENetworkCraftingCpuChange> update) {
        if (host instanceof TileAssemblyController) {
            context.assemblyHubAdded();
        }
        if (host instanceof TileComputationCore) {
            TileComputationCore core = (TileComputationCore) host;
            context.computationCores().add(core);
            if (core.isFormed()) {
                update.accept(new MENetworkCraftingCpuChange(node));
            }
        }
    }

    public static void removeNode(CraftingGridExecutionContext context, IGridNode node, IGridHost host,
            Consumer<MENetworkCraftingCpuChange> update) {
        if (host instanceof TileAssemblyController) {
            context.assemblyHubRemoved();
        }
        if (host instanceof TileComputationCore) {
            context.computationCores().remove(host);
            update.accept(new MENetworkCraftingCpuChange(node));
        }
    }

    public static void injectCores(CraftingGridExecutionContext context, Set<CraftingCPUCluster> cpus,
            Consumer<CraftingLink> addLink) {
        for (TileComputationCore core : context.computationCores()) {
            if (!core.isFormed()) {
                continue;
            }
            List<CraftingCPUCluster> pool = core.getCpuPool();
            if (pool != null) {
                for (CraftingCPUCluster cpu : pool) {
                    cpus.add(cpu);
                    if (cpu.getLastCraftingLink() != null) {
                        addLink.accept((CraftingLink) cpu.getLastCraftingLink());
                    }
                }
            }
        }
    }

    public static boolean hasCpu(CraftingGridExecutionContext context, ICraftingCPU cpu) {
        return cpu instanceof CraftingCPUCluster && isCoreCluster(context, (CraftingCPUCluster) cpu);
    }

    private static boolean isCoreCluster(CraftingGridExecutionContext context, CraftingCPUCluster cluster) {
        for (TileComputationCore core : context.computationCores()) {
            List<CraftingCPUCluster> pool = core.getCpuPool();
            if (pool != null && pool.contains(cluster)) {
                return true;
            }
        }
        return false;
    }

    public static final class RouteResult {
        public final boolean handled;
        public final ICraftingLink link;

        private RouteResult(boolean handled, ICraftingLink link) {
            this.handled = handled;
            this.link = link;
        }

        static RouteResult pass() {
            return new RouteResult(false, null);
        }

        static RouteResult handled(ICraftingLink link) {
            return new RouteResult(true, link);
        }
    }
}
