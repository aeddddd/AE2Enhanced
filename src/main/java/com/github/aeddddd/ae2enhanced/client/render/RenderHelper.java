package com.github.aeddddd.ae2enhanced.client.render;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * AE2Enhanced 渲染工具类，提供通用几何绘制与自定义 RenderType。
 * <p>所有方法均使用 1.20.1 NeoForge 的 PoseStack + VertexConsumer 管线，
 * 不直接操作 GL11 全局状态。</p>
 */
public final class RenderHelper {

    private RenderHelper() {
    }

    /**
     * 粗线框 RenderType，用于结构线框、光环。
     */
    public static final RenderType TESR_LINES = AE2ERenderTypes.TESR_LINES;

    /**
     * 半透明 RenderType，用于光晕、能量壳等发光效果。
     */
    public static final RenderType TESR_TRANSLUCENT = AE2ERenderTypes.TESR_TRANSLUCENT;

    /**
     * Additive 混合 RenderType，用于自发光层。
     */
    public static final RenderType TESR_ADDITIVE = AE2ERenderTypes.TESR_ADDITIVE;

    /**
     * 自定义不透明 RenderType，用于黑色事件视界等实心体。
     */
    public static final RenderType TESR_SOLID = AE2ERenderTypes.TESR_SOLID;

    /**
     * 装配枢纽黑洞主体 RenderType（事件视界 + 吸积盘）。
     */
    public static final RenderType ASSEMBLY_BLACK_HOLE = AE2ERenderTypes.ASSEMBLY_BLACK_HOLE;

    /**
     * 装配枢纽黑洞发光 RenderType（相对论性喷流）。
     */
    public static final RenderType ASSEMBLY_BLACK_HOLE_GLOW = AE2ERenderTypes.ASSEMBLY_BLACK_HOLE_GLOW;

    /**
     * 将 0xRRGGBB 整数颜色拆分为顶点颜色分量。
     *
     * @param color 整数颜色
     * @param alpha 透明度 0.0 ~ 1.0
     * @return int[4] {r, g, b, a}
     */
    public static int[] unpackColor(int color, float alpha) {
        return new int[] {
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF,
                (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f)
        };
    }

    /**
     * 绘制两点之间的线段。
     */
    public static void drawLine(VertexConsumer consumer, Matrix4f matrix,
            float x1, float y1, float z1, float x2, float y2, float z2,
            int color, float alpha) {
        int[] c = unpackColor(color, alpha);
        consumer.vertex(matrix, x1, y1, z1).color(c[0], c[1], c[2], c[3]).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(c[0], c[1], c[2], c[3]).endVertex();
    }

    /**
     * 绘制实心球体（使用三角面片）。
     *
     * @param consumer 顶点消费者
     * @param poseStack 姿态矩阵栈
     * @param radius 半径
     * @param color 颜色
     * @param alpha 透明度
     * @param latSegments 纬度分段数
     * @param lonSegments 经度分段数
     */
    public static void drawSphere(VertexConsumer consumer, PoseStack poseStack,
            float radius, int color, float alpha, int latSegments, int lonSegments) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(color, alpha);

        for (int lat = 0; lat < latSegments; lat++) {
            float lat0 = (float) Math.PI * (-0.5f + (float) lat / latSegments);
            float lat1 = (float) Math.PI * (-0.5f + (float) (lat + 1) / latSegments);
            float sinLat0 = Mth.sin(lat0);
            float cosLat0 = Mth.cos(lat0);
            float sinLat1 = Mth.sin(lat1);
            float cosLat1 = Mth.cos(lat1);

            for (int lon = 0; lon < lonSegments; lon++) {
                float lon0 = (float) (2 * Math.PI * (float) lon / lonSegments);
                float lon1 = (float) (2 * Math.PI * (float) (lon + 1) / lonSegments);
                float sinLon0 = Mth.sin(lon0);
                float cosLon0 = Mth.cos(lon0);
                float sinLon1 = Mth.sin(lon1);
                float cosLon1 = Mth.cos(lon1);

                // 四个顶点
                float[] v00 = sphereVertex(radius, sinLat0, cosLat0, sinLon0, cosLon0);
                float[] v10 = sphereVertex(radius, sinLat0, cosLat0, sinLon1, cosLon1);
                float[] v01 = sphereVertex(radius, sinLat1, cosLat1, sinLon0, cosLon0);
                float[] v11 = sphereVertex(radius, sinLat1, cosLat1, sinLon1, cosLon1);

                // 两个三角形
                vertex(consumer, matrix, v00, c);
                vertex(consumer, matrix, v01, c);
                vertex(consumer, matrix, v10, c);

                vertex(consumer, matrix, v10, c);
                vertex(consumer, matrix, v01, c);
                vertex(consumer, matrix, v11, c);
            }
        }
    }

    /**
     * 绘制线框球体。
     */
    public static void drawWireframeSphere(VertexConsumer consumer, PoseStack poseStack,
            float radius, int color, float alpha, int latSegments, int lonSegments) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(color, alpha);

        // 纬线
        for (int lat = 1; lat < latSegments; lat++) {
            float latAngle = (float) Math.PI * (-0.5f + (float) lat / latSegments);
            float sinLat = Mth.sin(latAngle);
            float cosLat = Mth.cos(latAngle);
            float r = radius * cosLat;
            float y = radius * sinLat;

            for (int lon = 0; lon < lonSegments; lon++) {
                float lon0 = (float) (2 * Math.PI * (float) lon / lonSegments);
                float lon1 = (float) (2 * Math.PI * (float) (lon + 1) / lonSegments);

                float x0 = r * Mth.cos(lon0);
                float z0 = r * Mth.sin(lon0);
                float x1 = r * Mth.cos(lon1);
                float z1 = r * Mth.sin(lon1);

                consumer.vertex(matrix, x0, y, z0).color(c[0], c[1], c[2], c[3]).endVertex();
                consumer.vertex(matrix, x1, y, z1).color(c[0], c[1], c[2], c[3]).endVertex();
            }
        }

        // 经线
        for (int lon = 0; lon < lonSegments; lon++) {
            float lonAngle = (float) (2 * Math.PI * (float) lon / lonSegments);
            float sinLon = Mth.sin(lonAngle);
            float cosLon = Mth.cos(lonAngle);

            for (int lat = 0; lat < latSegments; lat++) {
                float lat0 = (float) Math.PI * (-0.5f + (float) lat / latSegments);
                float lat1 = (float) Math.PI * (-0.5f + (float) (lat + 1) / latSegments);

                float x0 = radius * Mth.cos(lat0) * cosLon;
                float y0 = radius * Mth.sin(lat0);
                float z0 = radius * Mth.cos(lat0) * sinLon;

                float x1 = radius * Mth.cos(lat1) * cosLon;
                float y1 = radius * Mth.sin(lat1);
                float z1 = radius * Mth.cos(lat1) * sinLon;

                consumer.vertex(matrix, x0, y0, z0).color(c[0], c[1], c[2], c[3]).endVertex();
                consumer.vertex(matrix, x1, y1, z1).color(c[0], c[1], c[2], c[3]).endVertex();
            }
        }
    }

    /**
     * 绘制立方体线框。
     *
     * @param halfSize 半边长
     */
    public static void drawCubeWireframe(VertexConsumer consumer, PoseStack poseStack,
            float halfSize, int color, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(color, alpha);

        // 8 个顶点
        float[][] v = {
                { -halfSize, -halfSize, -halfSize },
                { halfSize, -halfSize, -halfSize },
                { halfSize, halfSize, -halfSize },
                { -halfSize, halfSize, -halfSize },
                { -halfSize, -halfSize, halfSize },
                { halfSize, -halfSize, halfSize },
                { halfSize, halfSize, halfSize },
                { -halfSize, halfSize, halfSize }
        };

        // 12 条边，每条边两个索引
        int[][] edges = {
                { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, // 底面
                { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, // 顶面
                { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } // 竖边
        };

        for (int[] edge : edges) {
            float[] a = v[edge[0]];
            float[] b = v[edge[1]];
            consumer.vertex(matrix, a[0], a[1], a[2]).color(c[0], c[1], c[2], c[3]).endVertex();
            consumer.vertex(matrix, b[0], b[1], b[2]).color(c[0], c[1], c[2], c[3]).endVertex();
        }
    }

    /**
     * 绘制实心立方体（6 个面，12 个三角形）。
     *
     * @param halfSize 半边长
     */
    public static void drawCube(VertexConsumer consumer, PoseStack poseStack,
            float halfSize, int color, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(color, alpha);

        // 8 个顶点
        float[][] v = {
                { -halfSize, -halfSize, -halfSize },
                { halfSize, -halfSize, -halfSize },
                { halfSize, halfSize, -halfSize },
                { -halfSize, halfSize, -halfSize },
                { -halfSize, -halfSize, halfSize },
                { halfSize, -halfSize, halfSize },
                { halfSize, halfSize, halfSize },
                { -halfSize, halfSize, halfSize }
        };

        // 6 个面，每个面 2 个三角形，顶点按逆时针顺序
        // 底面 (y = -halfSize): 0, 4, 5, 1
        drawTriangle(consumer, matrix, v[0], v[4], v[5], c);
        drawTriangle(consumer, matrix, v[0], v[5], v[1], c);
        // 顶面 (y = halfSize): 3, 2, 6, 7
        drawTriangle(consumer, matrix, v[3], v[2], v[6], c);
        drawTriangle(consumer, matrix, v[3], v[6], v[7], c);
        // 前面 (z = -halfSize): 0, 1, 2, 3
        drawTriangle(consumer, matrix, v[0], v[1], v[2], c);
        drawTriangle(consumer, matrix, v[0], v[2], v[3], c);
        // 后面 (z = halfSize): 4, 7, 6, 5
        drawTriangle(consumer, matrix, v[4], v[7], v[6], c);
        drawTriangle(consumer, matrix, v[4], v[6], v[5], c);
        // 左面 (x = -halfSize): 0, 3, 7, 4
        drawTriangle(consumer, matrix, v[0], v[3], v[7], c);
        drawTriangle(consumer, matrix, v[0], v[7], v[4], c);
        // 右面 (x = halfSize): 1, 5, 6, 2
        drawTriangle(consumer, matrix, v[1], v[5], v[6], c);
        drawTriangle(consumer, matrix, v[1], v[6], v[2], c);
    }

    /**
     * 绘制实心八面体，用于超立方体核心。
     */
    public static void drawOctahedron(VertexConsumer consumer, PoseStack poseStack,
            float radius, int color, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(color, alpha);

        float[] top = { 0, radius, 0 };
        float[] bottom = { 0, -radius, 0 };
        float[] front = { 0, 0, radius };
        float[] back = { 0, 0, -radius };
        float[] left = { -radius, 0, 0 };
        float[] right = { radius, 0, 0 };

        // 8 个三角面
        drawTriangle(consumer, matrix, top, front, right, c);
        drawTriangle(consumer, matrix, top, right, back, c);
        drawTriangle(consumer, matrix, top, back, left, c);
        drawTriangle(consumer, matrix, top, left, front, c);
        drawTriangle(consumer, matrix, bottom, right, front, c);
        drawTriangle(consumer, matrix, bottom, back, right, c);
        drawTriangle(consumer, matrix, bottom, left, back, c);
        drawTriangle(consumer, matrix, bottom, front, left, c);
    }

    /**
     * 绘制吸积盘（扁平填充圆环）。
     * <p>顶点颜色 R 通道作为 shader 部件 ID（建议传 0x010000）。</p>
     *
     * @param consumer 顶点消费者
     * @param poseStack 姿态矩阵栈
     * @param innerRadius 内半径
     * @param outerRadius 外半径
     * @param colorId 部件标识颜色
     * @param segments 分段数
     */
    public static void drawAccretionDisk(VertexConsumer consumer, PoseStack poseStack,
            float innerRadius, float outerRadius, int colorId, int segments) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(colorId, 1.0f);

        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2 * Math.PI * i / segments);
            float a1 = (float) (2 * Math.PI * (i + 1) / segments);

            float cos0 = Mth.cos(a0);
            float sin0 = Mth.sin(a0);
            float cos1 = Mth.cos(a1);
            float sin1 = Mth.sin(a1);

            float ix0 = innerRadius * cos0;
            float iz0 = innerRadius * sin0;
            float ox0 = outerRadius * cos0;
            float oz0 = outerRadius * sin0;

            float ix1 = innerRadius * cos1;
            float iz1 = innerRadius * sin1;
            float ox1 = outerRadius * cos1;
            float oz1 = outerRadius * sin1;

            // 两个三角形填充扇区
            vertex(consumer, matrix, new float[] { ix0, 0, iz0 }, c);
            vertex(consumer, matrix, new float[] { ox0, 0, oz0 }, c);
            vertex(consumer, matrix, new float[] { ox1, 0, oz1 }, c);

            vertex(consumer, matrix, new float[] { ix0, 0, iz0 }, c);
            vertex(consumer, matrix, new float[] { ox1, 0, oz1 }, c);
            vertex(consumer, matrix, new float[] { ix1, 0, iz1 }, c);
        }
    }

    /**
     * 绘制相对论性喷流（上下双锥）。
     * <p>顶点颜色 R 通道作为 shader 部件 ID（建议传 0x020000）。</p>
     *
     * @param consumer 顶点消费者
     * @param poseStack 姿态矩阵栈
     * @param baseRadius 底面半径
     * @param height 喷流高度
     * @param colorId 部件标识颜色
     * @param segments 分段数
     */
    public static void drawRelativisticJet(VertexConsumer consumer, PoseStack poseStack,
            float baseRadius, float height, int colorId, int segments) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(colorId, 1.0f);

        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2 * Math.PI * i / segments);
            float a1 = (float) (2 * Math.PI * (i + 1) / segments);

            float x0 = baseRadius * Mth.cos(a0);
            float z0 = baseRadius * Mth.sin(a0);
            float x1 = baseRadius * Mth.cos(a1);
            float z1 = baseRadius * Mth.sin(a1);

            // 上锥
            vertex(consumer, matrix, new float[] { 0, height, 0 }, c);
            vertex(consumer, matrix, new float[] { x0, 0, z0 }, c);
            vertex(consumer, matrix, new float[] { x1, 0, z1 }, c);

            // 下锥
            vertex(consumer, matrix, new float[] { 0, -height, 0 }, c);
            vertex(consumer, matrix, new float[] { x1, 0, z1 }, c);
            vertex(consumer, matrix, new float[] { x0, 0, z0 }, c);
        }
    }

    /**
     * 绘制水平环。
     */
    public static void drawRing(VertexConsumer consumer, PoseStack poseStack,
            float radius, int color, float alpha, int segments) {
        Matrix4f matrix = poseStack.last().pose();
        int[] c = unpackColor(color, alpha);

        for (int i = 0; i < segments; i++) {
            float a0 = (float) (2 * Math.PI * i / segments);
            float a1 = (float) (2 * Math.PI * (i + 1) / segments);
            float x0 = radius * Mth.cos(a0);
            float z0 = radius * Mth.sin(a0);
            float x1 = radius * Mth.cos(a1);
            float z1 = radius * Mth.sin(a1);
            consumer.vertex(matrix, x0, 0, z0).color(c[0], c[1], c[2], c[3]).endVertex();
            consumer.vertex(matrix, x1, 0, z1).color(c[0], c[1], c[2], c[3]).endVertex();
        }
    }

    /**
     * 立即结束当前批次，用于确保 translucent 渲染正确。
     */
    public static void endBatch(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource src) {
            src.endBatch();
        }
    }

    private static float[] sphereVertex(float radius, float sinLat, float cosLat, float sinLon, float cosLon) {
        return new float[] {
                radius * cosLat * cosLon,
                radius * sinLat,
                radius * cosLat * sinLon
        };
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float[] pos, int[] color) {
        consumer.vertex(matrix, pos[0], pos[1], pos[2]).color(color[0], color[1], color[2], color[3]).endVertex();
    }

    private static void drawTriangle(VertexConsumer consumer, Matrix4f matrix,
            float[] a, float[] b, float[] c, int[] color) {
        consumer.vertex(matrix, a[0], a[1], a[2]).color(color[0], color[1], color[2], color[3]).endVertex();
        consumer.vertex(matrix, b[0], b[1], b[2]).color(color[0], color[1], color[2], color[3]).endVertex();
        consumer.vertex(matrix, c[0], c[1], c[2]).color(color[0], color[1], color[2], color[3]).endVertex();
    }
}
