package com.github.aeddddd.ae2enhanced.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 同一线程、同一调用范围内的合法传送白名单。 */
public final class TeleportWhitelist {
    private static final ThreadLocal<Map<UUID, Integer>> DEPTH = ThreadLocal.withInitial(HashMap::new);

    private TeleportWhitelist() {
    }

    public static Scope open(UUID playerId) {
        Map<UUID, Integer> depths = DEPTH.get();
        depths.put(playerId, depths.getOrDefault(playerId, 0) + 1);
        return new Scope(playerId);
    }

    public static boolean isAllowed(UUID playerId) {
        return DEPTH.get().getOrDefault(playerId, 0) > 0;
    }

    public static void discard(UUID playerId) {
        DEPTH.get().remove(playerId);
    }

    public static void clear() {
        DEPTH.remove();
    }

    public static final class Scope implements AutoCloseable {
        private final UUID playerId;
        private boolean closed;

        private Scope(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            Map<UUID, Integer> depths = DEPTH.get();
            int depth = depths.getOrDefault(playerId, 0);
            if (depth <= 1) depths.remove(playerId);
            else depths.put(playerId, depth - 1);
            if (depths.isEmpty()) DEPTH.remove();
        }
    }
}
