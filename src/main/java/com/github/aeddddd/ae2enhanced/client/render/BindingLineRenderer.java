package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;

/**
 * 客户端绑定高亮渲染器(移植自 1.12 BindingLineRenderer).
 *
 * <p>1.12 渲染中枢 ME 接口一对多网络的源/目标描边;1.20 绑定逻辑重构为无线访问点后,
 * 改为:玩家主手持有已绑定的通用内存卡时,为绑定的无线访问点渲染青色描边,
 * 并绘制一条玩家到访问点的连线.</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BindingLineRenderer {

    // 访问点描边:青色
    private static final float R = 0.0f;
    private static final float G = 1.0f;
    private static final float B = 1.0f;
    private static final float A = 0.85f;
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    private BindingLineRenderer() {
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

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof UniversalMemoryCardItem)) {
            return;
        }

        GlobalPos linked = UMCNetworkLink.getLinkedPos(held);
        if (linked == null || linked.dimension() != player.level().dimension()) {
            return;
        }

        BlockPos pos = linked.pos();
        if (!player.level().isLoaded(pos)) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        double dx = pos.getX() + 0.5 - camera.x;
        double dy = pos.getY() + 0.5 - camera.y;
        double dz = pos.getZ() + 0.5 - camera.z;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RenderHelper.TESR_LINES);
        Matrix4f matrix = poseStack.last().pose();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        // 访问点描边
        drawBoxOutline(buffer, matrix, new AABB(pos).inflate(0.002));

        // 玩家视线到访问点中心的连线
        Vec3 eye = player.getEyePosition(event.getPartialTick());
        line(buffer, matrix, eye.x, eye.y - 0.2, eye.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static void drawBoxOutline(VertexConsumer buffer, Matrix4f matrix, AABB bb) {
        // 底面 4 条边
        line(buffer, matrix, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        line(buffer, matrix, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        line(buffer, matrix, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        line(buffer, matrix, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ);

        // 顶面 4 条边
        line(buffer, matrix, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        line(buffer, matrix, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        line(buffer, matrix, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        line(buffer, matrix, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);

        // 侧面 4 条边
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
