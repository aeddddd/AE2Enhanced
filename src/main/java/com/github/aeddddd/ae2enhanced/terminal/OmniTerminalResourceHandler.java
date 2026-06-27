package com.github.aeddddd.ae2enhanced.terminal;

import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

/**
 * Omni Terminal 非物品资源交互适配器.
 * <p>
 * Omni Terminal 的点击与 tooltip 实际通过 {@link TerminalClickBridge}/{@link TerminalTooltipBridge}
 * 注入到 {@link AEBaseGui}，因此本类作为显式门面，将 Omni Terminal 相关调用转发给统一的
 * {@link UnifiedResourceTerminalBridge}。
 * </p>
 * <p>
 * 分页、搜索、JEI 传输仍由 Omni Terminal 自身逻辑处理；本类只负责 RF/Mana/Starlight
 * 的点击、tooltip、容器填充/排空。
 * </p>
 */
public final class OmniTerminalResourceHandler {

    private OmniTerminalResourceHandler() {}

    /**
     * 处理 Omni Terminal 中的鼠标点击.
     *
     * @return true 表示已处理，应取消后续默认逻辑
     */
    public static boolean onHandleMouseClick(AEBaseGui gui, Slot slot, int slotId, int mouseButton,
                                             net.minecraft.inventory.ClickType clickType) {
        return UnifiedResourceTerminalBridge.onHandleMouseClick(gui, slot, slotId, mouseButton, clickType);
    }

    /**
     * 处理 Omni Terminal 中的悬停 tooltip.
     *
     * @return true 表示已处理
     */
    public static boolean onRenderHoveredToolTip(GuiContainer gui, Slot slot, int mouseX, int mouseY) {
        return UnifiedResourceTerminalBridge.onRenderHoveredToolTip(gui, slot, mouseX, mouseY);
    }

    /**
     * 判断给定槽位是否由资源处理器管理（用于 Omni Terminal 的特殊高亮/提示）.
     */
    public static boolean isResourceSlot(SlotME slot) {
        return slot != null && slot.getAEStack() != null
                && ResourceTerminalHandlerRegistry.findForStack(slot.getAEStack()) != null;
    }
}
