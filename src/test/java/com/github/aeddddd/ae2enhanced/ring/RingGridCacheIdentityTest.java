package com.github.aeddddd.ae2enhanced.ring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingGridCacheIdentityTest {
    @Test
    void sameStackAndBindingCanReuseButReplacementOrRebindingCannot() {
        Object first = new Object();
        Object second = new Object();
        RingGridCacheIdentity identity = new RingGridCacheIdentity(first, "123");

        assertThat(identity.matches(first, "123")).isTrue();
        assertThat(identity.matches(second, "123")).isFalse();
        assertThat(identity.matches(first, "456")).isFalse();
        assertThat(identity.matches(first, "")).isFalse();
    }
}
