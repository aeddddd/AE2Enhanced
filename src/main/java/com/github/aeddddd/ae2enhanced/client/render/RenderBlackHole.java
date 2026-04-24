package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class RenderBlackHole extends TileEntitySpecialRenderer<TileAssemblyController> {

    // 基础半径（扩张以此为基准）
    private static final double EVENT_HORIZON_RADIUS = 2.5;
    private static final double INNER_HALO_BASE = 3.2;
    private static final double MID_HALO_BASE = 4.6;
    private static final double OUTER_HALO_BASE = 6.0;

    private static final int LATITUDE_SEGMENTS = 24;
    private static final int LONGITUDE_SEGMENTS = 24;
    private static final int GRID_LAT = 8;
    private static final int GRID_LON = 12;

    private static final float ROTATION_SPEED = 0.25f;

    @Override
    public void render(TileAssemblyController te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!te.isFormed()) return;

        double centerX = x + 0.5;
        double centerY = y + 0.5;
        double centerZ = z + 0.5 + 7.0;

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROTATION_SPEED;

        // 扩张因子：0~1 循环，制造由内而外的涌动感
        float expand = 0.5f + 0.5f * (float) Math.sin(time * 0.5);
        // 亮度因子：始终保证最低可见度，不会淡到消失
        float brightness = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 0.35));
        // 网格能量涌动
        float gridEnergy = 0.5f + 0.5f * (float) Math.sin(time * 1.4);

        // 各层半径：由内而外扩张
        double innerR = INNER_HALO_BASE * (0.82 + 0.36 * expand);
        double midR = MID_HALO_BASE * (0.88 + 0.24 * (1.0f - expand * 0.5f));
        double outerR = OUTER_HALO_BASE * (0.92 + 0.16 * expand);

        // 各层透明度：有最低保证，不会消失
        float innerAlpha = 0.10f + 0.28f * brightness;
        float midAlpha = 0.06f + 0.14f * (1.0f - expand * 0.3f);
        float outerAlpha = 0.04f + 0.10f * brightness;

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, centerZ);
        GlStateManager.pushAttrib();

        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        // 1. 事件视界（纯黑，始终存在）
        drawSphere(EVENT_HORIZON_RADIUS, 0x000000, 0.99f);

        // 2. 内层光晕（深紫，主旋转，由内扩张）
        GlStateManager.pushMatrix();
        GlStateManager.rotate(time * 0.5f, 0, 1, 0);
        GlStateManager.rotate(18.0f, 1, 0, 0.3f);
        drawSphere(innerR, 0x140029, innerAlpha);
        // 内层网格：始终可见，仅有亮度变化
        drawWireframeSphere(innerR, 0x7700DD, 0.28f * (0.5f + 0.5f * gridEnergy));
        GlStateManager.popMatrix();

        // 3. 中层光晕（极深紫，反向旋转，与内层错相）
        GlStateManager.pushMatrix();
        GlStateManager.rotate(-time * 0.3f, 0, 1, 0);
        GlStateManager.rotate(12.0f, 0.5f, 0, 1.0f);
        drawSphere(midR, 0x05000D, midAlpha);
        // 中层网格：始终可见
        drawWireframeSphere(midR, 0x110022, 0.08f * (0.5f + 0.5f * gridEnergy));
        GlStateManager.popMatrix();

        // 4. 外层光晕（深紫雾，缓慢旋转）
        GlStateManager.pushMatrix();
        GlStateManager.rotate(time * 0.12f, 0, 1, 0);
        GlStateManager.rotate(8.0f, 1, 0.2f, 0);
        drawSphere(outerR, 0x020005, outerAlpha);
        GlStateManager.popMatrix();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private void drawSphere(double radius, int color, float alpha) {
        if (alpha <= 0.01f) return;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        for (int lat = 0; lat < LATITUDE_SEGMENTS; lat++) {
            double theta0 = Math.PI * lat / LATITUDE_SEGMENTS;
            double theta1 = Math.PI * (lat + 1) / LATITUDE_SEGMENTS;

            for (int lon = 0; lon < LONGITUDE_SEGMENTS; lon++) {
                double phi0 = 2 * Math.PI * lon / LONGITUDE_SEGMENTS;
                double phi1 = 2 * Math.PI * (lon + 1) / LONGITUDE_SEGMENTS;

                double[] v00 = sphereVertex(radius, theta0, phi0);
                double[] v01 = sphereVertex(radius, theta0, phi1);
                double[] v10 = sphereVertex(radius, theta1, phi0);
                double[] v11 = sphereVertex(radius, theta1, phi1);

                addTriangle(buffer, v00, v10, v01, r, g, b, alpha);
                addTriangle(buffer, v01, v10, v11, r, g, b, alpha);
            }
        }

        tessellator.draw();
    }

    private void drawWireframeSphere(double radius, int color, float alpha) {
        if (alpha <= 0.01f) return;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GlStateManager.glLineWidth(2.0f);

        // 纬线
        for (int lat = 1; lat < GRID_LAT; lat++) {
            double theta = Math.PI * lat / GRID_LAT;
            double y = radius * Math.cos(theta);
            double radH = radius * Math.sin(theta);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);

            for (int lon = 0; lon <= GRID_LON; lon++) {
                double phi = 2 * Math.PI * lon / GRID_LON;
                buffer.pos(radH * Math.cos(phi), y, radH * Math.sin(phi))
                      .color(r, g, b, alpha).endVertex();
            }
            tessellator.draw();
        }

        // 经线
        for (int lon = 0; lon < GRID_LON; lon++) {
            double phi = 2 * Math.PI * lon / GRID_LON;

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

            for (int lat = 0; lat <= GRID_LAT; lat++) {
                double theta = Math.PI * lat / GRID_LAT;
                buffer.pos(
                    radius * Math.sin(theta) * Math.cos(phi),
                    radius * Math.cos(theta),
                    radius * Math.sin(theta) * Math.sin(phi)
                ).color(r, g, b, alpha).endVertex();
            }
            tessellator.draw();
        }

        GlStateManager.glLineWidth(1.0f);
    }

    private double[] sphereVertex(double radius, double theta, double phi) {
        return new double[]{
            radius * Math.sin(theta) * Math.cos(phi),
            radius * Math.cos(theta),
            radius * Math.sin(theta) * Math.sin(phi)
        };
    }

    private void addTriangle(BufferBuilder buffer, double[] a, double[] b, double[] c,
                             float r, float g, float blue, float alpha) {
        buffer.pos(a[0], a[1], a[2]).color(r, g, blue, alpha).endVertex();
        buffer.pos(b[0], b[1], b[2]).color(r, g, blue, alpha).endVertex();
        buffer.pos(c[0], c[1], c[2]).color(r, g, blue, alpha).endVertex();
    }
}
