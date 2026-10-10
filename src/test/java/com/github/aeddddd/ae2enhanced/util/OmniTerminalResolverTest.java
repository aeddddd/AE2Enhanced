package com.github.aeddddd.ae2enhanced.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.gui.GuiHandler;

/**
 * 分发收口的映射规则：旧物品→11（零回归）、可拼接终端→15。
 *
 * <p>两代物品实例都无法在无引导环境里构造（构造器会触发 AE2 的 config/handler 注册），
 * 因此这里用纯映射重载 {@code guiIdFor(boolean)} 覆盖两个分支，
 * 再用空栈/无关物品覆盖 {@code resolveOmniGuiId} 的"回退到旧 GUI"一侧。</p>
 */
class OmniTerminalResolverTest {

    @Test
    void guiIdsAreFrozenForBothGenerations() {
        // 分发 id 是客户端/服务端的隐式协议，必须冻结；15 同时受 encodePatternId 的页码位约束（≤15）
        assertEquals(11, GuiHandler.GUI_OMNI_TERMINAL);
        assertEquals(15, GuiHandler.GUI_SPLICED_TERMINAL);
        assertTrue(GuiHandler.GUI_SPLICED_TERMINAL <= 15,
                "id 必须留在低 4 位,否则 encodePatternId 的页码编码会撞上");
    }

    @Test
    void splicedTerminalsOpenTheNewGui() {
        assertEquals(GuiHandler.GUI_SPLICED_TERMINAL, OmniTerminalResolver.guiIdFor(true));
    }

    @Test
    void legacyTerminalsKeepTheOldGui() {
        assertEquals(GuiHandler.GUI_OMNI_TERMINAL, OmniTerminalResolver.guiIdFor(false));
    }

    @Test
    void emptyAndForeignStacksFallBackToTheLegacyGui() {
        assertFalse(OmniTerminalResolver.isSplicedTerminal(null));
        assertFalse(OmniTerminalResolver.isSplicedTerminal(ItemStack.EMPTY));
        assertEquals(GuiHandler.GUI_OMNI_TERMINAL, OmniTerminalResolver.resolveOmniGuiId(null));
        assertEquals(GuiHandler.GUI_OMNI_TERMINAL, OmniTerminalResolver.resolveOmniGuiId(ItemStack.EMPTY));
    }
}
