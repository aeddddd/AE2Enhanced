package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 阶段 3b：{@code panelMask} 的位序、归一化与集合换算。
 *
 * <p>位序一旦重排，旧存档里的掩码含义就会错位，因此这里把"下标 ↔ 模块 id"钉死。</p>
 */
class SplicedTermModulesTest {

    @Test
    void bitOrderIsStableAndCoversEveryMaskablePanel() {
        assertEquals(SplicedTermSlotPolicy.MODULE_REPO, SplicedTermModules.moduleIdAt(SplicedTermModules.REPO));
        assertEquals(SplicedTermSlotPolicy.MODULE_PLAYER, SplicedTermModules.moduleIdAt(SplicedTermModules.PLAYER));
        assertEquals(SplicedTermSlotPolicy.MODULE_VIEWCELL, SplicedTermModules.moduleIdAt(SplicedTermModules.VIEWCELL));
        assertEquals(SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX,
                SplicedTermModules.moduleIdAt(SplicedTermModules.CRAFTING));
        assertEquals(SplicedTermSlotPolicy.MODULE_STORAGE, SplicedTermModules.moduleIdAt(SplicedTermModules.STORAGE));
        assertEquals(SplicedTermSlotPolicy.MODULE_UPGRADE, SplicedTermModules.moduleIdAt(SplicedTermModules.UPGRADE));
        assertEquals(SplicedTermSlotPolicy.MODULE_PATTERN, SplicedTermModules.moduleIdAt(SplicedTermModules.PATTERN));

        assertEquals(0, SplicedTermModules.indexOf(SplicedTermSlotPolicy.MODULE_REPO));
        assertEquals(6, SplicedTermModules.indexOf(SplicedTermSlotPolicy.MODULE_PATTERN));
        assertEquals(7, SplicedTermModules.moduleIds().size());
        assertEquals((1 << 7) - 1, SplicedTermModules.ALL, "ALL 是 7 位全开 = 127");
    }

    @Test
    void craftingOutputGroupIsNotMaskable() {
        // crafting_output 是 crafting 模块内的子槽位组，位掩码只按模块粒度开关
        assertFalse(SplicedTermModules.isMaskable(SplicedTermSlotPolicy.MODULE_CRAFTING_OUTPUT));
        assertFalse(SplicedTermModules.isMaskable(SplicedTermSlotPolicy.MODULE_PATTERN_FEED));
        assertEquals(0, SplicedTermModules.bit(SplicedTermSlotPolicy.MODULE_CRAFTING_OUTPUT));
    }

    @Test
    void alwaysVisibleModulesSurviveSanitize() {
        int mask = SplicedTermModules.sanitize(0);
        assertTrue(SplicedTermModules.isVisible(mask, SplicedTermModules.REPO));
        assertTrue(SplicedTermModules.isVisible(mask, SplicedTermModules.PLAYER));
        assertFalse(SplicedTermModules.isVisible(mask, SplicedTermModules.STORAGE));
    }

    @Test
    void sanitizeDropsUnknownBits() {
        int polluted = SplicedTermModules.ALL | 0x7FFFFF80; // 高位全是垃圾
        assertEquals(SplicedTermModules.ALL, SplicedTermModules.sanitize(polluted));
        assertEquals(SplicedTermModules.ALL, SplicedTermModules.sanitize(-1));
    }

    @Test
    void withVisibilityRoundTrips() {
        int mask = SplicedTermModules.withVisibility(SplicedTermModules.ALL, SplicedTermSlotPolicy.MODULE_STORAGE,
                false);
        assertFalse(SplicedTermModules.isVisible(mask, SplicedTermSlotPolicy.MODULE_STORAGE));
        assertTrue(SplicedTermModules.isVisible(mask, SplicedTermSlotPolicy.MODULE_UPGRADE));

        int back = SplicedTermModules.withVisibility(mask, SplicedTermSlotPolicy.MODULE_STORAGE, true);
        assertEquals(SplicedTermModules.ALL, back);

        // 未登记的模块 id 不应影响掩码
        assertEquals(mask, SplicedTermModules.withVisibility(mask, "nope", true));
    }

    @Test
    void maskToIdSetRoundTrip() {
        int mask = SplicedTermModules.sanitize(SplicedTermModules.bit(SplicedTermModules.PATTERN)
                | SplicedTermModules.bit(SplicedTermModules.VIEWCELL));
        Set<String> ids = SplicedTermModules.visibleModuleIds(mask);
        assertEquals(4, ids.size(), "repo/playerinv 常显 + pattern + viewcell");
        assertTrue(ids.contains(SplicedTermSlotPolicy.MODULE_PATTERN));
        assertTrue(ids.contains(SplicedTermSlotPolicy.MODULE_VIEWCELL));
        assertTrue(ids.contains(SplicedTermSlotPolicy.MODULE_REPO));
        assertTrue(ids.contains(SplicedTermSlotPolicy.MODULE_PLAYER));
        assertFalse(ids.contains(SplicedTermSlotPolicy.MODULE_STORAGE));

        assertEquals(mask, SplicedTermModules.maskOf(ids));
    }

    @Test
    void maskOfIgnoresUnknownIdsAndKeepsAlwaysVisible() {
        int mask = SplicedTermModules.maskOf(Arrays.asList("does-not-exist"));
        assertEquals(SplicedTermModules.ALWAYS_VISIBLE, mask);
    }
}
