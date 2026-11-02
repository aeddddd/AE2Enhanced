package com.github.aeddddd.ae2enhanced.centralinterface;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CentralOutboxDirtyTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void pendingProductsMarkDirtyOnEveryAdditionAndDrain() {
        AtomicInteger dirty = new AtomicInteger();
        PendingProductQueue queue = new PendingProductQueue(dirty::incrementAndGet);

        queue.addAll(Collections.singletonList(new ItemStack(Items.APPLE, 2)));
        assertThat(dirty).hasValue(1);
        queue.addAll(Collections.emptyList());
        assertThat(dirty).hasValue(1);
        queue.drainAll();
        assertThat(dirty).hasValue(2);
        queue.drainAll();
        assertThat(dirty).hasValue(2);
    }

    @Test
    void fluidBufferMarksDirtyForMergePartialAcceptRetryAndRemoval() {
        AtomicInteger dirty = new AtomicInteger();
        CentralFluidBuffer buffer = new CentralFluidBuffer(dirty::incrementAndGet);
        buffer.add(new FluidStack(FluidRegistry.WATER, 1000));
        buffer.add(new FluidStack(FluidRegistry.WATER, 250));
        assertThat(dirty).hasValue(2);

        CentralFluidBuffer.Entry entry = buffer.entries().get(0);
        entry.countAttempt();
        assertThat(dirty).hasValue(3);
        entry.setFluid(new FluidStack(FluidRegistry.WATER, 400));
        assertThat(dirty).hasValue(4);
        assertThat(buffer.remove(entry)).isTrue();
        assertThat(dirty).hasValue(5);
        assertThat(buffer.remove(entry)).isFalse();
        assertThat(dirty).hasValue(5);

        buffer.add(new FluidStack(FluidRegistry.LAVA, 500));
        buffer.clear();
        assertThat(dirty).hasValue(7);
        buffer.clear();
        assertThat(dirty).hasValue(7);
    }
}
