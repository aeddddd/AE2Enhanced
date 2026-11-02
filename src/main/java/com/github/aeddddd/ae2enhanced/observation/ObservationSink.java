package com.github.aeddddd.ae2enhanced.observation;

/** 业务观察事件的出站端口；实现只能消费快照，不得反向调用业务。 */
public interface ObservationSink {

    default void increment(String metric) {
    }

    default void recordTime(String metric, long elapsedNanos) {
    }

    default boolean shouldSampleTime(String metric) {
        return false;
    }

    default boolean isEnabled(String channel) {
        return false;
    }

    default void log(String level, String channel, String message, Object... arguments) {
    }

    default void event(String level, String system, String message) {
    }

    default void planComputed(PlanObservation observation) {
    }

    default void planSnapshot(PlanSnapshotObservation snapshot) {
    }
}
