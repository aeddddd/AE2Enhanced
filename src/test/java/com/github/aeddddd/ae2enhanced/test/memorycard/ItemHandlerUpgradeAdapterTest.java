package com.github.aeddddd.ae2enhanced.test.memorycard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import com.github.aeddddd.ae2enhanced.memorycard.upgrade.ItemHandlerUpgradeAdapter;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ItemHandlerUpgradeAdapter} 单元测试.
 * 可修改的 IItemHandler 用真实 {@link ItemStackHandler} 做读写往返;
 * 只读(非 IItemHandlerModifiable)handler 验证写入静默忽略.
 */
class ItemHandlerUpgradeAdapterTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testReadWriteRoundTripWithItemStackHandler() {
        ItemStackHandler handler = new ItemStackHandler(2);
        ItemHandlerUpgradeAdapter adapter = new ItemHandlerUpgradeAdapter(handler);

        assertThat(adapter.getSlotCount()).isEqualTo(2);
        assertThat(adapter.getStackInSlot(0).isEmpty()).isTrue();

        ItemStack diamond = new ItemStack(Items.DIAMOND, 3);
        adapter.setStackInSlot(0, diamond);
        assertThat(ItemStack.isSameItemSameTags(adapter.getStackInSlot(0), diamond)).isTrue();
        assertThat(adapter.getStackInSlot(0).getCount()).isEqualTo(3);
        assertThat(ItemStack.isSameItemSameTags(handler.getStackInSlot(0), diamond)).isTrue();
    }

    @Test
    void testClearSlotsEmptiesHandler() {
        ItemStackHandler handler = new ItemStackHandler(2);
        ItemHandlerUpgradeAdapter adapter = new ItemHandlerUpgradeAdapter(handler);
        adapter.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        adapter.setStackInSlot(1, new ItemStack(Items.STICK));

        adapter.clearSlots();
        assertThat(handler.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(handler.getStackInSlot(1).isEmpty()).isTrue();
    }

    @Test
    void testReadOnlyHandlerIgnoresWrites() {
        // 只读 IItemHandler(非 IItemHandlerModifiable):写入静默忽略,不得抛异常
        IItemHandler readOnly = mock(IItemHandler.class);
        when(readOnly.getSlots()).thenReturn(1);
        when(readOnly.getStackInSlot(0)).thenReturn(new ItemStack(Items.DIAMOND));

        ItemHandlerUpgradeAdapter adapter = new ItemHandlerUpgradeAdapter(readOnly);
        assertThat(adapter.getSlotCount()).isEqualTo(1);
        assertThat(adapter.getStackInSlot(0).is(Items.DIAMOND)).isTrue();
        assertThatCode(() -> {
            adapter.setStackInSlot(0, new ItemStack(Items.STICK));
            adapter.clearSlots();
        }).doesNotThrowAnyException();
    }
}
