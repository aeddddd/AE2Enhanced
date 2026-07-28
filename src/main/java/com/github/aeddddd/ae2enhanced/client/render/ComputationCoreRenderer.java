package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

/**
 * 超因果计算核心结构中心渲染器.
 * <p>渲染中心为立方体结构的几何中心（控制器背面 5 格）,位于 9x9x9 玻璃腔内,
 * 透过全透明的 casing_glass 可见.</p>
 * <p>当前仅搭好框架：成形检查、距离裁剪、中心定位与渲染包围盒均已就绪,
 * 具体渲染内容待定（半径预留 4 格,完全收在玻璃腔内）.</p>
 */
public class ComputationCoreRenderer extends AbstractMultiblockRenderer<ComputationCoreBlockEntity> {

    /** 渲染内容半径上限：完全收在 9x9x9 玻璃腔内. */
    private static final double RENDER_RADIUS = 4.0;

    public ComputationCoreRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderEffect(ComputationCoreBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // TODO: 结构中心渲染内容待定.框架已就位(中心见 getEffectCenterOffset),
        // 后续在此绘制,内容半径不超过 RENDER_RADIUS.
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
