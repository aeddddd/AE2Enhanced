package com.github.aeddddd.ae2enhanced.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;

/**
 * 通用内存卡选取方块的客户端边框高亮渲染(移植自 1.12 SelectionBoxRenderer).
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SelectionBoxRenderer {

    private static final float R = 0x3A / 255.0f;
    private static final float G = 0x8E / 255.0f;
    private static final float B = 0xBF / 255.0f;
    private static final float A = 0.8f;
    private static final double MAX_DISTANCE_SQ = 32.0 * 32.0;

    private SelectionBoxRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof UniversalMemoryCardItem)) {
            return;
        }

        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(stack);
        if (selections.isEmpty()) {
            return;
        }

        Level level = player.level();
        String dim = UniversalMemoryCardItem.dimensionId(level);

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderHelper.TESR_LINES);
        Matrix4f matrix = poseStack.last().pose();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (UniversalMemoryCardItem.SelectionEntry entry : selections) {
            if (!entry.dim.equals(dim)) {
                continue;
            }
            if (!level.isLoaded(entry.pos)) {
                continue;
            }

            double dx = entry.pos.getX() + 0.5 - camera.x;
            double dy = entry.pos.getY() + 0.5 - camera.y;
            double dz = entry.pos.getZ() + 0.5 - camera.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) {
                continue;
            }

            drawBoxEdges(buffer, matrix, new AABB(entry.pos).inflate(0.002));
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static void drawBoxEdges(VertexConsumer buffer, Matrix4f matrix, AABB bb) {
        // 底面
        line(buffer, matrix, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        line(buffer, matrix, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        line(buffer, matrix, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        line(buffer, matrix, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ);

        // 顶面
        line(buffer, matrix, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        line(buffer, matrix, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        line(buffer, matrix, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        line(buffer, matrix, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);

        // 竖线
        line(buffer, matrix, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        line(buffer, matrix, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        line(buffer, matrix, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ);
        line(buffer, matrix, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
    }

    private static void line(VertexConsumer buffer, Matrix4f matrix,
            double x1, double y1, double z1, double x2, double y2, double z2) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(R, G, B, A).endVertex();
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(R, G, B, A).endVertex();
    }
}
