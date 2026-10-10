package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminalV2;
import net.minecraft.item.ItemStack;

/**
 * Omni 终端物品 → GUI id 的唯一判定点（阶段 2a 的"分发收口"）。
 *
 * <p>旧版全链路有 6 处硬编码 {@code GuiHandler.GUI_OMNI_TERMINAL}，新增可拼接终端后必须区分两代物品：
 * 旧物品继续打开旧 GUI（零回归），{@link ItemOmniWirelessTerminalV2} 打开新 GUI
 * （{@link GuiHandler#GUI_SPLICED_TERMINAL}）。所有分发点统一调用
 * {@link #resolveOmniGuiId(ItemStack)}，不再各自写常量。</p>
 */
public final class OmniTerminalResolver {
    private OmniTerminalResolver() {
    }

    /** 该物品栈是否属于可拼接终端（阶段 2a 之后新增的一代）。 */
    public static boolean isSplicedTerminal(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemOmniWirelessTerminalV2;
    }

    /** 该物品栈应打开的 GUI id。空栈/无关物品按旧终端处理（保持历史行为）。 */
    public static int resolveOmniGuiId(ItemStack stack) {
        return guiIdFor(isSplicedTerminal(stack));
    }

    /**
     * 纯映射（便于单测覆盖两代物品的分支）：可拼接终端 → 15，其余 → 11。
     */
    public static int guiIdFor(boolean splicedTerminal) {
        return splicedTerminal ? GuiHandler.GUI_SPLICED_TERMINAL : GuiHandler.GUI_OMNI_TERMINAL;
    }
}
