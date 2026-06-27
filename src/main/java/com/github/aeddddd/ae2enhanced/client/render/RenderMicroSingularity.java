package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

/**
 * 微型奇点的 TESR.
 * 比正式黑洞更小、更致密、旋转更快,仅 2 层光晕.
 *
 * GL 状态恢复策略：不使用 pushAttrib/popAttrib(Kirino 不兼容底层 glPushAttrib),
 * 所有修改的状态在 finally 中显式恢复.
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
        GlStateManager.disableCull();

        try {
            RenderHelper.drawSphere(EVENT_HORIZON_RADIUS, 0x000000, 0.99f, LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);

            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.8f, 0, 1, 0);
            GlStateManager.rotate(18.0f, 1, 0, 0.3f);
            RenderHelper.drawSphere(innerR, 0x140029, innerAlpha, LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);
            RenderHelper.drawWireframeSphere(innerR, 0x7700DD, 0.4f * (0.5f + 0.5f * gridEnergy), GRID_LAT, GRID_LON);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.rotate(-time * 0.5f, 0, 1, 0);
            GlStateManager.rotate(12.0f, 0.5f, 0, 1.0f);
            RenderHelper.drawSphere(outerR, 0x05000D, outerAlpha, LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);
            RenderHelper.drawWireframeSphere(outerR, 0x440088, 0.12f * (0.5f + 0.5f * gridEnergy), GRID_LAT, GRID_LON);
            GlStateManager.popMatrix();
        } finally {
            if (cullWasEnabled) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
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
            RenderHelper.resetLineWidth();
            GlStateManager.popMatrix();
        }
    }
}
