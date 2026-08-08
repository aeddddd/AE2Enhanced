package com.github.aeddddd.ae2enhanced.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

/**
 * 超因果计算核心结构中心渲染器.
 * <p>渲染中心为立方体结构的几何中心（控制器背面 5 格）,位于 9x9x9 玻璃腔内,
 * 透过全透明的 casing_glass 可见.</p>
 * <p>渲染内容：动态展示当前合成目标集合——单个目标居中悬浮自旋,
 * 多个目标沿水平轨道环绕公转,外围两道异面轨道环作为常驻环境特效.
 * 无活跃任务时腔内空无一物,仅保留轨道环.</p>
 */
public class ComputationCoreRenderer extends AbstractMultiblockRenderer<ComputationCoreBlockEntity> {

    /** 渲染内容半径上限：完全收在 9x9x9 玻璃腔内. */
    private static final double RENDER_RADIUS = 4.0;

    /** 单目标居中显示时的缩放. */
    private static final float SINGLE_SCALE = 1.6f;

    /** 多目标轨道显示时的单物品缩放. */
    private static final float ORBIT_ITEM_SCALE = 0.9f;

    /** 多目标轨道半径. */
    private static final float ORBIT_RADIUS = 1.6f;

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

        // 动态合成目标：全亮度使其在密封腔内可见
        List<ItemStack> targets = be.getClientCraftingTargets();
        if (targets.size() == 1) {
            // 单目标：居中悬浮,缓慢自旋 + 轻微上下浮动
            poseStack.pushPose();
            float bob = Mth.sin(time * 0.06f) * 0.08f;
            poseStack.translate(0, bob, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees((time * 1.5f) % 360.0f));
            renderTargetItem(targets.get(0), SINGLE_SCALE, be, poseStack, bufferSource, packedOverlay);
            poseStack.popPose();
        } else if (!targets.isEmpty()) {
            // 多目标：均布在水平轨道上,整体缓旋,各目标相位错开浮动
            int count = targets.size();
            float orbitAngle = time * 0.5f;
            for (int i = 0; i < count; i++) {
                float angle = orbitAngle + (float) (Math.PI * 2.0) * i / count;
                float bob = Mth.sin(time * 0.06f + i * 1.3f) * 0.08f;
                poseStack.pushPose();
                poseStack.translate(Mth.cos(angle) * ORBIT_RADIUS, bob, Mth.sin(angle) * ORBIT_RADIUS);
                poseStack.mulPose(Axis.YP.rotationDegrees((time * 1.5f + i * 45.0f) % 360.0f));
                renderTargetItem(targets.get(i), ORBIT_ITEM_SCALE, be, poseStack, bufferSource, packedOverlay);
                poseStack.popPose();
            }
        }

        // 两道异面轨道环,异速异向旋转
        VertexConsumer lines = bufferSource.getBuffer(RenderHelper.TESR_LINES);
        int segments = lodSegments((float) center.distanceTo(
                context.getBlockEntityRenderDispatcher().camera.getPosition()), 48, 12);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees((time * 2.0f) % 360.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(20.0f));
        RenderHelper.drawRing(lines, poseStack, 2.0f, RING_COLOR, 0.85f, segments);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-(time * 1.2f) % 360.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(65.0f));
        RenderHelper.drawRing(lines, poseStack, 2.5f, RING_COLOR, 0.6f, segments);
        poseStack.popPose();

        poseStack.popPose();
    }

    /**
     * 以指定缩放渲染一个合成目标物品,方块类物品（如 ME 控制器）会渲染为 3D 方块模型.
     */
    private static void renderTargetItem(ItemStack stack, float scale, ComputationCoreBlockEntity be,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, packedOverlay,
                poseStack, bufferSource, be.getLevel(), 0);
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
