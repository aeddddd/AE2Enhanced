package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.storage.OmniTerminalInventory;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class OmniMultiplyOverflowTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static void set(ContainerOmniTerm container, String name, Object value) throws Exception {
        Field field = ContainerOmniTerm.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(container, value);
    }

    @Test
    void overflowLeavesEverySlotUnchanged() throws Exception {
        ContainerOmniTerm container = mock(ContainerOmniTerm.class, CALLS_REAL_METHODS);
        OmniTerminalInventory inputs = new OmniTerminalInventory(81);
        ItemStack original = new ItemStack(Items.DIAMOND, Integer.MAX_VALUE - 1);
        inputs.setStackInSlot(0, original);
        set(container, "patternCraftingInv", inputs);
        set(container, "patternCraftMode", true);

        container.multiply(3);
        container.increase(3);

        assertThat(inputs.getStackInSlot(0).getCount()).isEqualTo(Integer.MAX_VALUE - 1);
    }
}
