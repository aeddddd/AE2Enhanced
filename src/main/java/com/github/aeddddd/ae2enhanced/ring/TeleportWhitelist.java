package com.github.aeddddd.ae2enhanced.ring;

import java.util.UUID;

/** 指环调用方兼容壳；实际白名单状态属于通用 util 端口。 */
public final class TeleportWhitelist {

    private TeleportWhitelist() {
    }

    public static Scope open(UUID playerId) {
        return new Scope(com.github.aeddddd.ae2enhanced.util.TeleportWhitelist.open(playerId));
    }

    public static boolean isAllowed(UUID playerId) {
        return com.github.aeddddd.ae2enhanced.util.TeleportWhitelist.isAllowed(playerId);
    }

    public static void discard(UUID playerId) {
        com.github.aeddddd.ae2enhanced.util.TeleportWhitelist.discard(playerId);
    }

    public static void clear() {
        com.github.aeddddd.ae2enhanced.util.TeleportWhitelist.clear();
    }

    public static final class Scope implements AutoCloseable {
        private final com.github.aeddddd.ae2enhanced.util.TeleportWhitelist.Scope delegate;

        private Scope(com.github.aeddddd.ae2enhanced.util.TeleportWhitelist.Scope delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
