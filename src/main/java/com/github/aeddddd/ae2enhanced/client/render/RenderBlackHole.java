package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class RenderBlackHole extends TileEntitySpecialRenderer<TileAssemblyController> {

    private static final double EVENT_HORIZON_RADIUS = 2.0;
    private static final double PHOTON_SPHERE_RADIUS = 2.15;
    private static final double OUTER_HALO_RADIUS = 2.5;
    private static final double RING_INNER_RADIUS = 2.3;
    private static final double RING_OUTER_RADIUS = 2.8;
    private static final int LATITUDE_SEGMENTS = 20;
    private static final int LONGITUDE_SEGMENTS = 20;
    private static final int RING_SEGMENTS = 64;
    private static final float ROTATION_SPEED = 0.3f;

    @Override
    public void render(TileAssemblyController te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (!te.isFormed()) return;

        // 几何中心相对于控制器方块中心的偏移
        // AssemblyStructure.getOriginFromController(controllerPos) = controllerPos.add(0, 0, 7)
        double centerX = x + 0.5;
        double centerY = y + 0.5;
        double centerZ = z + 0.5 + 7.0;

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROTATION_SPEED;

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

        // 1. 事件视界（纯黑球体）
        drawSphere(EVENT_HORIZON_RADIUS, 0x000000, 0.98f);

        // 2. 光子球层（发光的薄球壳，缓慢旋转，带倾角）
        GlStateManager.pushMatrix();
        GlStateManager.rotate(time, 0, 1, 0);
        GlStateManager.rotate(23.5f, 1, 0, 0);
        drawSphere(PHOTON_SPHERE_RADIUS, 0x330066, 0.45f);
        GlStateManager.popMatrix();

        // 3. 外层光晕（反向旋转，不同倾角）
        GlStateManager.pushMatrix();
        GlStateManager.rotate(-time * 0.7f, 0, 1, 0);
        GlStateManager.rotate(15.0f, 0.3f, 0, 1.0f);
        drawSphere(OUTER_HALO_RADIUS, 0x001144, 0.18f);
        GlStateManager.popMatrix();

        // 4. 旋转光环（水平放置，快速旋转）
        GlStateManager.pushMatrix();
        GlStateManager.rotate(time * 1.5f, 0, 1, 0);
        drawRing(RING_INNER_RADIUS, RING_OUTER_RADIUS, 0x6600CC, 0.35f);
        GlStateManager.popMatrix();

        // 5. 奇点核心（极小的发光点，科幻风格）
        drawSphere(0.15, 0xFFFFFF, 0.7f);

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private void drawSphere(double radius, int color, float alpha) {
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

    private void drawRing(double innerRadius, double outerRadius, int color, float alpha) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = 2 * Math.PI * i / RING_SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            // 外圈顶点（较暗）
            buffer.pos(outerRadius * cos, 0, outerRadius * sin)
                  .color(r, g, b, alpha * 0.3f).endVertex();
            // 内圈顶点（较亮）
            buffer.pos(innerRadius * cos, 0, innerRadius * sin)
                  .color(r, g, b, alpha).endVertex();
        }

        tessellator.draw();
    }
}
