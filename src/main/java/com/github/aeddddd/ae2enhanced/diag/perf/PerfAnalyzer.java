package com.github.aeddddd.ae2enhanced.diag.perf;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.DimensionalCoord;
import appeng.hooks.TickHandler;
import appeng.me.Grid;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AE2 网格性能分析器.
 *
 * <p>数据源为 {@link NodeTimingRegistry}（每节点 tick 耗时 EMA，纳秒）——
 * AE2-UEL 删除了 TickTracker 的计时字段，原生 {@code getAvgNanoTime} 恒为 0，
 * 计时由 {@code MixinTickManagerCache} 包装 tickingRequest 调用完成。
 * 单次 {@link #scan(double)} 聚合出网格/机器类/慢节点三份排行，
 * 扫描自身耗时记入 {@code perf.analyzer.scan} 指标（开销自检）。</p>
 */
public final class PerfAnalyzer {

    private PerfAnalyzer() {
    }

    /** 单网格聚合。 */
    public static final class GridStat {
        public final Grid grid;
        public int nodes;
        public long totalAvgNanos;
        public double avgPowerUsage;
        public double storedPower;
        public int cpuTotal;
        public int cpuBusy;
        public ControllerState controllerState;

        GridStat(Grid grid) {
            this.grid = grid;
        }
    }

    /** 机器类聚合（跨全部网格）。 */
    public static final class MachineStat {
        public final String className;
        public int nodes;
        public long totalAvgNanos;

        MachineStat(String className) {
            this.className = className;
        }
    }

    /** 超阈值慢节点。 */
    public static final class SlowNode {
        public final String machine;
        public final String location;
        public final long avgNanos;

        SlowNode(String machine, String location, long avgNanos) {
            this.machine = machine;
            this.location = location;
            this.avgNanos = avgNanos;
        }

        public double avgMs() {
            return avgNanos / 1_000_000.0;
        }
    }

    /** 一次扫描的完整结果。 */
    public static final class ScanResult {
        public final long scanNanos;
        public final List<GridStat> grids;
        public final List<MachineStat> machines;
        public final List<SlowNode> slowNodes;

        ScanResult(long scanNanos, List<GridStat> grids, List<MachineStat> machines, List<SlowNode> slowNodes) {
            this.scanNanos = scanNanos;
            this.grids = grids;
            this.machines = machines;
            this.slowNodes = slowNodes;
        }

        public double scanMs() {
            return scanNanos / 1_000_000.0;
        }
    }

    /**
     * 全量扫描服务端所有网格.
     *
     * @param slowThresholdMs 慢节点阈值（毫秒），<= 0 表示不收集慢节点
     */
    public static ScanResult scan(double slowThresholdMs) {
        long start = System.nanoTime();
        List<GridStat> grids = new ArrayList<>();
        Map<String, MachineStat> machineMap = new HashMap<>();
        List<SlowNode> slowNodes = new ArrayList<>();

        for (Grid grid : TickHandler.INSTANCE.getGridList()) {
            if (grid.isEmpty()) {
                continue;
            }
            GridStat gs = new GridStat(grid);

            IPathingGrid pathing = grid.getCache(IPathingGrid.class);
            gs.controllerState = pathing.getControllerState();
            IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
            gs.avgPowerUsage = energy.getAvgPowerUsage();
            gs.storedPower = energy.getStoredPower();
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            for (ICraftingCPU cpu : crafting.getCpus()) {
                gs.cpuTotal++;
                if (cpu.isBusy()) {
                    gs.cpuBusy++;
                }
            }

            for (IGridNode node : grid.getNodes()) {
                gs.nodes++;
                long nanos = NodeTimingRegistry.getAvgNanos(node);
                if (nanos < 0) {
                    continue; // 无计时数据（非 tick 节点或休眠中）
                }
                gs.totalAvgNanos += nanos;

                String machineName = machineName(node);
                machineMap.computeIfAbsent(machineName, MachineStat::new);
                MachineStat ms = machineMap.get(machineName);
                ms.nodes++;
                ms.totalAvgNanos += nanos;

                if (slowThresholdMs > 0.0 && nanos / 1_000_000.0 >= slowThresholdMs) {
                    slowNodes.add(new SlowNode(machineName, locationOf(node), nanos));
                }
            }
            grids.add(gs);
        }

        grids.sort((a, b) -> Long.compare(b.totalAvgNanos, a.totalAvgNanos));
        List<MachineStat> machines = new ArrayList<>(machineMap.values());
        machines.sort((a, b) -> Long.compare(b.totalAvgNanos, a.totalAvgNanos));
        slowNodes.sort((a, b) -> Long.compare(b.avgNanos, a.avgNanos));

        long elapsed = System.nanoTime() - start;
        // 开销自检：扫描自身耗时纳入指标
        MetricsRegistry.timer("perf.analyzer.scan").record(elapsed);
        return new ScanResult(elapsed, grids, machines, slowNodes);
    }

    private static String machineName(IGridNode node) {
        try {
            IGridHost host = node.getMachine();
            return host != null ? host.getClass().getSimpleName() : "(node)";
        } catch (Throwable t) {
            return "(unknown)";
        }
    }

    private static String locationOf(IGridNode node) {
        try {
            DimensionalCoord loc = node.getGridBlock().getLocation();
            if (loc == null) {
                return "(no location)";
            }
            World w = loc.getWorld();
            String dim = (w != null && w.provider != null)
                    ? String.valueOf(w.provider.getDimension()) : "?";
            return loc.getPos() + " @dim" + dim;
        } catch (Throwable t) {
            return "(unknown)";
        }
    }

    /** 格式化纳秒为可读耗时（与 ToolDebugCard 口径一致）。 */
    public static String formatNanos(long nanos) {
        if (nanos < 100_000L) {
            return nanos + "ns";
        }
        return String.format(Locale.ROOT, "%.2fms", nanos / 1_000_000.0);
    }
}
