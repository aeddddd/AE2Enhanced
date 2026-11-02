package com.github.aeddddd.ae2enhanced.diag.plan;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.DiagEvents;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.observation.PlanObservation;

/** 消费业务侧已经分类、校验完毕的计划观察快照。 */
public final class PlanTracker {

    private PlanTracker() {
    }

    public static void onJobComputed(PlanObservation observation) {
        try {
            MetricsRegistry.counter("plan.path." + observation.path).increment();
            MetricsRegistry.timer("plan.computeMs." + observation.path).record(observation.elapsedNanos);
            if (!DiagSwitch.isEnabled(DiagSwitch.PLAN_VERIFY)) {
                return;
            }
            if (observation.simulation) {
                MetricsRegistry.counter("plan.verify.skippedSimulation").increment();
            } else if (observation.violation != null) {
                violation(observation);
            } else {
                MetricsRegistry.counter("plan.verify.passed").increment();
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E-Plan] 计划验证异常: {}", t.toString());
        }
    }

    private static void violation(PlanObservation observation) {
        MetricsRegistry.counter("plan.verify.violation").increment();
        String detail = observation.violation + ": path=" + observation.path + " output=" + observation.output;
        DiagEvents.error("plan", detail);
        AE2Enhanced.LOGGER.warn("[AE2E-Plan] {}", detail);
    }
}
