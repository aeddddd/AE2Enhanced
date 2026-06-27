package com.github.aeddddd.ae2enhanced.client.handler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolMode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolSilkTouch;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolDropMode;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 先进ME工具客户端按键处理.
 * N: 循环切换模式
 * Shift+N: 切换精准采集
 */
public class KeyHandlerOmniTool {

    public static final KeyBinding KEY_MODE = new KeyBinding(
        "key.ae2enhanced.omnitool_mode",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        Keyboard.KEY_N,
        "key.categories.ae2enhanced"
    );

    public static final KeyBinding KEY_SILK = new KeyBinding(
        "key.ae2enhanced.omnitool_silk",
        KeyConflictContext.IN_GAME,
        KeyModifier.SHIFT,
        Keyboard.KEY_N,
        "key.categories.ae2enhanced"
    );

    public static final KeyBinding KEY_DROP = new KeyBinding(
        "key.ae2enhanced.omnitool_drop_mode",
        KeyConflictContext.IN_GAME,
        KeyModifier.CONTROL,
        Keyboard.KEY_N,
        "key.categories.ae2enhanced"
    );

    public static final KeyBinding KEY_CONFIG = new KeyBinding(
        "key.ae2enhanced.omnitool_config",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        Keyboard.KEY_C,
        "key.categories.ae2enhanced"
    );

    public static final KeyBinding KEY_PLACEMENT_RADIAL = new KeyBinding(
        "key.ae2enhanced.placement_radial",
        KeyConflictContext.IN_GAME,
        KeyModifier.NONE,
        Keyboard.KEY_G,
        "key.categories.ae2enhanced"
    );

    public static void init() {
        ClientRegistry.registerKeyBinding(KEY_MODE);
        ClientRegistry.registerKeyBinding(KEY_SILK);
        ClientRegistry.registerKeyBinding(KEY_DROP);
        ClientRegistry.registerKeyBinding(KEY_CONFIG);
        ClientRegistry.registerKeyBinding(KEY_PLACEMENT_RADIAL);
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.player == null) return;

        ItemStack held = mc.player.getHeldItemMainhand();
        boolean isPlacementTool = held.getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
        boolean isOmniPlacement = held.getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool
                && com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool.getMode(held)
                    == com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool.MODE_PLACEMENT;

        // 中键选取（鼠标按键 2）
        if (event.getButton() == 2 && event.isButtonstate()) {
            if (!isPlacementTool && !isOmniPlacement) return;
            event.setCanceled(true);
            AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementSelectPreset(
                    com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig.MAX_PRESETS));
            return;
        }

        // Shift + 滚轮切换单格/批量（仅 Omni Tool 放置模式）
        if (isOmniPlacement && org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                && event.getDwheel() != 0) {
            event.setCanceled(true);
            AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniToolPlacementSubMode(event.getDwheel() > 0));
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KEY_CONFIG.isPressed()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.player != null) {
                for (net.minecraft.util.EnumHand hand : net.minecraft.util.EnumHand.values()) {
                    if (mc.player.getHeldItem(hand).getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool) {
                        AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniToolGui(hand.ordinal()));
                        break;
                    }
                }
            }
        } else if (KEY_DROP.isPressed()) {
            AE2Enhanced.network.sendToServer(new PacketOmniToolDropMode());
        } else if (KEY_SILK.isPressed()) {
            AE2Enhanced.network.sendToServer(new PacketOmniToolSilkTouch());
        } else if (KEY_MODE.isPressed()) {
            AE2Enhanced.network.sendToServer(new PacketOmniToolMode());
        } else if (KEY_PLACEMENT_RADIAL.isPressed()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.player != null) {
                net.minecraft.item.ItemStack held = mc.player.getHeldItemMainhand();
                boolean canOpen = held.getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool
                        || (held.getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool
                            && com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool.getMode(held)
                                == com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool.MODE_PLACEMENT);
                if (canOpen) {
                    mc.displayGuiScreen(new com.github.aeddddd.ae2enhanced.client.gui.GuiPlacementRadialMenu(
                            mc.player, KEY_PLACEMENT_RADIAL.getKeyCode()));
                }
            }
        }
    }
}
