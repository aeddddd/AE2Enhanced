package com.github.aeddddd.ae2enhanced.ring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TeleportWhitelistTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @AfterEach
    void cleanup() {
        TeleportWhitelist.clear();
    }

    @Test
    void scopedWhitelistSupportsNestingAndExpiresImmediately() {
        try (TeleportWhitelist.Scope outer = TeleportWhitelist.open(PLAYER)) {
            assertThat(TeleportWhitelist.isAllowed(PLAYER)).isTrue();
            assertThat(TeleportWhitelist.isAllowed(OTHER)).isFalse();
            try (TeleportWhitelist.Scope inner = TeleportWhitelist.open(PLAYER)) {
                assertThat(TeleportWhitelist.isAllowed(PLAYER)).isTrue();
            }
            assertThat(TeleportWhitelist.isAllowed(PLAYER)).isTrue();
        }
        assertThat(TeleportWhitelist.isAllowed(PLAYER)).isFalse();
    }

    @Test
    void whitelistDoesNotLeakToAnotherThread() throws Exception {
        AtomicBoolean visible = new AtomicBoolean(true);
        try (TeleportWhitelist.Scope ignored = TeleportWhitelist.open(PLAYER)) {
            Thread thread = new Thread(() -> visible.set(TeleportWhitelist.isAllowed(PLAYER)));
            thread.start();
            thread.join();
        }
        assertThat(visible).isFalse();
    }
}
