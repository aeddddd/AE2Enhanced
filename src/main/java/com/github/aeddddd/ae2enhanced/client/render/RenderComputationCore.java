package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.block.BlockComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/**
 * Supercausal Computation Core TESR — Dyson Sphere wrapped star.
 * Solid core + geodesic tech-grid frame + translucent panels + energy streams.
 */
public class RenderComputationCore extends TileEntitySpecialRenderer<TileComputationCore> {

    // --- Geometry ---
    private static final float CORE_RADIUS = 7.0f;
    private static final float FRAME_RADIUS = 8.5f;
    private static final float PANEL_RADIUS = 8.3f;
    private static final float RING_RADIUS = 10.0f;
    private static final float JET_LENGTH = 20.0f;

    // --- Animation ---
    private static final float ROT_SPEED = 0.4f;
    private static final float PULSE_SPEED = 0.04f;

    // --- Colors ---
    private static final int COLOR_CORE_HOT  = 0xFFF0E0; // warm white core
    private static final int COLOR_CORE_COOL = 0xFF6600; // orange outer
    private static final int COLOR_FRAME     = 0x99AABB; // silver frame
    private static final int COLOR_FRAME_GLOW= 0x00DDFF; // cyan glow on frame
    private static final int COLOR_PANEL     = 0x0A1A2A; // dark blue panel
    private static final int COLOR_PANEL_EDGE= 0x0088AA; // panel edge glow
    private static final int COLOR_ENERGY    = 0x00FFFF; // cyan energy stream
    private static final int COLOR_JET       = 0xAADDFF; // polar jet
    private static final int COLOR_RING      = 0xD4AF37; // gold ring

    @Override
    public void render(TileComputationCore te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te == null || !te.isFormed()) return;

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROT_SPEED;
        float pulse = 0.5f + 0.5f * (float) Math.sin((te.getWorld().getTotalWorldTime() + partialTicks) * PULSE_SPEED);

        IBlockState state = te.getWorld().getBlockState(te.getPos());
        EnumFacing controllerFacing = EnumFacing.NORTH;
        if (state.getBlock() instanceof BlockComputationCore) {
            controllerFacing = state.getValue(BlockComputationCore.FACING);
        }
        EnumFacing structureDir = controllerFacing.getOpposite();
        double forwardOffset = -9.0;

        double cx = x + 0.5 + structureDir.getXOffset() * forwardOffset;
        double cy = y + 0.5;
        double cz = z + 0.5 + structureDir.getZOffset() * forwardOffset;

        double distSq = cx * cx + cy * cy + cz * cz;
        if (distSq > 160.0 * 160.0) return;

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
            // --- Inner star core ---
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.8f, 0, 1, 0);
            drawSolidCore(CORE_RADIUS, 20, 28, COLOR_CORE_HOT, COLOR_CORE_COOL, 0.98f, time);
            GlStateManager.popMatrix();

            // --- Dyson sphere frame (outer grid) ---
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.15f, 0, 1, 0);
            drawDysonFrame(FRAME_RADIUS, COLOR_FRAME, COLOR_FRAME_GLOW, 0.55f + 0.15f * pulse, time, pulse);
            drawDysonPanels(PANEL_RADIUS, COLOR_PANEL, COLOR_PANEL_EDGE, 0.25f + 0.08f * pulse, time);
            GlStateManager.popMatrix();

            // --- Energy streams along frame ---
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.15f, 0, 1, 0);
            drawEnergyStreams(FRAME_RADIUS, COLOR_ENERGY, 0.70f + 0.20f * pulse, time);
            GlStateManager.popMatrix();

            // --- Support rings ---
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.10f, 0, 1, 0);
            drawSupportRings(RING_RADIUS, COLOR_RING, COLOR_FRAME_GLOW, 0.40f + 0.12f * pulse, time);
            GlStateManager.popMatrix();

            // (polar jets removed)

        } finally {
            GL11.glPointSize(1.0f);
            GlStateManager.glLineWidth(1.0f);
            if (!blendWasEnabled) GlStateManager.disableBlend();
            if (!cullWasEnabled) GlStateManager.disableCull();
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

    // -----------------------------------------------------------------
    // Solid core sphere
    // -----------------------------------------------------------------
    private void drawSolidCore(float radius, int latSegs, int lonSegs,
                               int hotColor, int coolColor, float alpha, float time) {
        float rh = ((hotColor >> 16) & 0xFF) / 255.0f;
        float gh = ((hotColor >> 8) & 0xFF) / 255.0f;
        float bh = (hotColor & 0xFF) / 255.0f;
        float rc = ((coolColor >> 16) & 0xFF) / 255.0f;
        float gc = ((coolColor >> 8) & 0xFF) / 255.0f;
        float bc = (coolColor & 0xFF) / 255.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        for (int lat = 0; lat < latSegs; lat++) {
            float t1 = lat / (float) latSegs;
            float t2 = (lat + 1) / (float) latSegs;
            float theta1 = (float) Math.PI * t1;
            float theta2 = (float) Math.PI * t2;

            buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int lon = 0; lon <= lonSegs; lon++) {
                float phi = 2f * (float) Math.PI * lon / lonSegs;
                for (int pass = 0; pass < 2; pass++) {
                    float theta = (pass == 0) ? theta1 : theta2;
                    float t = (pass == 0) ? t1 : t2;
                    float st = (float) Math.sin(theta);
                    float nx = st * (float) Math.cos(phi);
                    float ny = (float) Math.cos(theta);
                    float nz = st * (float) Math.sin(phi);
                    float px = nx * radius, py = ny * radius, pz = nz * radius;

                    float poleFactor = Math.abs(ny);
                    float r = rc + (rh - rc) * poleFactor;
                    float g = gc + (gh - gc) * poleFactor;
                    float b_ = bc + (bh - bc) * poleFactor;

                    float rim = 1.0f - Math.abs(ny);
                    rim = rim * rim * 0.30f;
                    r += rim; g += rim * 0.6f;

                    float pattern = (float) Math.sin(phi * 4f + time * 0.08f) * (float) Math.sin(theta * 3f);
                    pattern *= 0.05f; r += pattern; g += pattern; b_ += pattern;

                    float hs1 = (float) Math.exp(-((nx-0.4f)*(nx-0.4f) + (ny-0.7f)*(ny-0.7f) + nz*nz) * 8f);
                    float hs2 = (float) Math.exp(-((nx+0.4f)*(nx+0.4f) + (ny+0.7f)*(ny+0.7f) + nz*nz) * 8f);
                    float hs = Math.max(hs1, hs2) * 0.30f;
                    r += hs; g += hs; b_ += hs;

                    buf.pos(px, py, pz).color(Math.min(1f,r), Math.min(1f,g), Math.min(1f,b_), alpha).endVertex();
                }
            }
            tess.draw();
        }
    }

    // -----------------------------------------------------------------
    // Dyson sphere frame — thick lat/lon grid with node highlights
    // -----------------------------------------------------------------
    private void drawDysonFrame(float radius, int frameColor, int glowColor, float alpha, float time, float pulse) {
        float rf = ((frameColor >> 16) & 0xFF) / 255.0f;
        float gf = ((frameColor >> 8) & 0xFF) / 255.0f;
        float bf = (frameColor & 0xFF) / 255.0f;
        float rg = ((glowColor >> 16) & 0xFF) / 255.0f;
        float gg = ((glowColor >> 8) & 0xFF) / 255.0f;
        float bg = (glowColor & 0xFF) / 255.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        int latRings = 8;
        int lonRings = 12;
        int seg = 48;

        GL11.glLineWidth(3.0f);

        // Latitude rings
        for (int i = 1; i < latRings; i++) {
            float theta = (float) Math.PI * i / latRings;
            float y = (float) Math.cos(theta) * radius;
            float ringR = (float) Math.sin(theta) * radius;
            float a = alpha * (0.8f + 0.2f * (float) Math.sin(i * 1.1f + time * 0.08f));

            buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
            for (int j = 0; j <= seg; j++) {
                float phi = 2f * (float) Math.PI * j / seg;
                float px = (float) Math.cos(phi) * ringR;
                float pz = (float) Math.sin(phi) * ringR;
                buf.pos(px, y, pz).color(rf, gf, bf, a).endVertex();
            }
            tess.draw();
        }

        // Longitude meridians
        for (int i = 0; i < lonRings; i++) {
            float phi = 2f * (float) Math.PI * i / lonRings;
            float a = alpha * (0.8f + 0.2f * (float) Math.cos(i * 0.8f + time * 0.12f));

            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int j = 0; j <= seg; j++) {
                float theta = (float) Math.PI * j / seg;
                float px = (float) Math.sin(theta) * (float) Math.cos(phi) * radius;
                float py = (float) Math.cos(theta) * radius;
                float pz = (float) Math.sin(theta) * (float) Math.sin(phi) * radius;
                buf.pos(px, py, pz).color(rf, gf, bf, a).endVertex();
            }
            tess.draw();
        }

        // Frame nodes (glowing intersections)
        GL11.glPointSize(5.0f + 2.0f * pulse);
        buf.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION_COLOR);
        for (int lat = 1; lat < latRings; lat++) {
            float theta = (float) Math.PI * lat / latRings;
            float y = (float) Math.cos(theta) * radius;
            float ringR = (float) Math.sin(theta) * radius;
            for (int lon = 0; lon < lonRings; lon++) {
                float phi = 2f * (float) Math.PI * lon / lonRings;
                float px = (float) Math.cos(phi) * ringR;
                float pz = (float) Math.sin(phi) * ringR;
                float nodePulse = 0.6f + 0.4f * (float) Math.sin(lat * 2.3f + lon * 1.7f + time * 0.5f);
                buf.pos(px, y, pz).color(rg, gg, bg, alpha * nodePulse).endVertex();
            }
        }
        tess.draw();
    }

    // -----------------------------------------------------------------
    // Dyson sphere panels — translucent hex-like patches between frame lines
    // -----------------------------------------------------------------
    private void drawDysonPanels(float radius, int panelColor, int edgeColor, float alpha, float time) {
        float rp = ((panelColor >> 16) & 0xFF) / 255.0f;
        float gp = ((panelColor >> 8) & 0xFF) / 255.0f;
        float bp = (panelColor & 0xFF) / 255.0f;
        float re = ((edgeColor >> 16) & 0xFF) / 255.0f;
        float ge = ((edgeColor >> 8) & 0xFF) / 255.0f;
        float be = (edgeColor & 0xFF) / 255.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        int latSegs = 8;
        int lonSegs = 12;

        for (int lat = 0; lat < latSegs; lat++) {
            for (int lon = 0; lon < lonSegs; lon++) {
                // Staggered pattern for hex-like feel
                if ((lat + lon + (lat/2)) % 3 != 0) continue;

                float theta1 = (float) Math.PI * lat / latSegs;
                float theta2 = (float) Math.PI * (lat + 1) / latSegs;
                float phi1 = 2f * (float) Math.PI * lon / lonSegs;
                float phi2 = 2f * (float) Math.PI * (lon + 1) / lonSegs;

                float panelPulse = 0.5f + 0.5f * (float) Math.sin(lat * 1.3f + lon * 2.1f + time * 0.2f);
                float a = alpha * panelPulse;

                buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                for (int pass = 0; pass <= 1; pass++) {
                    float phi = (pass == 0) ? phi1 : phi2;
                    for (int p = 0; p <= 1; p++) {
                        float theta = (p == 0) ? theta1 : theta2;
                        float st = (float) Math.sin(theta);
                        float nx = st * (float) Math.cos(phi);
                        float ny = (float) Math.cos(theta);
                        float nz = st * (float) Math.sin(phi);
                        float px = nx * radius;
                        float py = ny * radius;
                        float pz = nz * radius;
                        // Edge glow on panel borders
                        boolean edge = (pass == 0 || pass == 1 || p == 0 || p == 1);
                        float cr = edge ? re : rp;
                        float cg = edge ? ge : gp;
                        float cb = edge ? be : bp;
                        float ca = edge ? a * 1.5f : a;
                        buf.pos(px, py, pz).color(cr, cg, cb, Math.min(1f, ca)).endVertex();
                    }
                }
                tess.draw();
            }
        }
    }

    // -----------------------------------------------------------------
    // Energy streams flowing along the frame
    // -----------------------------------------------------------------
    private void drawEnergyStreams(float radius, int color, float alpha, float time) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        GL11.glLineWidth(2.5f);

        int streams = 6;
        int seg = 32;
        for (int s = 0; s < streams; s++) {
            float phi = 2f * (float) Math.PI * s / streams;
            float flow = time * 0.4f + s * 1.5f;

            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= seg; i++) {
                float t = i / (float) seg;
                float theta = (float) Math.PI * t;
                // Flowing highlight position
                float flowPos = (flow + t * 3f) % 1f;
                float brightness = (float) Math.exp(-((flowPos - 0.5f) * (flowPos - 0.5f)) * 20f);
                brightness = Math.max(0.1f, brightness);

                float st = (float) Math.sin(theta);
                float px = st * (float) Math.cos(phi) * radius;
                float py = (float) Math.cos(theta) * radius;
                float pz = st * (float) Math.sin(phi) * radius;
                float a = alpha * brightness;
                buf.pos(px, py, pz).color(r, g, b, a).endVertex();
            }
            tess.draw();
        }
    }

    // -----------------------------------------------------------------
    // Support rings — large structural rings around the sphere
    // -----------------------------------------------------------------
    private void drawSupportRings(float radius, int ringColor, int glowColor, float alpha, float time) {
        float rr = ((ringColor >> 16) & 0xFF) / 255.0f;
        float gr = ((ringColor >> 8) & 0xFF) / 255.0f;
        float br = (ringColor & 0xFF) / 255.0f;
        float rg = ((glowColor >> 16) & 0xFF) / 255.0f;
        float gg = ((glowColor >> 8) & 0xFF) / 255.0f;
        float bg = (glowColor & 0xFF) / 255.0f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        int seg = 96;

        // Equatorial support ring (thick)
        GL11.glLineWidth(4.0f);
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = 2f * (float) Math.PI * i / seg;
            float px = (float) Math.cos(angle) * radius;
            float pz = (float) Math.sin(angle) * radius;
            float a = alpha * (0.9f + 0.1f * (float) Math.cos(angle * 3f + time));
            buf.pos(px, 0, pz).color(rr, gr, br, a).endVertex();
        }
        tess.draw();

        // Inner glow on equatorial ring
        GL11.glLineWidth(1.5f);
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = 2f * (float) Math.PI * i / seg;
            float px = (float) Math.cos(angle) * (radius - 0.3f);
            float pz = (float) Math.sin(angle) * (radius - 0.3f);
            float a = alpha * 0.6f * (0.7f + 0.3f * (float) Math.sin(angle * 5f + time * 2f));
            buf.pos(px, 0, pz).color(rg, gg, bg, a).endVertex();
        }
        tess.draw();

        // Tilted ring 1
        GlStateManager.pushMatrix();
        GlStateManager.rotate(55f, 1, 0, 0);
        GL11.glLineWidth(2.5f);
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = 2f * (float) Math.PI * i / seg;
            float px = (float) Math.cos(angle) * radius * 0.9f;
            float pz = (float) Math.sin(angle) * radius * 0.9f;
            float a = alpha * 0.75f;
            buf.pos(px, 0, pz).color(rr, gr, br, a).endVertex();
        }
        tess.draw();
        GlStateManager.popMatrix();

        // Tilted ring 2
        GlStateManager.pushMatrix();
        GlStateManager.rotate(-40f, 1, 0, 0);
        GlStateManager.rotate(70f, 0, 1, 0);
        GL11.glLineWidth(2.5f);
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= seg; i++) {
            float angle = 2f * (float) Math.PI * i / seg;
            float px = (float) Math.cos(angle) * radius * 0.85f;
            float pz = (float) Math.sin(angle) * radius * 0.85f;
            float a = alpha * 0.75f;
            buf.pos(px, 0, pz).color(rr, gr, br, a).endVertex();
        }
        tess.draw();
        GlStateManager.popMatrix();
    }


}
