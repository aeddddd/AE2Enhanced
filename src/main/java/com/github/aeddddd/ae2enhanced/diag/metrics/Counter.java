package com.github.aeddddd.ae2enhanced.diag.metrics;

import java.util.concurrent.atomic.LongAdder;

/**
 * 单调计数器（线程安全，热点路径使用 LongAdder 降低竞争）.
 */
public final class Counter {

    private final String name;
    private final LongAdder count = new LongAdder();

    Counter(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void increment() {
        count.increment();
    }

    public long get() {
        return count.sum();
    }
}
