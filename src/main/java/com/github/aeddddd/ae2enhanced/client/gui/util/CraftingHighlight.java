package com.github.aeddddd.ae2enhanced.client.gui.util;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

import com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch;
import com.github.aeddddd.ae2enhanced.client.me.CraftingStatus;

/**
 * 合成置顶行的绘制：底图高亮 + 每个在合成物品的动态描边。
 *
 * <p>从旧 {@code GuiOmniTerm.drawBG} 原样抽出，供旧终端与可拼接终端的 repo 面板共用，
 * 避免两处各写一份。几何约定：{@code left/top} 是<strong>单元格原点</strong>
 * （＝槽位 16×16 图标位置减 1），一格 18×18。</p>
 */
public final class CraftingHighlight {
    /** crafting.png 一次平铺覆盖的格数（贴图里就是一行 9 格 = 162 像素）。 */
    public static final int TILE_CELLS = 9;
    /** 单元格边长。 */
    public static final int CELL_SIZE = 18;
    private static final int TILE_WIDTH = TILE_CELLS * CELL_SIZE;
    /** crafting.png 的贴图边长。 */
    private static final int TEXTURE_SIZE = 256;

    private CraftingHighlight() {
    }

    /**
     * 根据合成进度计算动态描边颜色。
     * 进度从高（刚开始）到低（快完成）：橙色 → 绿色，同时带呼吸脉冲效果。
     * 已完成（remaining &lt;= 0）时返回完全透明。
     */
    public static int borderColor(CraftingStatus status, long time) {
        if (status.isDone()) {
            return 0;
        }
        float ratio = status.getRatio(); // 1.0=刚开始, 0.0=即将完成
        // 颜色插值：橙色(255,136,0) → 绿色(0,255,0)
        int r = (int) (255 * ratio);
        int g = (int) (255 - 119 * ratio);
        int b = 0;
        // 呼吸脉冲：频率随进度加快(快完成时闪烁更快)
        double pulse = Math.sin(time / (200.0 + 300.0 * ratio)) * 0.5 + 0.5;
        int alpha = (int) (120 + pulse * 80 + (1.0f - ratio) * 40);
        if (alpha > 255) {
            alpha = 255;
        }
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 在 {@code (left, top)} 起画 {@code columns} 格宽的合成置顶行：先平铺 crafting.png 高亮
     * （贴图只有 9 格，超过 9 列时继续平铺），再为前 {@code min(active.size(), columns)} 个
     * 合成中的物品各画一圈动态色描边。
     */
    public static void drawPinnedRow(int left, int top, int columns, List<CraftingStatus> active,
            float zLevel) {
        if (columns <= 0 || active == null || active.isEmpty()) {
            return;
        }
        boolean pushed = NinePatch.pushZLevel(zLevel);
        Minecraft.getMinecraft().getTextureManager().bindTexture(GuiResourceCache.CRAFTING_HIGHLIGHT);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int totalWidth = columns * CELL_SIZE;
        for (int drawn = 0; drawn < totalWidth; drawn += TILE_WIDTH) {
            Gui.drawModalRectWithCustomSizedTexture(left + drawn, top, 0, 0, TILE_WIDTH, CELL_SIZE,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }

        int bottom = top + CELL_SIZE;
        long time = System.currentTimeMillis();
        int count = Math.min(active.size(), columns);
        for (int i = 0; i < count; i++) {
            int borderColor = borderColor(active.get(i), time);
            if ((borderColor >>> 24) == 0) {
                continue; // 已完成(alpha=0)，跳过描边
            }
            int slotLeft = left + i * CELL_SIZE;
            int slotRight = slotLeft + CELL_SIZE;
            Gui.drawRect(slotLeft, top, slotRight, top + 1, borderColor);        // 上
            Gui.drawRect(slotLeft, bottom - 1, slotRight, bottom, borderColor);  // 下
            Gui.drawRect(slotLeft, top, slotLeft + 1, bottom, borderColor);      // 左
            Gui.drawRect(slotRight - 1, top, slotRight, bottom, borderColor);    // 右
        }
        // drawRect 会把 GlStateManager 的当前颜色留在矩形色上，后续纹理绘制前必须复位
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        NinePatch.popZLevel(pushed);
    }
}
