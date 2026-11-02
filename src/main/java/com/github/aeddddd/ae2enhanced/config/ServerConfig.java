package com.github.aeddddd.ae2enhanced.config;

import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;

import java.util.Optional;

/** 单个服务器配置版本的不可变深快照。 */
public final class ServerConfig {
    public final long version;
    public final Storage storage;
    public final BlackHole blackHole;
    public final Chamber chamber;
    public final AssemblyHub assemblyHub;
    public final Crafting crafting;
    public final WirelessChannel wirelessChannel;
    public final ChannelPathing channelPathing;
    public final Diagnostics diagnostics;
    public final DisplayWall displayWall;
    public final Terminal terminal;
    public final OmniTool omniTool;
    public final SmartPattern smartPattern;
    public final CentralInterface centralInterface;
    public final MemoryCard memoryCard;
    public final Thaumcraft thaumcraft;
    public final Collector collector;
    public final Recycler recycler;
    public final Energy energy;
    public final Mana mana;
    public final Starlight starlight;
    public final EMCInterface emcInterface;
    public final PersonalDimension personalDimension;
    public final Ring ring;

    public ServerConfig(long version) {
        this.version = version;
        storage = new Storage(AE2EnhancedConfig.storage);
        blackHole = new BlackHole(AE2EnhancedConfig.blackHole);
        chamber = new Chamber(AE2EnhancedConfig.chamber);
        assemblyHub = new AssemblyHub(AE2EnhancedConfig.assemblyHub);
        crafting = new Crafting(AE2EnhancedConfig.crafting);
        wirelessChannel = new WirelessChannel(AE2EnhancedConfig.wirelessChannel);
        channelPathing = new ChannelPathing(AE2EnhancedConfig.channelPathing);
        diagnostics = new Diagnostics(AE2EnhancedConfig.diagnostics);
        displayWall = new DisplayWall(AE2EnhancedConfig.displayWall);
        terminal = new Terminal(AE2EnhancedConfig.terminal);
        omniTool = new OmniTool(AE2EnhancedConfig.omniTool);
        smartPattern = new SmartPattern(AE2EnhancedConfig.smartPattern);
        centralInterface = new CentralInterface(AE2EnhancedConfig.centralInterface);
        memoryCard = new MemoryCard(AE2EnhancedConfig.memoryCard);
        thaumcraft = new Thaumcraft(AE2EnhancedConfig.thaumcraft);
        collector = new Collector(AE2EnhancedConfig.collector);
        recycler = new Recycler(AE2EnhancedConfig.recycler);
        energy = new Energy(AE2EnhancedConfig.energy);
        mana = new Mana(AE2EnhancedConfig.mana);
        starlight = new Starlight(AE2EnhancedConfig.starlight);
        emcInterface = new EMCInterface(AE2EnhancedConfig.emcInterface);
        personalDimension = new PersonalDimension(AE2EnhancedConfig.personalDimension);
        ring = new Ring(AE2EnhancedConfig.ring);
    }

    public static ServerConfig current() {
        Optional<com.github.aeddddd.ae2enhanced.runtime.ServerScope> scope = RuntimeScopes.currentServer();
        return scope.isPresent() ? current(scope.get()) : new ServerConfig(0L);
    }

    public static ServerConfig current(com.github.aeddddd.ae2enhanced.runtime.ServerScope scope) {
        if (scope.acceptingWork()) {
            return scope.service(Store.class, Store::new).current;
        }
        return scope.existingService(Store.class).map(store -> store.current)
                .orElseGet(() -> new ServerConfig(0L));
    }

    /** Forge reload 和运行期命令在同步原始字段后调用，发布一个全新版本。 */
    public static ServerConfig publish() {
        Optional<com.github.aeddddd.ae2enhanced.runtime.ServerScope> scope = RuntimeScopes.currentServer();
        if (!scope.isPresent() || !scope.get().acceptingWork()) {
            return current();
        }
        Store store = scope.get().service(Store.class, Store::new);
        synchronized (store) {
            store.current = new ServerConfig(store.current.version + 1L);
            return store.current;
        }
    }

    private static final class Store {
        private ServerConfig current = new ServerConfig(1L);
    }

    public static final class Storage {
        public final int flushIntervalSeconds;
        public final int monitorFullScanIntervalTicks;
        public final int checkpointIdleSeconds;
        public final int walCheckpointThresholdBytes;
        public final int sessionIdleCloseSeconds;
        private Storage(AE2EnhancedConfig.Storage source) {
            flushIntervalSeconds = source.flushIntervalSeconds;
            monitorFullScanIntervalTicks = source.monitorFullScanIntervalTicks;
            checkpointIdleSeconds = source.checkpointIdleSeconds;
            walCheckpointThresholdBytes = source.walCheckpointThresholdBytes;
            sessionIdleCloseSeconds = source.sessionIdleCloseSeconds;
        }
    }

    public static final class BlackHole {
        public final String damageMode;
        private BlackHole(AE2EnhancedConfig.BlackHole source) { damageMode = source.damageMode; }
        public AE2EnhancedConfig.BlackHole.DamageMode getDamageMode() {
            if (damageMode == null) return AE2EnhancedConfig.BlackHole.DamageMode.ALL;
            try {
                return AE2EnhancedConfig.BlackHole.DamageMode.valueOf(damageMode.trim().toUpperCase());
            } catch (IllegalArgumentException exception) {
                return AE2EnhancedConfig.BlackHole.DamageMode.ALL;
            }
        }
    }

    public static final class Chamber {
        public final int energyPerBatch;
        public final boolean requireChannel;
        private Chamber(AE2EnhancedConfig.Chamber source) {
            energyPerBatch = source.energyPerBatch;
            requireChannel = source.requireChannel;
        }
    }

    public static final class AssemblyHub {
        public final int energyPerCraft;
        public final int energyCapPerTick;
        private AssemblyHub(AE2EnhancedConfig.AssemblyHub source) {
            energyPerCraft = source.energyPerCraft;
            energyCapPerTick = source.energyCapPerTick;
        }
    }

    public static final class Crafting {
        public final int maxParallel;
        public final int maxActiveOrders;
        public final boolean specialCrafting;
        public final AE2EnhancedConfig.DagPlannerMode dagPlannerMode;
        public final int specialDetectorBudgetMs;
        public final int nativeCalcBudgetMs;
        public final int cycleBoundarySolveBudgetMs;
        public final int dagMaxNodes;
        private Crafting(AE2EnhancedConfig.Crafting source) {
            maxParallel = source.maxParallel;
            maxActiveOrders = source.maxActiveOrders;
            specialCrafting = source.specialCrafting;
            dagPlannerMode = source.dagPlannerMode;
            specialDetectorBudgetMs = source.specialDetectorBudgetMs;
            nativeCalcBudgetMs = source.nativeCalcBudgetMs;
            cycleBoundarySolveBudgetMs = source.cycleBoundarySolveBudgetMs;
            dagMaxNodes = source.dagMaxNodes;
        }
    }

    public static final class WirelessChannel {
        public final boolean crossDimension;
        public final int maxRange;
        public final int transmitterPower;
        public final int extraUpgradeSlots;
        public final int reconnectIntervalTicks;
        private WirelessChannel(AE2EnhancedConfig.WirelessChannel source) {
            crossDimension = source.crossDimension;
            maxRange = source.maxRange;
            transmitterPower = source.transmitterPower;
            extraUpgradeSlots = source.extraUpgradeSlots;
            reconnectIntervalTicks = source.reconnectIntervalTicks;
        }
    }

    public static final class ChannelPathing {
        public final boolean fastPathing;
        public final boolean skipRecalcWhenChannelsDisabled;
        private ChannelPathing(AE2EnhancedConfig.ChannelPathing source) {
            fastPathing = source.fastPathing;
            skipRecalcWhenChannelsDisabled = source.skipRecalcWhenChannelsDisabled;
        }
    }

    public static final class Diagnostics {
        public final int analysisPermissionLevel;
        private Diagnostics(AE2EnhancedConfig.Diagnostics source) {
            analysisPermissionLevel = source.analysisPermissionLevel;
        }
    }

    public static final class DisplayWall {
        public final double powerPerBlock;
        private DisplayWall(AE2EnhancedConfig.DisplayWall source) { powerPerBlock = source.powerPerBlock; }
    }

    public static final class Terminal {
        public final int modSearchFuzzyThreshold;
        public final int guiSyncFloodThreshold;
        public final int guiSyncFloodIntervalTicks;
        public final int terminalRebuildDebounceMs;
        public final int maxRepoColumns;
        private Terminal(AE2EnhancedConfig.Terminal source) {
            modSearchFuzzyThreshold = source.modSearchFuzzyThreshold;
            guiSyncFloodThreshold = source.guiSyncFloodThreshold;
            guiSyncFloodIntervalTicks = source.guiSyncFloodIntervalTicks;
            terminalRebuildDebounceMs = source.terminalRebuildDebounceMs;
            maxRepoColumns = source.maxRepoColumns;
        }
    }

    public static final class OmniTool {
        public final int maxBlinkDistance;
        public final int maxBreakCooldown;
        public final double baseAttackDamage;
        public final boolean enableWallPhase;
        public final int maxEnchantmentLevel;
        public final int forceKillReassertTicks;
        public final int forceKillMaxPending;
        public final int forceKillTickBudget;
        public final boolean forceKillSubstituteGuard;
        public final double forceKillSubstituteRadius;
        public final int forceKillSubstituteWindowTicks;
        public final int forceKillPlayerReasserts;
        public final boolean forceKillPlayers;
        private final String[] breakableBlacklist;
        private OmniTool(AE2EnhancedConfig.OmniTool source) {
            maxBlinkDistance = source.maxBlinkDistance;
            maxBreakCooldown = source.maxBreakCooldown;
            baseAttackDamage = source.baseAttackDamage;
            enableWallPhase = source.enableWallPhase;
            breakableBlacklist = source.breakableBlacklist.clone();
            maxEnchantmentLevel = source.maxEnchantmentLevel;
            forceKillReassertTicks = source.forceKillReassertTicks;
            forceKillMaxPending = source.forceKillMaxPending;
            forceKillTickBudget = source.forceKillTickBudget;
            forceKillSubstituteGuard = source.forceKillSubstituteGuard;
            forceKillSubstituteRadius = source.forceKillSubstituteRadius;
            forceKillSubstituteWindowTicks = source.forceKillSubstituteWindowTicks;
            forceKillPlayerReasserts = source.forceKillPlayerReasserts;
            forceKillPlayers = source.forceKillPlayers;
        }
        public String[] breakableBlacklist() { return breakableBlacklist.clone(); }
    }

    public static final class SmartPattern {
        public final int maxRecipes;
        private final String[] blacklist;
        private SmartPattern(AE2EnhancedConfig.SmartPattern source) {
            maxRecipes = source.maxRecipes;
            blacklist = source.blacklist.clone();
        }
        public String[] blacklist() { return blacklist.clone(); }
    }

    public static final class CentralInterface {
        public final int processingTimeoutTicks;
        public final int virtualCooldownTargetTicks;
        public final int virtualCooldownGlobalTicks;
        public final double virtualParallelEnergyCost;
        public final int virtualParticleMaxTargets;
        public final int virtualParticleCountPerTick;
        public final int virtualParticleDurationTicks;
        private CentralInterface(AE2EnhancedConfig.CentralInterface source) {
            processingTimeoutTicks = source.processingTimeoutTicks;
            virtualCooldownTargetTicks = source.virtualCooldownTargetTicks;
            virtualCooldownGlobalTicks = source.virtualCooldownGlobalTicks;
            virtualParallelEnergyCost = source.virtualParallelEnergyCost;
            virtualParticleMaxTargets = source.virtualParticleMaxTargets;
            virtualParticleCountPerTick = source.virtualParticleCountPerTick;
            virtualParticleDurationTicks = source.virtualParticleDurationTicks;
        }
    }

    public static final class MemoryCard {
        public final boolean vanillaContainerCopy;
        private MemoryCard(AE2EnhancedConfig.MemoryCard source) {
            vanillaContainerCopy = source.vanillaContainerCopy;
        }
    }

    public static final class Thaumcraft {
        public final boolean clearAfterCraft;
        private Thaumcraft(AE2EnhancedConfig.Thaumcraft source) { clearAfterCraft = source.clearAfterCraft; }
    }

    public static final class Collector {
        public final int defaultRange;
        public final int maxRange;
        public final int maxCenterOffset;
        public final double idlePower;
        private Collector(AE2EnhancedConfig.Collector source) {
            defaultRange = source.defaultRange;
            maxRange = source.maxRange;
            maxCenterOffset = source.maxCenterOffset;
            idlePower = source.idlePower;
        }
    }

    public static final class Recycler {
        public final boolean forceHyperdimensionalStorage;
        public final boolean machineOutputRedirect;
        public final boolean requireHyperStorageForRedirect;
        public final int maxTargets;
        public final int heartbeatIntervalTicks;
        public final double idlePower;
        public final int fullScanIntervalTicks;
        private Recycler(AE2EnhancedConfig.Recycler source) {
            forceHyperdimensionalStorage = source.forceHyperdimensionalStorage;
            machineOutputRedirect = source.machineOutputRedirect;
            requireHyperStorageForRedirect = source.requireHyperStorageForRedirect;
            maxTargets = source.maxTargets;
            heartbeatIntervalTicks = source.heartbeatIntervalTicks;
            idlePower = source.idlePower;
            fullScanIntervalTicks = source.fullScanIntervalTicks;
        }
    }

    public static final class Energy {
        public final boolean creativeRfSourceBoostEnabled;
        public final String creativeRfSourceBoostAmount;
        public final int rfAccessNodeMaxTransfer;
        private Energy(AE2EnhancedConfig.Energy source) {
            creativeRfSourceBoostEnabled = source.creativeRfSourceBoostEnabled;
            creativeRfSourceBoostAmount = source.creativeRfSourceBoostAmount;
            rfAccessNodeMaxTransfer = source.rfAccessNodeMaxTransfer;
        }
        public long getParsedBoostAmount() {
            try {
                double value = Double.parseDouble(creativeRfSourceBoostAmount.trim());
                if (value <= 0) return 0;
                if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
                return (long) value;
            } catch (NumberFormatException exception) {
                return 1_000_000_000_000L;
            }
        }
    }

    public static final class Mana {
        public final int manaAccessNodeMaxTransfer;
        private Mana(AE2EnhancedConfig.Mana source) { manaAccessNodeMaxTransfer = source.manaAccessNodeMaxTransfer; }
    }

    public static final class Starlight {
        public final int starlightAccessNodeMaxInput;
        public final int starlightAccessNodeMaxOutput;
        private Starlight(AE2EnhancedConfig.Starlight source) {
            starlightAccessNodeMaxInput = source.starlightAccessNodeMaxInput;
            starlightAccessNodeMaxOutput = source.starlightAccessNodeMaxOutput;
        }
    }

    public static final class EMCInterface {
        public final int idlePower;
        public final int syncIntervalTicks;
        private EMCInterface(AE2EnhancedConfig.EMCInterface source) {
            idlePower = source.idlePower;
            syncIntervalTicks = source.syncIntervalTicks;
        }
    }

    public static final class PersonalDimension {
        public final String presetPath;
        public final int floorY;
        public final int entryY;
        public final int repaintRadiusChunks;
        public final int repaintChunksPerTick;
        private PersonalDimension(AE2EnhancedConfig.PersonalDimension source) {
            presetPath = source.presetPath;
            floorY = source.floorY;
            entryY = source.entryY;
            repaintRadiusChunks = source.repaintRadiusChunks;
            repaintChunksPerTick = source.repaintChunksPerTick;
        }
    }

    public static final class Ring {
        public final double tier1CostMultiplier;
        public final double tier2CostMultiplier;
        public final double tier3CostMultiplier;
        public final double ascendedCostMultiplier;
        public final int deathBlockCooldownTicks;
        public final int internalBufferSize;
        public final int networkRechargePerTick;
        public final int flightCostPerTick;
        public final int forceFlightCostPerTick;
        public final int wallPhaseCostPerTick;
        public final int nightVisionCostPerTick;
        public final int saturationCostPerTick;
        public final int healCostPerPoint;
        public final int healMaxPerTick;
        public final int potionRemoveCost;
        public final int blockCostPerOverflowPoint;
        public final int ascendedBlockCostPerPoint;
        public final int strongKillBlockCost;
        public final int healMaxRfPerSecond;
        public final int blockMaxRfPerSecond;
        public final double maxReachDistance;
        public final int maxSpeedPercent;
        public final int maxJumpPercent;
        private Ring(AE2EnhancedConfig.Ring source) {
            tier1CostMultiplier = source.tier1CostMultiplier;
            tier2CostMultiplier = source.tier2CostMultiplier;
            tier3CostMultiplier = source.tier3CostMultiplier;
            ascendedCostMultiplier = source.ascendedCostMultiplier;
            deathBlockCooldownTicks = source.deathBlockCooldownTicks;
            internalBufferSize = source.internalBufferSize;
            networkRechargePerTick = source.networkRechargePerTick;
            flightCostPerTick = source.flightCostPerTick;
            forceFlightCostPerTick = source.forceFlightCostPerTick;
            wallPhaseCostPerTick = source.wallPhaseCostPerTick;
            nightVisionCostPerTick = source.nightVisionCostPerTick;
            saturationCostPerTick = source.saturationCostPerTick;
            healCostPerPoint = source.healCostPerPoint;
            healMaxPerTick = source.healMaxPerTick;
            potionRemoveCost = source.potionRemoveCost;
            blockCostPerOverflowPoint = source.blockCostPerOverflowPoint;
            ascendedBlockCostPerPoint = source.ascendedBlockCostPerPoint;
            strongKillBlockCost = source.strongKillBlockCost;
            healMaxRfPerSecond = source.healMaxRfPerSecond;
            blockMaxRfPerSecond = source.blockMaxRfPerSecond;
            maxReachDistance = source.maxReachDistance;
            maxSpeedPercent = source.maxSpeedPercent;
            maxJumpPercent = source.maxJumpPercent;
        }
    }
}
