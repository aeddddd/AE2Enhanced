package com.github.aeddddd.ae2enhanced.observation;

import java.util.ServiceLoader;

/** 业务侧统一观察入口；诊断实现经 Java SPI 装配，缺席时全部操作无效果。 */
public final class Observations {

    private static final ObservationSink SINK = loadSink();

    private Observations() {
    }

    private static ObservationSink loadSink() {
        try {
            for (ObservationSink sink : ServiceLoader.load(ObservationSink.class, Observations.class.getClassLoader())) {
                if (sink != null) {
                    return sink;
                }
            }
        } catch (Throwable ignored) {
        }
        return new ObservationSink() {
        };
    }

    public static Counter counter(String metric) {
        return new Counter(metric);
    }

    public static Timer timer(String metric) {
        return new Timer(metric);
    }

    public static void increment(String metric) {
        try {
            SINK.increment(metric);
        } catch (Throwable ignored) {
        }
    }

    public static void recordTime(String metric, long elapsedNanos) {
        try {
            SINK.recordTime(metric, elapsedNanos);
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldSampleTime(String metric) {
        try {
            return SINK.shouldSampleTime(metric);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isEnabled(String channel) {
        try {
            return SINK.isEnabled(channel);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void log(String level, String channel, String message, Object... arguments) {
        try {
            SINK.log(level, channel, message, arguments);
        } catch (Throwable ignored) {
        }
    }

    public static void event(String level, String system, String message) {
        try {
            SINK.event(level, system, message);
        } catch (Throwable ignored) {
        }
    }

    public static void planComputed(PlanObservation observation) {
        try {
            SINK.planComputed(observation);
        } catch (Throwable ignored) {
        }
    }

    public static void planSnapshot(PlanSnapshotObservation snapshot) {
        try {
            SINK.planSnapshot(snapshot);
        } catch (Throwable ignored) {
        }
    }

    public static final class Counter {
        private final String metric;

        private Counter(String metric) {
            this.metric = metric;
        }

        public void increment() {
            Observations.increment(metric);
        }
    }

    public static final class Timer {
        private final String metric;

        private Timer(String metric) {
            this.metric = metric;
        }

        public void record(long elapsedNanos) {
            Observations.recordTime(metric, elapsedNanos);
        }

        public boolean shouldSample() {
            return Observations.shouldSampleTime(metric);
        }
    }
}
