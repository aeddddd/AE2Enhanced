package com.github.aeddddd.ae2enhanced.event;

import net.minecraftforge.common.MinecraftForge;

/** 公共 Forge 事件登记入口；具体监听归各功能 listener。 */
public final class ModEventHandler {
    private ModEventHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.ring.RingEventHandler());
        MinecraftForge.EVENT_BUS.register(new PreservedItemsEventHandler());
        MinecraftForge.EVENT_BUS.register(new CollectorEventHandler());
        MinecraftForge.EVENT_BUS.register(new OmniToolEventHandler());
        MinecraftForge.EVENT_BUS.register(new TerminationEventHandler());
        MinecraftForge.EVENT_BUS.register(new PlacementEventHandler());
        MinecraftForge.EVENT_BUS.register(new ConfigEventHandler());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.integration.guide.GuideSyncEvents());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.item.crafting.SmartPatternMergeHelper());
    }
}
