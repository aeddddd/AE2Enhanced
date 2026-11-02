package com.github.aeddddd.ae2enhanced.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;

/** 无根版本的 Omni 六库存、设置与布局静态旧 NBT 验收。 */
class OmniLegacyCompatibilityTest {

    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String LAYOUT = "{\"version\":1,\"roots\":[{\"id\":\"legacy\"}]}";

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static void assertStack(ItemStack stack, net.minecraft.item.Item item, int count) {
        assertThat(stack.getItem()).isEqualTo(item);
        assertThat(stack.getCount()).isEqualTo(count);
    }

    @Test
    void unversionedWorldDataRestoresAllSixInventoriesAndLargeCounts() {
        NBTTagCompound legacy = CompatibilityFixtures.readNbt("omni/world-v0.nbt");
        assertThat(legacy.hasKey("version")).isFalse();
        OmniTerminalData data = new OmniTerminalData();
        data.readFromNBT(legacy);
        OmniTerminalStorage storage = data.getOrCreate(ID);

        assertStack(storage.getCraftingInventory().getStackInSlot(3), Items.APPLE, 5);
        assertThat(storage.getCraftingInventory().getStackInSlot(3).getTagCompound().getString("k")).isEqualTo("v");
        assertStack(storage.getPatternInputInventory().getStackInSlot(80), Items.DYE, 1_000_000);
        assertThat(storage.getPatternInputInventory().getStackInSlot(80).getMetadata()).isEqualTo(4);
        assertStack(storage.getPatternOutputInventory().getStackInSlot(10), Items.STICK, 16);
        assertStack(storage.getRightStorageInventory().getStackInSlot(35), Items.WHEAT, 64);
        assertStack(storage.getUpgradeInventory().getStackInSlot(0), Items.APPLE, 1);
        assertStack(storage.getPatternInventory().getStackInSlot(1), Items.DIAMOND, 1);
    }

    @Test
    void unversionedItemNbtRestoresSettingsAndLayout() {
        ItemStack stack = new ItemStack(CompatibilityFixtures.readNbt("omni/item-v0.nbt"));
        NBTTagCompound tag = stack.getTagCompound();

        assertThat(tag.getBoolean("omni_crafting_mode")).isTrue();
        assertThat(tag.getBoolean("omni_substitute")).isTrue();
        assertThat(tag.getInteger("omni_scroll_offset")).isEqualTo(3);
        assertThat(OmniTerminalLayoutNbt.read(stack)).isEqualTo(LAYOUT);
    }

    @Test
    void unknownEntriesAndUnregisteredCapabilitiesArePreservedOnWriteBack() {
        NBTTagCompound legacy = CompatibilityFixtures.readNbt("omni/world-v0.nbt");
        assertThat(legacy.hasKey("unknownRoot")).isTrue();
        OmniTerminalData data = new OmniTerminalData();
        data.readFromNBT(legacy);
        NBTTagCompound rewritten = data.writeToNBT(new NBTTagCompound());

        assertThat(rewritten.hasKey("unknownRoot")).isTrue();
        assertThat(rewritten.getTagList("storages", 10).getCompoundTagAt(0)
                .getCompoundTag("data").hasKey("legacyUnknown")).isTrue();
        assertThat(rewritten.toString()).contains("removed:cap");
    }
}
