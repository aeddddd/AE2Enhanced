package com.github.aeddddd.ae2enhanced.tile;

import appeng.items.misc.ItemEncodedPattern;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.block.BlockAssemblyController;
import com.github.aeddddd.ae2enhanced.block.BlockAssemblyMeInterface;
import com.github.aeddddd.ae2enhanced.block.BlockRegistry;
import com.github.aeddddd.ae2enhanced.block.ControllerIndex;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TileAssemblyControllerRecoveryTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        if (BlockRegistry.ASSEMBLY_ME_INTERFACE == null) {
            BlockRegistry.ASSEMBLY_ME_INTERFACE = new BlockAssemblyMeInterface();
            net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(BlockRegistry.ASSEMBLY_ME_INTERFACE);
        }
        if (net.minecraft.tileentity.TileEntity.getKey(TileAssemblyController.class) == null) {
            net.minecraft.tileentity.TileEntity.register("ae2enhanced:assembly_controller", TileAssemblyController.class);
        }
    }

    private static void setProvider(World world) {
        try {
            Field field = World.class.getDeclaredField("provider");
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            field.set(world, mock(net.minecraft.world.WorldProvider.class));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
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
    void disassembleDropsEveryStoredKindBeforeClearingIt() {
        Fixture fixture = new Fixture();
        fixture.tile.disassemble();
        fixture.assertCompleteManifest();
    }

    @Test
    void blockBreakUsesTheSameRecoveryManifest() {
        Fixture fixture = new Fixture();
        fixture.block.breakBlock(fixture.world, fixture.tile.getPos(), fixture.block.getDefaultState());
        fixture.assertCompleteManifest();
    }

    @Test
    void longPendingOutputIsSplitWithoutTruncation() {
        TileAssemblyController tile = new TileAssemblyController();
        appeng.api.storage.data.IAEItemStack output = appeng.util.item.AEItemStack.fromItemStack(
                new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE)));
        output.setStackSize((long) Integer.MAX_VALUE * 2L + 17L);

        assertThat(tile.addPendingOutput(output)).isTrue();
        List<ItemStack> pending = list(tile, "pendingOutputs");
        assertThat(pending).hasSize(3);
        assertThat(pending.get(0).getCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(pending.get(1).getCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(pending.get(2).getCount()).isEqualTo(17);

        TileAssemblyController restored = new TileAssemblyController();
        restored.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
        assertThat(list(restored, "pendingOutputs")).hasSize(3);
        assertThat(list(restored, "pendingOutputs").stream().mapToLong(ItemStack::getCount).sum())
                .isEqualTo((long) Integer.MAX_VALUE * 2L + 17L);
    }

    private static final class Fixture {
        final World world = mock(World.class);
        final BlockAssemblyController block = new BlockAssemblyController();
        final TileAssemblyController tile = new TileAssemblyController();
        final List<ItemStack> drops = new ArrayList<>();

        Fixture() {
            setProvider(world);
            when(world.provider.getDimension()).thenReturn(0);
            tile.setWorld(world);
            tile.setPos(new BlockPos(1, 2, 3));
            tile.proxy = mock(AENetworkProxy.class);
            when(world.spawnEntity(any())).thenAnswer(invocation -> {
                EntityItem entity = invocation.getArgument(0);
                drops.add(entity.getItem().copy());
                return true;
            });
            MapStorage storage = mock(MapStorage.class);
            when(world.getPerWorldStorage()).thenReturn(storage);
            when(storage.getOrLoadData(ControllerIndex.class, "ae2enhanced_controller_index"))
                    .thenReturn(new ControllerIndex());
            when(world.getTileEntity(tile.getPos())).thenReturn(tile);
            when(world.getBlockState(any())).thenReturn(block.getDefaultState());

            tile.getItemHandler().setStackInSlot(0, new ItemStack(new ItemUpgradeCard(), 2,
                    ItemUpgradeCard.META_PARALLEL));
            ItemStack pattern = new ItemStack(new ItemEncodedPattern());
            pattern.setTagInfo("crafting", new net.minecraft.nbt.NBTTagByte((byte) 1));
            tile.getItemHandler().setStackInSlot(TileAssemblyController.UPGRADE_SLOTS, pattern);
            assertThat(tile.addPendingOutput(new ItemStack(Items.COAL, 3))).isTrue();
            list(tile, "blackHoleBuffer").add(new ItemStack(Items.DIAMOND, 4));
        }

        void assertCompleteManifest() {
            assertThat(drops).anySatisfy(stack -> {
                assertThat(stack.getItem()).isInstanceOf(ItemUpgradeCard.class);
                assertThat(stack.getCount()).isEqualTo(2);
            });
            assertThat(drops).anySatisfy(stack -> {
                assertThat(stack.getItem()).isInstanceOf(ItemEncodedPattern.class);
                assertThat(stack.getTagCompound().getBoolean("crafting")).isTrue();
            });
            assertThat(countOf(Items.COAL)).isEqualTo(3);
            assertThat(countOf(Items.DIAMOND)).isEqualTo(4);
            assertThat(list(tile, "pendingOutputs")).isEmpty();
            assertThat(list(tile, "blackHoleBuffer")).isEmpty();
            assertThat(tile.getItemHandler().getStackInSlot(0).isEmpty()).isTrue();
            assertThat(tile.getItemHandler().getStackInSlot(TileAssemblyController.UPGRADE_SLOTS).isEmpty()).isTrue();
        }

        private int countOf(Item item) {
            return drops.stream().filter(stack -> stack.getItem() == item).mapToInt(ItemStack::getCount).sum();
        }
    }
}
