package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.omnitool.placement.PlacementToolHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public final class PlacementEventHandler {
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlacementToolHelper.clearUndoData(event.player.getUniqueID());
    }
}
