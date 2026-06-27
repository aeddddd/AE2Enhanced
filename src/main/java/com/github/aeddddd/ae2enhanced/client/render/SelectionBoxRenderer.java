package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * 通用内存卡选取方块的客户端边框高亮渲染.
 */
@SideOnly(Side.CLIENT)
public class SelectionBoxRenderer {

    private static final float R = 0x3A / 255.0f;
    private static final float G = 0x8E / 255.0f;
    private static final float B = 0xBF / 255.0f;
    private static final float A = 0.8f;
    private static final double MAX_DISTANCE_SQ = 32.0 * 32.0;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemUniversalMemoryCard)) return;

        List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(stack);
        if (selections.isEmpty()) return;

        World world = player.world;
        int dim = world.provider.getDimension();

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);

        for (ItemUniversalMemoryCard.SelectionEntry entry : selections) {
            if (entry.dim != dim) continue;
            if (!world.isBlockLoaded(entry.pos)) continue;

            double dx = entry.pos.getX() + 0.5 - px;
            double dy = entry.pos.getY() + 0.5 - py;
            double dz = entry.pos.getZ() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) continue;

            AxisAlignedBB bb = new AxisAlignedBB(entry.pos).grow(0.002);
            drawBoxEdges(buffer, bb);
        }

        tessellator.draw();

        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawBoxEdges(BufferBuilder buffer, AxisAlignedBB bb) {
        // 底面
        buffer.pos(bb.minX, bb.minY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.minY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.minY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.minY, bb.minZ).color(R, G, B, A).endVertex();

        // 顶面
        buffer.pos(bb.minX, bb.maxY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.minZ).color(R, G, B, A).endVertex();

        // 竖线
        buffer.pos(bb.minX, bb.minY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.minZ).color(R, G, B, A).endVertex();

        buffer.pos(bb.maxX, bb.minY, bb.minZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.minZ).color(R, G, B, A).endVertex();

        buffer.pos(bb.maxX, bb.minY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.maxZ).color(R, G, B, A).endVertex();

        buffer.pos(bb.minX, bb.minY, bb.maxZ).color(R, G, B, A).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.maxZ).color(R, G, B, A).endVertex();
    }
}
