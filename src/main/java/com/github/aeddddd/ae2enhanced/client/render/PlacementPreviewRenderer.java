package com.github.aeddddd.ae2enhanced.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.util.placement.CablePlacementHelper;
import com.github.aeddddd.ae2enhanced.util.placement.ConstructionWandHelper;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;

/**
 * 放置工具预览渲染器：批量放置位置预览 + 线缆路径预览 + 线缆起点高亮。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PlacementPreviewRenderer {

    private static final float R = 0.0f;
    private static final float G = 0.75f;
    private static final float B = 1.0f;
    private static final float A = 0.6f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        boolean isOmniPlacement = stack.getItem() instanceof AdvancedMEOmniToolItem
                && AdvancedMEOmniToolItem.getMode(stack) == AdvancedMEOmniToolItem.MODE_PLACEMENT;
        if (!isOmniPlacement) return;

        PlacementConfig config = new PlacementConfig(stack);
        Level level = player.level();

        HitResult ray = player.pick(32.0, event.getPartialTick(), false);
        if (!(ray instanceof BlockHitResult blockHit) || ray.getType() != HitResult.Type.BLOCK) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderHelper.TESR_LINES);
        Matrix4f matrix = poseStack.last().pose();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // 线缆起点高亮
        BlockPos cableStart = config.getCableStart();
        if (cableStart != null) {
            drawBoxEdges(buffer, matrix, new AABB(cableStart).inflate(0.005), 1.0f, 0.5f, 0.0f, 0.8f);
            BlockPos end = blockHit.getBlockPos().relative(blockHit.getDirection());
            List<BlockPos> path = CablePlacementHelper.calculatePath(cableStart, end);
            for (BlockPos pos : path) {
                if (!pos.equals(cableStart)) {
                    drawBoxEdges(buffer, matrix, new AABB(pos).inflate(0.002), R, G, B, 0.4f);
                }
            }
        }

        // 批量放置预览
        PlacementMode mode = config.getPlacementMode();
        ItemStack target = PlacementTargetResolver.resolveBulk(player, level, blockHit.getBlockPos());
        if (mode == PlacementMode.BULK && !target.isEmpty()) {
            List<BlockPos> positions = ConstructionWandHelper.calculatePositions(level, blockHit.getBlockPos(),
                    blockHit.getDirection(), config.getPlacementRestriction());
            for (BlockPos pos : positions) {
                drawBoxEdges(buffer, matrix, new AABB(pos).inflate(0.002), R, G, B, A);
            }
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static void drawBoxEdges(VertexConsumer buffer, Matrix4f matrix, AABB bb,
            float r, float g, float b, float a) {
        // 底面
        line(buffer, matrix, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, r, g, b, a);
        line(buffer, matrix, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, r, g, b, a);
        line(buffer, matrix, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ, r, g, b, a);
        line(buffer, matrix, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ, r, g, b, a);

        // 顶面
        line(buffer, matrix, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        line(buffer, matrix, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        line(buffer, matrix, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
        line(buffer, matrix, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);

        // 竖线
        line(buffer, matrix, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        line(buffer, matrix, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        line(buffer, matrix, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        line(buffer, matrix, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
    }

    private static void line(VertexConsumer buffer, Matrix4f matrix,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
    }
}
