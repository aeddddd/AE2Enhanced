package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.client.shader.AE2EShaders;
import com.github.aeddddd.ae2enhanced.client.shader.ShaderProgram;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

/**
 * 微型奇点的 TESR.
 * shader 可用时复用装配枢纽黑洞 shader 的迷你版（事件视界+吸积盘,uScale=0.08）；
 * 不可用时回退为固定管线：比正式黑洞更小、更致密、旋转更快,仅 2 层光晕.
 *
 * GL 状态恢复策略：不使用 pushAttrib/popAttrib(Kirino 不兼容底层 glPushAttrib),
 * 所有修改的状态在 finally 中显式恢复.
 */
public class RenderMicroSingularity extends TileEntitySpecialRenderer<TileMicroSingularity> {

    /** 相对装配枢纽黑洞的缩放系数（事件视界 4.0 × 0.08 = 0.32 格） */
    private static final float MICRO_SCALE = 0.08f;

    private static final int EVENT_HORIZON_PART_ID = 0x000000;
    private static final int ACCRETION_DISK_PART_ID = 0x010000;

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
        ShaderProgram shader = AE2EShaders.getBlackHole();
        if (shader != null) {
            renderShader(te, x, y, z, partialTicks, shader);
            return;
        }
        renderFallback(te, x, y, z, partialTicks);
    }

    /**
     * shader 渲染路径：黑色事件视界小球 + fbm 噪声吸积盘（1.20 微型奇点渲染的移植）.
     */
    private void renderShader(TileMicroSingularity te, double x, double y, double z, float partialTicks,
                              ShaderProgram shader) {
        double renderDist = AE2EnhancedConfig.render.renderDistance;
        double distSq = x * x + y * y + z * z;
        if (distSq > renderDist * renderDist) {
            return;
        }

        float time = (te.getWorld().getTotalWorldTime() + partialTicks) * 0.05f;

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean lightingWasEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture2DWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int shadeModelWas = GL11.glGetInteger(GL11.GL_SHADE_MODEL);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableCull();
            GlStateManager.enableBlend();

            shader.use();
            shader.setFloat("uTime", time);
            shader.setFloat("uIntensity", (float) AE2EnhancedConfig.render.blackHoleShaderIntensity);
            shader.setFloat("uScale", MICRO_SCALE);
            shader.setVec3("uCenter", 0.0f, 0.0f, 0.0f);

            // 事件视界：标准混合,写入深度
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(true);
            RenderHelper.drawSphere(4.0 * MICRO_SCALE, EVENT_HORIZON_PART_ID, 1.0f,
                    LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);

            // 吸积盘：加法混合,不写深度
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            RenderHelper.drawAccretionDisk(4.6 * MICRO_SCALE, 12.0 * MICRO_SCALE,
                    ACCRETION_DISK_PART_ID, 1.0f, 48);
        } finally {
            ShaderProgram.stop();
            GlStateManager.depthMask(true);
            if (cullWasEnabled) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            GlStateManager.shadeModel(shadeModelWas);
            if (texture2DWasEnabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            if (lightingWasEnabled) {
                GL11.glEnable(GL11.GL_LIGHTING);
                GlStateManager.enableLighting();
            } else {
                GL11.glDisable(GL11.GL_LIGHTING);
                GlStateManager.disableLighting();
            }
            if (!blendWasEnabled) {
                GlStateManager.disableBlend();
            }
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.popMatrix();
        }
    }

    /**
     * 固定管线回退渲染.
     */
    private void renderFallback(TileMicroSingularity te, double x, double y, double z, float partialTicks) {
        // 与 shader 路径一致的距离剔除
        double renderDist = AE2EnhancedConfig.render.renderDistance;
        double distSq = x * x + y * y + z * z;
        if (distSq > renderDist * renderDist) {
            return;
        }

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
        boolean lightingWasEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture2DWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int shadeModelWas = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
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
            GlStateManager.shadeModel(shadeModelWas);
            if (texture2DWasEnabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            if (lightingWasEnabled) {
                GL11.glEnable(GL11.GL_LIGHTING);
                GlStateManager.enableLighting();
            } else {
                GL11.glDisable(GL11.GL_LIGHTING);
                GlStateManager.disableLighting();
            }
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
