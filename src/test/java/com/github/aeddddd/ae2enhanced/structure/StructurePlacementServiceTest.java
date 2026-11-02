package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StructurePlacementServiceTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static InventoryPlayer inventory(int count) {
        InventoryPlayer inventory = new InventoryPlayer(mock(EntityPlayer.class));
        inventory.setInventorySlotContents(0, new ItemStack(Blocks.STONE, count));
        return inventory;
    }

    @Test
    void unloadedOrOccupiedTargetDoesNotConsumeMaterials() {
        BlockPos pos = new BlockPos(1, 64, 1);
        FakeWorld fake = new FakeWorld();
        fake.loaded = false;
        InventoryPlayer inventory = inventory(2);
        Map<BlockPos, net.minecraft.block.Block> targets = new LinkedHashMap<>();
        targets.put(pos, Blocks.STONE);

        StructurePlacementService.Plan unloaded = StructurePlacementService.prepare(fake.world, targets);
        assertThat(unloaded).isNull();
        assertThat(inventory.getStackInSlot(0).getCount()).isEqualTo(2);

        fake.loaded = true;
        fake.states.put(pos, Blocks.DIRT.getDefaultState());
        StructurePlacementService.Plan occupied = StructurePlacementService.prepare(fake.world, targets);
        assertThat(occupied).isNull();
        assertThat(inventory.getStackInSlot(0).getCount()).isEqualTo(2);
        verify(fake.world, never()).setBlockState(any(), any());
    }

    @Test
    void failedPlacementRestoresBlocksAndInventory() {
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 1);
        FakeWorld fake = new FakeWorld();
        fake.failAt = second;
        InventoryPlayer inventory = inventory(2);
        Map<BlockPos, net.minecraft.block.Block> targets = new LinkedHashMap<>();
        targets.put(first, Blocks.STONE);
        targets.put(second, Blocks.STONE);
        StructurePlacementService.Plan plan = StructurePlacementService.prepare(fake.world, targets);

        assertThat(StructurePlacementService.consume(inventory, plan, () -> true)).isFalse();
        assertThat(fake.states.get(first).getBlock()).isEqualTo(Blocks.AIR);
        assertThat(fake.states.getOrDefault(second, Blocks.AIR.getDefaultState()).getBlock()).isEqualTo(Blocks.AIR);
        assertThat(inventory.getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void failedVerificationRestoresEverySnapshotAndRefund() {
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 1);
        FakeWorld fake = new FakeWorld();
        fake.states.put(second, Blocks.TALLGRASS.getDefaultState());
        InventoryPlayer inventory = inventory(2);
        Map<BlockPos, net.minecraft.block.Block> targets = new LinkedHashMap<>();
        targets.put(first, Blocks.STONE);
        targets.put(second, Blocks.STONE);
        StructurePlacementService.Plan plan = StructurePlacementService.prepare(fake.world, targets);

        assertThat(StructurePlacementService.consume(inventory, plan, () -> false)).isFalse();
        assertThat(fake.states.get(first).getBlock()).isEqualTo(Blocks.AIR);
        assertThat(fake.states.get(second).getBlock()).isEqualTo(Blocks.TALLGRASS);
        assertThat(inventory.getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void successfulTransactionConsumesOnceAndKeepsPlacedBlocks() {
        BlockPos pos = new BlockPos(1, 64, 1);
        FakeWorld fake = new FakeWorld();
        InventoryPlayer inventory = inventory(2);
        Map<BlockPos, net.minecraft.block.Block> targets = new LinkedHashMap<>();
        targets.put(pos, Blocks.STONE);
        StructurePlacementService.Plan plan = StructurePlacementService.prepare(fake.world, targets);

        assertThat(StructurePlacementService.consume(inventory, plan, () -> true)).isTrue();
        assertThat(fake.states.get(pos).getBlock()).isEqualTo(Blocks.STONE);
        assertThat(inventory.getStackInSlot(0).getCount()).isEqualTo(1);
    }

    private static final class FakeWorld {
        final World world = mock(World.class);
        final Map<BlockPos, IBlockState> states = new HashMap<>();
        boolean loaded = true;
        BlockPos failAt;

        FakeWorld() {
            when(world.isBlockLoaded(any())).thenAnswer(invocation -> loaded);
            when(world.getBlockState(any())).thenAnswer(invocation ->
                    states.getOrDefault(invocation.getArgument(0), Blocks.AIR.getDefaultState()));
            when(world.isAirBlock(any())).thenAnswer(invocation ->
                    states.getOrDefault(invocation.getArgument(0), Blocks.AIR.getDefaultState()).getBlock() == Blocks.AIR);
            when(world.setBlockState(any(), any())).thenAnswer(invocation -> {
                BlockPos pos = invocation.getArgument(0);
                if (pos.equals(failAt)) return false;
                states.put(pos, invocation.getArgument(1));
                return true;
            });
        }
    }
}
