package com.github.aeddddd.ae2enhanced.client.render;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Quaternionf;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

import com.mojang.blaze3d.shaders.Uniform;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;

/**
 * 装配枢纽控制器渲染器：在结构几何中心绘制按比例放大的黑洞事件视界与光晕。
 * <p>优先使用自定义 shader 渲染事件视界、吸积盘与相对论性喷流；
 * 当 shader 不可用或兼容模式开启时回退到原有 VertexConsumer 路径。</p>
 */
public class AssemblyHubRenderer extends AbstractMultiblockRenderer<AssemblyControllerBlockEntity> {

    // 旧结构近似半径，用于参数缩放基准
    private static final double OLD_STRUCTURE_RADIUS = 7.0;

    // 旧版黑洞渲染参数（来自 1.12 主分支）
    private static final double EVENT_HORIZON_RADIUS_BASE = 2.5;
    private static final double INNER_HALO_BASE = 3.2;
    private static final double MID_HALO_BASE = 4.6;
    private static final double OUTER_HALO_BASE = 6.0;

    private static final int LATITUDE_SEGMENTS = 24;
    private static final int LONGITUDE_SEGMENTS = 24;
    private static final int GRID_LAT = 8;
    private static final int GRID_LON = 12;
    private static final float ROTATION_SPEED = 0.25f;

    // shader 部件 ID：通过顶点颜色 R 通道区分
    private static final int EVENT_HORIZON_PART_ID = 0x000000;
    private static final int ACCRETION_DISK_PART_ID = 0x010000;
    private static final int RELATIVISTIC_JET_PART_ID = 0x020000;

    // 按朝向缓存的结构包围盒，首次使用时计算
    private static final Map<Direction, float[]> BOUNDS_CACHE = new EnumMap<>(Direction.class);

    private static float[] getBounds(Direction facing) {
        return BOUNDS_CACHE.computeIfAbsent(facing,
                f -> computeBounds(AssemblyStructure.getAllSet(), f));
    }

    public AssemblyHubRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected boolean isRendererEnabled() {
        return AE2EnhancedConfig.CLIENT.enableAssemblyRenderer.get();
    }

    @Override
    protected Vec3 getEffectCenterOffset(AssemblyControllerBlockEntity be) {
        Direction facing = getFacing(be);
        float[] bounds = getBounds(facing);
        return computeCenterOffset(bounds);
    }

    @Override
    protected double getRenderRadius() {
        // 渲染半径约为结构最大半径 + 外光晕
        Direction facing = Direction.NORTH;
        float[] bounds = getBounds(facing);
        double structureRadius = computeRadius(bounds);
        return structureRadius + OUTER_HALO_BASE * getScaleFactor(bounds);
    }

    @Override
    protected void renderEffect(AssemblyControllerBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = getFacing(be);
        float[] bounds = getBounds(facing);
        double scale = getScaleFactor(bounds);

        Vec3 centerOffset = getEffectCenterOffset(be);
        poseStack.translate(centerOffset.x, centerOffset.y, centerOffset.z);

        double renderDist = AE2EnhancedConfig.CLIENT.renderDistance.get();
        Vec3 centerWorld = getEffectCenterWorld(be);
        double dist = centerWorld.distanceTo(context.getBlockEntityRenderDispatcher().camera.getPosition());
        if (dist > renderDist) {
            return;
        }

        if (shouldUseShader()) {
            renderShaderEffect(be, partialTicks, poseStack, bufferSource, scale, dist);
        } else {
            renderFallbackEffect(be, partialTicks, poseStack, bufferSource, scale, dist);
        }
    }

    /**
     * 是否使用自定义 shader 路径。
     */
    private static boolean shouldUseShader() {
        return AE2EnhancedConfig.CLIENT.enableAssemblyShader.get()
                && !AE2EnhancedConfig.CLIENT.forceCompatibilityMode.get()
                && !isOculusLoaded()
                && AE2EnhancedShaders.isAssemblyBlackHoleLoaded();
    }

    private static boolean isOculusLoaded() {
        return ModList.get().isLoaded("oculus");
    }

    /**
     * 使用自定义 shader 渲染黑洞主体与发光喷流。
     */
    private void renderShaderEffect(AssemblyControllerBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, double scale, double dist) {
        int lodLat = lodSegments(dist, LATITUDE_SEGMENTS, 8);
        int lodLon = lodSegments(dist, LONGITUDE_SEGMENTS, 12);

        float time = (be.getLevel().getGameTime() + partialTicks) * 0.05f;
        float intensity = AE2EnhancedConfig.CLIENT.dynamicRenderIntensity.get().floatValue();

        ShaderInstance shader = AE2EnhancedShaders.getAssemblyBlackHole();
        if (shader != null) {
            applyUniforms(shader, time, intensity);
        }

        // 事件视界 + 吸积盘（translucent）
        VertexConsumer main = bufferSource.getBuffer(RenderHelper.ASSEMBLY_BLACK_HOLE);
        RenderHelper.drawSphere(main, poseStack, (float) (EVENT_HORIZON_RADIUS_BASE * scale),
                EVENT_HORIZON_PART_ID, 1.0f, lodLat, lodLon);
        RenderHelper.drawAccretionDisk(main, poseStack, (float) (EVENT_HORIZON_RADIUS_BASE * scale * 1.2f),
                (float) (OUTER_HALO_BASE * scale * 1.3f), ACCRETION_DISK_PART_ID, 64);

        // 相对论性喷流（additive）
        VertexConsumer glow = bufferSource.getBuffer(RenderHelper.ASSEMBLY_BLACK_HOLE_GLOW);
        RenderHelper.drawRelativisticJet(glow, poseStack, (float) (1.0f * scale),
                (float) (8.0f * scale), RELATIVISTIC_JET_PART_ID, 32);
    }

    /**
     * 设置 shader 时间/强度 uniform。
     * <p>值会在 {@link ShaderInstance#apply()} 时被上传。</p>
     */
    private static void applyUniforms(ShaderInstance shader, float time, float intensity) {
        Uniform uTime = shader.getUniform("uTime");
        Uniform uIntensity = shader.getUniform("uIntensity");
        if (uTime != null) {
            uTime.set(time);
        }
        if (uIntensity != null) {
            uIntensity.set(intensity);
        }
    }

    /**
     * 原有 VertexConsumer 渲染路径，作为 shader 不可用时回退。
     */
    private void renderFallbackEffect(AssemblyControllerBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, double scale, double dist) {
        int lodLat = lodSegments(dist, LATITUDE_SEGMENTS, 8);
        int lodLon = lodSegments(dist, LONGITUDE_SEGMENTS, 12);

        float time = (be.getLevel().getGameTime() + partialTicks) * ROTATION_SPEED;
        float expand = 0.5f + 0.5f * Mth.sin(time * 0.5f);
        float brightness = 0.35f + 0.65f * (0.5f + 0.5f * Mth.sin(time * 0.35f));
        float gridEnergy = 0.5f + 0.5f * Mth.sin(time * 1.4f);

        double innerR = INNER_HALO_BASE * scale * (0.82 + 0.36 * expand);
        double midR = MID_HALO_BASE * scale * (0.88 + 0.24 * (1.0f - expand * 0.5f));
        double outerR = OUTER_HALO_BASE * scale * (0.92 + 0.16 * expand);

        float innerAlpha = 0.10f + 0.28f * brightness;
        float midAlpha = 0.06f + 0.14f * (1.0f - expand * 0.3f);
        float outerAlpha = 0.04f + 0.10f * brightness;

        // 事件视界：黑色实心球
        VertexConsumer solid = bufferSource.getBuffer(RenderHelper.TESR_SOLID);
        RenderHelper.drawSphere(solid, poseStack, (float) (EVENT_HORIZON_RADIUS_BASE * scale), 0x000000, 0.99f,
                lodLat, lodLon);

        // 内层光晕 + 线框
        VertexConsumer translucent = bufferSource.getBuffer(RenderHelper.TESR_TRANSLUCENT);
        VertexConsumer lines = bufferSource.getBuffer(RenderHelper.TESR_LINES);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.5f));
        poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(18.0f), 1.0f, 0.0f, 0.3f));
        RenderHelper.drawSphere(translucent, poseStack, (float) innerR, 0x140029, innerAlpha, lodLat, lodLon);
        RenderHelper.drawWireframeSphere(lines, poseStack, (float) innerR, 0x7700DD,
                0.28f * (0.5f + 0.5f * gridEnergy), GRID_LAT, GRID_LON);
        poseStack.popPose();

        // 中层光晕
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.3f));
        poseStack.mulPose(Axis.XP.rotationDegrees(12.0f));
        RenderHelper.drawSphere(translucent, poseStack, (float) midR, 0x05000D, midAlpha, lodLat, lodLon);
        RenderHelper.drawWireframeSphere(lines, poseStack, (float) midR, 0x110022,
                0.08f * (0.5f + 0.5f * gridEnergy), GRID_LAT, GRID_LON);
        poseStack.popPose();

        // 外层光晕
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.12f));
        poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(8.0f), 1.0f, 0.2f, 0.0f));
        RenderHelper.drawSphere(translucent, poseStack, (float) outerR, 0x020005, outerAlpha, lodLat, lodLon);
        poseStack.popPose();
    }

    private static double getScaleFactor(float[] bounds) {
        double maxDim = Math.max(bounds[3] - bounds[0], Math.max(bounds[4] - bounds[1], bounds[5] - bounds[2]));
        double newRadius = maxDim * 0.5;
        return newRadius / OLD_STRUCTURE_RADIUS;
    }
}
