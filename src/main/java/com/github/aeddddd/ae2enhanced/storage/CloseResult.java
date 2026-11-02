package com.github.aeddddd.ae2enhanced.storage;

import java.util.Properties;

/** 超维度仓储一次关闭尝试各阶段的实际结果。 */
public final class CloseResult {
    private final Status initialLoad;
    private final Status pending;
    private final Status wal;
    private final Status checkpoint;
    private final Status truncation;
    public CloseResult(Status initialLoad, Status pending, Status wal, Status checkpoint, Status truncation) {
        this.initialLoad = initialLoad;
        this.pending = pending;
        this.wal = wal;
        this.checkpoint = checkpoint;
        this.truncation = truncation;
    }

    public static CloseResult fromProperties(Properties properties) {
        return new CloseResult(
            Status.valueOf(properties.getProperty("initialLoad")),
            Status.valueOf(properties.getProperty("pending")),
            Status.valueOf(properties.getProperty("wal")),
            Status.valueOf(properties.getProperty("checkpoint")),
            Status.valueOf(properties.getProperty("truncation")));
    }

    public Status getInitialLoad() {
        return initialLoad;
    }

    public Status getPending() {
        return pending;
    }

    public Status getWal() {
        return wal;
    }

    public Status getCheckpoint() {
        return checkpoint;
    }

    public Status getTruncation() {
        return truncation;
    }

    public boolean isSuccess() {
        return initialLoad == Status.SUCCESS && pending == Status.SUCCESS && wal == Status.SUCCESS
            && checkpoint == Status.SUCCESS && truncation == Status.SUCCESS;
    }

    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("initialLoad", initialLoad.name());
        properties.setProperty("pending", pending.name());
        properties.setProperty("wal", wal.name());
        properties.setProperty("checkpoint", checkpoint.name());
        properties.setProperty("truncation", truncation.name());
        return properties;
    }

    @Override
    public String toString() {
        return "CloseResult{" +
            "initialLoad=" + initialLoad +
            ", pending=" + pending +
            ", wal=" + wal +
            ", checkpoint=" + checkpoint +
            ", truncation=" + truncation +
            '}';
    }

    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT
    }
}
