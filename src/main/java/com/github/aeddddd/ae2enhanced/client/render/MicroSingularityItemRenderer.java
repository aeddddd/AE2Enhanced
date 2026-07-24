package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 被约束微型奇点（物品形态）的自定义渲染器.
 * 方块形态黑洞渲染样式的微缩版：黑色事件视界小球 + 缓慢旋转的发光吸积环.
 * 使用内建 RenderType（solid / additive）,保证在 GUI、第一人称与掉落物实体上均可工作.
 */
public class MicroSingularityItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final float HORIZON_RADIUS = 0.22f;
    private static final float DISK_INNER = 0.26f;
    private static final float DISK_OUTER = 0.42f;
    private static final int SPHERE_SEGMENTS = 16;
    private static final int DISK_SEGMENTS = 32;

    public MicroSingularityItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;

        poseStack.pushPose();
        // builtin/entity 模型的渲染原点在物品包围盒角点,先平移到中心（同原版 BEWLR 约定）
        poseStack.translate(0.5, 0.5, 0.5);

        // 事件视界：纯黑实心小球
        VertexConsumer solid = bufferSource.getBuffer(RenderHelper.TESR_SOLID);
        RenderHelper.drawSphere(solid, poseStack, HORIZON_RADIUS, 0x000000, 1.0f, SPHERE_SEGMENTS, SPHERE_SEGMENTS);

        // 吸积环：暖橙到紫的发光盘,缓慢旋转并略带呼吸
        float breath = 0.75f + 0.25f * Mth.sin(time * 2.0f);
        VertexConsumer glow = bufferSource.getBuffer(RenderHelper.TESR_ADDITIVE);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 45.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(15.0f));
        RenderHelper.drawAccretionDisk(glow, poseStack, DISK_INNER * breath, DISK_OUTER * breath, 0xB048E0,
                DISK_SEGMENTS);
        poseStack.popPose();

        // 微弱紫色辉光球
        VertexConsumer halo = bufferSource.getBuffer(RenderHelper.TESR_TRANSLUCENT);
        RenderHelper.drawSphere(halo, poseStack, HORIZON_RADIUS * 1.6f, 0x2A0A44, 0.35f * breath,
                SPHERE_SEGMENTS, SPHERE_SEGMENTS);

        poseStack.popPose();
    }
}
