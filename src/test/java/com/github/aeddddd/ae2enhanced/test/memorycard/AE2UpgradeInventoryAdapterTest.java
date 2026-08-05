package com.github.aeddddd.ae2enhanced.test.memorycard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.inventories.InternalInventory;

import com.github.aeddddd.ae2enhanced.memorycard.upgrade.AE2UpgradeInventoryAdapter;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AE2UpgradeInventoryAdapter} 单元测试:对 AE2 InternalInventory 的委托行为.
 */
class AE2UpgradeInventoryAdapterTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testSlotCountDelegatesToInventorySize() {
        InternalInventory inventory = mock(InternalInventory.class);
        when(inventory.size()).thenReturn(4);

        assertThat(new AE2UpgradeInventoryAdapter(inventory).getSlotCount()).isEqualTo(4);
    }

    @Test
    void testGetStackInSlotDelegates() {
        InternalInventory inventory = mock(InternalInventory.class);
        ItemStack diamond = new ItemStack(Items.DIAMOND, 2);
        when(inventory.getStackInSlot(1)).thenReturn(diamond);

        assertThat(new AE2UpgradeInventoryAdapter(inventory).getStackInSlot(1)).isSameAs(diamond);
    }

    @Test
    void testSetStackInSlotUsesSetItemDirect() {
        InternalInventory inventory = mock(InternalInventory.class);
        ItemStack stick = new ItemStack(Items.STICK);

        new AE2UpgradeInventoryAdapter(inventory).setStackInSlot(2, stick);
        verify(inventory).setItemDirect(2, stick);
    }

    @Test
    void testClearSlotsEmptiesEverySlot() {
        InternalInventory inventory = mock(InternalInventory.class);
        when(inventory.size()).thenReturn(3);

        new AE2UpgradeInventoryAdapter(inventory).clearSlots();
        for (int i = 0; i < 3; i++) {
            verify(inventory).setItemDirect(eq(i), argThat(ItemStack::isEmpty));
        }
    }
}
