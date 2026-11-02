package com.github.aeddddd.ae2enhanced.integration.guide;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** 玩家登录后同步脚本指南；首版不提供运行期热重载。 */
public final class GuideSyncEvents {
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        try {
            GuideSyncService.sendTo((EntityPlayerMP) event.player);
        } catch (Exception exception) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to synchronize script guide to {}",
                    event.player.getName(), exception);
        }
    }
}
