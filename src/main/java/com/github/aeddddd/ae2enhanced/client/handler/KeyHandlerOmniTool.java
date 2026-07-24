package com.github.aeddddd.ae2enhanced.client.handler;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.PlacementRadialMenuScreen;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolDropMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolPlacementSubMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolSilkTouch;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniToolGui;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;

/**
 * 先进 ME 全能工具客户端按键处理（移植自 1.12 KeyHandlerOmniTool）.
 * N: 循环切换模式
 * Shift+N: 切换精准采集
 * Ctrl+N: 循环掉落模式
 * C: 打开配置 GUI
 * G: 放置模式径向菜单
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KeyHandlerOmniTool {

    public static final KeyMapping KEY_MODE = new KeyMapping(
            "key.ae2enhanced.omnitool_mode",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.ae2enhanced");

    public static final KeyMapping KEY_SILK = new KeyMapping(
            "key.ae2enhanced.omnitool_silk",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.ae2enhanced");

    public static final KeyMapping KEY_DROP = new KeyMapping(
            "key.ae2enhanced.omnitool_drop_mode",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.ae2enhanced");

    public static final KeyMapping KEY_CONFIG = new KeyMapping(
            "key.ae2enhanced.omnitool_config",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.ae2enhanced");

    public static final KeyMapping KEY_PLACEMENT_RADIAL = new KeyMapping(
            "key.ae2enhanced.placement_radial",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.ae2enhanced");

    private KeyHandlerOmniTool() {
    }

    /** 由 ClientSetup 的 RegisterKeyMappingsEvent 调用. */
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_MODE);
        event.register(KEY_SILK);
        event.register(KEY_DROP);
        event.register(KEY_CONFIG);
        event.register(KEY_PLACEMENT_RADIAL);
    }

    /** 主手是否持有处于放置模式的全能工具. */
    private static boolean isOmniPlacement(Minecraft mc) {
        if (mc.player == null) return false;
        ItemStack held = mc.player.getMainHandItem();
        return held.getItem() instanceof AdvancedMEOmniToolItem
                && AdvancedMEOmniToolItem.getMode(held) == AdvancedMEOmniToolItem.MODE_PLACEMENT;
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // 中键选取准星目标（循环 9 预设槽语义由服务端处理）
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.getAction() == GLFW.GLFW_PRESS) {
            if (!isOmniPlacement(mc)) return;
            event.setCanceled(true);
            ModNetwork.CHANNEL.sendToServer(new PacketPlacementSelectPreset(PlacementConfig.MAX_PRESETS));
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // Shift + 滚轮切换单格/批量（仅 Omni Tool 放置模式）
        if (isOmniPlacement(mc) && Screen.hasShiftDown() && event.getScrollDelta() != 0) {
            event.setCanceled(true);
            ModNetwork.CHANNEL.sendToServer(new PacketOmniToolPlacementSubMode(event.getScrollDelta() > 0));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (KEY_CONFIG.consumeClick()) {
            if (mc.player != null) {
                for (InteractionHand hand : InteractionHand.values()) {
                    if (mc.player.getItemInHand(hand).getItem() instanceof AdvancedMEOmniToolItem) {
                        ModNetwork.CHANNEL.sendToServer(new PacketOpenOmniToolGui(hand.ordinal()));
                        break;
                    }
                }
            }
        } else if (KEY_DROP.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(new PacketOmniToolDropMode());
        } else if (KEY_SILK.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(new PacketOmniToolSilkTouch());
        } else if (KEY_MODE.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(new PacketOmniToolMode());
        } else if (KEY_PLACEMENT_RADIAL.consumeClick()) {
            if (mc.player != null && isOmniPlacement(mc)) {
                mc.setScreen(new PlacementRadialMenuScreen(mc.player, KEY_PLACEMENT_RADIAL.getKey().getValue()));
            }
        }
    }
}
