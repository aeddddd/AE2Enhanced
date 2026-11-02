package com.github.aeddddd.ae2enhanced.config;

import java.util.Locale;

/** 注册期一次性冻结的条件开关。运行期配置发布不会改变这些值。 */
public final class BootConfig {
    public final boolean emcInterfaceEnabled;
    public final boolean chaosCoreUpgradeRecipe;
    public final boolean bedrockBreakerUpgradeRecipe;
    public final boolean ringAscendRecipe;
    public final boolean ringTierRecipes;
    public final Integrations integrations;
    public final StorageChannels storageChannels;

    public BootConfig(boolean emcInterfaceEnabled, boolean chaosCoreUpgradeRecipe,
            boolean bedrockBreakerUpgradeRecipe, boolean ringAscendRecipe, boolean ringTierRecipes) {
        this(emcInterfaceEnabled, chaosCoreUpgradeRecipe, bedrockBreakerUpgradeRecipe,
                ringAscendRecipe, ringTierRecipes, new Integrations(), new StorageChannels());
    }

    public BootConfig(boolean emcInterfaceEnabled, boolean chaosCoreUpgradeRecipe,
            boolean bedrockBreakerUpgradeRecipe, boolean ringAscendRecipe, boolean ringTierRecipes,
            Integrations integrations, StorageChannels storageChannels) {
        this.emcInterfaceEnabled = emcInterfaceEnabled;
        this.chaosCoreUpgradeRecipe = chaosCoreUpgradeRecipe;
        this.bedrockBreakerUpgradeRecipe = bedrockBreakerUpgradeRecipe;
        this.ringAscendRecipe = ringAscendRecipe;
        this.ringTierRecipes = ringTierRecipes;
        this.integrations = integrations;
        this.storageChannels = storageChannels;
    }

    public static BootConfig capture() {
        return new BootConfig(AE2EnhancedConfig.emcInterface.enabled,
                AE2EnhancedConfig.omniTool.enableChaosCoreUpgrade,
                AE2EnhancedConfig.omniTool.enableBedrockBreakerUpgrade,
                AE2EnhancedConfig.ring.enableAscendRecipe,
                AE2EnhancedConfig.ring.enableTierRecipes,
                Integrations.capture(), StorageChannels.capture());
    }

    public static BootConfig current() {
        return com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.requireProcess().bootConfig(BootConfig.class);
    }

    public boolean integrationEnabled(String modId) {
        String id = modId.toLowerCase(Locale.ROOT);
        switch (id) {
            case "mekanism":
            case "mekeng":
                return integrations.mekanism;
            case "thaumcraft":
            case "thaumicenergistics":
                return integrations.thaumcraft;
            case "fluxapplied":
                return integrations.fluxApplied;
            case "botania":
                return integrations.botania;
            case "botaniaapplie":
                return integrations.botaniaApplie;
            case "astralsorcery":
                return integrations.astralSorcery;
            case "projecte":
                return integrations.projecte;
            case "cellterminal":
                return integrations.cellTerminal;
            case "tii":
                return integrations.tii;
            case "ftblib":
            case "ftbutilities":
                return integrations.ftb;
            case "crafttweaker":
                return integrations.crafttweaker;
            default:
                return true;
        }
    }

    public boolean channelEnabled(String channel) {
        switch (channel.toLowerCase(Locale.ROOT)) {
            case "energy": return storageChannels.energy;
            case "mana": return storageChannels.mana;
            case "starlight": return storageChannels.starlight;
            case "gas": return storageChannels.gas;
            case "essentia": return storageChannels.essentia;
            case "emc": return storageChannels.emc;
            default: return true;
        }
    }

    public boolean externalChannelAllowed(String channel) {
        return provider(channel) != ChannelProvider.INTERNAL && externalIntegrationEnabled(channel);
    }

    public boolean internalChannelAllowed(String channel) {
        return provider(channel) != ChannelProvider.EXTERNAL;
    }

    public boolean externalChannelRequired(String channel) {
        return provider(channel) == ChannelProvider.EXTERNAL;
    }

    private boolean externalIntegrationEnabled(String channel) {
        switch (channel.toLowerCase(Locale.ROOT)) {
            case "energy": return integrationEnabled("fluxapplied");
            case "mana": return integrationEnabled("botaniaapplie");
            default: return true;
        }
    }

    private ChannelProvider provider(String channel) {
        return "mana".equalsIgnoreCase(channel) ? storageChannels.manaProvider : storageChannels.energyProvider;
    }

    public enum ChannelProvider {
        AUTO, EXTERNAL, INTERNAL;

        public static ChannelProvider parse(String value) {
            if (value != null) {
                try {
                    return valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return AUTO;
        }
    }

    public static final class Integrations {
        public final boolean mekanism;
        public final boolean thaumcraft;
        public final boolean fluxApplied;
        public final boolean botania;
        public final boolean botaniaApplie;
        public final boolean astralSorcery;
        public final boolean projecte;
        public final boolean cellTerminal;
        public final boolean tii;
        public final boolean ftb;
        public final boolean crafttweaker;

        public Integrations() {
            this(true, true, true, true, true, true, true, true, true, true, true);
        }

        public Integrations(boolean mekanism, boolean thaumcraft, boolean fluxApplied, boolean botania,
                boolean botaniaApplie, boolean astralSorcery, boolean projecte, boolean cellTerminal,
                boolean tii, boolean ftb, boolean crafttweaker) {
            this.mekanism = mekanism;
            this.thaumcraft = thaumcraft;
            this.fluxApplied = fluxApplied;
            this.botania = botania;
            this.botaniaApplie = botaniaApplie;
            this.astralSorcery = astralSorcery;
            this.projecte = projecte;
            this.cellTerminal = cellTerminal;
            this.tii = tii;
            this.ftb = ftb;
            this.crafttweaker = crafttweaker;
        }

        private static Integrations capture() {
            AE2EnhancedConfig.Integrations c = AE2EnhancedConfig.integrations;
            return new Integrations(c.mekanism, c.thaumcraft, c.fluxApplied, c.botania, c.botaniaApplie,
                    c.astralSorcery, c.projecte, c.cellTerminal, c.tii, c.ftb, c.crafttweaker);
        }
    }

    public static final class StorageChannels {
        public final boolean energy;
        public final boolean mana;
        public final boolean starlight;
        public final boolean gas;
        public final boolean essentia;
        public final boolean emc;
        public final ChannelProvider energyProvider;
        public final ChannelProvider manaProvider;

        public StorageChannels() {
            this(true, true, true, true, true, true, ChannelProvider.AUTO, ChannelProvider.AUTO);
        }

        public StorageChannels(boolean energy, boolean mana, boolean starlight, boolean gas,
                boolean essentia, boolean emc, ChannelProvider energyProvider, ChannelProvider manaProvider) {
            this.energy = energy;
            this.mana = mana;
            this.starlight = starlight;
            this.gas = gas;
            this.essentia = essentia;
            this.emc = emc;
            this.energyProvider = energyProvider;
            this.manaProvider = manaProvider;
        }

        private static StorageChannels capture() {
            AE2EnhancedConfig.StorageChannels c = AE2EnhancedConfig.storageChannels;
            return new StorageChannels(c.energy, c.mana, c.starlight, c.gas, c.essentia, c.emc,
                    ChannelProvider.parse(c.energyProvider), ChannelProvider.parse(c.manaProvider));
        }
    }
}
