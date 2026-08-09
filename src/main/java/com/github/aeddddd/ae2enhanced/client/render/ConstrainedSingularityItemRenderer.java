package com.github.aeddddd.ae2enhanced.client.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

/**
 * 被约束微型奇点（物品形态）的 TEISR.
 * 方块形态黑洞渲染样式的微缩版：黑色事件视界小球 + 缓慢旋转的发光吸积环 + 微弱辉光.
 * 纯固定管线实现,在 GUI、第一人称与掉落物实体上均可工作.
 *
 * GL 状态恢复策略：不使用 pushAttrib/popAttrib(Kirino 不兼容底层 glPushAttrib),
 * 所有修改的状态在 finally 中显式恢复.
 */
public class ConstrainedSingularityItemRenderer extends TileEntityItemStackRenderer {

    public static final ConstrainedSingularityItemRenderer INSTANCE = new ConstrainedSingularityItemRenderer();

    private static final float HORIZON_RADIUS = 0.22f;
    private static final float DISK_INNER = 0.26f;
    private static final float DISK_OUTER = 0.42f;
    private static final int SPHERE_SEGMENTS = 16;
    private static final int DISK_SEGMENTS = 32;

    private ConstrainedSingularityItemRenderer() {
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
        float breath = 0.75f + 0.25f * MathHelper.sin(time * 2.0f);

        boolean textureWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lightingWasEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        GlStateManager.pushMatrix();
        try {
            // TEISR 渲染原点在物品包围盒角点,先平移到中心
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();

            // 事件视界：纯黑实心小球
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            drawSphere(buf, HORIZON_RADIUS, 0.0f, 0.0f, 0.0f, 1.0f, SPHERE_SEGMENTS, SPHERE_SEGMENTS);
            tess.draw();

            // 吸积环：紫色发光盘,缓慢旋转并略带呼吸（加法混合）
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.pushMatrix();
            GlStateManager.rotate(time * 45.0f, 0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(15.0f, 1.0f, 0.0f, 0.0f);
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            drawDisk(buf, DISK_INNER * breath, DISK_OUTER * breath, 0.69f, 0.28f, 0.88f, DISK_SEGMENTS);
            tess.draw();
            GlStateManager.popMatrix();

            // 微弱紫色辉光球
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            drawSphere(buf, HORIZON_RADIUS * 1.6f, 0.16f, 0.04f, 0.27f, 0.35f * breath,
                    SPHERE_SEGMENTS, SPHERE_SEGMENTS);
            tess.draw();
        } finally {
            if (!blendWasEnabled) {
                GlStateManager.disableBlend();
            } else {
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
            if (cullWasEnabled) {
                GlStateManager.enableCull();
            }
            if (lightingWasEnabled) {
                GlStateManager.enableLighting();
            }
            if (textureWasEnabled) {
                GlStateManager.enableTexture2D();
            }
            GlStateManager.popMatrix();
        }
    }

    /**
     * 以原点为中心向缓冲写入球体四边形网格（POSITION_COLOR）.
     */
    static void drawSphere(BufferBuilder buf, float radius, float r, float g, float b, float a,
                           int latSegments, int lonSegments) {
        for (int i = 0; i < latSegments; i++) {
            double theta0 = Math.PI * i / latSegments - Math.PI / 2.0;
            double theta1 = Math.PI * (i + 1) / latSegments - Math.PI / 2.0;
            for (int j = 0; j < lonSegments; j++) {
                double phi0 = 2.0 * Math.PI * j / lonSegments;
                double phi1 = 2.0 * Math.PI * (j + 1) / lonSegments;

                sphereVertex(buf, radius, theta0, phi0, r, g, b, a);
                sphereVertex(buf, radius, theta0, phi1, r, g, b, a);
                sphereVertex(buf, radius, theta1, phi1, r, g, b, a);
                sphereVertex(buf, radius, theta1, phi0, r, g, b, a);
            }
        }
    }

    private static void sphereVertex(BufferBuilder buf, float radius, double theta, double phi,
                                     float r, float g, float b, float a) {
        double x = radius * Math.cos(theta) * Math.cos(phi);
        double y = radius * Math.sin(theta);
        double z = radius * Math.cos(theta) * Math.sin(phi);
        buf.pos(x, y, z).color(r, g, b, a).endVertex();
    }

    /**
     * 在 XZ 平面写入环形吸积盘网格,内缘亮外缘透明.
     */
    static void drawDisk(BufferBuilder buf, float inner, float outer, float r, float g, float b, int segments) {
        for (int j = 0; j < segments; j++) {
            double phi0 = 2.0 * Math.PI * j / segments;
            double phi1 = 2.0 * Math.PI * (j + 1) / segments;
            float c0 = (float) Math.cos(phi0);
            float s0 = (float) Math.sin(phi0);
            float c1 = (float) Math.cos(phi1);
            float s1 = (float) Math.sin(phi1);

            buf.pos(inner * c0, 0, inner * s0).color(r, g, b, 0.9f).endVertex();
            buf.pos(inner * c1, 0, inner * s1).color(r, g, b, 0.9f).endVertex();
            buf.pos(outer * c1, 0, outer * s1).color(r * 0.5f, g * 0.3f, b, 0.0f).endVertex();
            buf.pos(outer * c0, 0, outer * s0).color(r * 0.5f, g * 0.3f, b, 0.0f).endVertex();
        }
    }
}
