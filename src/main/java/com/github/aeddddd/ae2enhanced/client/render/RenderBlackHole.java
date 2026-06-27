package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.block.BlockAssemblyController;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/**
 * 装配枢纽控制器中心黑洞的 TESR.
 *
 * GL 状态恢复策略：不使用 pushAttrib/popAttrib(Kirino 不兼容底层 glPushAttrib),
 * 所有修改的状态在 finally 中显式恢复.
 */
public class RenderBlackHole extends TileEntitySpecialRenderer<TileAssemblyController> {

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

        EnumFacing facing = EnumFacing.NORTH;
        if (te.getWorld() != null) {
            facing = te.getWorld().getBlockState(te.getPos()).getValue(BlockAssemblyController.FACING);
        }

        double centerX = x + 0.5;
        double centerY = y + 0.5;
        double centerZ = z + 0.5;
        switch (facing) {
            case SOUTH: centerZ -= 7.0; break;
            case EAST:  centerX -= 7.0; break;
            case WEST:  centerX += 7.0; break;
            default:    centerZ += 7.0; break;
        }

        double renderDist = AE2EnhancedConfig.render.renderDistance;
        double distSq = centerX * centerX + centerY * centerY + centerZ * centerZ;
        if (distSq > renderDist * renderDist) {
            return;
        }

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * ROTATION_SPEED;

        float expand = 0.5f + 0.5f * (float) Math.sin(time * 0.5);
        float brightness = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 0.35));
        float gridEnergy = 0.5f + 0.5f * (float) Math.sin(time * 1.4);

        double innerR = INNER_HALO_BASE * (0.82 + 0.36 * expand);
        double midR = MID_HALO_BASE * (0.88 + 0.24 * (1.0f - expand * 0.5f));
        double outerR = OUTER_HALO_BASE * (0.92 + 0.16 * expand);

        float innerAlpha = 0.10f + 0.28f * brightness;
        float midAlpha = 0.06f + 0.14f * (1.0f - expand * 0.3f);
        float outerAlpha = 0.04f + 0.10f * brightness;

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, centerZ);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean fogWasEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        boolean alphaTestWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean colorMaterialWasEnabled = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmapTexWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        if (lightmapTexWasEnabled) {
            GlStateManager.disableTexture2D();
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_FOG);
        GlStateManager.disableFog();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.enableDepth();
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        GL11.glNormal3f(0.0f, 1.0f, 0.0f);
        GlStateManager.disableCull();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        try {
            RenderHelper.drawSphere(EVENT_HORIZON_RADIUS, 0x000000, 0.99f, LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);

            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.5f, 0, 1, 0);
            GlStateManager.rotate(18.0f, 1, 0, 0.3f);
            RenderHelper.drawSphere(innerR, 0x140029, innerAlpha, LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);
            RenderHelper.drawWireframeSphere(innerR, 0x7700DD, 0.28f * (0.5f + 0.5f * gridEnergy), GRID_LAT, GRID_LON);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.rotate(-time * 0.3f, 0, 1, 0);
            GlStateManager.rotate(12.0f, 0.5f, 0, 1.0f);
            RenderHelper.drawSphere(midR, 0x05000D, midAlpha, LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);
            RenderHelper.drawWireframeSphere(midR, 0x110022, 0.08f * (0.5f + 0.5f * gridEnergy), GRID_LAT, GRID_LON);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 0.12f, 0, 1, 0);
            GlStateManager.rotate(8.0f, 1, 0.2f, 0);
            RenderHelper.drawSphere(outerR, 0x020005, outerAlpha, LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);
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
            if (fogWasEnabled) {
                GL11.glEnable(GL11.GL_FOG);
                GlStateManager.enableFog();
            } else {
                GL11.glDisable(GL11.GL_FOG);
                GlStateManager.disableFog();
            }
            if (alphaTestWasEnabled) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GlStateManager.enableAlpha();
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GlStateManager.disableAlpha();
            }
            if (depthTestWasEnabled) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            if (colorMaterialWasEnabled) {
                GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            } else {
                GL11.glDisable(GL11.GL_COLOR_MATERIAL);
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.popMatrix();
        }
    }
}
