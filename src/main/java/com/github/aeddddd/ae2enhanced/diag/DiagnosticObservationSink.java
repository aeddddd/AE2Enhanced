package com.github.aeddddd.ae2enhanced.diag;

import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.diag.plan.PlanTracker;
import com.github.aeddddd.ae2enhanced.diag.plansnapshot.PlanSnapshot;
import com.github.aeddddd.ae2enhanced.observation.ObservationSink;
import com.github.aeddddd.ae2enhanced.observation.PlanObservation;
import com.github.aeddddd.ae2enhanced.observation.PlanSnapshotObservation;

/** 将通用观察事件投影到现有诊断注册表。 */
public final class DiagnosticObservationSink implements ObservationSink {

    @Override
    public void increment(String metric) {
        MetricsRegistry.counter(metric).increment();
    }

    @Override
    public void recordTime(String metric, long elapsedNanos) {
        MetricsRegistry.timer(metric).record(elapsedNanos);
    }

    @Override
    public boolean shouldSampleTime(String metric) {
        return MetricsRegistry.timer(metric).shouldSample();
    }

    @Override
    public boolean isEnabled(String channel) {
        return DiagSwitch.isEnabled(channel);
    }

    @Override
    public void log(String level, String channel, String message, Object... arguments) {
        if ("warn".equals(level)) {
            DiagLog.warn(channel, message, arguments);
        } else {
            DiagLog.info(channel, message, arguments);
        }
    }

    @Override
    public void event(String level, String system, String message) {
        DiagEvents.record(DiagEvents.Level.valueOf(level), system, message);
    }

    @Override
    public void planComputed(PlanObservation observation) {
        PlanTracker.onJobComputed(observation);
    }

    @Override
    public void planSnapshot(PlanSnapshotObservation snapshot) {
        PlanSnapshot.dump(snapshot);
    }
}
