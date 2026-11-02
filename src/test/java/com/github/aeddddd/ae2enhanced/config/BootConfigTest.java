package com.github.aeddddd.ae2enhanced.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootConfigTest {

    private static void assertThatconfigDoesNotDisableUnrelatedMods(BootConfig config) {
        assertThat(config.integrationEnabled("thaumcraft")).isTrue();
        assertThat(config.integrationEnabled("botania")).isTrue();
        assertThat(config.integrationEnabled("astralsorcery")).isTrue();
        assertThat(config.integrationEnabled("cellterminal")).isTrue();
        assertThat(config.integrationEnabled("tii")).isTrue();
        assertThat(config.integrationEnabled("crafttweaker")).isTrue();
    }

    @Test
    void defaultsPreserveExistingAutoDetection() {
        BootConfig config = new BootConfig(true, true, true, true, true);
        assertThat(config.integrationEnabled("mekanism")).isTrue();
        assertThat(config.integrationEnabled("ftblib")).isTrue();
        assertThat(config.channelEnabled("energy")).isTrue();
        assertThat(config.externalChannelAllowed("energy")).isTrue();
        assertThat(config.internalChannelAllowed("mana")).isTrue();
        assertThat(config.externalChannelRequired("energy")).isFalse();
    }

    @Test
    void integrationAndChannelSwitchesAreIndependent() {
        BootConfig.Integrations integrations = new BootConfig.Integrations(
                false, true, false, true, false, true, false, true, true, false, true);
        BootConfig.StorageChannels channels = new BootConfig.StorageChannels(
                false, true, false, false, true, false,
                BootConfig.ChannelProvider.EXTERNAL, BootConfig.ChannelProvider.INTERNAL);
        BootConfig config = new BootConfig(true, true, true, true, true, integrations, channels);

        assertThat(config.integrationEnabled("mekeng")).isFalse();
        assertThat(config.integrationEnabled("fluxapplied")).isFalse();
        assertThat(config.integrationEnabled("botaniaapplie")).isFalse();
        assertThatconfigDoesNotDisableUnrelatedMods(config);
        assertThat(config.channelEnabled("energy")).isFalse();
        assertThat(config.channelEnabled("starlight")).isFalse();
        assertThat(config.channelEnabled("gas")).isFalse();
        assertThat(config.channelEnabled("mana")).isTrue();
        assertThat(config.externalChannelAllowed("energy")).isFalse();
        assertThat(config.internalChannelAllowed("energy")).isFalse();
        assertThat(config.externalChannelRequired("energy")).isTrue();
        assertThat(config.externalChannelAllowed("mana")).isFalse();
        assertThat(config.internalChannelAllowed("mana")).isTrue();
    }

    @Test
    void providerParserFallsBackToAutoForInvalidValues() {
        assertThat(BootConfig.ChannelProvider.parse("external")).isEqualTo(BootConfig.ChannelProvider.EXTERNAL);
        assertThat(BootConfig.ChannelProvider.parse(" INTERNAL ")).isEqualTo(BootConfig.ChannelProvider.INTERNAL);
        assertThat(BootConfig.ChannelProvider.parse("invalid")).isEqualTo(BootConfig.ChannelProvider.AUTO);
        assertThat(BootConfig.ChannelProvider.parse(null)).isEqualTo(BootConfig.ChannelProvider.AUTO);
    }
}
