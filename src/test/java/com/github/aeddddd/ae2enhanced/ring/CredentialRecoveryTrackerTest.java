package com.github.aeddddd.ae2enhanced.ring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialRecoveryTrackerTest {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENTITY = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void cleanup() {
        CredentialRecoveryTracker.clear();
    }

    @Test
    void onlyMostRecentlyHeldCredentialIdentityCanBeRecovered() {
        CredentialRecoveryTracker.observe(OWNER, "credential-a", 100);
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, ENTITY, "credential-a", 101)).isTrue();
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, ENTITY, "credential-b", 101)).isFalse();
        assertThat(CredentialRecoveryTracker.canRecover(OTHER, ENTITY, "credential-a", 101)).isFalse();
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, ENTITY, null, 101)).isFalse();
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, ENTITY, "credential-a", 701)).isFalse();
    }

    @Test
    void intentionalDropIsExcludedAndClearsRecoveryTracking() {
        CredentialRecoveryTracker.observe(OWNER, "credential-a", 100);
        CredentialRecoveryTracker.markIntentionalDrop(OWNER, ENTITY);
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, ENTITY, "credential-a", 101)).isFalse();
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, UUID.randomUUID(), "credential-a", 101)).isFalse();
    }

    @Test
    void logoutAndServerClearDiscardBothSidesOfIdentity() {
        CredentialRecoveryTracker.observe(OWNER, "credential-a", 100);
        CredentialRecoveryTracker.discard(OWNER);
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, ENTITY, "credential-a", 101)).isFalse();
        CredentialRecoveryTracker.observe(OWNER, "credential-a", 100);
        CredentialRecoveryTracker.clear();
        assertThat(CredentialRecoveryTracker.canRecover(OWNER, ENTITY, "credential-a", 101)).isFalse();
    }
}
