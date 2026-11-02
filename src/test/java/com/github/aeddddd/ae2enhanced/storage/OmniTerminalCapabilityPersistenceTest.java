package com.github.aeddddd.ae2enhanced.storage;

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

class OmniTerminalCapabilityPersistenceTest {

    private static Item capabilityItem;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        ResourceLocation id = new ResourceLocation("ae2enhanced:test_omni_capability");
        capabilityItem = ForgeRegistries.ITEMS.getValue(id);
        if (capabilityItem == null) {
            capabilityItem = new CapabilityItem().setRegistryName(id);
            ForgeRegistries.ITEMS.register(capabilityItem);
        }
    }

    @Test
    void fullItemStackNbtRoundTripPreservesCapabilitiesAndLargeCount() {
        OmniTerminalInventory inventory = new OmniTerminalInventory(1);
        inventory.setStackInSlot(0, new ItemStack(capabilityItem, 500));

        NBTTagCompound serialized = inventory.serializeNBT();
        NBTTagCompound entry = serialized.getTagList("Items", 10).getCompoundTagAt(0);
        assertThat(entry.getCompoundTag("ForgeCaps").getCompoundTag("Parent")
                .getCompoundTag("ae2enhanced:test").getInteger("value")).isEqualTo(37);

        OmniTerminalInventory restored = new OmniTerminalInventory(1);
        restored.deserializeNBT(serialized);
        ItemStack stack = restored.getStackInSlot(0);
        assertThat(stack.getCount()).isEqualTo(500);
        assertThat(stack.serializeNBT().getCompoundTag("ForgeCaps"))
                .isEqualTo(entry.getCompoundTag("ForgeCaps"));
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
