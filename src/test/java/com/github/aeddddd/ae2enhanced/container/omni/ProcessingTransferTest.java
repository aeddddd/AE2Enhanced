package com.github.aeddddd.ae2enhanced.container.omni;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

class ProcessingTransferTest {
    static {
        AE2TestBootstrap.boot();
    }

    @Test
    void smallerRecipeClearsEveryInputAndOutputPage() {
        FakeItemHandler inputs = new FakeItemHandler(81);
        FakeItemHandler outputs = new FakeItemHandler(27);
        Map<Integer, ItemStack> oldInputs = new LinkedHashMap<>();
        for (int slot = 0; slot < 40; slot++) {
            oldInputs.put(slot, new ItemStack(Items.IRON_INGOT));
        }
        ProcessingPatternTransfer.replace(inputs, outputs, oldInputs,
                Arrays.asList(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD),
                        new ItemStack(Items.REDSTONE), new ItemStack(Items.COAL)));

        ProcessingPatternTransfer.replace(inputs, outputs, Collections.singletonMap(2, new ItemStack(Items.IRON_INGOT)),
                Collections.singletonList(new ItemStack(Items.GOLD_INGOT)));

        for (int slot = 0; slot < inputs.getSlots(); slot++) {
            ItemStack expected = slot == 2 ? new ItemStack(Items.IRON_INGOT) : ItemStack.EMPTY;
            assertThat(ItemStack.areItemStacksEqual(inputs.getStackInSlot(slot), expected)).isTrue();
        }
        assertThat(ItemStack.areItemStacksEqual(outputs.getStackInSlot(0), new ItemStack(Items.GOLD_INGOT))).isTrue();
        for (int slot = 1; slot < outputs.getSlots(); slot++) {
            assertThat(outputs.getStackInSlot(slot).isEmpty()).isTrue();
        }
    }

    @Test
    void storageReplacementReturnsEveryOldStackAndInsertionRemainder() {
        FakeItemHandler storage = new FakeItemHandler(36);
        List<ItemStack> returned = new ArrayList<>();
        storage.setStack(0, new ItemStack(Items.IRON_INGOT, 12));
        storage.setStack(35, new ItemStack(Items.GOLD_NUGGET, 7));
        ProcessingStorageTransfer transfer = new ProcessingStorageTransfer(storage,
                stack -> stack, returned::add);

        transfer.replace(Collections.singletonList(new ItemStack(Items.DIAMOND, 3)));

        assertThat(returned).anySatisfy(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.IRON_INGOT);
            assertThat(stack.getCount()).isEqualTo(12);
        });
        assertThat(returned).anySatisfy(stack -> {
            assertThat(stack.getItem()).isEqualTo(Items.GOLD_NUGGET);
            assertThat(stack.getCount()).isEqualTo(7);
        });
        assertThat(ItemStack.areItemStacksEqual(storage.getStackInSlot(0), new ItemStack(Items.DIAMOND, 3))).isTrue();
        for (int slot = 1; slot < storage.getSlots(); slot++) {
            assertThat(storage.getStackInSlot(slot).isEmpty()).isTrue();
        }
    }
}
