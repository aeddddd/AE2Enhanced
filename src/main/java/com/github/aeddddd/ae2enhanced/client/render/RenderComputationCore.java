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
 * Renders a massive wireframe sphere (~radius 8) with layered shells,
 * rotating latitude/longitude lines, an equatorial ring, and a pulsing inner core.
 */
public class RenderComputationCore extends TileEntitySpecialRenderer<TileComputationCore> {

    private static final float SPHERE_RADIUS = 8.0f;
    private static final float SHELL_RADIUS_2 = 6.5f;
    private static final float SHELL_RADIUS_3 = 5.0f;
    private static final float CORE_RADIUS = 1.5f;
    private static final float RING_RADIUS = 8.5f;

    private static final float ROT_SPEED = 0.3f;
    private static final float RING_SPEED = 0.5f;
    private static final float PULSE_SPEED = 0.03f;

    private static final int COLOR_SPHERE = 0x00d4ff;
    private static final int COLOR_SHELL = 0x0088cc;
    private static final int COLOR_SHELL_INNER = 0x004488;
    private static final int COLOR_CORE = 0x66ffff;
    private static final int COLOR_RING = 0x44aaff;
    private static final int COLOR_GLOW = 0x88eeff;

    @Override
    public void render(TileComputationCore te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te == null || !te.isFormed()) return;

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROT_SPEED;
        float ringTime = (te.getWorld().getTotalWorldTime() + partialTicks) * RING_SPEED;
        float pulse = 0.5f + 0.5f * (float) Math.sin((te.getWorld().getTotalWorldTime() + partialTicks) * PULSE_SPEED);

        // Structure center relative to controller
        double cx = x + 0.5;
        double cy = y + 4.0;
        double cz = z + 0.5 + 2.0;

        double distSq = cx * cx + cy * cy + cz * cz;
        if (distSq > 96.0 * 96.0) return;

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
            // Outermost wireframe sphere
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.2f, 0, 1, 0);
            drawSphereWireframe(SPHERE_RADIUS, 24, 16, COLOR_SPHERE, 0.35f + 0.15f * pulse, 2.0f);
            GlStateManager.popMatrix();

            // Middle shell
            GlStateManager.pushMatrix();
            GlStateManager.rotate(-time * 0.3f, 1, 0, 0);
            drawSphereWireframe(SHELL_RADIUS_2, 18, 12, COLOR_SHELL, 0.25f + 0.1f * pulse, 1.5f);
            GlStateManager.popMatrix();

            // Inner shell
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.4f, 0, 0, 1);
            drawSphereWireframe(SHELL_RADIUS_3, 12, 8, COLOR_SHELL_INNER, 0.15f + 0.08f * pulse, 1.0f);
            GlStateManager.popMatrix();

            // Equatorial ring (rotating)
            GlStateManager.pushMatrix();
            GlStateManager.rotate(ringTime, 0, 1, 0);
            drawEquatorialRing(RING_RADIUS, 1.2f, COLOR_RING, 0.4f + 0.2f * pulse, 2.5f);
            GlStateManager.popMatrix();

            // Secondary tilted ring
            GlStateManager.pushMatrix();
            GlStateManager.rotate(60f, 1, 0, 0);
            GlStateManager.rotate(ringTime * 0.7f, 0, 1, 0);
            drawEquatorialRing(RING_RADIUS * 0.9f, 0.8f, COLOR_CORE, 0.2f + 0.1f * pulse, 1.8f);
            GlStateManager.popMatrix();

            // Inner glowing core (icosahedron-like polyhedron)
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time, 0, 1, 0);
            GlStateManager.rotate(time * 0.6f, 1, 0, 0);
            drawInnerCore(CORE_RADIUS, COLOR_CORE, 0.6f + 0.25f * pulse, 2.0f);
            drawVertexGlows(CORE_RADIUS, COLOR_GLOW, 0.8f + 0.15f * pulse, 0.15f);
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

    private void drawSphereWireframe(float radius, int latSegments, int lonSegments, int color, float alpha, float lineWidth) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glLineWidth(lineWidth);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // Latitude lines (horizontal circles)
        for (int lat = 1; lat < latSegments; lat++) {
            float theta = (float) Math.PI * lat / latSegments;
            float y = (float) Math.cos(theta) * radius;
            float ringR = (float) Math.sin(theta) * radius;

            buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
            for (int lon = 0; lon <= lonSegments; lon++) {
                float phi = 2f * (float) Math.PI * lon / lonSegments;
                float px = (float) Math.cos(phi) * ringR;
                float pz = (float) Math.sin(phi) * ringR;
                buf.pos(px, y, pz).color(r, g, b, alpha).endVertex();
            }
            tess.draw();
        }

        // Longitude lines (vertical arcs)
        for (int lon = 0; lon < lonSegments; lon++) {
            float phi = 2f * (float) Math.PI * lon / lonSegments;

            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int lat = 0; lat <= latSegments; lat++) {
                float theta = (float) Math.PI * lat / latSegments;
                float px = (float) Math.sin(theta) * (float) Math.cos(phi) * radius;
                float py = (float) Math.cos(theta) * radius;
                float pz = (float) Math.sin(theta) * (float) Math.sin(phi) * radius;
                buf.pos(px, py, pz).color(r, g, b, alpha).endVertex();
            }
            tess.draw();
        }
    }

    private void drawEquatorialRing(float radius, float width, int color, float alpha, float lineWidth) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glLineWidth(lineWidth);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        int segments = 128;
        float innerR = radius - width;

        // Outer edge
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float px = (float) Math.cos(angle) * radius;
            float pz = (float) Math.sin(angle) * radius;
            buf.pos(px, 0, pz).color(r, g, b, alpha).endVertex();
        }
        tess.draw();

        // Inner edge
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float px = (float) Math.cos(angle) * innerR;
            float pz = (float) Math.sin(angle) * innerR;
            buf.pos(px, 0, pz).color(r, g, b, alpha * 0.4f).endVertex();
        }
        tess.draw();

        // Radial spokes
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < 16; i++) {
            float angle = (float) (2 * Math.PI * i / 16);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buf.pos(cos * innerR, 0, sin * innerR).color(r, g, b, alpha * 0.3f).endVertex();
            buf.pos(cos * radius, 0, sin * radius).color(r, g, b, alpha * 0.6f).endVertex();
        }
        tess.draw();
    }

    private void drawInnerCore(float size, int color, float alpha, float lineWidth) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        GL11.glLineWidth(lineWidth);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        float s = size;
        // Icosahedron-ish: 6 diagonal axes through the cube
        float[][] ends = {
            {-s, -s, -s}, { s,  s,  s},
            {-s, -s,  s}, { s,  s, -s},
            {-s,  s, -s}, { s, -s,  s},
            {-s,  s,  s}, { s, -s, -s},
            { 0, -s,  0}, { 0,  s,  0},
            {-s,  0,  0}, { s,  0,  0},
            { 0,  0, -s}, { 0,  0,  s},
        };
        for (int i = 0; i < ends.length; i += 2) {
            buf.pos(ends[i][0], ends[i][1], ends[i][2]).color(r, g, b, alpha).endVertex();
            buf.pos(ends[i+1][0], ends[i+1][1], ends[i+1][2]).color(r, g, b, alpha).endVertex();
        }
        tess.draw();
    }

    private void drawVertexGlows(float size, int color, float alpha, float pointSize) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GL11.glPointSize(pointSize * 30);
        buf.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);

        float s = size;
        float[][] verts = {
            {-s, -s, -s}, { s, -s, -s}, { s,  s, -s}, {-s,  s, -s},
            {-s, -s,  s}, { s, -s,  s}, { s,  s,  s}, {-s,  s,  s},
        };
        for (float[] v : verts) {
            buf.pos(v[0], v[1], v[2]).color(r, g, b, alpha).endVertex();
        }
        tess.draw();
        GL11.glPointSize(1.0f);
    }
}
