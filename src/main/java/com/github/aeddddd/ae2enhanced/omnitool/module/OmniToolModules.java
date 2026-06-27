package com.github.aeddddd.ae2enhanced.omnitool.module;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;

/**
 * 全能工具模式模块注册表。
 */
public final class OmniToolModules {

    private OmniToolModules() {}

    private static final IOmniToolModule[] MODULES = new IOmniToolModule[ItemAdvancedMEOmniTool.MODE_COUNT];

    static {
        register(new MiningModule());
        register(new PlacementModule());
        register(new RotationModule());
        register(new TravelModule());
    }

    private static void register(IOmniToolModule module) {
        MODULES[module.getMode()] = module;
    }

    public static IOmniToolModule getForMode(int mode) {
        return MODULES[mode % MODULES.length];
    }
}
