package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** 每个服务器生命周期独立、跨维度共享的单调 tick。 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class ServerTickCounter {
    public static final long NEVER = Long.MIN_VALUE;
    private static final Map<MinecraftServer, Long> TICKS = new WeakHashMap<>();

    private ServerTickCounter() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        synchronized (TICKS) {
            Long current = TICKS.get(server);
            TICKS.put(server, current == null ? 1L : current + 1L);
        }
    }

    public static long now(EntityPlayer player) {
        return now(player == null ? null : player.getServer());
    }

    public static long now(MinecraftServer server) {
        if (server == null) return 0L;
        synchronized (TICKS) {
            Long tick = TICKS.get(server);
            return tick == null ? 0L : tick;
        }
    }

    public static boolean isCoolingDown(long lastUse, long now, int cooldown) {
        if (cooldown <= 0 || lastUse == NEVER || lastUse > now) return false;
        return now - lastUse < cooldown;
    }

    public static void clear() {
        synchronized (TICKS) {
            TICKS.clear();
        }
    }
}
