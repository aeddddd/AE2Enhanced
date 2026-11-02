package com.github.aeddddd.ae2enhanced.dimension.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerAbilityStateTest {
    private static final PlayerAbilityState.Base BASE = new PlayerAbilityState.Base(false, 0.05f, 0.1f);
    private static final PlayerAbilityState.Modifier A = new PlayerAbilityState.Modifier(true, 0.2f, 0.2f);
    private static final PlayerAbilityState.Modifier B = new PlayerAbilityState.Modifier(false, 0.3f, 0.3f);
    private static final PlayerAbilityState.Modifier RING = new PlayerAbilityState.Modifier(true, 0.4f, 0.5f);

    private static PlayerAbilityState.Resolved resolve(PlayerAbilityState.Modifier dimension,
                                                         PlayerAbilityState.Modifier ring,
                                                         boolean privileged, boolean flying) {
        return PlayerAbilityState.resolve(BASE, dimension, ring, privileged, flying);
    }

    @Test
    void dimensionAndRingSurviveEveryRemovalOrder() {
        PlayerAbilityState.Resolved both = resolve(A, RING, false, true);
        assertThat(both.allowFlying).isTrue();
        assertThat(both.isFlying).isTrue();
        assertThat(both.flySpeed).isEqualTo(0.4f);
        assertThat(both.walkSpeed).isEqualTo(0.5f);

        PlayerAbilityState.Resolved dimensionOnly = resolve(A, null, false, true);
        assertThat(dimensionOnly.allowFlying).isTrue();
        assertThat(dimensionOnly.isFlying).isTrue();
        assertThat(dimensionOnly.flySpeed).isEqualTo(0.2f);
        assertThat(dimensionOnly.walkSpeed).isEqualTo(0.2f);

        PlayerAbilityState.Resolved ringOnly = resolve(null, RING, false, true);
        assertThat(ringOnly.allowFlying).isTrue();
        assertThat(ringOnly.flySpeed).isEqualTo(0.4f);
        assertThat(ringOnly.walkSpeed).isEqualTo(0.5f);

        PlayerAbilityState.Resolved base = resolve(null, null, false, true);
        assertThat(base.allowFlying).isFalse();
        assertThat(base.isFlying).isFalse();
        assertThat(base.flySpeed).isEqualTo(0.05f);
        assertThat(base.walkSpeed).isEqualTo(0.1f);
    }

    @Test
    void everyValidEnterLeaveAndRemovalOrderKeepsRemainingSource() {
        int[][] orders = {
                {0, 1, 2, 3}, {0, 1, 3, 2}, {0, 2, 1, 3},
                {1, 0, 2, 3}, {1, 0, 3, 2}, {1, 2, 0, 3}
        };
        for (int[] order : orders) {
            boolean dimension = false;
            boolean ring = false;
            for (int operation : order) {
                if (operation == 0) dimension = true;
                if (operation == 1) ring = true;
                if (operation == 2) dimension = false;
                if (operation == 3) ring = false;
                PlayerAbilityState.Resolved result = resolve(dimension ? A : null,
                        ring ? RING : null, false, true);
                assertThat(result.allowFlying).isEqualTo(dimension || ring);
                assertThat(result.flySpeed).isEqualTo(ring ? 0.4f : dimension ? 0.2f : 0.05f);
                assertThat(result.walkSpeed).isEqualTo(ring ? 0.5f : dimension ? 0.2f : 0.1f);
            }
        }
    }

    @Test
    void movingAToBToANeverRestoresBaseBetweenPersonalDimensions() {
        PlayerAbilityState.Resolved inA = resolve(A, RING, false, true);
        PlayerAbilityState.Resolved inB = resolve(B, RING, false, inA.isFlying);
        assertThat(inB.allowFlying).isTrue();
        assertThat(inB.flySpeed).isEqualTo(0.4f);
        PlayerAbilityState.Resolved backInA = resolve(A, RING, false, inB.isFlying);
        assertThat(backInA.allowFlying).isTrue();
        assertThat(backInA.isFlying).isTrue();
        assertThat(backInA.flySpeed).isEqualTo(0.4f);
    }

    @Test
    void visitorRulesAreSelectedByCurrentDimensionOwner() {
        PlayerAbilityState.Modifier visitorOwnerRules = new PlayerAbilityState.Modifier(true, 0.7f, 0.8f);
        PlayerAbilityState.Resolved visitor = resolve(visitorOwnerRules, null, false, false);
        assertThat(visitor.allowFlying).isTrue();
        assertThat(visitor.walkSpeed).isEqualTo(0.8f);
        PlayerAbilityState.Resolved updated = resolve(A, null, false, false);
        assertThat(updated.walkSpeed).isEqualTo(0.2f);
    }

    @Test
    void creativeAndSpectatorKeepVanillaFlightWithoutConsumingModifiers() {
        PlayerAbilityState.Resolved creative = resolve(null, RING, true, true);
        assertThat(creative.allowFlying).isTrue();
        assertThat(creative.isFlying).isTrue();
        assertThat(creative.flySpeed).isEqualTo(0.05f);
        assertThat(creative.walkSpeed).isEqualTo(0.1f);
        PlayerAbilityState.Resolved survivalAgain = resolve(A, null, false, creative.isFlying);
        assertThat(survivalAgain.allowFlying).isTrue();
        assertThat(survivalAgain.flySpeed).isEqualTo(0.2f);
    }

    @Test
    void baseFlightFromAnotherModIsPreserved() {
        PlayerAbilityState.Base externalFlight = new PlayerAbilityState.Base(true, 0.08f, 0.12f);
        PlayerAbilityState.Resolved result = PlayerAbilityState.resolve(externalFlight, null, null, false, true);
        assertThat(result.allowFlying).isTrue();
        assertThat(result.isFlying).isTrue();
        assertThat(result.flySpeed).isEqualTo(0.08f);
        assertThat(result.walkSpeed).isEqualTo(0.12f);
    }
}
