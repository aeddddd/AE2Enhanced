package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PresetLoader;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ConfigEventHandler {
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (AE2Enhanced.MOD_ID.equals(event.getModID())) PresetLoader.reload();
    }
}
