package com.github.aeddddd.ae2enhanced.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;

/**
 * ME 放置工具 HUD —— 显示当前模式、选中物品、线缆颜色、绑定状态。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PlacementToolHudRenderer {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().equals(VanillaGuiOverlay.HOTBAR.id())) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        boolean isOmniPlacement = held.getItem() instanceof AdvancedMEOmniToolItem
                && AdvancedMEOmniToolItem.getMode(held) == AdvancedMEOmniToolItem.MODE_PLACEMENT;
        if (!isOmniPlacement) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int x = event.getWindow().getGuiScaledWidth() / 2 + 16;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 8;

        PlacementConfig config = new PlacementConfig(held);
        ItemStack off = player.getOffhandItem();
        ItemStack selected = !off.isEmpty() && PlacementTargetResolver.isPlaceable(off)
                ? off
                : config.getSelectedStack();
        PlacementMode mode = config.getPlacementMode();

        // 绑定状态
        Component status = Component.translatable(OmniToolNetworkLink.isLinked(held)
                ? "item.ae2enhanced.me_placement_tool.linked"
                : "item.ae2enhanced.me_placement_tool.unlinked");
        guiGraphics.drawString(mc.font, status, x, y - 24, 0xFFFFFF);

        // 模式
        Component modeName = Component.translatable("gui.ae2enhanced.placement.mode." + mode.name().toLowerCase());
        guiGraphics.drawString(mc.font, modeName, x, y - 12, 0xAAAAAA);

        // 当前选中物品
        if (!selected.isEmpty()) {
            guiGraphics.renderItem(selected, x, y);
            guiGraphics.renderItemDecorations(mc.font, selected, x, y);

            guiGraphics.drawString(mc.font, selected.getHoverName(), x + 20, y + 4, 0xFFFFFF);
        } else {
            guiGraphics.drawString(mc.font,
                    Component.translatable("item.ae2enhanced.me_placement_tool.no_selection"), x + 20, y + 4,
                    0xFFFFFF);
        }

        // 线缆颜色
        if (PlacementTargetResolver.isCable(selected)) {
            Component colorName = Component.translatable(config.getCableColor().translationKey);
            guiGraphics.drawString(mc.font,
                    Component.translatable("gui.ae2enhanced.placement.cable_color", colorName), x + 20, y + 14,
                    0xFFFFFF);
        }
    }
}
