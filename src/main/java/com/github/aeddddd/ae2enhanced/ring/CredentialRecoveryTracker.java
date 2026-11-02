package com.github.aeddddd.ae2enhanced.ring;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 按玩家、凭证实例 ID 与掉落实体 UUID 限制 disarm 找回。 */
public final class CredentialRecoveryTracker {
    private static final long MAX_AGE_TICKS = 600L;
    private static final Map<UUID, Seen> LAST_SEEN = new HashMap<>();
    private static final Map<UUID, UUID> INTENTIONAL_DROPS = new HashMap<>();
    private CredentialRecoveryTracker() {}

    public static void observe(UUID playerId, String credentialId, long tick) {
        if (!credentialId.isEmpty()) LAST_SEEN.put(playerId, new Seen(credentialId, tick));
    }

    public static boolean canRecover(UUID playerId, UUID entityId, @Nullable String credentialId, long tick) {
        if (INTENTIONAL_DROPS.containsKey(entityId) || credentialId == null || credentialId.isEmpty()) return false;
        Seen seen = LAST_SEEN.get(playerId);
        return seen != null && tick >= seen.tick && tick - seen.tick <= MAX_AGE_TICKS
                && seen.credentialId.equals(credentialId);
    }

    public static void recovered(UUID playerId, String credentialId, long tick) {
        observe(playerId, credentialId, tick);
    }

    public static void markIntentionalDrop(UUID playerId, UUID entityId) {
        INTENTIONAL_DROPS.put(entityId, playerId);
        LAST_SEEN.remove(playerId);
    }

    public static void discard(UUID playerId) {
        LAST_SEEN.remove(playerId);
        INTENTIONAL_DROPS.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
    }

    public static void clear() {
        LAST_SEEN.clear();
        INTENTIONAL_DROPS.clear();
    }

    private static final class Seen {
        final String credentialId;
        final long tick;

        Seen(String credentialId, long tick) {
            this.credentialId = credentialId;
            this.tick = tick;
        }
    }
}
