package com.github.aeddddd.ae2enhanced.integration.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * 奇点处理仓 JEI 槽位渲染器.
 * 输入组单批数量可达数千(黑洞配方常见 1024+), 栈数量不做 64 截断: 角标显示格式化计数(如 1.0k),
 * 超过 999 时 tooltip 追加精确数量, 避免截断显示造成误解.
 */
public class ChamberIngredientRenderer implements IIngredientRenderer<ItemStack> {

    public static final ChamberIngredientRenderer INSTANCE = new ChamberIngredientRenderer();

    @Override
    public void render(Minecraft minecraft, int x, int y, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        int count = stack.getCount();
        if (count <= 1) {
            return;
        }
        String text = formatCount(count);
        FontRenderer fr = minecraft.fontRenderer;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0f, 0.0f, 200.0f);
        float scale = text.length() > 3 ? 0.5f : 0.75f;
        GlStateManager.scale(scale, scale, 1.0f);
        int w = fr.getStringWidth(text);
        fr.drawString(text, (int) ((x + 17 - w * scale) / scale), (int) ((y + 18 - 9 * scale) / scale),
                0xFFFFFF, true);
        GlStateManager.popMatrix();
    }

    @Override
    public List<String> getTooltip(Minecraft minecraft, ItemStack stack, ITooltipFlag flag) {
        List<String> tooltip = stack.getTooltip(minecraft.player, flag);
        if (stack.getCount() > 999) {
            tooltip.add(I18n.format("jei.ae2enhanced.chamber.count", stack.getCount()));
        }
        return tooltip;
    }

    @Override
    public FontRenderer getFontRenderer(Minecraft minecraft, ItemStack stack) {
        FontRenderer fr = stack.getItem().getFontRenderer(stack);
        return fr != null ? fr : minecraft.fontRenderer;
    }

    @Override
    public List<String> getTooltip(Minecraft minecraft, ItemStack stack, boolean advanced) {
        return getTooltip(minecraft, stack,
                advanced ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL);
    }

    @Override
    public List<String> getTooltip(Minecraft minecraft, ItemStack stack) {
        return getTooltip(minecraft, stack, ITooltipFlag.TooltipFlags.NORMAL);
    }

    static String formatCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        String[] units = {"k", "M", "G", "T", "P", "E"};
        double value = count;
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000.0;
            unit++;
        }
        return String.format("%.1f%s", value, units[unit]);
    }
}
