package com.github.aeddddd.ae2enhanced.diag.perf;

import appeng.api.networking.IGridNode;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 每节点 tick 耗时注册表.
 *
 * <p><b>背景</b>：AE2-UEL 的 {@code TickTracker.LastFiveTicksTime} 是 final 常量 0
 * 且从不更新（该 fork 删除了原生计时），{@code TickManagerCache.getAvgNanoTime()}
 * 恒返回 0。本注册表通过 {@code MixinTickManagerCache} 包装
 * {@code TickManagerCache.onUpdateTick} 中的 {@code tickingRequest} 调用自行计时。</p>
 *
 * <p>统计口径：<b>按时间加权的指数滑动平均</b>（时间常数 2 秒）。
 * 按次加权的 EMA 在机器降载/休眠后收敛极慢甚至永不更新（慢速 tick 机器 5 秒才 tick
 * 一次），按时间加权可在数秒内反映负载变化；超过 {@link #STALE_NANOS} 未 tick 的
 * 节点视为无数据（读取返回 -1），避免陈旧值污染排行。</p>
 *
 * <p>键为弱引用，节点移除时另有 {@code removeNode} 钩子主动清理。</p>
 */
public final class NodeTimingRegistry {

    /** 半衰期意义上的时间常数：2 秒（alpha = 1 - e^(-dt/τ)） */
    private static final double TIME_CONSTANT_NANOS = 2.0e9;
    /** 超过 30 秒未 tick 视为无数据（覆盖最慢合法 tick 周期） */
    private static final long STALE_NANOS = 30_000_000_000L;

    private static final Map<IGridNode, NodeStat> STATS = new WeakHashMap<>();

    private NodeTimingRegistry() {
    }

    private static final class NodeStat {
        private double avgNanos = -1.0;
        private long lastUpdateNanos = -1L;

        void add(long nanos, long now) {
            if (avgNanos < 0.0 || lastUpdateNanos < 0L) {
                avgNanos = nanos;
            } else {
                double dt = (now - lastUpdateNanos) / 1.0e9;
                double alpha = 1.0 - Math.exp(-dt / (TIME_CONSTANT_NANOS / 1.0e9));
                avgNanos += alpha * (nanos - avgNanos);
            }
            lastUpdateNanos = now;
        }

        /** 当前有效平均值；过期返回 -1。 */
        long get(long now) {
            if (lastUpdateNanos < 0L || now - lastUpdateNanos > STALE_NANOS) {
                return -1L;
            }
            return (long) avgNanos;
        }
    }

    /** 记录一次节点 tick 耗时（由 Mixin 在服务端线程调用）。 */
    public static void record(IGridNode node, long nanos) {
        long now = System.nanoTime();
        synchronized (STATS) {
            NodeStat stat = STATS.get(node);
            if (stat == null) {
                stat = new NodeStat();
                STATS.put(node, stat);
            }
            stat.add(nanos, now);
        }
    }

    /** 节点平均 tick 耗时（纳秒）；无数据或已过期返回 -1（与原生 getAvgNanoTime 口径一致）。 */
    public static long getAvgNanos(IGridNode node) {
        long now = System.nanoTime();
        synchronized (STATS) {
            NodeStat stat = STATS.get(node);
            return stat == null ? -1L : stat.get(now);
        }
    }

    /** 节点移除时清理（TickManagerCache.removeNode 钩子调用）。 */
    public static void remove(IGridNode node) {
        synchronized (STATS) {
            STATS.remove(node);
        }
    }
}
