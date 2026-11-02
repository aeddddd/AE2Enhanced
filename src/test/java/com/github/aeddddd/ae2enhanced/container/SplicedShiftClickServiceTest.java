package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

class SplicedShiftClickServiceTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static ItemStack namedDiamond(int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        stack.setStackDisplayName("special");
        return stack;
    }

    @Test
    void mergeUsesExistingStacksBeforeEmptySlotsAndPreservesNbt() {
        SplicedTermSlotPolicy policy = new SplicedTermSlotPolicy();
        InventoryBasic firstInventory = new InventoryBasic("first", false, 1);
        InventoryBasic secondInventory = new InventoryBasic("second", false, 1);
        Slot first = new Slot(firstInventory, 0, 0, 0);
        Slot second = new Slot(secondInventory, 0, 0, 0);
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, Arrays.asList(first, second));
        ItemStack existing = namedDiamond(10);
        firstInventory.setInventorySlotContents(0, existing);

        ItemStack remainder = SplicedShiftClickService.mergeInto(policy, namedDiamond(80), Arrays.asList(first, second));

        assertTrue(remainder.isEmpty());
        assertEquals(64, firstInventory.getStackInSlot(0).getCount());
        assertEquals(26, secondInventory.getStackInSlot(0).getCount());
        assertEquals("special", firstInventory.getStackInSlot(0).getDisplayName());
        assertEquals("special", secondInventory.getStackInSlot(0).getDisplayName());
    }

    @Test
    void incompatibleNbtDoesNotMergeIntoExistingStack() {
        SplicedTermSlotPolicy policy = new SplicedTermSlotPolicy();
        InventoryBasic inventory = new InventoryBasic("first", false, 1);
        Slot target = new Slot(inventory, 0, 0, 0);
        policy.setModuleSlots(SplicedTermSlotPolicy.MODULE_STORAGE, java.util.Collections.singletonList(target));
        inventory.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 10));

        ItemStack remainder = SplicedShiftClickService.mergeInto(policy, namedDiamond(20),
                java.util.Collections.singletonList(target));

        assertEquals(20, remainder.getCount());
        assertEquals(10, inventory.getStackInSlot(0).getCount());
        assertTrue(inventory.getStackInSlot(0).getDisplayName().equals("Diamond"));
    }
}
