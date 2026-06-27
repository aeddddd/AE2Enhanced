package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 区块供电节点目标高亮渲染器.
 * 收到服务端包后，在目标方块位置绘制线框，持续指定 tick 数.
 */
@SideOnly(Side.CLIENT)
public class ChunkPowerHighlightRenderer {

    private static final Map<BlockPos, Integer> HIGHLIGHTS = new HashMap<>();

    private static final float R = 0xFF / 255.0f;
    private static final float G = 0xA5 / 255.0f;
    private static final float B = 0x00 / 255.0f;
    private static final float A = 0.8f;
    private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;

    public static void addHighlights(PacketChunkPowerHighlight packet) {
        int duration = packet.getDurationTicks();
        for (BlockPos pos : packet.getTargets()) {
            HIGHLIGHTS.put(pos, duration);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<BlockPos, Integer>> it = HIGHLIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (HIGHLIGHTS.isEmpty()) return;

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

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

        for (Map.Entry<BlockPos, Integer> entry : HIGHLIGHTS.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!world.isBlockLoaded(pos)) continue;

            double dx = pos.getX() + 0.5 - px;
            double dy = pos.getY() + 0.5 - py;
            double dz = pos.getZ() + 0.5 - pz;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) continue;

            AxisAlignedBB bb = new AxisAlignedBB(pos).grow(0.002);
            drawBoxEdges(buffer, bb);
        }

        tessellator.draw();

        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawBoxEdges(BufferBuilder buffer, AxisAlignedBB bb) {
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
