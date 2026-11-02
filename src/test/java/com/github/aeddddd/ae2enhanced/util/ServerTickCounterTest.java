package com.github.aeddddd.ae2enhanced.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerTickCounterTest {
    @Test
    void cooldownUsesOneTickDomainAcrossDimensions() {
        long lastUse = 500;
        assertThat(ServerTickCounter.isCoolingDown(lastUse, 504, 5)).isTrue();
        assertThat(ServerTickCounter.isCoolingDown(lastUse, 505, 5)).isFalse();
    }

    @Test
    void missingAndLegacyFutureTimestampsDoNotLockTool() {
        assertThat(ServerTickCounter.isCoolingDown(ServerTickCounter.NEVER, 0, 20)).isFalse();
        assertThat(ServerTickCounter.isCoolingDown(9_000_000, 10, 20)).isFalse();
    }

    @Test
    void nonPositiveCooldownIsDisabled() {
        assertThat(ServerTickCounter.isCoolingDown(10, 10, 0)).isFalse();
        assertThat(ServerTickCounter.isCoolingDown(10, 10, -1)).isFalse();
    }
}
