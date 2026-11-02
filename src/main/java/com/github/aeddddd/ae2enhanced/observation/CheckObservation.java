package com.github.aeddddd.ae2enhanced.observation;

/** 主动健康检查的中性结果快照。 */
public final class CheckObservation {

    public final Level level;
    public final String key;
    public final Object[] args;
    private CheckObservation(Level level, String key, Object... args) {
        this.level = level;
        this.key = key;
        this.args = args;
    }

    public static CheckObservation ok(String key, Object... args) {
        return new CheckObservation(Level.OK, key, args);
    }

    public static CheckObservation warn(String key, Object... args) {
        return new CheckObservation(Level.WARN, key, args);
    }

    public static CheckObservation error(String key, Object... args) {
        return new CheckObservation(Level.ERROR, key, args);
    }

    public enum Level {
        OK, WARN, ERROR
    }
}
