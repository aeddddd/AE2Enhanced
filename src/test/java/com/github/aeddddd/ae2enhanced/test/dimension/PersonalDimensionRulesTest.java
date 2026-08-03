package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PersonalDimensionRules} 单元测试.
 */
class PersonalDimensionRulesTest {

    @Test
    void shouldHaveExpectedDefaults() {
        PersonalDimensionRules rules = new PersonalDimensionRules();

        assertThat(rules.disableMobSpawning).isFalse();
        assertThat(rules.lockWeather).isFalse();
        assertThat(rules.lockTime).isFalse();
        assertThat(rules.daylightCycle).isTrue();
        assertThat(rules.timeValue).isEqualTo(6000L);
        assertThat(rules.flightEnabled).isFalse();
        assertThat(rules.movementSpeed).isEqualTo(0.1f);
        assertThat(rules.noFlightInertia).isFalse();
    }

    @Test
    void nbtRoundTripShouldPreserveAllFields() {
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = true;
        rules.lockWeather = true;
        rules.lockTime = true;
        rules.daylightCycle = false;
        rules.timeValue = 123456789L;
        rules.flightEnabled = true;
        rules.movementSpeed = 0.37f;
        rules.noFlightInertia = true;

        PersonalDimensionRules restored = new PersonalDimensionRules();
        restored.readFromNBT(rules.writeToNBT());

        assertThat(restored.disableMobSpawning).isTrue();
        assertThat(restored.lockWeather).isTrue();
        assertThat(restored.lockTime).isTrue();
        assertThat(restored.daylightCycle).isFalse();
        assertThat(restored.timeValue).isEqualTo(123456789L);
        assertThat(restored.flightEnabled).isTrue();
        assertThat(restored.movementSpeed).isEqualTo(0.37f);
        assertThat(restored.noFlightInertia).isTrue();
    }

    @Test
    void readFromEmptyTagShouldResetToNbtDefaults() {
        // 缺失键按 NBT 缺省值(false/0)读取,而不是保留原值
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.readFromNBT(new CompoundTag());

        assertThat(rules.daylightCycle).isFalse();
        assertThat(rules.timeValue).isZero();
        assertThat(rules.movementSpeed).isZero();
    }

    @Test
    void copyShouldProduceIndependentEqualValueCopy() {
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = true;
        rules.timeValue = 42L;
        rules.movementSpeed = 1.5f;

        PersonalDimensionRules copy = rules.copy();

        assertThat(copy).isNotSameAs(rules);
        assertThat(copy.disableMobSpawning).isTrue();
        assertThat(copy.timeValue).isEqualTo(42L);
        assertThat(copy.movementSpeed).isEqualTo(1.5f);

        // 修改副本不影响原对象
        copy.timeValue = -1L;
        copy.flightEnabled = true;
        assertThat(rules.timeValue).isEqualTo(42L);
        assertThat(rules.flightEnabled).isFalse();
    }
}
