package com.github.aeddddd.ae2enhanced.diag.metrics;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标注册中心：按名称统一管理 Counter / Gauge / Timer.
 *
 * <p>命名约定：{@code <system>.<metric>}，如 {@code crafting.nativeCalcAbort}、
 * {@code grid.tickTime}。获取即创建，重复获取返回同一实例。</p>
 */
public final class MetricsRegistry {

    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Gauge> GAUGES = new ConcurrentHashMap<>();
    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();

    private MetricsRegistry() {
    }

    public static Counter counter(String name) {
        return COUNTERS.computeIfAbsent(name, Counter::new);
    }

    public static Timer timer(String name) {
        return TIMERS.computeIfAbsent(name, Timer::new);
    }

    public static Collection<Counter> counters() {
        return Collections.unmodifiableCollection(COUNTERS.values());
    }

    public static Collection<Gauge> gauges() {
        return Collections.unmodifiableCollection(GAUGES.values());
    }

    public static Collection<Timer> timers() {
        return Collections.unmodifiableCollection(TIMERS.values());
    }
}
