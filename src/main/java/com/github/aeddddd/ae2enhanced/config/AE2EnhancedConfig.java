package com.github.aeddddd.ae2enhanced.config;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Central configuration for AE2Enhanced.
 * File location: config/ae2enhanced.cfg
 * Changes via in-game Mod Options GUI are applied immediately (F3+T not required).
 */
@Config(modid = AE2Enhanced.MOD_ID)
@Config.LangKey("config.ae2enhanced.title")
public class AE2EnhancedConfig {

    @Config.Name("Storage")
    @Config.Comment({
        "Persistent storage settings for the Hyperdimensional Storage Nexus.",
        "These values affect server-side I/O behavior and data safety."
    })
    public static Storage storage = new Storage();

    @Config.Name("Render")
    @Config.Comment({
        "Client-side visual settings for the Hyperdimensional Controller TESR.",
        "Only processed on the client; changing them on a dedicated server has no effect."
    })
    public static Render render = new Render();

    @Config.Name("BlackHole")
    @Config.Comment({
        "Event-horizon behavior for Micro Singularity black holes.",
        "Controls whether entities inside the 3x3x3 horizon take damage."
    })
    public static BlackHole blackHole = new BlackHole();

    @Config.Name("Crafting")
    @Config.Comment({
        "Supercausal Computation Core crafting engine settings.",
        "Affects parallel limit, order scheduling, and batch behavior."
    })
    public static Crafting crafting = new Crafting();

    @Config.Name("WirelessChannel")
    @Config.Comment({
        "Wireless Channel System settings.",
        "Controls cross-dimension behavior, range, and transmitter power draw."
    })
    public static WirelessChannel wirelessChannel = new WirelessChannel();

    @Config.Name("ChannelPathing")
    @Config.Comment({
        "Experimental fast channel pathing algorithm settings.",
        "Ported from AE2 PR #8285: hierarchical BFS + iterative DFS.",
        "Requires manual command to enable. May change channel assignment in edge cases."
    })
    public static ChannelPathing channelPathing = new ChannelPathing();

    @Config.Name("Terminal")
    @Config.Comment({
        "Omni Terminal settings."
    })
    public static Terminal terminal = new Terminal();

    @Config.Name("OmniTool")
    @Config.Comment({
        "Advanced ME Omni Tool settings."
    })
    public static OmniTool omniTool = new OmniTool();

    @Config.Name("SmartPattern")
    @Config.Comment({
        "Smart Pattern Interface settings.",
        "Controls recipe query limits, blacklist, and overload protection."
    })
    public static SmartPattern smartPattern = new SmartPattern();

    @Config.Name("CentralInterface")
    @Config.Comment({
        "Central ME Interface settings."
    })
    public static CentralInterface centralInterface = new CentralInterface();

    @Config.Name("Thaumcraft")
    @Config.Comment({
        "Thaumcraft automation settings for the Central ME Interface."
    })
    public static Thaumcraft thaumcraft = new Thaumcraft();

    @Config.Name("Collector")
    @Config.Comment({
        "Advanced ME Collector settings."
    })
    public static Collector collector = new Collector();

    @Config.Name("Recycler")
    @Config.Comment({
        "ME Network Recycler settings."
    })
    public static Recycler recycler = new Recycler();

    @Config.Name("Energy")
    @Config.Comment({
        "RF energy bridge settings.",
        "Controls the behavior of RF Access Node when adjacent to creative energy sources."
    })
    public static Energy energy = new Energy();

    @Config.Name("Mana")
    @Config.Comment({
        "Botania Mana bridge settings for Mana Access Node."
    })
    public static Mana mana = new Mana();

    @Config.Name("Starlight")
    @Config.Comment({
        "Astral Sorcery Starlight bridge settings for Starlight Access Node."
    })
    public static Starlight starlight = new Starlight();

    @Config.Name("EMCInterface")
    @Config.Comment({
        "ProjectE EMC Interface settings."
    })
    public static EMCInterface emcInterface = new EMCInterface();

    @Config.Name("PersonalDimension")
    @Config.Comment({
        "Personal dimension settings."
    })
    public static PersonalDimension personalDimension = new PersonalDimension();

    public static class PersonalDimension {
        @Config.Comment({
            "Path to the floor preset file.",
            "Can be an absolute file path, a path relative to the config folder,",
            "or an asset path like assets/ae2enhanced/presets/personal_dimension_floor.json."
        })
        public String presetPath = "ae2enhanced/personal_dimension_floor.json";

        @Config.Comment({
            "Y level of the floor in the personal dimension.",
            "Range: 1 ~ 250, Default: 64"
        })
        @Config.RangeInt(min = 1, max = 250)
        public int floorY = 64;

        @Config.Comment({
            "Y level where players enter the personal dimension.",
            "Range: 2 ~ 255, Default: 65"
        })
        @Config.RangeInt(min = 2, max = 255)
        public int entryY = 65;
    }

    public static class Storage {
        @Config.Comment({
            "Auto-flush interval for the external .dat storage file (seconds).",
            "Lower values reduce data loss risk on crash but increase disk I/O.",
            "Higher values improve performance but may lose up to this many seconds of changes.",
            "WARNING: Extremely high values (e.g. > 3600) may result in significant",
            "data loss if the server crashes before the next flush.",
            "Range: 1 ~ 86400, Default: 5"
        })
        @Config.RangeInt(min = 1, max = 86400)
        public int flushIntervalSeconds = 5;

        @Config.Comment({
            "Full-scan interval for the AE2 NetworkMonitor cache reconciliation (ticks).",
            "The Hyperdimensional Storage Nexus uses postAlterationOfStoredItems() for",
            "real-time incremental updates. This full scan is ONLY a safety net to correct",
            "any cache drift that may occur under extreme edge cases (e.g. chunk load/unload",
            "races, network splits, or AE2-UEL internal inconsistencies).",
            "",
            "Set to 0 to disable the safety-net scan entirely. If you observe ME terminals",
            "showing stale or incorrect item counts, try lowering this value. If performance",
            "is critical and terminals remain accurate, raise it or set to 0.",
            "",
            "WARNING: Values below 100 (5 seconds) defeat the purpose and may reintroduce",
            "the lag caused by excessive forceUpdate() calls. The default of 200 ticks",
            "(10 seconds) provides a reasonable balance between correctness and performance.",
            "Range: 0 ~ 72000, Default: 200 (10 seconds)"
        })
        @Config.RangeInt(min = 0, max = 72000)
        public int monitorFullScanIntervalTicks = 200;
    }

    public static class Render {
        @Config.Comment({
            "Enable the holographic tesseract renderer above the Hyperdimensional Controller.",
            "If false, the spinning wireframe cube, rings, and core are completely skipped.",
            "Useful for low-end GPUs or when many controllers are visible.",
            "Default: true"
        })
        public boolean enableHyperdimensionalRenderer = true;

        @Config.Comment({
            "Maximum camera-to-structure distance (in blocks) at which the hologram is drawn.",
            "Beyond this distance the TESR returns early, saving FPS.",
            "WARNING: Very high values may cause FPS drops when many controllers",
            "are loaded, as the TESR will render at extreme distances.",
            "Range: 8 ~ 2147483647, Default: 64"
        })
        @Config.RangeInt(min = 8, max = Integer.MAX_VALUE)
        public int renderDistance = 64;
    }

    public static class Crafting {
        @Config.Comment({
            "Maximum parallel crafting limit for the Computation Core.",
            "The actual limit is the smaller of this value and the structure-derived limit.",
            "WARNING: Extremely high values may cause lag, memory pressure, or",
            "integer overflow in internal calculations. Use with caution.",
            "Range: 1 ~ 2147483647, Default: 16384"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int maxParallel = 16384;

        @Config.Comment({
            "Maximum number of concurrently active crafting orders.",
            "Each order consumes parallel from the pool; excess orders queue.",
            "WARNING: Very high values increase memory usage and scheduling overhead.",
            "Range: 1 ~ 2147483647, Default: 8"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int maxActiveOrders = 8;
    }

    public static class Terminal {
        @Config.Comment({
            "Maximum number of stored item types before disabling fuzzy @ mod search.",
            "When the network stores more than this many distinct item types,",
            "@ prefix search will only match exact modId instead of scanning",
            "all modIds with contains(). This prevents severe lag on huge networks.",
            "Set to 0 to always allow fuzzy @ search regardless of size.",
            "Range: 0 ~ 2147483647, Default: 5000"
        })
        @Config.RangeInt(min = 0, max = Integer.MAX_VALUE)
        public int modSearchFuzzyThreshold = 5000;
    }

    public static class OmniTool {
        @Config.Comment({
            "Enable the Chaos Core upgrade recipe for the Advanced ME Omni Tool.",
            "Requires Draconic Evolution to be loaded. When disabled, the recipe",
            "is not registered and players cannot craft the Chaos Core upgrade.",
            "Default: true"
        })
        public boolean enableChaosCoreUpgrade = true;

        @Config.Comment({
            "Enable the Bedrock Breaker upgrade recipe for the Advanced ME Omni Tool.",
            "When enabled, players can craft the Omni Tool with Bedrock to break",
            "all unbreakable blocks (block hardness < 0).",
            "Default: true"
        })
        public boolean enableBedrockBreakerUpgrade = true;

        @Config.Comment({
            "Maximum blink (teleport) distance in blocks for Travel Mode.",
            "Players can configure a shorter distance in-game, but cannot exceed this value.",
            "Range: 1 ~ 1000, Default: 256"
        })
        @Config.RangeInt(min = 1, max = 1000)
        public int maxBlinkDistance = 256;

        @Config.Comment({
            "Maximum break cooldown in ticks for Universal Mode.",
            "Players can configure a shorter cooldown in-game, but cannot exceed this value.",
            "Range: 0 ~ 200, Default: 20"
        })
        @Config.RangeInt(min = 0, max = 200)
        public int maxBreakCooldown = 20;

        @Config.Comment({
            "Base attack damage dealt by the Omni Tool in normal mode.",
            "This bypasses armor and damage events (true damage).",
            "Range: 0.0 ~ 10000.0, Default: 6.0"
        })
        @Config.RangeDouble(min = 0.0, max = 10000.0)
        public double baseAttackDamage = 6.0;

        @Config.Comment({
            "Allow blink (teleport) to phase through walls in Travel Mode.",
            "When disabled, the player stops at the first solid block hit.",
            "Default: true"
        })
        public boolean enableWallPhase = true;

        @Config.Comment({
            "Blacklist of block registry names that the Advanced ME Omni Tool cannot break.",
            "Format: modid:blockname (e.g. minecraft:bedrock).",
            "Default: empty (all blocks are breakable by default)."
        })
        public String[] breakableBlacklist = {};

        @Config.Comment({
            "Maximum enchantment level that can be configured on the Advanced ME Omni Tool.",
            "Applies to enchantments imported from enchanted books.",
            "Range: 1 ~ 32767, Default: 255"
        })
        @Config.RangeInt(min = 1, max = Short.MAX_VALUE)
        public int maxEnchantmentLevel = 255;
    }

    public static class WirelessChannel {
        @Config.Comment({
            "Allow wireless channel transmitters to connect across dimensions.",
            "If false, transmitters and receivers must be in the same dimension.",
            "Default: true"
        })
        public boolean crossDimension = true;

        @Config.Comment({
            "Maximum range (in blocks) between a transmitter and receiver.",
            "Set to 0 for infinite range (same dimension only).",
            "WARNING: Extremely large ranges may cause chunk loading issues",
            "or unexpected behavior with cross-dimension connections.",
            "Range: 0 ~ 2147483647, Default: 0"
        })
        @Config.RangeInt(min = 0, max = Integer.MAX_VALUE)
        public int maxRange = 0;

        @Config.Comment({
            "Power draw (AE/t) for an active wireless channel transmitter.",
            "WARNING: Very high power draw may exceed your network generation",
            "capacity and cause devices to shut down.",
            "Range: 1 ~ 2147483647, Default: 512"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int transmitterPower = 512;

        @Config.Comment({
            "Extra upgrade slots added to all AE2 devices (Parts and Tiles).",
            "This compensates for the channel receiver card occupying a slot.",
            "WARNING: Excessive values may cause GUI overflow or rendering",
            "issues since upgrade slot positions are fixed in AE2 GUIs.",
            "Range: 0 ~ 2147483647, Default: 2"
        })
        @Config.RangeInt(min = 0, max = Integer.MAX_VALUE)
        public int extraUpgradeSlots = 2;

        @Config.Comment({
            "Interval (in ticks) for automatic wireless channel connection validation.",
            "The system scans all cached wireless connections and destroys stale ones.",
            "Stale connections are then rebuilt on the next valid event (inventory change,",
            "chunk load, or world reload). Lower values improve recovery speed but add tick overhead.",
            "Set to 0 to disable automatic validation entirely (connections are only checked",
            "during inventory changes and world reloads).",
            "Range: 0 ~ 72000, Default: 100 (5 seconds)"
        })
        @Config.RangeInt(min = 0, max = 72000)
        public int reconnectIntervalTicks = 100;
    }

    public static class ChannelPathing {
        @Config.Comment({
            "Enable the experimental O(N) channel pathing algorithm ported from AE2 PR #8285.",
            "When true, controller-based ME networks use hierarchical BFS + iterative DFS",
            "instead of the original PathSegment-based multi-tick BFS.",
            "Use /ae2e fastpathing to toggle at runtime.",
            "Default: false"
        })
        public boolean fastPathing = false;
    }

    public static class SmartPattern {
        @Config.Comment({
            "Maximum number of recipes a Smart Pattern can hold.",
            "If the target machine has more recipes than this limit,",
            "only the first N recipes will be included (truncation).",
            "Range: 1 ~ 4096, Default: 256"
        })
        @Config.RangeInt(min = 1, max = 4096)
        public int maxRecipes = 256;

        @Config.Comment({
            "Blacklist of block registry names that cannot be bound to the Smart Pattern Interface.",
            "Format: modid:blockname (e.g. minecraft:furnace)",
            "Default: [minecraft:furnace, extendedcrafting:crafting_table_base]"
        })
        public String[] blacklist = {
            "minecraft:furnace",
            "extendedcrafting:crafting_table_base"
        };

        @Config.Comment({
            "Garbage collection interval for orphaned Smart Pattern files (minutes).",
            "The collector scans ME interfaces for referenced patterns and deletes",
            "files that are both unreferenced and older than the newest file by gcMaxAgeDays.",
            "Set to 0 to disable automatic garbage collection entirely.",
            "Range: 0 ~ 10080, Default: 1440 (24 hours)"
        })
        @Config.RangeInt(min = 0, max = 10080)
        public int gcIntervalMinutes = 1440;

        @Config.Comment({
            "Maximum age of an unreferenced Smart Pattern file before deletion (days).",
            "Files referenced by any loaded ME interface are exempt from age checks.",
            "Range: 1 ~ 365, Default: 7"
        })
        @Config.RangeInt(min = 1, max = 365)
        public int gcMaxAgeDays = 7;
    }

    public static class BlackHole {
        public enum DamageMode {
            ALL,
            NON_CREATIVE,
            NONE
        }

        private static DamageMode parseDamageMode(String raw) {
            if (raw == null) return DamageMode.ALL;
            try {
                return DamageMode.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return DamageMode.ALL;
            }
        }

        @Config.Comment({
            "Damage dealt by the Micro Singularity event horizon.",
            "  ALL          - All living entities are instantly killed, including creative-mode players.",
            "  NON_CREATIVE - Only non-creative entities are killed; creative players are immune.",
            "  NONE         - No damage is dealt; the black hole only decays after its lifetime expires.",
            "Default: ALL"
        })
        public String damageMode = "ALL";

        public DamageMode getDamageMode() {
            return parseDamageMode(damageMode);
        }
    }

    public static class Energy {
        @Config.Comment({
            "When enabled, RF Access Node adjacent to a Draconic Evolution Creative RF Source",
            "will directly inject energy into the ME network as long, bypassing the int limit",
            "of Forge Energy API (2.147B per tick).",
            "Default: true"
        })
        public boolean creativeRfSourceBoostEnabled = true;

        @Config.Comment({
            "Amount of RF to inject per operation when adjacent to a creative RF source.",
            "Supports scientific notation (e.g. '1.0E12' for 1 trillion, '2.5E9' for 2.5 billion).",
            "Default: 1.0E12"
        })
        public String creativeRfSourceBoostAmount = "1.0E12";

        public long getParsedBoostAmount() {
            try {
                double val = Double.parseDouble(creativeRfSourceBoostAmount.trim());
                if (val <= 0) return 0;
                if (val >= Long.MAX_VALUE) return Long.MAX_VALUE;
                return (long) val;
            } catch (NumberFormatException e) {
                return 1_000_000_000_000L;
            }
        }

        @Config.Comment({
            "Maximum RF transfer per tick for RF Access Node in output mode.",
            "Also limits how much external devices can push into the node per tick in input mode.",
            "Range: 1 ~ 2147483647, Default: 1000000"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int rfAccessNodeMaxTransfer = Integer.MAX_VALUE;
    }

    public static class Mana {
        @Config.Comment({
            "Maximum Mana transfer per tick for Mana Access Node when NOT adjacent to a Mana Pool.",
            "When adjacent to a Mana Pool, the node bypasses this limit and exchanges mana directly.",
            "Range: 1 ~ 2147483647, Default: 10000"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int manaAccessNodeMaxTransfer = 10_000;
    }

    public static class Starlight {
        @Config.Comment({
            "Maximum Starlight the node can collect into the ME network per tick.",
            "This is a hard cap to prevent players from stockpiling huge amounts of starlight too quickly.",
            "Range: 1 ~ 2147483647, Default: 100"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int starlightAccessNodeMaxInput = 100;

        @Config.Comment({
            "Maximum Starlight the node can output from the ME network per tick.",
            "Range: 1 ~ 2147483647, Default: 1000"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int starlightAccessNodeMaxOutput = 1_000;
    }

    public static class EMCInterface {
        @Config.Comment({
            "Enable the ProjectE EMC Interface block.",
            "Default: true"
        })
        public boolean enabled = true;

        @Config.Comment({
            "Idle AE power draw per EMC Interface (AE/t).",
            "Range: 0 ~ 1000, Default: 5"
        })
        @Config.RangeInt(min = 0, max = 1000)
        public int idlePower = 5;
    }

    public static class CentralInterface {
        @Config.Comment({
            "Timeout in ticks before a processing target is forcefully reset to IDLE.",
            "Increase this if you use slow machines (e.g. long GT recipes).",
            "Range: 20 ~ 72000, Default: 600 (30 seconds)"
        })
        @Config.RangeInt(min = 20, max = 72000)
        public int processingTimeoutTicks = 600;

        @Config.Comment({
            "Cooldown in ticks applied to a virtual crafting target after each batch.",
            "Prevents the same target from being spammed every tick.",
            "Range: 0 ~ 6000, Default: 20 (1 second)"
        })
        @Config.RangeInt(min = 0, max = 6000)
        public int virtualCooldownTargetTicks = 20;

        @Config.Comment({
            "Global cooldown in ticks after each pushPattern attempt that involves",
            "virtual parallel crafting. Applied whether the batch succeeded or failed.",
            "Range: 0 ~ 6000, Default: 20 (1 second)"
        })
        @Config.RangeInt(min = 0, max = 6000)
        public int virtualCooldownGlobalTicks = 20;

        @Config.Comment({
            "AE energy consumed per virtual parallel crafting operation.",
            "Total cost = actualVirtualParallel x this value.",
            "Range: 0.0 ~ 1000000.0, Default: 16.0"
        })
        @Config.RangeDouble(min = 0.0, max = 1000000.0)
        public double virtualParallelEnergyCost = 16.0;

        @Config.Comment({
            "Maximum number of virtual crafting targets that can simultaneously emit particles",
            "per Central Interface. Excess targets are dropped in FIFO order.",
            "Range: 1 ~ 256, Default: 32"
        })
        @Config.RangeInt(min = 1, max = 256)
        public int virtualParticleMaxTargets = 32;

        @Config.Comment({
            "Number of particles spawned per target per tick during virtual crafting.",
            "Range: 1 ~ 64, Default: 16"
        })
        @Config.RangeInt(min = 1, max = 64)
        public int virtualParticleCountPerTick = 16;

        @Config.Comment({
            "Maximum distance (in blocks) at which virtual crafting particles are rendered.",
            "Range: 8 ~ 128, Default: 32"
        })
        @Config.RangeInt(min = 8, max = 128)
        public int virtualParticleRenderDistance = 32;

        @Config.Comment({
            "Duration (in ticks) that virtual crafting particles continue after a batch completes.",
            "Range: 1 ~ 200, Default: 30"
        })
        @Config.RangeInt(min = 1, max = 200)
        public int virtualParticleDurationTicks = 30;
    }

    public static class Thaumcraft {
        @Config.Comment({
            "Clear crucible aspects and remaining water after each craft to prevent",
            "cross-contamination between different recipes.",
            "Default: true"
        })
        public boolean clearAfterCraft = true;
    }

    public static class Collector {
        @Config.Comment({
            "Default collection range radius (in blocks). The actual cubic area is",
            "(2*range+1)^3. Default 2 means a 5x5x5 cube."
        })
        @Config.RangeInt(min = 0, max = 7)
        public int defaultRange = 2;

        @Config.Comment({
            "Maximum collection range radius (in blocks). The actual cubic area is",
            "(2*maxRange+1)^3. Default 7 means a 15x15x15 cube."
        })
        @Config.RangeInt(min = 1, max = 15)
        public int maxRange = 7;

        @Config.Comment({
            "Idle power draw (AE/t) for an active Advanced ME Collector.",
            "Default: 16.0"
        })
        @Config.RangeDouble(min = 0.0, max = 1000000.0)
        public double idlePower = 16.0;
    }

    public static class Recycler {
        @Config.Comment({
            "Force all recycled items to be injected directly into the Hyperdimensional Storage Nexus.",
            "If true, the recycler will only work when a Hyperdimensional Controller is present on the same ME network.",
            "This bypasses AE2 handler priority traversal and provides the best performance.",
            "Default: true"
        })
        public boolean forceHyperdimensionalStorage = true;

        @Config.Comment({
            "If true, machines bound to a recycler will redirect their output directly into the ME network",
            "at the moment the output is produced, bypassing the machine's output slot entirely.",
            "Default: true"
        })
        public boolean machineOutputRedirect = true;

        @Config.Comment({
            "If true, machine output redirect only works when a Hyperdimensional Controller is present on the same ME network.",
            "If false, output falls back to normal ME storage when hyper storage is unavailable.",
            "Default: true"
        })
        public boolean requireHyperStorageForRedirect = true;

        @Config.Comment({
            "Maximum number of recycling targets that can be bound to a single node.",
            "Range: 1 ~ 65536, Default: 1024"
        })
        @Config.RangeInt(min = 1, max = 65536)
        public int maxTargets = 1024;

        @Config.Comment({
            "Heartbeat interval in ticks for scanning bound targets when no immediate events occur.",
            "Range: 1 ~ 1200, Default: 20"
        })
        @Config.RangeInt(min = 1, max = 1200)
        public int heartbeatIntervalTicks = 20;

        @Config.Comment({
            "Idle power draw (AE/t) for an active ME Network Recycler.",
            "Range: 0.0 ~ 1000000.0, Default: 32.0"
        })
        @Config.RangeDouble(min = 0.0, max = 1000000.0)
        public double idlePower = 32.0;

        @Config.Comment({
            "Full target re-scan interval in ticks to correct index drift.",
            "Set to 0 to disable (not recommended).",
            "Range: 0 ~ 72000, Default: 600"
        })
        @Config.RangeInt(min = 0, max = 72000)
        public int fullScanIntervalTicks = 600;
    }

    @Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
    public static class SyncHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (AE2Enhanced.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }
}
