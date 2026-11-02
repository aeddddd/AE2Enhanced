package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockHyperdimensionalControllerTest {

    private static final UUID NEXUS_ID = UUID.fromString("12345678-1234-5678-90ab-1234567890ab");

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static TileHyperdimensionalController place(BlockHyperdimensionalController block, ItemStack stack,
                                                         BlockPos pos) {
        World world = mock(World.class);
        TileHyperdimensionalController placed = new TileHyperdimensionalController();
        when(world.getTileEntity(pos)).thenReturn(placed);
        block.onBlockPlacedBy(world, pos, block.getDefaultState(), null, stack);
        return placed;
    }

    @Test
    void ordinaryDropRestoresRootNexusId() {
        BlockHyperdimensionalController block = new BlockHyperdimensionalController();
        BlockPos pos = new BlockPos(4, 5, 6);
        TileHyperdimensionalController tile = new TileHyperdimensionalController();
        tile.setNexusId(NEXUS_ID);
        IBlockAccess world = mock(IBlockAccess.class);
        when(world.getTileEntity(pos)).thenReturn(tile);
        NonNullList<ItemStack> drops = NonNullList.create();

        block.getDrops(drops, world, pos, block.getDefaultState(), 0);

        assertThat(drops).hasSize(1);
        ItemStack drop = drops.get(0);
        assertThat(drop.getTagCompound().getUniqueId("nexusId")).isEqualTo(NEXUS_ID);
        TileHyperdimensionalController placed = place(block, drop, pos);
        assertThat(placed.getNexusId()).isEqualTo(NEXUS_ID);
    }

    @Test
    void placementAlsoAcceptsBlockEntityTagCompatibilityPath() {
        BlockHyperdimensionalController block = new BlockHyperdimensionalController();
        ItemStack stack = new ItemStack(block);
        NBTTagCompound tileTag = new NBTTagCompound();
        tileTag.setUniqueId("nexusId", NEXUS_ID);
        stack.setTagInfo("BlockEntityTag", tileTag);

        TileHyperdimensionalController placed = place(block, stack, BlockPos.ORIGIN);
        assertThat(placed.getNexusId()).isEqualTo(NEXUS_ID);
    }
}
