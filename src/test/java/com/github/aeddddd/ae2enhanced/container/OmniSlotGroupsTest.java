package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.container.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.container.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

import appeng.container.slot.SlotDisabled;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;

class OmniSlotGroupsTest {
    @Test
    void disabledPlayerSlotStaysVisibleAndViewCellsRemainSeparate() {
        SlotDisabled disabled = new SlotDisabled(new FakeItemHandler(1), 0, 0, 0);
        Slot viewCell = new Slot(new InventoryBasic("view", false, 1), 0, 0, 0);

        OmniSlotGroups groups = OmniSlotGroups.classify(Collections.singletonList(disabled),
                new RCSlotFakeCraftingMatrix[0][0], new RCSlotPatternOutputs[0][0], null, null, null,
                new Slot[] { viewCell });

        assertEquals(Collections.singletonList(disabled), groups.player);
        assertEquals(Collections.singletonList(viewCell), groups.viewCells);
        assertTrue(groups.craftingMatrix.isEmpty());
        assertTrue(groups.storage.isEmpty());
        assertTrue(groups.upgrades.isEmpty());
        assertTrue(groups.pattern.isEmpty());
        assertTrue(groups.patternFeed.isEmpty());
    }
}
