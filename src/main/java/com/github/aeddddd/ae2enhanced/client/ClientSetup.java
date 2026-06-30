package com.github.aeddddd.ae2enhanced.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 客户端专属初始化：注册菜单屏幕等。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Phase 1+ 在此注册 Screen（MenuScreens.register(...)）
        });
    }

    private ClientSetup() {
    }
}
