package com.github.aeddddd.ae2enhanced.client.handler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenRingGui;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRingManualHeal;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRingToggleFlight;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * 网络链接指环客户端按键处理.
 * V: 打开配置 GUI
 * Shift+V: 切换飞行
 * B: 瞬间完全回血
 */
public class KeyHandlerRing {

    public static final KeyBinding KEY_RING_CONFIG = new KeyBinding(
        "key.ae2enhanced.ring_config",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        Keyboard.KEY_V,
        "key.categories.ae2enhanced"
    );

    public static final KeyBinding KEY_RING_FLIGHT = new KeyBinding(
        "key.ae2enhanced.ring_flight",
        KeyConflictContext.IN_GAME,
        KeyModifier.SHIFT,
        Keyboard.KEY_V,
        "key.categories.ae2enhanced"
    );

    public static final KeyBinding KEY_RING_HEAL = new KeyBinding(
        "key.ae2enhanced.ring_heal",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        Keyboard.KEY_B,
        "key.categories.ae2enhanced"
    );

    public static void init() {
        ClientRegistry.registerKeyBinding(KEY_RING_CONFIG);
        ClientRegistry.registerKeyBinding(KEY_RING_FLIGHT);
        ClientRegistry.registerKeyBinding(KEY_RING_HEAL);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KEY_RING_FLIGHT.isPressed()) {
            AE2Enhanced.network.sendToServer(new PacketRingToggleFlight());
        } else if (KEY_RING_CONFIG.isPressed()) {
            AE2Enhanced.network.sendToServer(new PacketOpenRingGui());
        } else if (KEY_RING_HEAL.isPressed()) {
            AE2Enhanced.network.sendToServer(new PacketRingManualHeal());
        }
    }

    /**
     * ClientTickEvent.END：客户端镜像恢复被外部禁飞模组清除的飞行状态
     * (移动由客户端权威计算,必须在客户端恢复).
     */
    @SubscribeEvent
    public void onClientTickEnd(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        net.minecraft.client.entity.EntityPlayerSP player = net.minecraft.client.Minecraft.getMinecraft().player;
        if (player != null) {
            com.github.aeddddd.ae2enhanced.ring.RingManager.tickClientEndFlightRestore(player);
        }
    }
}
