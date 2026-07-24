package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

/**
 * 超维度仓储中枢控制器渲染器：超立方体(Tesseract)全息投影.
 * <p>移植自 1.12 RenderHyperdimensionalController：外立方体线框 + 顶点发光小立方体 +
 * 反向旋转内立方体 + 内外顶点连接线 + 对角交叉支撑线 + 两条旋转光环 + 中心八面体核心,
 * 全部带青色发光与脉冲呼吸动画.</p>
 */
public class HyperdimensionalControllerRenderer extends AbstractMultiblockRenderer<HyperdimensionalControllerBlockEntity> {

    // 外立方体半对角线长度(从中心到顶点)
    private static final float OUTER_SIZE = 3.2f;
    // 内立方体半对角线长度
    private static final float INNER_SIZE = 1.6f;
    // 主旋转速度
    private static final float ROT_SPEED = 0.8f;
    // 内立方体反向旋转速度
    private static final float INNER_ROT_SPEED = -0.5f;
    // 脉冲速度
    private static final float PULSE_SPEED = 0.06f;
    // 光环旋转速度
    private static final float RING_SPEED = 1.2f;

    // 颜色：青色发光
    private static final int COLOR_OUTER = 0x00d4ff;
    private static final int COLOR_INNER = 0x0088cc;
    private static final int COLOR_CONNECT = 0x44aaff;
    private static final int COLOR_VERTEX = 0x66ffff;
    private static final int COLOR_RING = 0x88eeff;
    private static final int COLOR_DIAGONAL = 0x2266aa;
    private static final int COLOR_CORE = 0x00d4ff;

    // sqrt(3),半对角线 -> 半边长 的换算
    private static final float SQRT3 = 1.73205f;

    // 特效中心相对控制器方块中心的偏移：结构中心 (0,0,2),抬高 3.5(即方块原点上方 4.0)
    private static final Vec3 CENTER_FROM_BLOCK_CENTER = new Vec3(0.0, 3.5, 2.0);

    // 立方体 8 个顶点的方向
    private static final float[][] VERTEX_DIRS = {
            { -1, -1, -1 }, { 1, -1, -1 }, { 1, 1, -1 }, { -1, 1, -1 },
            { -1, -1, 1 }, { 1, -1, 1 }, { 1, 1, 1 }, { -1, 1, 1 }
    };

    // 对角连接映射：每个外顶点连接到两个相邻的内顶点(索引偏移)
    private static final int[][] DIAG_MAP = {
            { 1, 4 }, { 0, 5 }, { 3, 6 }, { 2, 7 },
            { 0, 5 }, { 1, 4 }, { 3, 6 }, { 2, 7 }
    };

    public HyperdimensionalControllerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected boolean isRendererEnabled() {
        return AE2EnhancedConfig.CLIENT.enableHyperdimensionalRenderer.get();
    }

    @Override
    protected Vec3 getEffectCenterOffset(HyperdimensionalControllerBlockEntity be) {
        // 旋转偏移以方块中心为基准,换算回方块原点需 +0.5
        Vec3 rotated = rotateOffsetByFacing(CENTER_FROM_BLOCK_CENTER, getFacing(be));
        return rotated.add(0.5, 0.5, 0.5);
    }

    @Override
    protected double getRenderRadius() {
        return 5.5;
    }

    @Override
    public int getViewDistance() {
        // 对应 1.12 getMaxRenderDistanceSquared() = 65536
        return 256;
    }

    @Override
    protected void renderEffect(HyperdimensionalControllerBlockEntity be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Vec3 centerOffset = getEffectCenterOffset(be);
        poseStack.translate(centerOffset.x, centerOffset.y, centerOffset.z);

        float ticks = getTime(be, partialTicks);
        float time = ticks * ROT_SPEED;
        float innerTime = ticks * INNER_ROT_SPEED;
        float pulse = 0.5f + 0.5f * Mth.sin(ticks * PULSE_SPEED);
        float ringTime = ticks * RING_SPEED;

        // ---- 外立方体线框 + 顶点发光 ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 0.3f));
        RenderHelper.drawCubeWireframe(bufferSource.getBuffer(AE2ERenderTypes.lines(3.0)), poseStack,
                OUTER_SIZE / SQRT3, COLOR_OUTER, 0.55f + 0.25f * pulse);
        drawVertexGlows(bufferSource.getBuffer(RenderHelper.TESR_TRANSLUCENT), poseStack,
                OUTER_SIZE / SQRT3, 0.10f, COLOR_VERTEX, 0.75f + 0.2f * pulse);
        poseStack.popPose();

        // ---- 内立方体线框(反向旋转) ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(innerTime));
        poseStack.mulPose(Axis.ZP.rotationDegrees(innerTime * 0.4f));
        RenderHelper.drawCubeWireframe(bufferSource.getBuffer(RenderHelper.TESR_LINES), poseStack,
                INNER_SIZE / SQRT3, COLOR_INNER, 0.35f + 0.20f * pulse);
        poseStack.popPose();

        // ---- 连接内外立方体对应顶点的边(超立方体特征) ----
        drawConnectionLines(bufferSource.getBuffer(RenderHelper.TESR_LINES), poseStack.last().pose(),
                time, innerTime, COLOR_CONNECT, 0.20f + 0.14f * pulse);

        // ---- 对角交叉支撑(增强超立方体感) ----
        drawDiagonalBraces(bufferSource.getBuffer(AE2ERenderTypes.lines(1.2)), poseStack.last().pose(),
                time, innerTime, COLOR_DIAGONAL, 0.12f + 0.08f * pulse);

        // ---- 水平旋转光环 ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(ringTime));
        RenderHelper.drawRing(bufferSource.getBuffer(AE2ERenderTypes.lines(2.2)), poseStack,
                OUTER_SIZE * 0.75f, COLOR_RING, 0.25f + 0.15f * pulse, 48);
        poseStack.popPose();

        // ---- 垂直倾斜旋转光环 ----
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(ringTime * 0.7f));
        poseStack.mulPose(Axis.YP.rotationDegrees(45));
        RenderHelper.drawRing(bufferSource.getBuffer(AE2ERenderTypes.lines(1.8)), poseStack,
                INNER_SIZE * 1.2f, COLOR_RING, 0.18f + 0.12f * pulse, 48);
        poseStack.popPose();

        // ---- 中心核心(微小八面体,表示奇点) ----
        RenderHelper.drawOctahedron(bufferSource.getBuffer(RenderHelper.TESR_TRANSLUCENT), poseStack,
                0.10f + 0.05f * pulse, COLOR_CORE, 0.35f + 0.35f * pulse);
    }

    /**
     * 在立方体 8 个顶点处绘制发光小立方体.
     *
     * @param half 外立方体半边长
     * @param glowSize 小立方体半边长
     */
    private static void drawVertexGlows(VertexConsumer consumer, PoseStack poseStack,
            float half, float glowSize, int color, float alpha) {
        for (float[] dir : VERTEX_DIRS) {
            poseStack.pushPose();
            poseStack.translate(dir[0] * half, dir[1] * half, dir[2] * half);
            RenderHelper.drawCube(consumer, poseStack, glowSize, color, alpha);
            poseStack.popPose();
        }
    }

    /**
     * 绘制连接内外立方体对应顶点的 8 条线.
     * 这是超立方体(Tesseract)在 3D 投影中的核心特征.
     */
    private static void drawConnectionLines(VertexConsumer consumer, Matrix4f matrix,
            float outerTime, float innerTime, int color, float alpha) {
        float outerHalf = OUTER_SIZE / SQRT3;
        float innerHalf = INNER_SIZE / SQRT3;

        float[] ov = new float[3];
        float[] iv = new float[3];
        for (float[] dir : VERTEX_DIRS) {
            // 外顶点(应用外旋转)
            rotatePoint(dir[0] * outerHalf, dir[1] * outerHalf, dir[2] * outerHalf,
                    outerTime, outerTime * 0.3f, 0, ov);
            // 内顶点(应用内旋转)
            rotatePoint(dir[0] * innerHalf, dir[1] * innerHalf, dir[2] * innerHalf,
                    0, 0, innerTime, iv);
            RenderHelper.drawLine(consumer, matrix,
                    ov[0], ov[1], ov[2], iv[0], iv[1], iv[2], color, alpha);
        }
    }

    /**
     * 绘制内外立方体之间的对角交叉支撑线.
     * 每个外顶点连接到相邻的两个内顶点,形成 X 形支撑.
     */
    private static void drawDiagonalBraces(VertexConsumer consumer, Matrix4f matrix,
            float outerTime, float innerTime, int color, float alpha) {
        float outerHalf = OUTER_SIZE / SQRT3;
        float innerHalf = INNER_SIZE / SQRT3;

        float[] ov = new float[3];
        float[] iv = new float[3];
        for (int i = 0; i < 8; i++) {
            float[] dir = VERTEX_DIRS[i];
            rotatePoint(dir[0] * outerHalf, dir[1] * outerHalf, dir[2] * outerHalf,
                    outerTime, outerTime * 0.3f, 0, ov);
            for (int j : DIAG_MAP[i]) {
                float[] innerDir = VERTEX_DIRS[j];
                rotatePoint(innerDir[0] * innerHalf, innerDir[1] * innerHalf, innerDir[2] * innerHalf,
                        0, 0, innerTime, iv);
                RenderHelper.drawLine(consumer, matrix,
                        ov[0], ov[1], ov[2], iv[0], iv[1], iv[2], color, alpha);
            }
        }
    }

    /**
     * 简单旋转：先绕 Y 轴旋转 ry,再绕 X 轴旋转 rx,再绕 Z 轴旋转 rz(角度制).
     */
    private static void rotatePoint(float x, float y, float z, float ry, float rx, float rz, float[] out) {
        // 绕 Y 轴
        float cosY = Mth.cos((float) Math.toRadians(ry));
        float sinY = Mth.sin((float) Math.toRadians(ry));
        float x1 = x * cosY - z * sinY;
        float z1 = x * sinY + z * cosY;
        float y1 = y;

        // 绕 X 轴
        float cosX = Mth.cos((float) Math.toRadians(rx));
        float sinX = Mth.sin((float) Math.toRadians(rx));
        float y2 = y1 * cosX - z1 * sinX;
        float z2 = y1 * sinX + z1 * cosX;
        float x2 = x1;

        // 绕 Z 轴
        float cosZ = Mth.cos((float) Math.toRadians(rz));
        float sinZ = Mth.sin((float) Math.toRadians(rz));
        out[0] = x2 * cosZ - y2 * sinZ;
        out[1] = x2 * sinZ + y2 * cosZ;
        out[2] = z2;
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
