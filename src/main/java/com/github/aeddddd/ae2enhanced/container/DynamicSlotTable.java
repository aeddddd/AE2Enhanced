package com.github.aeddddd.ae2enhanced.container;

import java.util.Collection;
import java.util.List;

import net.minecraft.inventory.Slot;

public final class DynamicSlotTable {
    private DynamicSlotTable() {
    }

    public static void replace(List<Slot> table, Collection<Slot> previous, Collection<Slot> next) {
        for (Slot slot : previous) {
            table.remove(slot);
        }
        for (Slot slot : next) {
            slot.slotNumber = 0;
            table.add(slot);
        }
    }
}
