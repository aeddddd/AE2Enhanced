package com.github.aeddddd.ae2enhanced.integration.projecte;

import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * ProjectE 事件监听.
 *
 * <p>该类仅在 ProjectE 已加载时通过反射实例化,因此可以直接引用 ProjectE 事件类.</p>
 */
public class ProjectEEventHandler {

    private static ProjectEEventHandler INSTANCE;
    private static boolean registered = false;

    private final List<WeakReference<com.github.aeddddd.ae2enhanced.tile.TileEMCInterface>> tiles = new LinkedList<>();

    public static synchronized void ensureRegistered() {
        if (!registered) {
            INSTANCE = new ProjectEEventHandler();
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    public static synchronized void registerTile(com.github.aeddddd.ae2enhanced.tile.TileEMCInterface tile) {
        ensureRegistered();
        if (INSTANCE == null) return;
        INSTANCE.tiles.add(new WeakReference<>(tile));
    }

    public static synchronized void unregisterTile(com.github.aeddddd.ae2enhanced.tile.TileEMCInterface tile) {
        if (INSTANCE == null) return;
        Iterator<WeakReference<com.github.aeddddd.ae2enhanced.tile.TileEMCInterface>> it = INSTANCE.tiles.iterator();
        while (it.hasNext()) {
            com.github.aeddddd.ae2enhanced.tile.TileEMCInterface t = it.next().get();
            if (t == null || t == tile) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerKnowledgeChange(PlayerKnowledgeChangeEvent event) {
        UUID uuid = event.getPlayerUUID();
        synchronized (ProjectEEventHandler.class) {
            Iterator<WeakReference<com.github.aeddddd.ae2enhanced.tile.TileEMCInterface>> it = tiles.iterator();
            while (it.hasNext()) {
                com.github.aeddddd.ae2enhanced.tile.TileEMCInterface tile = it.next().get();
                if (tile == null || tile.isInvalid()) {
                    it.remove();
                    continue;
                }
                if (uuid != null && uuid.equals(tile.getOwnerUUID())) {
                    tile.invalidateHandlerCache();
                }
            }
        }
    }

    @SubscribeEvent
    public void onEMCRemap(EMCRemapEvent event) {
        synchronized (ProjectEEventHandler.class) {
            Iterator<WeakReference<com.github.aeddddd.ae2enhanced.tile.TileEMCInterface>> it = tiles.iterator();
            while (it.hasNext()) {
                com.github.aeddddd.ae2enhanced.tile.TileEMCInterface tile = it.next().get();
                if (tile == null || tile.isInvalid()) {
                    it.remove();
                    continue;
                }
                tile.invalidateHandlerCache();
                tile.invalidateEmcCache();
            }
        }
    }
}
