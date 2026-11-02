package com.github.aeddddd.ae2enhanced.config;

import java.util.concurrent.atomic.AtomicReference;

/** 当前客户端进程的不可变显示偏好快照，不进入 ServerConfig 或合成请求。 */
public final class ClientPreferences {
    private static final AtomicReference<ClientPreferences> CURRENT =
            new AtomicReference<>(new ClientPreferences(1L));

    public final long version;
    public final Render render;
    public final Guide guide;
    public final CraftingDisplay craftingDisplay;
    public final DisplayWall displayWall;
    public final CentralInterface centralInterface;
    public final Terminal terminal;

    public ClientPreferences(long version) {
        this.version = version;
        render = new Render(AE2EnhancedConfig.render);
        guide = new Guide(AE2EnhancedConfig.guide);
        craftingDisplay = new CraftingDisplay(AE2EnhancedConfig.craftingDisplay);
        displayWall = new DisplayWall(AE2EnhancedConfig.displayWall);
        centralInterface = new CentralInterface(AE2EnhancedConfig.centralInterface);
        terminal = new Terminal(AE2EnhancedConfig.terminal);
    }

    public static ClientPreferences current() {
        return CURRENT.get();
    }

    public static ClientPreferences publish() {
        return CURRENT.updateAndGet(previous -> new ClientPreferences(previous.version + 1L));
    }

    public static void setConfirmSortMode(String mode) {
        AE2EnhancedConfig.craftingDisplay.confirmSortMode = mode;
        publish();
    }

    public static void setCpuSortMode(String mode) {
        AE2EnhancedConfig.craftingDisplay.cpuSortMode = mode;
        publish();
    }

    public static void setGuideTheme(String theme) {
        AE2EnhancedConfig.guide.theme = theme;
        publish();
    }

    public static final class Render {
        public final boolean enableHyperdimensionalRenderer;
        public final int renderDistance;
        public final boolean enableBlackHoleShader;
        public final boolean forceCompatibilityMode;
        public final double blackHoleShaderIntensity;
        private Render(AE2EnhancedConfig.Render source) {
            enableHyperdimensionalRenderer = source.enableHyperdimensionalRenderer;
            renderDistance = source.renderDistance;
            enableBlackHoleShader = source.enableBlackHoleShader;
            forceCompatibilityMode = source.forceCompatibilityMode;
            blackHoleShaderIntensity = source.blackHoleShaderIntensity;
        }
    }

    public static final class Guide {
        public final boolean enabled;
        public final String theme;
        private Guide(AE2EnhancedConfig.Guide source) {
            enabled = source.enabled;
            theme = source.theme;
        }
    }

    public static final class CraftingDisplay {
        public final String confirmSortMode;
        public final String cpuSortMode;
        private CraftingDisplay(AE2EnhancedConfig.CraftingDisplay source) {
            confirmSortMode = source.confirmSortMode;
            cpuSortMode = source.cpuSortMode;
        }
    }

    public static final class DisplayWall {
        public final int renderDistance;
        private DisplayWall(AE2EnhancedConfig.DisplayWall source) { renderDistance = source.renderDistance; }
    }

    public static final class CentralInterface {
        public final int virtualParticleRenderDistance;
        private CentralInterface(AE2EnhancedConfig.CentralInterface source) {
            virtualParticleRenderDistance = source.virtualParticleRenderDistance;
        }
    }

    public static final class Terminal {
        public final boolean splicedTerminalTestHarness;
        public final boolean splicedTerminalHarnessAutoWorld;
        private Terminal(AE2EnhancedConfig.Terminal source) {
            splicedTerminalTestHarness = source.splicedTerminalTestHarness;
            splicedTerminalHarnessAutoWorld = source.splicedTerminalHarnessAutoWorld;
        }
    }
}
