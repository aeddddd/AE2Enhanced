package com.github.aeddddd.ae2enhanced.observation;

/** 已完成合成计划的不可变观察快照。 */
public final class PlanObservation {

    public final String path;
    public final String output;
    public final long elapsedNanos;
    public final boolean simulation;
    public final String violation;

    public PlanObservation(String path, String output, long elapsedNanos, boolean simulation, String violation) {
        this.path = path;
        this.output = output;
        this.elapsedNanos = elapsedNanos;
        this.simulation = simulation;
        this.violation = violation;
    }
}
