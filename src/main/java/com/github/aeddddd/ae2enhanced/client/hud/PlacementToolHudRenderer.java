package com.github.aeddddd.ae2enhanced.client.hud;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * ME 放置工具 HUD —— 显示当前模式、选中物品、线缆颜色、绑定状态。
 */
public class PlacementToolHudRenderer {

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        ItemStack held = player.getHeldItemMainhand();
        boolean isPlacementTool = held.getItem() instanceof ItemMEPlacementTool;
        boolean isOmniPlacement = held.getItem() instanceof ItemAdvancedMEOmniTool
                && ItemAdvancedMEOmniTool.getMode(held) == ItemAdvancedMEOmniTool.MODE_PLACEMENT;
        if (!isPlacementTool && !isOmniPlacement) return;

        ScaledResolution sr = event.getResolution();
        int x = sr.getScaledWidth() / 2 + 16;
        int y = sr.getScaledHeight() / 2 + 8;

        PlacementConfig config = new PlacementConfig(held);
        ItemStack off = player.getHeldItemOffhand();
        ItemStack selected = !off.isEmpty() && PlacementTargetResolver.isPlaceable(off)
                ? off
                : config.getSelectedStack();
        PlacementMode mode = config.getPlacementMode();

        // 绑定状态
        String status = SecurityTerminalBindingHelper.isLinked(held)
                ? I18n.format("item.ae2enhanced.me_placement_tool.linked")
                : I18n.format("item.ae2enhanced.me_placement_tool.unlinked");
        mc.fontRenderer.drawString(status, x, y - 24, 0xFFFFFF);

        // 模式
        String modeName = I18n.format("gui.ae2enhanced.placement.mode." + mode.name().toLowerCase());
        mc.fontRenderer.drawString(modeName, x, y - 12, 0xAAAAAA);

        // 当前选中物品
        if (!selected.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(selected, x, y);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, selected, x, y, null);
            RenderHelper.disableStandardItemLighting();

            String name = selected.getDisplayName();
            mc.fontRenderer.drawString(name, x + 20, y + 4, 0xFFFFFF);
        } else {
            mc.fontRenderer.drawString(I18n.format("item.ae2enhanced.me_placement_tool.no_selection"), x + 20, y + 4, 0xFFFFFF);
        }

        // 线缆颜色
        if (PlacementTargetResolver.isCable(selected)) {
            String colorName = I18n.format("gui.appliedenergistics2." + config.getCableColor().name());
            mc.fontRenderer.drawString(I18n.format("gui.ae2enhanced.placement.cable_color", colorName), x + 20, y + 14, 0xFFFFFF);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
