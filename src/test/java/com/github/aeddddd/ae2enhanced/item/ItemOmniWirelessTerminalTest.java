package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemOmniWirelessTerminalTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @SuppressWarnings("restriction")
    private static ItemOmniWirelessTerminal allocateTerminal() throws Exception {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        return (ItemOmniWirelessTerminal) unsafe.allocateInstance(ItemOmniWirelessTerminal.class);
    }

    @Test
    void powerOnlyNbtChangesDoNotReplayTheEquipAnimation() throws Exception {
        ItemOmniWirelessTerminal terminal = allocateTerminal();
        ItemStack before = new ItemStack(terminal);
        before.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        ItemStack changed = before.copy();
        changed.getTagCompound().setDouble("internalCurrentPower", 123.0D);

        assertFalse(terminal.shouldCauseReequipAnimation(before, changed, false));
        assertTrue(terminal.shouldCauseReequipAnimation(before, changed, true));
    }

    @Test
    void readingStorageIdDoesNotCreateAuthoritativeNbt() throws Exception {
        ItemStack stack = new ItemStack(allocateTerminal());

        assertTrue(ItemOmniWirelessTerminal.getStorageId(stack).equals(new java.util.UUID(0L, 0L)));
        assertFalse(stack.hasTagCompound());

        java.util.UUID created = ItemOmniWirelessTerminal.ensureStorageId(stack);
        assertFalse(created.equals(new java.util.UUID(0L, 0L)));
        assertTrue(ItemOmniWirelessTerminal.ensureStorageId(stack).equals(created));
        assertTrue(ItemOmniWirelessTerminal.getStorageId(stack).equals(created));
    }
}
