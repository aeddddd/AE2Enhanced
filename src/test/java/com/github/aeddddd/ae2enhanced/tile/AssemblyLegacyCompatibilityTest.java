package com.github.aeddddd.ae2enhanced.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;

/** Assembly 静态旧 NBT 兼容与在途条目保留。 */
class AssemblyLegacyCompatibilityTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        ResourceLocation id = new ResourceLocation("ae2enhanced:upgrade_card");
        Item registered = ForgeRegistries.ITEMS.getValue(id);
        if (registered == null) {
            registered = new ItemUpgradeCard();
            ForgeRegistries.ITEMS.register(registered);
        }
        ItemRegistry.UPGRADE_CARD = (ItemUpgradeCard) registered;
        if (net.minecraft.tileentity.TileEntity.getKey(TileAssemblyController.class) == null) {
            net.minecraft.tileentity.TileEntity.register("ae2enhanced:assembly_controller", TileAssemblyController.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> list(TileAssemblyController tile, String name) {
        try {
            Field field = TileAssemblyController.class.getDeclaredField(name);
            field.setAccessible(true);
            return (List<ItemStack>) field.get(tile);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void staticLegacyNbtRestoresSmallSizeExpansionQueuesAndOrdinaryTags() {
        TileAssemblyController tile = new TileAssemblyController();
        tile.readFromNBT(CompatibilityFixtures.readNbt("assembly/legacy-v0.nbt"));

        assertThat(tile.isFormed()).isTrue();
        assertThat(tile.getPatternPages()).isEqualTo(15);
        assertThat(tile.getItemHandler().getSlots()).isEqualTo(1536);
        assertThat(tile.getItemHandler().getStackInSlot(3).getCount()).isEqualTo(2);
        assertThat(tile.getItemHandler().getStackInSlot(6).getItem()).isEqualTo(Items.PAPER);

        List<ItemStack> pending = list(tile, "pendingOutputs");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(pending.get(0).getCount()).isEqualTo(200);
        assertThat(pending.get(0).getTagCompound().getString("quality")).isEqualTo("legacy");
        List<ItemStack> blackHole = list(tile, "blackHoleBuffer");
        assertThat(blackHole).hasSize(1);
        assertThat(blackHole.get(0).getItem()).isEqualTo(Items.COAL);
        assertThat(blackHole.get(0).getMetadata()).isEqualTo(1);
        assertThat(blackHole.get(0).getTagCompound().getString("source")).isEqualTo("legacy");
    }

    @Test
    void orphanedItemEntrySurvivesReadWriteRecovery() {
        NBTTagCompound fixture = CompatibilityFixtures.readNbt("assembly/legacy-v0.nbt");
        assertThat(fixture.getTagList("pendingOutputs", 10).tagCount()).isEqualTo(2);
        assertThat(fixture.getTagList("pendingOutputs", 10).getCompoundTagAt(0).hasKey("ForgeCaps")).isTrue();

        TileAssemblyController tile = new TileAssemblyController();
        tile.readFromNBT(fixture);
        List<ItemStack> pending = list(tile, "pendingOutputs");
        assertThat(pending).hasSize(1);

        net.minecraft.nbt.NBTTagList restored = tile.writeToNBT(new NBTTagCompound())
                .getTagList("pendingOutputs", 10);
        assertThat(restored.tagCount()).isEqualTo(2);
        assertThat(restored.getCompoundTagAt(0)).isEqualTo(
                fixture.getTagList("pendingOutputs", 10).getCompoundTagAt(1));
    }
}
