package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Supercausal Computation Core TESR.
 * Renders a rotating wireframe CPU core with orbital rings above the structure center.
 */
public class RenderComputationCore extends TileEntitySpecialRenderer<TileComputationCore> {

    private static final float CORE_SIZE = 2.5f;
    private static final float RING_OUTER = 4.0f;
    private static final float RING_INNER = 3.2f;
    private static final float ROT_SPEED = 1.0f;
    private static final float RING_SPEED = 0.6f;
    private static final float PULSE_SPEED = 0.04f;

    private static final int COLOR_CORE = 0x00d4ff;
    private static final int COLOR_RING = 0x44aaff;
    private static final int COLOR_GLOW = 0x88eeff;

    @Override
    public void render(TileComputationCore te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te == null || !te.isFormed()) return;

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROT_SPEED;
        float ringTime = (te.getWorld().getTotalWorldTime() + partialTicks) * RING_SPEED;
        float pulse = 0.5f + 0.5f * (float) Math.sin((te.getWorld().getTotalWorldTime() + partialTicks) * PULSE_SPEED);

        // Structure center relative to controller: (0, 0, 2)
        double cx = x + 0.5;
        double cy = y + 6.0;
        double cz = z + 0.5 + 2.0;

        double distSq = cx * cx + cy * cy + cz * cz;
        if (distSq > 64.0 * 64.0) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, cz);

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
        GlStateManager.enableCull();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        try {
            // Rotating wireframe cube (CPU core)
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time, 0, 1, 0);
            GlStateManager.rotate(time * 0.5f, 1, 0, 0);
            drawCubeWireframe(CORE_SIZE, COLOR_CORE, 0.6f + 0.2f * pulse, 2.5f);
            drawVertexGlows(CORE_SIZE, COLOR_GLOW, 0.7f + 0.2f * pulse, 0.12f);
            GlStateManager.popMatrix();

            // Horizontal orbital ring
            GlStateManager.pushMatrix();
            GlStateManager.rotate(ringTime, 0, 1, 0);
            drawRing(RING_OUTER, RING_INNER, COLOR_RING, 0.25f + 0.15f * pulse, 2.0f);
            GlStateManager.popMatrix();

            // Tilted secondary ring
            GlStateManager.pushMatrix();
            GlStateManager.rotate(45f, 1, 0, 0);
            GlStateManager.rotate(ringTime * 0.7f, 0, 1, 0);
            drawRing(RING_OUTER * 0.8f, RING_INNER * 0.8f, COLOR_CORE, 0.15f + 0.1f * pulse, 1.5f);
            GlStateManager.popMatrix();

        } finally {
            if (!cullWasEnabled) GlStateManager.disableCull();
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
            );
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
        }
    }

    private void drawCubeWireframe(float size, int color, float alpha, float lineWidth) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glLineWidth(lineWidth);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        float s = size;
        // 12 edges of a cube
        // Bottom face
        buf.pos(-s, -s, -s).color(r, g, b, alpha).endVertex(); buf.pos( s, -s, -s).color(r, g, b, alpha).endVertex();
        buf.pos( s, -s, -s).color(r, g, b, alpha).endVertex(); buf.pos( s, -s,  s).color(r, g, b, alpha).endVertex();
        buf.pos( s, -s,  s).color(r, g, b, alpha).endVertex(); buf.pos(-s, -s,  s).color(r, g, b, alpha).endVertex();
        buf.pos(-s, -s,  s).color(r, g, b, alpha).endVertex(); buf.pos(-s, -s, -s).color(r, g, b, alpha).endVertex();
        // Top face
        buf.pos(-s,  s, -s).color(r, g, b, alpha).endVertex(); buf.pos( s,  s, -s).color(r, g, b, alpha).endVertex();
        buf.pos( s,  s, -s).color(r, g, b, alpha).endVertex(); buf.pos( s,  s,  s).color(r, g, b, alpha).endVertex();
        buf.pos( s,  s,  s).color(r, g, b, alpha).endVertex(); buf.pos(-s,  s,  s).color(r, g, b, alpha).endVertex();
        buf.pos(-s,  s,  s).color(r, g, b, alpha).endVertex(); buf.pos(-s,  s, -s).color(r, g, b, alpha).endVertex();
        // Vertical edges
        buf.pos(-s, -s, -s).color(r, g, b, alpha).endVertex(); buf.pos(-s,  s, -s).color(r, g, b, alpha).endVertex();
        buf.pos( s, -s, -s).color(r, g, b, alpha).endVertex(); buf.pos( s,  s, -s).color(r, g, b, alpha).endVertex();
        buf.pos( s, -s,  s).color(r, g, b, alpha).endVertex(); buf.pos( s,  s,  s).color(r, g, b, alpha).endVertex();
        buf.pos(-s, -s,  s).color(r, g, b, alpha).endVertex(); buf.pos(-s,  s,  s).color(r, g, b, alpha).endVertex();

        tess.draw();
    }

    private void drawVertexGlows(float size, int color, float alpha, float pointSize) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GL11.glPointSize(pointSize * 20);
        buf.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);

        float s = size;
        for (int dx : new int[]{-1, 1}) {
            for (int dy : new int[]{-1, 1}) {
                for (int dz : new int[]{-1, 1}) {
                    buf.pos(dx * s, dy * s, dz * s).color(r, g, b, alpha).endVertex();
                }
            }
        }
        tess.draw();
        GL11.glPointSize(1.0f);
    }

    private void drawRing(float outer, float inner, int color, float alpha, float lineWidth) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glLineWidth(lineWidth);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);

        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float px = (float) Math.cos(angle) * outer;
            float pz = (float) Math.sin(angle) * outer;
            buf.pos(px, 0, pz).color(r, g, b, alpha).endVertex();
        }
        tess.draw();

        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float px = (float) Math.cos(angle) * inner;
            float pz = (float) Math.sin(angle) * inner;
            buf.pos(px, 0, pz).color(r, g, b, alpha * 0.5f).endVertex();
        }
        tess.draw();
    }
}
