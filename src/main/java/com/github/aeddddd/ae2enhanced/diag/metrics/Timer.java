package com.github.aeddddd.ae2enhanced.diag.metrics;

import java.util.Arrays;
import java.util.Locale;

/**
 * 耗时指标：nanoTime 采样 + 固定容量环形缓冲，支持 p50/p95/p99 百分位.
 *
 * <p>环形缓冲只保留最近 {@code capacity} 次采样用于百分位估算；
 * 总次数/总耗时/min/max 为全生命周期精确值。</p>
 */
public final class Timer {

    private static final int DEFAULT_CAPACITY = 1024;
    /** 采样率：每 20 次调用采样 1 次（观测开销控制） */
    private static final int SAMPLE_EVERY = 20;

    private final String name;
    private int sampleCounter = 0;
    private final long[] ring;
    private int pos = 0;
    private int size = 0;
    private long count = 0;
    private long totalNanos = 0;
    private long minNanos = Long.MAX_VALUE;
    private long maxNanos = 0;

    Timer(String name) {
        this(name, DEFAULT_CAPACITY);
    }

    Timer(String name, int capacity) {
        this.name = name;
        this.ring = new long[capacity];
    }

    public String name() {
        return name;
    }

    public void record(long nanos) {
        if (nanos < 0) {
            return;
        }
        synchronized (this) {
            ring[pos] = nanos;
            pos = (pos + 1) % ring.length;
            if (size < ring.length) {
                size++;
            }
            count++;
            totalNanos += nanos;
            if (nanos < minNanos) minNanos = nanos;
            if (nanos > maxNanos) maxNanos = nanos;
        }
    }

    /**
     * 采样判定：每 {@value #SAMPLE_EVERY} 次调用返回一次 true.
     * 调用方在返回 true 时记录 nanoTime 起点，结束后调用 {@link #record(long)}.
     * 非线程安全（埋点均为服务端线程路径，竞态仅影响采样相位，不影响正确性）.
     */
    public boolean shouldSample() {
        return (sampleCounter++ % SAMPLE_EVERY) == 0;
    }

    public synchronized Snapshot snapshot() {
        long[] samples = new long[size];
        System.arraycopy(ring, 0, samples, 0, size);
        Arrays.sort(samples);
        return new Snapshot(
                count,
                count == 0 ? 0.0 : totalNanos / (double) count / 1_000_000.0,
                count == 0 ? 0.0 : minNanos / 1_000_000.0,
                maxNanos / 1_000_000.0,
                percentileMs(samples, 0.50),
                percentileMs(samples, 0.95),
                percentileMs(samples, 0.99));
    }

    private static double percentileMs(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0.0;
        }
        int idx = (int) Math.ceil(p * sorted.length) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.length) idx = sorted.length - 1;
        return sorted[idx] / 1_000_000.0;
    }

    /** 不可变快照，毫秒单位。 */
    public static final class Snapshot {
        public final long count;
        public final double avgMs;
        public final double minMs;
        public final double maxMs;
        public final double p50Ms;
        public final double p95Ms;
        public final double p99Ms;

        Snapshot(long count, double avgMs, double minMs, double maxMs,
                 double p50Ms, double p95Ms, double p99Ms) {
            this.count = count;
            this.avgMs = avgMs;
            this.minMs = minMs;
            this.maxMs = maxMs;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "count=%d avg=%.3fms min=%.3fms max=%.3fms p50=%.3fms p95=%.3fms p99=%.3fms",
                    count, avgMs, minMs, maxMs, p50Ms, p95Ms, p99Ms);
        }
    }
}
