package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyBatchSupportTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static IAEItemStack stack(Item item, long count) {
        IAEItemStack stack = AEItemStack.fromItemStack(new ItemStack(item));
        stack.setStackSize(count);
        return stack;
    }

    private static long available(IMEInventory<IAEItemStack> inventory, Item item) {
        IAEItemStack result = inventory.extractItems(stack(item, Long.MAX_VALUE), Actionable.SIMULATE, null);
        return result == null ? 0 : result.getStackSize();
    }

    @Test
    void checkedAmountPreservesIntBoundaryAndRejectsLongOverflow() {
        assertThat(AssemblyBatchSupport.checkedAmount(Integer.MAX_VALUE, 2))
                .isEqualTo((long) Integer.MAX_VALUE * 2L);
        assertThatThrownBy(() -> AssemblyBatchSupport.checkedAmount(Long.MAX_VALUE, 2))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void duplicateInputsAreAggregatedBeforeOneExactWithdrawal() {
        MECraftingInventory inventory = new MECraftingInventory();
        inventory.injectItems(stack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 10), Actionable.MODULATE, null);

        List<IAEItemStack> requests = Arrays.asList(stack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 3), stack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 4));
        assertThat(AssemblyBatchSupport.aggregate(requests)).hasSize(1);
        assertThat(AssemblyBatchSupport.aggregate(requests).get(0).getStackSize()).isEqualTo(7);
        List<IAEItemStack> extracted = AssemblyBatchSupport.extractExact(inventory, requests, null);

        assertThat(extracted).hasSize(1);
        assertThat(extracted.get(0).getStackSize()).isEqualTo(7);
        assertThat(available(inventory, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE))).isEqualTo(3);
    }

    @Test
    void shortActualExtractionRollsBackEveryWithdrawnItem() {
        MECraftingInventory delegate = new MECraftingInventory();
        delegate.injectItems(stack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 10), Actionable.MODULATE, null);
        delegate.injectItems(stack(Items.DIAMOND, 6), Actionable.MODULATE, null);
        IMEInventory<IAEItemStack> flaky = new ShortDiamondInventory(delegate);

        List<IAEItemStack> extracted = AssemblyBatchSupport.extractExact(flaky,
                Arrays.asList(stack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE), 4), stack(Items.DIAMOND, 6)), null);

        assertThat(extracted).isNull();
        assertThat(available(delegate, net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE))).isEqualTo(10);
        assertThat(available(delegate, Items.DIAMOND)).isEqualTo(6);
    }

    private static final class ShortDiamondInventory implements IMEInventory<IAEItemStack> {
        private final MECraftingInventory delegate;

        private ShortDiamondInventory(MECraftingInventory delegate) {
            this.delegate = delegate;
        }

        @Override
        public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource source) {
            return delegate.injectItems(input, mode, source);
        }

        @Override
        public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE && request.getItem() == Items.DIAMOND) {
                IAEItemStack shortRequest = request.copy();
                shortRequest.setStackSize(request.getStackSize() / 2);
                return delegate.extractItems(shortRequest, mode, source);
            }
            return delegate.extractItems(request, mode, source);
        }

        @Override
        public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
            return delegate.getAvailableItems(out);
        }

        @Override
        public IStorageChannel<IAEItemStack> getChannel() {
            return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        }
    }
}
