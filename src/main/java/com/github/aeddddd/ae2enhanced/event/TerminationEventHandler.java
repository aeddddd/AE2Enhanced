package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.util.termination.TerminationRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** 终止窗口的服务端驱动：tick 复核与死亡后短窗替身防护。 */
public final class TerminationEventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        TerminationRegistry registry = TerminationRegistry.active();
        if (registry == null) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            registry.tick(server);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || event.isCanceled()) {
            return;
        }
        TerminationRegistry registry = TerminationRegistry.active();
        if (registry != null && registry.shouldBlockJoin(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
