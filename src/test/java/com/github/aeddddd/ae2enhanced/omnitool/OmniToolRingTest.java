package com.github.aeddddd.ae2enhanced.omnitool;

import com.github.aeddddd.ae2enhanced.ring.crafting.RecipeRingModuleInstall;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.util.ResourceLocation;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import static org.assertj.core.api.Assertions.assertThat;

class OmniToolRingTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void installPreservesToolAndCompleteLegacyRingState() {
        ItemStack tool = new ItemStack(new ItemAdvancedMEOmniTool());
        tool.setTagCompound(new NBTTagCompound());
        tool.getTagCompound().setString("owner", "tool-state");
        ItemStack legacy = new ItemStack(Items.DIAMOND);
        RingNBT.setTier(legacy, 2);
        RingNBT.setAscended(legacy, true);
        RingNBT.setEnergy(legacy, 123456);
        RingNBT.setNetworkEncryptionKey(legacy, "network-key");
        RingNBT.setBool(legacy, RingNBT.FORCE_FLIGHT, true);

        ItemStack installed = OmniToolRing.install(tool, legacy);

        assertThat(installed.getTagCompound().getString("owner")).isEqualTo("tool-state");
        assertThat(OmniToolRing.hasModule(installed)).isTrue();
        assertThat(RingNBT.getTier(installed)).isEqualTo(2);
        assertThat(RingNBT.isAscended(installed)).isTrue();
        assertThat(RingNBT.getEnergy(installed)).isEqualTo(123456);
        assertThat(RingNBT.getNetworkEncryptionKey(installed)).isEqualTo("network-key");
        assertThat(RingNBT.getBool(installed, RingNBT.FORCE_FLIGHT, false)).isTrue();
        assertThat(RingNBT.hasRingModule(legacy)).isFalse();
    }

    @Test
    void recipeMatchesExactlyOneToolAndOneLegacyCredential() {
        ItemAdvancedMEOmniTool toolItem = new ItemAdvancedMEOmniTool();
        ItemStack tool = new ItemStack(toolItem);
        ItemStack legacy = new ItemStack(Items.DIAMOND);
        RingNBT.setTier(legacy, 1);
        RecipeRingModuleInstall recipe = new RecipeRingModuleInstall(
                new ResourceLocation("ae2enhanced", "test_ring_module"),
                toolItem, Items.DIAMOND);
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        }, 2, 1);
        inventory.setInventorySlotContents(0, tool);
        inventory.setInventorySlotContents(1, legacy);

        assertThat(recipe.matches(inventory, null)).isTrue();
        ItemStack result = recipe.getCraftingResult(inventory);
        assertThat(result.getItem()).isInstanceOf(ItemAdvancedMEOmniTool.class);
        assertThat(RingNBT.getTier(result)).isEqualTo(1);
    }
}
