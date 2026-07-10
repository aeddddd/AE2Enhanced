package com.github.aeddddd.ae2enhanced.client.render;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

/**
 * 超维度仓储中枢渲染器：实现“维度填充”全息效果。
 * <p>动态表现完全基于存储量（storageTotal）与物品种类数（storageTypes），
 * 不使用颜色变化，而是通过尺寸、旋转、几何层数、透明度、粒子密度等维度表达。</p>
 */
public class HyperdimensionalControllerRenderer extends AbstractMultiblockRenderer<HyperdimensionalControllerBlockEntity> {

    // 外立方体半边长
    private static final float OUTER_CUBE_HALF = 1.6f;
    // 内立方体基础半边长
    private static final float INNER_CUBE_HALF = 0.8f;
    // 核心基础大小
    private static final float CORE_BASE_SIZE = 0.35f;
    // 光环基础半径
    private static final float RING_BASE_RADIUS = 2.2f;
    // 最大日志数量级
    private static final float MAX_MAGNITUDE = 15.0f;
    // 结构中心相对控制器原点的偏移：结构中心 (0,0,2)，再抬高 2.5
    private static final Vec3 LOCAL_CENTER = new Vec3(0.0, 2.5, 2.0);

    private final Random random = new Random();

    public HyperdimensionalControllerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected boolean isRendererEnabled() {
        return AE2EnhancedConfig.CLIENT.enableHyperdimensionalRenderer.get();
    }

    @Override
    protected Vec3 getEffectCenterOffset(HyperdimensionalControllerBlockEntity be) {
        Direction facing = getFacing(be);
        return rotateOffsetByFacing(LOCAL_CENTER, facing);
    }

    @Override
    protected double getRenderRadius() {
        return 6.0;
    }

    @Override
    protected void renderEffect(HyperdimensionalControllerBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        Vec3 centerOffset = getEffectCenterOffset(be);
        poseStack.translate(centerOffset.x, centerOffset.y, centerOffset.z);

        float time = getTime(be, partialTicks);
        long total = be.getStorageTotal();
        int types = be.getStorageTypes();

        // ---- 动态参数 ----
        float magnitude = total <= 0 ? 0.0f : Math.min(MAX_MAGNITUDE, (float) Math.log10(total));
        float intensity = magnitude / MAX_MAGNITUDE; // 0.0 ~ 1.0
        float configIntensity = AE2EnhancedConfig.CLIENT.dynamicRenderIntensity.get().floatValue();
        intensity *= (float) Mth.clamp(configIntensity, 0.0, 2.0);
        intensity = Mth.clamp(intensity, 0.0f, 1.0f);

        int maxDynamic = AE2EnhancedConfig.CLIENT.maxDynamicElements.get();
        int ringCount = 1 + (int) (intensity * (maxDynamic - 1));
        int innerSubdivision = Math.max(1, Math.min(3, 1 + (int) (Math.sqrt(types) / 2.0)));
        float innerScale = 0.3f + 0.7f * intensity;
        float coreScale = CORE_BASE_SIZE * (0.4f + 0.6f * intensity);
        float rotationSpeed = 1.0f + 1.5f * intensity;
        float baseAlpha = 0.18f + 0.22f * intensity;
        float pulse = 0.5f + 0.5f * Mth.sin(time * (0.04f + 0.04f * intensity));
        float alpha = baseAlpha * (0.85f + 0.15f * pulse);

        VertexConsumer lines = bufferSource.getBuffer(RenderHelper.TESR_LINES);
        VertexConsumer additive = bufferSource.getBuffer(RenderHelper.TESR_ADDITIVE);
        VertexConsumer translucent = bufferSource.getBuffer(RenderHelper.TESR_TRANSLUCENT);

        // ---- 外立方体（固定框架） ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.15f * rotationSpeed));
        RenderHelper.drawCubeWireframe(lines, poseStack, OUTER_CUBE_HALF, 0x8800FF, 0.85f);
        poseStack.popPose();

        // ---- 内立方体 / 子立方体群（随存储量膨胀、细分） ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.4f * rotationSpeed));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 0.12f * rotationSpeed));
        drawInnerCubes(lines, poseStack, innerScale, innerSubdivision, alpha);
        poseStack.popPose();

        // ---- 内外顶点连接线（超立方体边） ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.15f * rotationSpeed));
        drawConnectionLines(lines, poseStack, OUTER_CUBE_HALF, INNER_CUBE_HALF * innerScale, 0x4400AA, alpha * 0.8f);
        poseStack.popPose();

        // ---- 同心环（随存储量增加） ----
        for (int i = 0; i < ringCount; i++) {
            float radius = RING_BASE_RADIUS + i * 0.7f;
            float ringAlpha = alpha * (1.0f - (float) i / ringCount * 0.6f);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (0.1f + 0.05f * i) * rotationSpeed));
            poseStack.mulPose(Axis.XP.rotationDegrees(15.0f * i));
            RenderHelper.drawRing(lines, poseStack, radius, 0xAA66FF, ringAlpha, 48 - i * 4);
            poseStack.popPose();
        }

        // ---- 核心八面体（脉冲发光） ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.6f * rotationSpeed));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 0.25f * rotationSpeed));
        RenderHelper.drawOctahedron(additive, poseStack, coreScale, 0xFFFFFF, alpha + 0.15f * pulse);
        // 核心内层小核
        RenderHelper.drawOctahedron(translucent, poseStack, coreScale * 0.5f, 0x00FFFF, alpha * 0.6f);
        poseStack.popPose();

        // ---- 粒子 ----
        spawnParticles(be, centerOffset, intensity);
    }

    private void drawInnerCubes(VertexConsumer consumer, PoseStack poseStack, float scale, int subdivision, float alpha) {
        poseStack.scale(scale, scale, scale);
        if (subdivision <= 1) {
            RenderHelper.drawCubeWireframe(consumer, poseStack, INNER_CUBE_HALF, 0x00FFFF, alpha * 0.8f);
            return;
        }

        // 细分：在立方体 8 个角放置小立方体，象征物品种类
        float step = INNER_CUBE_HALF * 2.0f / (subdivision + 1);
        float start = -INNER_CUBE_HALF + step;
        int count = 0;
        for (int x = 0; x < subdivision && count < 8; x++) {
            for (int y = 0; y < subdivision && count < 8; y++) {
                for (int z = 0; z < subdivision && count < 8; z++) {
                    poseStack.pushPose();
                    poseStack.translate(start + x * step - INNER_CUBE_HALF, start + y * step - INNER_CUBE_HALF,
                            start + z * step - INNER_CUBE_HALF);
                    RenderHelper.drawCubeWireframe(consumer, poseStack, INNER_CUBE_HALF * 0.3f, 0x00FFFF,
                            alpha * (0.6f + 0.4f * count / 8.0f));
                    poseStack.popPose();
                    count++;
                }
            }
        }
    }

    private void drawConnectionLines(VertexConsumer consumer, PoseStack poseStack,
            float outerHalf, float innerHalf, int color, float alpha) {
        float[][] outer = cubeVertices(outerHalf);
        float[][] inner = cubeVertices(innerHalf);
        Matrix4f matrix = poseStack.last().pose(); // 需要导入 com.mojang.math.Matrix4f

        for (int i = 0; i < 8; i++) {
            RenderHelper.drawLine(consumer, matrix,
                    outer[i][0], outer[i][1], outer[i][2],
                    inner[i][0], inner[i][1], inner[i][2], color, alpha);
        }
    }

    private float[][] cubeVertices(float half) {
        return new float[][] {
                { -half, -half, -half },
                { half, -half, -half },
                { half, half, -half },
                { -half, half, -half },
                { -half, -half, half },
                { half, -half, half },
                { half, half, half },
                { -half, half, half }
        };
    }

    private void spawnParticles(HyperdimensionalControllerBlockEntity be, Vec3 centerOffset, float intensity) {
        Level level = be.getLevel();
        if (level == null || intensity <= 0.01f) {
            return;
        }
        double density = AE2EnhancedConfig.CLIENT.particleDensity.get();
        if (density <= 0.0) {
            return;
        }
        if (random.nextFloat() > intensity * density * 0.15f) {
            return;
        }

        Vec3 centerWorld = getEffectCenterWorld(be);
        float angle = random.nextFloat() * (float) (2 * Math.PI);
        float radius = RING_BASE_RADIUS + random.nextFloat() * 2.0f;
        double px = centerWorld.x + Math.cos(angle) * radius;
        double pz = centerWorld.z + Math.sin(angle) * radius;
        double py = centerWorld.y + (random.nextFloat() - 0.5f) * 2.0f;

        double vx = (random.nextFloat() - 0.5f) * 0.02;
        double vy = 0.02 + random.nextFloat() * 0.02;
        double vz = (random.nextFloat() - 0.5f) * 0.02;

        level.addParticle(ParticleTypes.END_ROD, px, py, pz, vx, vy, vz);
    }

    private static Vec3 rotateOffsetByFacing(Vec3 local, Direction facing) {
        double x = local.x;
        double y = local.y;
        double z = local.z;
        return switch (facing) {
            case SOUTH -> new Vec3(-x, y, -z);
            case EAST -> new Vec3(-z, y, x);
            case WEST -> new Vec3(z, y, -x);
            default -> new Vec3(x, y, z);
        };
    }
}
