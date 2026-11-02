package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CentralFluidBufferTest {
    @org.junit.jupiter.api.BeforeAll
    static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
    }

    @Test
    void intOverflowIsSplitIntoPersistentEntriesWithoutLoss() {
        CentralFluidBuffer buffer = new CentralFluidBuffer();
        buffer.add(new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE));
        buffer.add(new FluidStack(FluidRegistry.WATER, 1));

        assertThat(buffer.totalAmount()).isEqualTo((long) Integer.MAX_VALUE + 1L);
        assertThat(buffer.entries()).hasSize(2);
        assertThat(buffer.entries()).allMatch(entry -> entry.fluid().amount > 0);

        NBTTagCompound nbt = new NBTTagCompound();
        buffer.writeToNBT(nbt);
        CentralFluidBuffer restored = new CentralFluidBuffer();
        restored.readFromNBT(nbt);
        assertThat(restored.totalAmount()).isEqualTo((long) Integer.MAX_VALUE + 1L);
        assertThat(restored.entries()).hasSize(2);
    }

    @Test
    void unknownFluidEntryIsPreservedWithoutEnteringTheLiveBuffer() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("FluidName", "missing_mod:fluid");
        entry.setInteger("Amount", 1000);
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        list.appendTag(entry);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("centralFluidBuffer", list);

        CentralFluidBuffer buffer = new CentralFluidBuffer();
        buffer.readFromNBT(nbt);
        NBTTagCompound rewritten = new NBTTagCompound();
        buffer.writeToNBT(rewritten);

        assertThat(buffer.isEmpty()).isTrue();
        assertThat(rewritten.getTagList("centralFluidBuffer", 10).getCompoundTagAt(0))
                .isEqualTo(entry);
    }
}
