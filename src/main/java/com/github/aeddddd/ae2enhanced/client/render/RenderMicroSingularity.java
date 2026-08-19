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
 * 不可用时回退为固定管线：黑色事件视界小球 + 缓慢旋转的紫色吸积环（对齐 1.20.1）.
 *
 * GL 状态恢复策略：不使用 pushAttrib/popAttrib(Kirino 不兼容底层 glPushAttrib),
 * 所有修改的状态在 finally 中显式恢复.
 */
public class RenderMicroSingularity extends TileEntitySpecialRenderer<TileMicroSingularity> {

    /** 相对装配枢纽黑洞的缩放系数（事件视界 4.0 × 0.08 = 0.32 格） */
    private static final float MICRO_SCALE = 0.08f;

    private static final int EVENT_HORIZON_PART_ID = 0x000000;
    private static final int ACCRETION_DISK_PART_ID = 0x010000;
    /** 回退路径吸积环颜色（蓝紫,对齐 1.20.1 的 0x8A2BE2） */
    private static final int FALLBACK_DISK_COLOR = 0x8A2BE2;

    private static final int LATITUDE_SEGMENTS = 16;
    private static final int LONGITUDE_SEGMENTS = 16;
    private static final int DISK_SEGMENTS = 48;

    @Override
    public void render(TileMicroSingularity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        renderSingularity(te.getWorld(), x, y, z, partialTicks, MICRO_SCALE);
    }

    /**
     * 通用奇点渲染入口（供微型奇点 TESR 与奇点处理仓 TESR 复用）.
     *
     * @param scale 相对装配枢纽黑洞的缩放（事件视界半径 = 4.0 × scale 格）
     */
    public static void renderSingularity(net.minecraft.world.World world, double x, double y, double z,
                                         float partialTicks, float scale) {
        ShaderProgram shader = AE2EShaders.getBlackHole();
        if (shader != null) {
            renderShader(world, x, y, z, partialTicks, scale, shader);
            return;
        }
        renderFallback(world, x, y, z, partialTicks, scale);
    }

    /**
     * shader 渲染路径：黑色事件视界小球 + fbm 噪声吸积盘（1.20 微型奇点渲染的移植）.
     */
    private static void renderShader(net.minecraft.world.World world, double x, double y, double z,
                                     float partialTicks, float scale, ShaderProgram shader) {
        double renderDist = AE2EnhancedConfig.render.renderDistance;
        double distSq = x * x + y * y + z * z;
        if (distSq > renderDist * renderDist) {
            return;
        }

        float time = (world.getTotalWorldTime() + partialTicks) * 0.05f;
        double horizon = 4.0 * scale;
        double diskInner = 4.6 * scale;
        double diskOuter = 12.0 * scale;

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
            shader.setFloat("uScale", scale);
            shader.setVec3("uCenter", 0.0f, 0.0f, 0.0f);

            // 事件视界：标准混合,写入深度
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(true);
            RenderHelper.drawSphere(horizon, EVENT_HORIZON_PART_ID, 1.0f,
                    LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);

            // 吸积盘：加法混合,不写深度
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            RenderHelper.drawAccretionDisk(diskInner, diskOuter,
                    ACCRETION_DISK_PART_ID, 1.0f, DISK_SEGMENTS);
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
     * 固定管线回退渲染：黑色事件视界小球 + 缓慢旋转的紫色吸积环（对齐 1.20.1 回退路径）.
     */
    private static void renderFallback(net.minecraft.world.World world, double x, double y, double z,
                                       float partialTicks, float scale) {
        // 与 shader 路径一致的距离剔除
        double renderDist = AE2EnhancedConfig.render.renderDistance;
        double distSq = x * x + y * y + z * z;
        if (distSq > renderDist * renderDist) {
            return;
        }

        float time = (world.getTotalWorldTime() + partialTicks) * 0.05f;
        double horizon = 4.0 * scale;
        double diskInner = 4.6 * scale;
        double diskOuter = 12.0 * scale;

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

            // 事件视界：标准混合,写入深度
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(true);
            RenderHelper.drawSphere(horizon, 0x000000, 0.99f,
                    LATITUDE_SEGMENTS, LONGITUDE_SEGMENTS);

            // 吸积环：加法混合,不写深度,缓慢旋转
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 40.0f, 0, 1, 0);
            RenderHelper.drawAccretionDisk(diskInner, diskOuter,
                    FALLBACK_DISK_COLOR, 1.0f, DISK_SEGMENTS);
            GlStateManager.popMatrix();
        } finally {
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
}
