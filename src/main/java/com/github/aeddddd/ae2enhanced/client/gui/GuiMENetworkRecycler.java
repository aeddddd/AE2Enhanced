package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * ME 网络回收节点的客户端 GUI.
 *
 * <p>仅用于信息展示：目标数量、网络状态、最近回收统计.</p>
 */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public class GuiMENetworkRecycler extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/me_network_recycler.png");

    private final TileMENetworkRecycler tile;

    public GuiMENetworkRecycler(InventoryPlayer playerInventory, ContainerMENetworkRecycler container) {
        super(container);
        this.tile = container.getTile();
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.ae2enhanced.me_network_recycler.name");
        fontRenderer.drawString(title, (xSize - fontRenderer.getStringWidth(title)) / 2, 6, 0x404040);

        fontRenderer.drawString(I18n.format("gui.ae2enhanced.me_network_recycler.targets",
                tile.getClientTargetCount()), 8, 22, 0x404040);

        String statusKey = tile.isClientActive() ? "gui.ae2enhanced.me_network_recycler.active"
                : tile.isClientPowered() ? "gui.ae2enhanced.me_network_recycler.powered"
                : "gui.ae2enhanced.me_network_recycler.offline";
        fontRenderer.drawString(I18n.format(statusKey), 8, 34, 0x404040);

        fontRenderer.drawString(I18n.format("gui.ae2enhanced.me_network_recycler.last_recycled",
                tile.getClientLastRecycledCount()), 8, 46, 0x404040);

        fontRenderer.drawString(I18n.format("container.inventory"), 8, ySize - 96 + 2, 0x404040);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }
}
