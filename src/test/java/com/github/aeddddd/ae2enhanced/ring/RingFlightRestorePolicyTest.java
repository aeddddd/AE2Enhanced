package com.github.aeddddd.ae2enhanced.ring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingFlightRestorePolicyTest {
    @Test
    void exhaustedEnergyCannotReassertOrRestoreFlight() {
        assertThat(RingFlightRestorePolicy.mayRestore(false)).isFalse();
        assertThat(RingFlightRestorePolicy.shouldRestoreFlying(false, true, false)).isFalse();
    }

    @Test
    void paidFlightRestoresOnlyPreviousAirborneFlight() {
        assertThat(RingFlightRestorePolicy.mayRestore(true)).isTrue();
        assertThat(RingFlightRestorePolicy.shouldRestoreFlying(true, true, false)).isTrue();
        assertThat(RingFlightRestorePolicy.shouldRestoreFlying(true, false, false)).isFalse();
        assertThat(RingFlightRestorePolicy.shouldRestoreFlying(true, true, true)).isFalse();
    }
}
