package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/**
 * 超维度仓储中枢控制器的 TESR：超立方体全息投影。
 * 绘制一个缓慢旋转的线框超立方体（双立方体 + 连接边 + 顶点发光 + 旋转光环），
 * 带有青色发光效果和脉冲呼吸动画。
 *
 * GL 状态恢复策略：所有修改的状态在 finally 中显式恢复。
 */
public class RenderHyperdimensionalController extends TileEntitySpecialRenderer<TileHyperdimensionalController> {

    // 外立方体半对角线长度（从中心到顶点）
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

    @Override
    public void render(TileHyperdimensionalController te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te == null || !te.isFormed()) return;
        if (!AE2EnhancedConfig.render.enableHyperdimensionalRenderer) return;

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROT_SPEED;
        float innerTime = (te.getWorld().getTotalWorldTime() + partialTicks) * INNER_ROT_SPEED;
        float pulse = 0.5f + 0.5f * (float) Math.sin((te.getWorld().getTotalWorldTime() + partialTicks) * PULSE_SPEED);
        float ringTime = (te.getWorld().getTotalWorldTime() + partialTicks) * RING_SPEED;

        // 结构几何中心（相对于控制器）
        // 结构范围：x∈[-2,2], z∈[0,4]，中心在 (0,0,2)
        EnumFacing facing = EnumFacing.NORTH;
        if (te.getWorld() != null) {
            facing = te.getWorld().getBlockState(te.getPos()).getValue(BlockHyperdimensionalController.FACING);
        }
        double offX, offZ;
        switch (facing) {
            case SOUTH: offX = 0; offZ = -2.0; break;
            case EAST:  offX = -2.0; offZ = 0; break;
            case WEST:  offX = 2.0; offZ = 0; break;
            default:    offX = 0; offZ = 2.0; break;
        }

        double cx = x + 0.5 + offX;
        double cy = y + 4.0; // 在结构平面上方 3.5 格
        double cz = z + 0.5 + offZ;

        double renderDist = AE2EnhancedConfig.render.renderDistance;
        // cx/cy/cz are already camera-relative (x = tileX - entityX)
        double distSq = cx * cx + cy * cy + cz * cz;
        if (distSq > renderDist * renderDist) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, cz);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glNormal3f(0.0f, 1.0f, 0.0f);
        GlStateManager.enableCull();

        try {
            // 外立方体线框
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time, 0, 1, 0);
            GlStateManager.rotate(time * 0.3f, 1, 0, 0);
            drawCubeWireframe(OUTER_SIZE, COLOR_OUTER, 0.55f + 0.25f * pulse, 3.0f);
            // 外立方体顶点发光
            drawVertexGlows(OUTER_SIZE, COLOR_VERTEX, 0.75f + 0.2f * pulse, 0.10f);
            GlStateManager.popMatrix();

            // 内立方体线框（反向旋转）
            GlStateManager.pushMatrix();
            GlStateManager.rotate(innerTime, 0, 1, 0);
            GlStateManager.rotate(innerTime * 0.4f, 0, 0, 1);
            drawCubeWireframe(INNER_SIZE, COLOR_INNER, 0.35f + 0.20f * pulse, 2.0f);
            GlStateManager.popMatrix();

            // 连接内外立方体对应顶点的边（超立方体特征）
            drawConnectionLines(time, innerTime, OUTER_SIZE, INNER_SIZE, COLOR_CONNECT, 0.20f + 0.14f * pulse, 2.0f);

            // 对角交叉支撑（增强超立方体感）
            drawDiagonalBraces(time, innerTime, OUTER_SIZE, INNER_SIZE, COLOR_DIAGONAL, 0.12f + 0.08f * pulse, 1.2f);

            // 水平旋转光环
            GlStateManager.pushMatrix();
            GlStateManager.rotate(ringTime, 0, 1, 0);
            drawRing(OUTER_SIZE * 0.75f, COLOR_RING, 0.25f + 0.15f * pulse, 2.2f);
            GlStateManager.popMatrix();

            // 垂直倾斜旋转光环
            GlStateManager.pushMatrix();
            GlStateManager.rotate(ringTime * 0.7f, 1, 0, 0);
            GlStateManager.rotate(45, 0, 1, 0);
            drawRing(INNER_SIZE * 1.2f, COLOR_RING, 0.18f + 0.12f * pulse, 1.8f);
            GlStateManager.popMatrix();

            // 中心核心点（小光球）
            drawCenterCore(pulse);

        } finally {
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            if (!blendWasEnabled) {
                GlStateManager.disableBlend();
            }
            GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
            );
            if (cullWasEnabled) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            GlStateManager.popMatrix();
        }
    }

    /**
     * 绘制一个轴对齐立方体的线框。
     * size 是从中心到每个顶点的距离（即半对角线长度 = sqrt(3) * 半边长）。
     * 半边长 = size / sqrt(3)。
     */
    private void drawCubeWireframe(float size, int color, float alpha, float lineWidth) {
        if (alpha <= 0.01f) return;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float half = size / 1.73205f; // size / sqrt(3)

        // 8 个顶点
        float[][] v = {
            {-half, -half, -half},
            { half, -half, -half},
            { half,  half, -half},
            {-half,  half, -half},
            {-half, -half,  half},
            { half, -half,  half},
            { half,  half,  half},
            {-half,  half,  half},
        };

        // 12 条边（每对顶点索引）
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0}, // 后面
            {4,5},{5,6},{6,7},{7,4}, // 前面
            {0,4},{1,5},{2,6},{3,7}, // 连接前后
        };

        GlStateManager.glLineWidth(lineWidth);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        for (int[] e : edges) {
            buf.pos(v[e[0]][0], v[e[0]][1], v[e[0]][2]).color(r, g, b, alpha).endVertex();
            buf.pos(v[e[1]][0], v[e[1]][1], v[e[1]][2]).color(r, g, b, alpha).endVertex();
        }

        tess.draw();
        GlStateManager.glLineWidth(1.0f);
    }

    /**
     * 在立方体 8 个顶点处绘制发光小立方体。
     */
    private void drawVertexGlows(float size, int color, float alpha, float glowSize) {
        if (alpha <= 0.01f) return;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float half = size / 1.73205f;

        float[][] verts = {
            {-half, -half, -half},
            { half, -half, -half},
            { half,  half, -half},
            {-half,  half, -half},
            {-half, -half,  half},
            { half, -half,  half},
            { half,  half,  half},
            {-half,  half,  half},
        };

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (float[] v : verts) {
            drawSmallCube(buf, v[0], v[1], v[2], glowSize, r, g, b, alpha);
        }

        tess.draw();
    }

    private void drawSmallCube(BufferBuilder buf, float cx, float cy, float cz, float s, float r, float g, float b, float a) {
        // 6 个面，每个面 4 个顶点
        // 底面
        buf.pos(cx-s, cy-s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy-s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy-s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx-s, cy-s, cz+s).color(r, g, b, a).endVertex();
        // 顶面
        buf.pos(cx-s, cy+s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx-s, cy+s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy+s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy+s, cz-s).color(r, g, b, a).endVertex();
        // 前面
        buf.pos(cx-s, cy-s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy-s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy+s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx-s, cy+s, cz+s).color(r, g, b, a).endVertex();
        // 后面
        buf.pos(cx-s, cy-s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx-s, cy+s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy+s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy-s, cz-s).color(r, g, b, a).endVertex();
        // 左面
        buf.pos(cx-s, cy-s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx-s, cy-s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx-s, cy+s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx-s, cy+s, cz-s).color(r, g, b, a).endVertex();
        // 右面
        buf.pos(cx+s, cy-s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy+s, cz-s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy+s, cz+s).color(r, g, b, a).endVertex();
        buf.pos(cx+s, cy-s, cz+s).color(r, g, b, a).endVertex();
    }

    /**
     * 绘制连接内外立方体对应顶点的 8 条线。
     * 这是超立方体（Tesseract）在 3D 投影中的核心特征。
     */
    private void drawConnectionLines(float outerTime, float innerTime,
                                     float outerSize, float innerSize,
                                     int color, float alpha, float lineWidth) {
        if (alpha <= 0.01f) return;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float outerHalf = outerSize / 1.73205f;
        float innerHalf = innerSize / 1.73205f;

        // 8 个顶点方向（归一化的角方向）
        float[][] dirs = {
            {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
            {-1, -1,  1}, {1, -1,  1}, {1, 1,  1}, {-1, 1,  1},
        };

        GlStateManager.glLineWidth(lineWidth);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        float[] ov = new float[3];
        float[] iv = new float[3];
        for (float[] dir : dirs) {
            // 外顶点（应用外旋转）
            rotatePoint(
                dir[0] * outerHalf, dir[1] * outerHalf, dir[2] * outerHalf,
                outerTime, outerTime * 0.3f, 0, ov
            );
            // 内顶点（应用内旋转）
            rotatePoint(
                dir[0] * innerHalf, dir[1] * innerHalf, dir[2] * innerHalf,
                0, 0, innerTime, iv
            );

            buf.pos(ov[0], ov[1], ov[2]).color(r, g, b, alpha).endVertex();
            buf.pos(iv[0], iv[1], iv[2]).color(r, g, b, alpha).endVertex();
        }

        tess.draw();
        GlStateManager.glLineWidth(1.0f);
    }

    /**
     * 绘制内外立方体之间的对角交叉支撑线。
     * 每个外顶点连接到相邻的两个内顶点，形成 X 形支撑。
     */
    private void drawDiagonalBraces(float outerTime, float innerTime,
                                    float outerSize, float innerSize,
                                    int color, float alpha, float lineWidth) {
        if (alpha <= 0.01f) return;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float outerHalf = outerSize / 1.73205f;
        float innerHalf = innerSize / 1.73205f;

        float[][] dirs = {
            {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
            {-1, -1,  1}, {1, -1,  1}, {1, 1,  1}, {-1, 1,  1},
        };

        // 对角连接映射：每个外顶点连接到两个相邻的内顶点（索引偏移）
        int[][] diagMap = {
            {1, 4}, {0, 5}, {3, 6}, {2, 7},
            {0, 5}, {1, 4}, {3, 6}, {2, 7},
        };

        GlStateManager.glLineWidth(lineWidth);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        float[] ov = new float[3];
        float[] iv = new float[3];
        for (int i = 0; i < 8; i++) {
            rotatePoint(
                dirs[i][0] * outerHalf, dirs[i][1] * outerHalf, dirs[i][2] * outerHalf,
                outerTime, outerTime * 0.3f, 0, ov
            );
            for (int j : diagMap[i]) {
                rotatePoint(
                    dirs[j][0] * innerHalf, dirs[j][1] * innerHalf, dirs[j][2] * innerHalf,
                    0, 0, innerTime, iv
                );
                buf.pos(ov[0], ov[1], ov[2]).color(r, g, b, alpha).endVertex();
                buf.pos(iv[0], iv[1], iv[2]).color(r, g, b, alpha).endVertex();
            }
        }

        tess.draw();
        GlStateManager.glLineWidth(1.0f);
    }

    /**
     * 绘制一个水平圆环（由线段近似）。
     */
    private void drawRing(float radius, int color, float alpha, float lineWidth) {
        if (alpha <= 0.01f) return;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        int segments = 48;

        GlStateManager.glLineWidth(lineWidth);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float px = (float) Math.cos(angle) * radius;
            float pz = (float) Math.sin(angle) * radius;
            buf.pos(px, 0, pz).color(r, g, b, alpha).endVertex();
        }

        tess.draw();
        GlStateManager.glLineWidth(1.0f);
    }

    /**
     * 简单旋转：先绕 Y 轴旋转 ry，再绕 X 轴旋转 rx，再绕 Z 轴旋转 rz。
     */
    private void rotatePoint(float x, float y, float z, float ry, float rx, float rz, float[] out) {
        // 绕 Y 轴
        float cosY = (float) Math.cos(Math.toRadians(ry));
        float sinY = (float) Math.sin(Math.toRadians(ry));
        float x1 = x * cosY - z * sinY;
        float z1 = x * sinY + z * cosY;
        float y1 = y;

        // 绕 X 轴
        float cosX = (float) Math.cos(Math.toRadians(rx));
        float sinX = (float) Math.sin(Math.toRadians(rx));
        float y2 = y1 * cosX - z1 * sinX;
        float z2 = y1 * sinX + z1 * cosX;
        float x2 = x1;

        // 绕 Z 轴
        float cosZ = (float) Math.cos(Math.toRadians(rz));
        float sinZ = (float) Math.sin(Math.toRadians(rz));
        out[0] = x2 * cosZ - y2 * sinZ;
        out[1] = x2 * sinZ + y2 * cosZ;
        out[2] = z2;
    }

    /**
     * 中心核心：一个微小的旋转八面体，表示奇点。
     */
    private void drawCenterCore(float pulse) {
        float alpha = 0.35f + 0.35f * pulse;
        float size = 0.10f + 0.05f * pulse;

        float r = 0.0f, g = 0.83f, b = 1.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // 上下两个锥体组成八面体
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        // 上锥
        buf.pos(-size, 0, -size).color(r, g, b, alpha).endVertex();
        buf.pos( size, 0, -size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,    size,  0).color(r, g, b, alpha).endVertex();

        buf.pos( size, 0, -size).color(r, g, b, alpha).endVertex();
        buf.pos( size, 0,  size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,    size,  0).color(r, g, b, alpha).endVertex();

        buf.pos( size, 0,  size).color(r, g, b, alpha).endVertex();
        buf.pos(-size, 0,  size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,    size,  0).color(r, g, b, alpha).endVertex();

        buf.pos(-size, 0,  size).color(r, g, b, alpha).endVertex();
        buf.pos(-size, 0, -size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,    size,  0).color(r, g, b, alpha).endVertex();

        // 下锥
        buf.pos(-size, 0, -size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,   -size,  0).color(r, g, b, alpha).endVertex();
        buf.pos( size, 0, -size).color(r, g, b, alpha).endVertex();

        buf.pos( size, 0, -size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,   -size,  0).color(r, g, b, alpha).endVertex();
        buf.pos( size, 0,  size).color(r, g, b, alpha).endVertex();

        buf.pos( size, 0,  size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,   -size,  0).color(r, g, b, alpha).endVertex();
        buf.pos(-size, 0,  size).color(r, g, b, alpha).endVertex();

        buf.pos(-size, 0,  size).color(r, g, b, alpha).endVertex();
        buf.pos( 0,   -size,  0).color(r, g, b, alpha).endVertex();
        buf.pos(-size, 0, -size).color(r, g, b, alpha).endVertex();

        tess.draw();
    }
}
