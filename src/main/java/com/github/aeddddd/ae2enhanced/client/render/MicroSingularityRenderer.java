package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import com.mojang.blaze3d.shaders.Uniform;

import com.github.aeddddd.ae2enhanced.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 微型奇点渲染器：继承装配枢纽黑洞 shader 的迷你版.
 * <p>复用 assembly_black_hole shader 的事件视界（部件 0）与吸积盘（部件 1）,
 * 通过 uScale 将 shader 内置尺寸常量整体缩小 {@link #MICRO_SCALE} 倍；
 * 不绘制相对论性喷流与约束壳,也不接入全屏光线步进后处理.</p>
 * <p>shader 不可用或兼容模式开启时回退为黑色小球 + 紫色光晕.</p>
 */
public class MicroSingularityRenderer implements BlockEntityRenderer<MicroSingularityBlockEntity> {

    /** 相对装配枢纽黑洞的缩放系数（事件视界 4.0 × 0.08 = 0.32 格） */
    private static final float MICRO_SCALE = 0.08f;

    private static final float EVENT_HORIZON_RADIUS = 4.0f * MICRO_SCALE;
    private static final float DISK_INNER = 4.6f * MICRO_SCALE;
    private static final float DISK_OUTER = 12.0f * MICRO_SCALE;

    private static final int EVENT_HORIZON_PART_ID = 0x000000;
    private static final int ACCRETION_DISK_PART_ID = 0x010000;

    private static final int SPHERE_SEGMENTS = 16;
    private static final int DISK_SEGMENTS = 48;

    private final BlockEntityRendererProvider.Context context;

    public MicroSingularityRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(MicroSingularityBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) {
            return;
        }
        double renderDist = AE2EnhancedConfig.CLIENT.renderDistance.get();
        Vec3 cameraPos = context.getBlockEntityRenderDispatcher().camera.getPosition();
        Vec3 centerWorld = Vec3.atCenterOf(be.getBlockPos());
        if (centerWorld.distanceTo(cameraPos) > renderDist) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        float time = (be.getLevel().getGameTime() + partialTicks) * 0.05f;

        if (shouldUseShader()) {
            // 独立的 shader 实例,uniform 与装配枢纽隔离；绘制后立即 endBatch,
            // 保证同一帧多个微型奇点各自以自己的 uCenter/uScale 结算,避免互相覆盖
            ShaderInstance shader = AE2EnhancedShaders.getMicroSingularity();
            applyUniforms(shader, time, centerWorld.subtract(cameraPos));
            VertexConsumer main = bufferSource.getBuffer(AE2ERenderTypes.MICRO_SINGULARITY_BLACK_HOLE);
            RenderHelper.drawSphere(main, poseStack, EVENT_HORIZON_RADIUS, EVENT_HORIZON_PART_ID, 1.0f,
                    SPHERE_SEGMENTS, SPHERE_SEGMENTS);
            RenderHelper.drawAccretionDisk(main, poseStack, DISK_INNER, DISK_OUTER, ACCRETION_DISK_PART_ID,
                    DISK_SEGMENTS);
            RenderHelper.endBatch(bufferSource);
        } else {
            renderFallback(poseStack, bufferSource, time);
        }

        poseStack.popPose();
    }

    /**
     * 是否使用自定义 shader 路径（与装配枢纽一致的配置与环境检查）.
     */
    private static boolean shouldUseShader() {
        return AE2EnhancedConfig.CLIENT.enableAssemblyShader.get()
                && !AE2EnhancedConfig.CLIENT.forceCompatibilityMode.get()
                && !ModList.get().isLoaded("oculus")
                && AE2EnhancedShaders.isMicroSingularityLoaded();
    }

    private static void applyUniforms(ShaderInstance shader, float time, Vec3 centerCam) {
        Uniform uTime = shader.getUniform("uTime");
        Uniform uIntensity = shader.getUniform("uIntensity");
        Uniform uScale = shader.getUniform("uScale");
        Uniform uCenter = shader.getUniform("uCenter");
        if (uTime != null) {
            uTime.set(time);
        }
        if (uIntensity != null) {
            uIntensity.set(AE2EnhancedConfig.CLIENT.dynamicRenderIntensity.get().floatValue());
        }
        if (uScale != null) {
            uScale.set(MICRO_SCALE);
        }
        if (uCenter != null) {
            uCenter.set((float) centerCam.x, (float) centerCam.y, (float) centerCam.z);
        }
    }

    /**
     * 回退渲染：黑色事件视界小球 + 缓慢旋转的紫色吸积光环.
     */
    private static void renderFallback(PoseStack poseStack, MultiBufferSource bufferSource, float time) {
        VertexConsumer solid = bufferSource.getBuffer(RenderHelper.TESR_SOLID);
        RenderHelper.drawSphere(solid, poseStack, EVENT_HORIZON_RADIUS, 0x000000, 0.99f,
                SPHERE_SEGMENTS, SPHERE_SEGMENTS);

        VertexConsumer glow = bufferSource.getBuffer(RenderHelper.TESR_ADDITIVE);
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 40.0f));
        RenderHelper.drawAccretionDisk(glow, poseStack, DISK_INNER, DISK_OUTER, 0x8A2BE2, DISK_SEGMENTS);
        poseStack.popPose();
    }
}
