package com.github.aeddddd.ae2enhanced.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.ClientPersonalDimRules;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;

/**
 * 个人维度的客户端规则执行.
 *
 * <p>服务端无法获知非骑乘玩家的移动输入（{@code zza}/{@code xxa} 仅在骑乘时通过
 * {@code ServerboundPlayerInputPacket} 更新）,且飞行移动为客户端权威,
 * 服务端清零 deltaMovement 不会影响客户端,因此"无飞行惯性"必须在客户端执行.</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Dist.CLIENT)
public final class PersonalDimClientHandler {

    private PersonalDimClientHandler() {
    }

    /**
     * 无飞行惯性：飞行时无移动输入即清零水平速度,停止漂移.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PersonalDimensionRules rules = ClientPersonalDimRules.getCurrent();
        if (rules == null || !rules.noFlightInertia) {
            return;
        }
        if (player.getAbilities().flying && player.zza == 0.0f && player.xxa == 0.0f) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    /**
     * 退出服务器时清空规则缓存,避免残留到下一个存档.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPersonalDimRules.update(null);
    }
}
