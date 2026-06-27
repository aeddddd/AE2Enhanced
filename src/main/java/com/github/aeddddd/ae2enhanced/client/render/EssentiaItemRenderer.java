package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import thaumcraft.api.aspects.Aspect;

import java.awt.Color;

/**
 * E2a：为 ItemEssentiaDrop 提供内置物品渲染器.
 *
 * 完全复制 Thaumic Energistics DummyAspectRenderer 的绘制逻辑,
 * 仅将 aspect 获取方式改为 ItemEssentiaDrop.getAspectTag.
 */
public class EssentiaItemRenderer extends TileEntityItemStackRenderer {

    public static final EssentiaItemRenderer INSTANCE = new EssentiaItemRenderer();
    private static final java.util.Set<String> loggedAspects = new java.util.HashSet<>();

    private EssentiaItemRenderer() {
    }

    @Override
    public void renderByItem(ItemStack stack, float partialTicks) {
        String aspectTag = ItemEssentiaDrop.getAspectTag(stack);
        int damage = stack.getItemDamage();
        if (aspectTag == null) {
            return;
        }
        Aspect aspect = Aspect.getAspect(aspectTag);
        if (aspect == null) {
            return;
        }

        // 以下代码与 DummyAspectRenderer.func_192838_a 完全一致
        GlStateManager.pushMatrix();
        Minecraft.getMinecraft().getTextureManager().bindTexture(aspect.getImage());
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.rotate(180.0f, 1.0f, 1.0f, 0.0f);
        GlStateManager.rotate(90.0f, 0.0f, 0.0f, 1.0f);
        GlStateManager.translate(0.0f, -1.0f, 0.0f);

        Color c = new Color(aspect.getColor());
        GlStateManager.color(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, 1.0f);

        Tessellator tess = Tessellator.getInstance();
        tess.getBuffer().begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);

        tess.getBuffer().pos(0.0, 1.0, 0.0).tex(0.0, 1.0)
                .color(c.getRed(), c.getGreen(), c.getBlue(), 255).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(1.0, 1.0, 0.0).tex(1.0, 1.0)
                .color(c.getRed(), c.getGreen(), c.getBlue(), 255).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(1.0, 0.0, 0.0).tex(1.0, 0.0)
                .color(c.getRed(), c.getGreen(), c.getBlue(), 255).normal(0, 0, 1).endVertex();
        tess.getBuffer().pos(0.0, 0.0, 0.0).tex(0.0, 0.0)
                .color(c.getRed(), c.getGreen(), c.getBlue(), 255).normal(0, 0, 1).endVertex();

        tess.draw();

        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
