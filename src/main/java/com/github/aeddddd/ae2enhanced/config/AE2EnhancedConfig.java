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

    public static class Storage {
        @Config.Comment({
            "Auto-flush interval for the external .dat storage file (seconds).",
            "Lower values reduce data loss risk on crash but increase disk I/O.",
            "Higher values improve performance but may lose up to this many seconds of changes.",
            "Range: 1 ~ 300, Default: 5"
        })
        @Config.RangeInt(min = 1, max = 300)
        public int flushIntervalSeconds = 5;
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
            "Range: 8 ~ 512, Default: 64"
        })
        @Config.RangeInt(min = 8, max = 512)
        public int renderDistance = 64;
    }

    public enum DamageMode {
        ALL,
        NON_CREATIVE,
        NONE
    }

    public static class Crafting {
        @Config.Comment({
            "Maximum parallel crafting limit for the Computation Core.",
            "The actual limit is the smaller of this value and the structure-derived limit.",
            "Range: 1024 ~ 65536, Default: 16384"
        })
        @Config.RangeInt(min = 1024, max = 65536)
        public int maxParallel = 16384;

        @Config.Comment({
            "Maximum number of concurrently active crafting orders.",
            "Each order consumes parallel from the pool; excess orders queue.",
            "Range: 1 ~ 64, Default: 8"
        })
        @Config.RangeInt(min = 1, max = 64)
        public int maxActiveOrders = 8;

        @Config.Comment({
            "Base parallel units allocated to every Computation Core regardless of Causal Anchor count.",
            "Range: 256 ~ 4096, Default: 1024"
        })
        @Config.RangeInt(min = 256, max = 4096)
        public int baseParallel = 1024;

        @Config.Comment({
            "Number of Causal Anchor Cores required per +1024 parallel increment.",
            "Lower values make each anchor more impactful; higher values dampen scaling.",
            "Range: 10 ~ 50, Default: 21"
        })
        @Config.RangeInt(min = 10, max = 50)
        public int parallelPerAnchorCount = 21;
    }

    public static class BlackHole {
        @Config.Comment({
            "Damage dealt by the Micro Singularity event horizon.",
            "  ALL          - All living entities are instantly killed, including creative-mode players.",
            "  NON_CREATIVE - Only non-creative entities are killed; creative players are immune.",
            "  NONE         - No damage is dealt; the black hole only decays after its lifetime expires.",
            "Default: ALL"
        })
        public DamageMode damageMode = DamageMode.ALL;
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