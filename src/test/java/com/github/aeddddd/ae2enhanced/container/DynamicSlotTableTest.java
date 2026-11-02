package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;

class DynamicSlotTableTest {
    private static Slot slot() {
        return new Slot(new InventoryBasic("test", false, 1), 0, 0, 0);
    }

    @Test
    void replacementKeepsStableSlotsAndUsesAe2DynamicSlotNumbers() {
        Slot stable = slot();
        stable.slotNumber = 7;
        Slot oldDynamic = slot();
        List<Slot> table = new ArrayList<Slot>(Arrays.asList(stable, oldDynamic));
        Slot first = slot();
        Slot second = slot();

        DynamicSlotTable.replace(table, Collections.singletonList(oldDynamic), Arrays.asList(first, second));

        assertEquals(Arrays.asList(stable, first, second), table);
        assertSame(stable, table.get(0));
        assertEquals(7, stable.slotNumber);
        assertEquals(0, first.slotNumber);
        assertEquals(0, second.slotNumber);
    }
}
