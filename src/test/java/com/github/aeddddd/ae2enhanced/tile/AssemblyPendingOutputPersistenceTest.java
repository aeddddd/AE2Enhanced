package com.github.aeddddd.ae2enhanced.tile;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyPendingOutputPersistenceTest {

    private static Item capabilityItem;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        ResourceLocation id = new ResourceLocation("ae2enhanced:test_assembly_capability");
        capabilityItem = ForgeRegistries.ITEMS.getValue(id);
        if (capabilityItem == null) {
            capabilityItem = new CapabilityItem().setRegistryName(id);
            ForgeRegistries.ITEMS.register(capabilityItem);
        }
    }

    @Test
    void registeredCapabilityAndLargeCountSurvivePendingOutputRoundTrip() {
        TileAssemblyController tile = new TileAssemblyController();
        assertThat(tile.addPendingOutput(new ItemStack(capabilityItem, 500))).isTrue();
        NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
        NBTTagCompound entry = saved.getTagList("pendingOutputs", 10).getCompoundTagAt(0);
        assertThat(entry.getInteger("Count")).isEqualTo(500);
        assertThat(entry.getCompoundTag("ForgeCaps").getCompoundTag("Parent")
                .getCompoundTag("ae2enhanced:test").getInteger("value")).isEqualTo(37);

        TileAssemblyController restored = new TileAssemblyController();
        restored.readFromNBT(saved);
        NBTTagCompound roundTrip = restored.writeToNBT(new NBTTagCompound())
                .getTagList("pendingOutputs", 10).getCompoundTagAt(0);
        assertThat(roundTrip.getInteger("Count")).isEqualTo(500);
        assertThat(roundTrip.getCompoundTag("ForgeCaps")).isEqualTo(entry.getCompoundTag("ForgeCaps"));
    }

    private static final class CapabilityItem extends Item {
        @Override
        public ICapabilitySerializable<NBTTagCompound> initCapabilities(ItemStack stack, NBTTagCompound nbt) {
            return new ICapabilitySerializable<NBTTagCompound>() {
                @Override
                public NBTTagCompound serializeNBT() {
                    NBTTagCompound root = new NBTTagCompound();
                    NBTTagCompound value = new NBTTagCompound();
                    value.setInteger("value", 37);
                    root.setTag("ae2enhanced:test", value);
                    return root;
                }

                @Override
                public void deserializeNBT(NBTTagCompound nbt) {
                }

                @Override
                public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
                    return false;
                }

                @Nullable
                @Override
                public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
                    return null;
                }
            };
        }
    }
}
