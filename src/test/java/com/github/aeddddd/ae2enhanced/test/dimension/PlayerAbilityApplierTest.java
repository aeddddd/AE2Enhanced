package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlayerAbilityApplier} 单元测试.
 *
 * <p>ServerPlayer 用 Mockito mock,Abilities 使用真实对象以便观察状态变化.</p>
 */
class PlayerAbilityApplierTest {

    @BeforeAll
    static void bootstrap() {
        // ServerPlayer 类加载依赖原版注册表
        MinecraftTestBootstrap.bootstrap();
    }

    private static ServerPlayer mockPlayer(Abilities abilities, boolean creative, boolean spectator) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getAbilities()).thenReturn(abilities);
        when(player.isCreative()).thenReturn(creative);
        when(player.isSpectator()).thenReturn(spectator);
        return player;
    }

    // ---- clampMovementSpeed ----

    @Test
    void clampShouldHandleNaN() {
        assertThat(PlayerAbilityApplier.clampMovementSpeed(Float.NaN)).isEqualTo(0.05f);
    }

    @Test
    void clampShouldEnforceLowerBound() {
        assertThat(PlayerAbilityApplier.clampMovementSpeed(0.0f)).isEqualTo(0.05f);
        assertThat(PlayerAbilityApplier.clampMovementSpeed(0.049f)).isEqualTo(0.05f);
        assertThat(PlayerAbilityApplier.clampMovementSpeed(-1.0f)).isEqualTo(0.05f);
        assertThat(PlayerAbilityApplier.clampMovementSpeed(Float.NEGATIVE_INFINITY)).isEqualTo(0.05f);
    }

    @Test
    void clampShouldEnforceUpperBound() {
        assertThat(PlayerAbilityApplier.clampMovementSpeed(2.001f)).isEqualTo(2.0f);
        assertThat(PlayerAbilityApplier.clampMovementSpeed(100.0f)).isEqualTo(2.0f);
        assertThat(PlayerAbilityApplier.clampMovementSpeed(Float.POSITIVE_INFINITY)).isEqualTo(2.0f);
    }

    @Test
    void clampShouldPassThroughInRangeValues() {
        assertThat(PlayerAbilityApplier.clampMovementSpeed(0.05f)).isEqualTo(0.05f);
        assertThat(PlayerAbilityApplier.clampMovementSpeed(0.1f)).isEqualTo(0.1f);
        assertThat(PlayerAbilityApplier.clampMovementSpeed(2.0f)).isEqualTo(2.0f);
    }

    // ---- applyCapabilities ----

    @Test
    void applyShouldEnableFlightWhenRuleEnabled() {
        Abilities abilities = new Abilities();
        ServerPlayer player = mockPlayer(abilities, false, false);
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.flightEnabled = true;

        boolean changed = PlayerAbilityApplier.applyCapabilities(player, rules);

        assertThat(changed).isTrue();
        assertThat(abilities.mayfly).isTrue();
        verify(player).onUpdateAbilities();
    }

    @Test
    void applyShouldKeepFlightForCreativePlayer() {
        Abilities abilities = new Abilities();
        ServerPlayer player = mockPlayer(abilities, true, false);
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.flightEnabled = false;

        boolean changed = PlayerAbilityApplier.applyCapabilities(player, rules);

        // 创造模式玩家即使规则未开飞行也应保持 mayfly
        assertThat(changed).isTrue();
        assertThat(abilities.mayfly).isTrue();
    }

    @Test
    void applyShouldDisableFlightAndStopFlyingWhenRuleDisabled() {
        Abilities abilities = new Abilities();
        abilities.mayfly = true;
        abilities.flying = true;
        ServerPlayer player = mockPlayer(abilities, false, false);
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.flightEnabled = false;
        rules.movementSpeed = 0.1f;
        abilities.setWalkingSpeed(0.1f);
        abilities.setFlyingSpeed(0.1f);

        boolean changed = PlayerAbilityApplier.applyCapabilities(player, rules);

        assertThat(changed).isTrue();
        assertThat(abilities.mayfly).isFalse();
        assertThat(abilities.flying).isFalse();
    }

    @Test
    void applyShouldClampMovementSpeed() {
        Abilities abilities = new Abilities();
        ServerPlayer player = mockPlayer(abilities, false, false);
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.movementSpeed = 99.0f;

        PlayerAbilityApplier.applyCapabilities(player, rules);

        assertThat(abilities.getWalkingSpeed()).isEqualTo(2.0f);
        assertThat(abilities.getFlyingSpeed()).isEqualTo(2.0f);
    }

    @Test
    void applyShouldReturnFalseWhenNothingChanged() {
        Abilities abilities = new Abilities();
        abilities.mayfly = false;
        abilities.setWalkingSpeed(0.1f);
        abilities.setFlyingSpeed(0.1f);
        ServerPlayer player = mockPlayer(abilities, false, false);
        PersonalDimensionRules rules = new PersonalDimensionRules();

        boolean changed = PlayerAbilityApplier.applyCapabilities(player, rules);

        assertThat(changed).isFalse();
        verify(player, never()).onUpdateAbilities();
    }

    // ---- resetAbilities ----

    @Test
    void resetShouldRestoreDefaults() {
        Abilities abilities = new Abilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setWalkingSpeed(0.5f);
        abilities.setFlyingSpeed(0.3f);
        ServerPlayer player = mockPlayer(abilities, false, false);

        PlayerAbilityApplier.resetAbilities(player);

        assertThat(abilities.mayfly).isFalse();
        assertThat(abilities.flying).isFalse();
        assertThat(abilities.getWalkingSpeed()).isEqualTo(0.1f);
        assertThat(abilities.getFlyingSpeed()).isEqualTo(0.05f);
        verify(player).onUpdateAbilities();
    }

    @Test
    void resetShouldSkipCreativePlayer() {
        Abilities abilities = new Abilities();
        abilities.mayfly = true;
        ServerPlayer player = mockPlayer(abilities, true, false);

        PlayerAbilityApplier.resetAbilities(player);

        assertThat(abilities.mayfly).isTrue();
        verify(player, never()).onUpdateAbilities();
    }

    @Test
    void resetShouldSkipSpectatorPlayer() {
        Abilities abilities = new Abilities();
        abilities.mayfly = true;
        ServerPlayer player = mockPlayer(abilities, false, true);

        PlayerAbilityApplier.resetAbilities(player);

        assertThat(abilities.mayfly).isTrue();
        verify(player, never()).onUpdateAbilities();
    }

    @Test
    void resetShouldNotNotifyWhenAlreadyDefault() {
        Abilities abilities = new Abilities();
        abilities.mayfly = false;
        abilities.flying = false;
        abilities.setWalkingSpeed(0.1f);
        abilities.setFlyingSpeed(0.05f);
        ServerPlayer player = mockPlayer(abilities, false, false);

        PlayerAbilityApplier.resetAbilities(player);

        verify(player, never()).onUpdateAbilities();
    }

    // ---- tickNoFlightInertia ----

    @Test
    void tickShouldDoNothingWhenRuleDisabled() {
        Abilities abilities = new Abilities();
        abilities.flying = true;
        ServerPlayer player = mockPlayer(abilities, false, false);
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.noFlightInertia = false;

        PlayerAbilityApplier.tickNoFlightInertia(player, rules);

        verify(player, never()).getDeltaMovement();
        verify(player, never()).setDeltaMovement(org.mockito.ArgumentMatchers.any(Vec3.class));
    }

    @Test
    void tickShouldZeroHorizontalMotionWhenFlyingWithoutInput() {
        Abilities abilities = new Abilities();
        abilities.flying = true;
        ServerPlayer player = mockPlayer(abilities, false, false);
        when(player.getDeltaMovement()).thenReturn(new Vec3(1.0, -0.5, 2.0));
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.noFlightInertia = true;

        PlayerAbilityApplier.tickNoFlightInertia(player, rules);

        ArgumentCaptor<Vec3> captor = ArgumentCaptor.forClass(Vec3.class);
        verify(player).setDeltaMovement(captor.capture());
        assertThat(captor.getValue().x).isZero();
        assertThat(captor.getValue().y).isEqualTo(-0.5);
        assertThat(captor.getValue().z).isZero();
    }

    @Test
    void tickShouldKeepMotionWhenPlayerHasMovementInput() {
        Abilities abilities = new Abilities();
        abilities.flying = true;
        ServerPlayer player = mockPlayer(abilities, false, false);
        player.zza = 1.0f;
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.noFlightInertia = true;

        PlayerAbilityApplier.tickNoFlightInertia(player, rules);

        verify(player, never()).setDeltaMovement(org.mockito.ArgumentMatchers.any(Vec3.class));
    }

    @Test
    void tickShouldDoNothingWhenNotFlying() {
        Abilities abilities = new Abilities();
        abilities.flying = false;
        ServerPlayer player = mockPlayer(abilities, false, false);
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.noFlightInertia = true;

        PlayerAbilityApplier.tickNoFlightInertia(player, rules);

        verify(player, never()).setDeltaMovement(org.mockito.ArgumentMatchers.any(Vec3.class));
    }
}
