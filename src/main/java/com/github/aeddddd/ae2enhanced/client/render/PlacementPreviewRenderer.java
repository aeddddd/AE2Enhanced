package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.CablePlacementHelper;
import com.github.aeddddd.ae2enhanced.util.placement.ConstructionWandHelper;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * 放置工具预览渲染器：批量放置位置预览 + 线缆路径预览 + 线缆起点高亮。
 */
@SideOnly(Side.CLIENT)
public class PlacementPreviewRenderer {

    private static final float R = 0.0f;
    private static final float G = 0.75f;
    private static final float B = 1.0f;
    private static final float A = 0.6f;
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        ItemStack stack = player.getHeldItemMainhand();
        boolean isPlacementTool = stack.getItem() instanceof ItemMEPlacementTool;
        boolean isOmniPlacement = stack.getItem() instanceof ItemAdvancedMEOmniTool
                && ItemAdvancedMEOmniTool.getMode(stack) == ItemAdvancedMEOmniTool.MODE_PLACEMENT;
        if (!isPlacementTool && !isOmniPlacement) return;

        PlacementConfig config = new PlacementConfig(stack);
        World world = player.world;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        RayTraceResult ray = player.rayTrace(32.0, event.getPartialTicks());
        if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) return;

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

        // 线缆起点高亮
        BlockPos cableStart = config.getCableStart();
        if (cableStart != null) {
            drawBoxEdges(buffer, new AxisAlignedBB(cableStart).grow(0.005), 1.0f, 0.5f, 0.0f, 0.8f);
            if (ray != null) {
                BlockPos end = ray.getBlockPos().offset(ray.sideHit);
                List<BlockPos> path = CablePlacementHelper.calculatePath(cableStart, end);
                for (BlockPos pos : path) {
                    if (!pos.equals(cableStart)) {
                        drawBoxEdges(buffer, new AxisAlignedBB(pos).grow(0.002), R, G, B, 0.4f);
                    }
                }
            }
        }

        // 批量放置预览
        PlacementMode mode = config.getPlacementMode();
        ItemStack target = PlacementTargetResolver.resolveBulk(player, world, ray.getBlockPos());
        if (mode == PlacementMode.BULK && !target.isEmpty()) {
            List<BlockPos> positions = ConstructionWandHelper.calculatePositions(world, ray.getBlockPos(), ray.sideHit, config.getPlacementRestriction());
            for (BlockPos pos : positions) {
                drawBoxEdges(buffer, new AxisAlignedBB(pos).grow(0.002), R, G, B, A);
            }
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
