package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;

/**
 * 先进 ME 收集器收集区域的客户端线框渲染.
 *
 * <p>渲染开启可视化的收集器:青色线框为收集区域,橙色线框为中心点所在方块.</p>
 */
@SideOnly(Side.CLIENT)
public class CollectorBoundsRenderer {

    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (TileAdvancedMECollector.CLIENT_BOUNDS_VISIBLE.isEmpty()) return;
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

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

        // WeakHashMap -backed Set 遍历时可能并发修改,先拷贝快照
        for (TileAdvancedMECollector tile : new ArrayList<>(TileAdvancedMECollector.CLIENT_BOUNDS_VISIBLE)) {
            if (tile.isInvalid() || tile.getWorld() != player.world) continue;
            BlockPos pos = tile.getPos();
            if (!player.world.isBlockLoaded(pos)) continue;
            double dx = pos.getX() + 0.5 - px;
            double dy = pos.getY() + 0.5 - py;
            double dz = pos.getZ() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) continue;

            // 收集区域(青色)
            drawBoxEdges(buffer, tile.getCollectionArea().grow(0.002), 0x3A / 255f, 0x8E / 255f, 0xBF / 255f, 0.8f);
            // 中心点方块(橙色)
            drawBoxEdges(buffer, new AxisAlignedBB(tile.getRegionCenter()).grow(0.004), 1.0f, 0x8C / 255f, 0.2f, 0.9f);
        }

        tessellator.draw();

        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawBoxEdges(BufferBuilder buffer, AxisAlignedBB bb, float r, float g, float b, float a) {
        // 底面
        buffer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();

        // 顶面
        buffer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();

        // 竖线
        buffer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();

        buffer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();

        buffer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();

        buffer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        buffer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
    }
}
