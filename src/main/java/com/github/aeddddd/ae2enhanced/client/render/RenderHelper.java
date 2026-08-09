package com.github.aeddddd.ae2enhanced.client.render;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * TESR 渲染工具类.
 *
 * 提取重复的球体绘制、线框球体、颜色解包等工具方法,
 * 消除 RenderBlackHole / RenderMicroSingularity 中 ~150 行重复代码.
 */
public final class RenderHelper {

    private RenderHelper() {}

    /** 将 0xRRGGBB 整数颜色解包为 float[3] RGB */
    public static float[] unpackRGB(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f
        };
    }

    /** 绘制实心球体(三角形网格) */
    public static void drawSphere(double radius, int color, float alpha, int latSegs, int lonSegs) {
        if (alpha <= 0.01f) return;
        float[] rgb = unpackRGB(color);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int lat = 0; lat < latSegs; lat++) {
            double theta0 = Math.PI * lat / latSegs;
            double theta1 = Math.PI * (lat + 1) / latSegs;
            for (int lon = 0; lon < lonSegs; lon++) {
                double phi0 = 2 * Math.PI * lon / lonSegs;
                double phi1 = 2 * Math.PI * (lon + 1) / lonSegs;
                double[] v00 = sphereVertex(radius, theta0, phi0);
                double[] v01 = sphereVertex(radius, theta0, phi1);
                double[] v10 = sphereVertex(radius, theta1, phi0);
                double[] v11 = sphereVertex(radius, theta1, phi1);
                addTriangle(buf, v00, v10, v01, rgb[0], rgb[1], rgb[2], alpha);
                addTriangle(buf, v01, v10, v11, rgb[0], rgb[1], rgb[2], alpha);
            }
        }
        tess.draw();
    }

    /** 绘制线框球体(经纬线网格) */
    public static void drawWireframeSphere(double radius, int color, float alpha, int gridLat, int gridLon) {
        if (alpha <= 0.01f) return;
        float[] rgb = unpackRGB(color);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        for (int lat = 1; lat < gridLat; lat++) {
            double theta = Math.PI * lat / gridLat;
            double y = radius * Math.cos(theta);
            double radH = radius * Math.sin(theta);
            buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
            for (int lon = 0; lon <= gridLon; lon++) {
                double phi = 2 * Math.PI * lon / gridLon;
                buf.pos(radH * Math.cos(phi), y, radH * Math.sin(phi))
                   .color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            }
            tess.draw();
        }

        for (int lon = 0; lon < gridLon; lon++) {
            double phi = 2 * Math.PI * lon / gridLon;
            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int lat = 0; lat <= gridLat; lat++) {
                double theta = Math.PI * lat / gridLat;
                buf.pos(radius * Math.sin(theta) * Math.cos(phi),
                        radius * Math.cos(theta),
                        radius * Math.sin(theta) * Math.sin(phi))
                   .color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            }
            tess.draw();
        }
    }

    private static double[] sphereVertex(double radius, double theta, double phi) {
        return new double[]{
                radius * Math.sin(theta) * Math.cos(phi),
                radius * Math.cos(theta),
                radius * Math.sin(theta) * Math.sin(phi)
        };
    }

    public static void addTriangle(BufferBuilder buffer, double[] a, double[] b, double[] c,
                                    float r, float g, float blue, float alpha) {
        buffer.pos(a[0], a[1], a[2]).color(r, g, blue, alpha).endVertex();
        buffer.pos(b[0], b[1], b[2]).color(r, g, blue, alpha).endVertex();
        buffer.pos(c[0], c[1], c[2]).color(r, g, blue, alpha).endVertex();
    }

    /** 重置 line width 到 1.0 */
    public static void resetLineWidth() {
        GlStateManager.glLineWidth(1.0f);
    }

    /** 在 XZ 平面绘制吸积盘环带几何(四边形网格),片元效果由 shader 计算 */
    public static void drawAccretionDisk(double inner, double outer, int color, float alpha, int segments) {
        float[] rgb = unpackRGB(color);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int j = 0; j < segments; j++) {
            double phi0 = 2 * Math.PI * j / segments;
            double phi1 = 2 * Math.PI * (j + 1) / segments;
            double c0 = Math.cos(phi0), s0 = Math.sin(phi0);
            double c1 = Math.cos(phi1), s1 = Math.sin(phi1);
            buf.pos(inner * c0, 0, inner * s0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(inner * c1, 0, inner * s1).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(outer * c1, 0, outer * s1).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(outer * c0, 0, outer * s0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
        }
        tess.draw();
    }

    /** 沿 ±Y 轴绘制相对论性喷流双锥几何,片元效果由 shader 计算 */
    public static void drawRelativisticJet(double baseRadius, double height, int color, float alpha, int segments) {
        float[] rgb = unpackRGB(color);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int j = 0; j < segments; j++) {
            double phi0 = 2 * Math.PI * j / segments;
            double phi1 = 2 * Math.PI * (j + 1) / segments;
            double c0 = Math.cos(phi0), s0 = Math.sin(phi0);
            double c1 = Math.cos(phi1), s1 = Math.sin(phi1);
            // 上锥：基圆 -> 顶点
            buf.pos(baseRadius * c0, 0, baseRadius * s0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(baseRadius * c1, 0, baseRadius * s1).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(0, height, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(0, height, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            // 下锥：基圆 -> 顶点
            buf.pos(baseRadius * c1, 0, baseRadius * s1).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(baseRadius * c0, 0, baseRadius * s0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(0, -height, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            buf.pos(0, -height, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
        }
        tess.draw();
    }
}
