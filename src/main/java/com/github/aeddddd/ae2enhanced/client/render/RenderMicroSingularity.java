package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * 微型奇点的 TESR。
 * 比正式黑洞更小、更致密、旋转更快，仅 2 层光晕。
 *
 * GL 状态恢复策略：不使用 pushAttrib/popAttrib（Kirino 不兼容底层 glPushAttrib），
 * 所有修改的状态在 finally 中显式恢复。
 */
public class RenderMicroSingularity extends TileEntitySpecialRenderer<TileMicroSingularity> {

    private static final double EVENT_HORIZON_RADIUS = 1.2;
    private static final double INNER_HALO_BASE = 1.8;
    private static final double OUTER_HALO_BASE = 2.8;

    private static final int LATITUDE_SEGMENTS = 16;
    private static final int LONGITUDE_SEGMENTS = 16;
    private static final int GRID_LAT = 6;
    private static final int GRID_LON = 8;

    private static final float ROTATION_SPEED = 1.5f;

    @Override
    public void render(TileMicroSingularity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        double centerX = x + 0.5;
        double centerY = y + 0.5;
        double centerZ = z + 0.5;

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROTATION_SPEED;

        float expand = 0.5f + 0.5f * (float) Math.sin(time * 0.8);
        float brightness = 0.5f + 0.5f * (0.5f + 0.5f * (float) Math.sin(time * 0.6));
        float gridEnergy = 0.5f + 0.5f * (float) Math.sin(time * 2.0);

        double innerR = INNER_HALO_BASE * (0.82 + 0.36 * expand);
        double outerR = OUTER_HALO_BASE * (0.88 + 0.24 * expand);

        float innerAlpha = 0.15f + 0.35f * brightness;
        float outerAlpha = 0.08f + 0.18f * brightness;

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, centerZ);

        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();

        try {
            // 1. 事件视界（纯黑）
            drawSphere(EVENT_HORIZON_RADIUS, 0x000000, 0.99f);

            // 2. 内层光晕（深紫，主旋转）
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.8f, 0, 1, 0);
            GlStateManager.rotate(18.0f, 1, 0, 0.3f);
            drawSphere(innerR, 0x140029, innerAlpha);
            // 内层网格：常亮 80% 亮度
            drawWireframeSphere(innerR, 0x7700DD, 0.4f * (0.5f + 0.5f * gridEnergy));
            GlStateManager.popMatrix();

            // 3. 外层光晕（深紫雾，反向旋转）
            GlStateManager.pushMatrix();
            GlStateManager.rotate(-time * 0.5f, 0, 1, 0);
            GlStateManager.rotate(12.0f, 0.5f, 0, 1.0f);
            drawSphere(outerR, 0x05000D, outerAlpha);
            drawWireframeSphere(outerR, 0x440088, 0.12f * (0.5f + 0.5f * gridEnergy));
            GlStateManager.popMatrix();
        } finally {
            // 显式恢复所有修改的状态（Kirino 不兼容 glPushAttrib/glPopAttrib）
            if (cullWasEnabled) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
            );
            GlStateManager.popMatrix();
        }
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

        GlStateManager.glLineWidth(1.5f);

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
