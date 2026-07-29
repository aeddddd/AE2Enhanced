package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

import appeng.core.definitions.AEBlocks;

import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

/**
 * 超因果计算核心结构中心渲染器.
 * <p>渲染中心为立方体结构的几何中心（控制器背面 5 格）,位于 9x9x9 玻璃腔内,
 * 透过全透明的 casing_glass 可见.</p>
 * <p>渲染内容：中央悬浮一台缓慢自旋的实际合成 CPU（64k 合成存储器）,
 * 外围两道异面轨道环环绕,表现"超因果计算"的核心意象.</p>
 */
public class ComputationCoreRenderer extends AbstractMultiblockRenderer<ComputationCoreBlockEntity> {

    /** 渲染内容半径上限：完全收在 9x9x9 玻璃腔内. */
    private static final double RENDER_RADIUS = 4.0;

    /** 轨道环颜色（项目青色主色调）. */
    private static final int RING_COLOR = 0x50C8FF;

    public ComputationCoreRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderEffect(ComputationCoreBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Vec3 center = getEffectCenterOffset(be);
        float time = getTime(be, partialTicks);

        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);

        // 中央悬浮 CPU:64k 合成存储器,缓慢自旋 + 轻微上下浮动,全亮度使其在密封腔内可见
        poseStack.pushPose();
        float bob = Mth.sin(time * 0.06f) * 0.08f;
        float spin = (time * 1.5f) % 360.0f;
        poseStack.translate(0, bob, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(1.25f, 1.25f, 1.25f);
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState(),
                poseStack, bufferSource, LightTexture.FULL_BRIGHT, packedOverlay,
                ModelData.EMPTY, null);
        poseStack.popPose();

        // 两道异面轨道环,异速异向旋转
        VertexConsumer lines = bufferSource.getBuffer(RenderHelper.TESR_LINES);
        int segments = lodSegments((float) center.distanceTo(
                context.getBlockEntityRenderDispatcher().camera.getPosition()), 48, 12);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees((time * 2.0f) % 360.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(20.0f));
        RenderHelper.drawRing(lines, poseStack, 1.7f, RING_COLOR, 0.85f, segments);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-(time * 1.2f) % 360.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(65.0f));
        RenderHelper.drawRing(lines, poseStack, 2.1f, RING_COLOR, 0.6f, segments);
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    protected boolean isRendererEnabled() {
        return true;
    }

    @Override
    protected Vec3 getEffectCenterOffset(ComputationCoreBlockEntity be) {
        // 结构旋转方向与 SupercausalStructure 一致:控制器朝向的反方向
        Direction rotation = getFacing(be).getOpposite();
        float[] bounds = computeBounds(SupercausalStructure.getAllSet(), rotation);
        return computeCenterOffset(bounds);
    }

    @Override
    protected double getRenderRadius() {
        return RENDER_RADIUS;
    }
}
