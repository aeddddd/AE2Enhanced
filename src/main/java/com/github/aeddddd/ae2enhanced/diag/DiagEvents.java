package com.github.aeddddd.ae2enhanced.diag;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 异常事件流：记录系统运行中的异常/预警事件（预算超时、检查失败等），
 * 供 {@code /ae2e diag report} 汇总输出.
 *
 * <p>固定容量环形缓冲（默认 256 条），只保留最近事件，无磁盘 IO。</p>
 */
public final class DiagEvents {

    public enum Level {
        INFO, WARN, ERROR
    }

    public static final class Event {
        public final long timestamp;
        public final Level level;
        public final String system;
        public final String message;

        Event(long timestamp, Level level, String system, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.system = system;
            this.message = message;
        }

        @Override
        public String toString() {
            return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp))
                    + "] [" + level + "] [" + system + "] " + message;
        }
    }

    private static final int CAPACITY = 256;
    private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>(CAPACITY);

    private DiagEvents() {
    }

    public static void record(Level level, String system, String message) {
        synchronized (EVENTS) {
            if (EVENTS.size() >= CAPACITY) {
                EVENTS.pollFirst();
            }
            EVENTS.addLast(new Event(System.currentTimeMillis(), level, system, message));
        }
    }

    public static void warn(String system, String message) {
        record(Level.WARN, system, message);
    }

    public static void error(String system, String message) {
        record(Level.ERROR, system, message);
    }

    /** 最近 n 条事件（新→旧顺序）。 */
    public static List<Event> latest(int n) {
        synchronized (EVENTS) {
            List<Event> all = new ArrayList<>(EVENTS);
            int from = Math.max(0, all.size() - n);
            List<Event> tail = all.subList(from, all.size());
            List<Event> result = new ArrayList<>(tail.size());
            for (int i = tail.size() - 1; i >= 0; i--) {
                result.add(tail.get(i));
            }
            return result;
        }
    }
}
