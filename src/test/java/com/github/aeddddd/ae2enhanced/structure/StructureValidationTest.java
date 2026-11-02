package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.block.*;
import com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructureValidationTest {
    private static final BlockPos CONTROLLER = new BlockPos(32, 64, -16);

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        BlockRegistry.HYPERDIMENSIONAL_CONTROLLER = new BlockHyperdimensionalController();
        BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE = new BlockHyperdimensionalMeInterface();
        BlockRegistry.HYPERDIMENSIONAL_CASING = new BlockHyperdimensionalCasing();
        BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE = new BlockHyperdimensionalSingularityCore();
    }

    private static World hyperWorld(Set<BlockPos> loaded) {
        return hyperWorld(loaded, null);
    }

    private static World hyperWorld(Set<BlockPos> loaded, BlockPos broken) {
        World world = mock(World.class);
        try {
            setProvider(world);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        when(world.isBlockLoaded(any())).thenAnswer(invocation -> loaded.contains(invocation.getArgument(0)));
        when(world.getBlockState(any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            if (pos.equals(CONTROLLER)) return BlockRegistry.HYPERDIMENSIONAL_CONTROLLER.getDefaultState()
                    .withProperty(BlockHyperdimensionalController.FACING, EnumFacing.NORTH);
            BlockPos rel = pos.subtract(CONTROLLER);
            Block expected = Blocks.AIR;
            if (HyperdimensionalStructure.ME_INTERFACE_SET.contains(rel)) expected = BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE;
            else if (HyperdimensionalStructure.CORE_SET.contains(rel)) expected = BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE;
            else if (HyperdimensionalStructure.CASING_SET.contains(rel)) expected = BlockRegistry.HYPERDIMENSIONAL_CASING;
            return (pos.equals(broken) ? Blocks.STONE : expected).getDefaultState();
        });
        return world;
    }

    private static void setProvider(World world) throws Exception {
        Field field = World.class.getDeclaredField("provider");
        field.setAccessible(true);
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        WorldProvider provider = mock(WorldProvider.class);
        when(provider.getDimension()).thenReturn(0);
        field.set(world, provider);
    }

    @SuppressWarnings("unchecked")
    private static <T> T staticMap(String name) throws Exception {
        Class<?> owner = name.equals("deferredChecks")
                ? com.github.aeddddd.ae2enhanced.structureapi.StructureDeferral.class : StructureEventHandler.class;
        String fieldName = name.equals("deferredChecks") ? "DEFERRED" : name;
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(null);
    }

    @Test
    void unloadedPartDefersAndNeverCountsAsValid() {
        World world = hyperWorld(new HashSet<>());
        assertThat(HyperdimensionalStructure.validate(world, CONTROLLER))
                .isEqualTo(StructureValidationResult.DEFER_UNLOADED);

        Set<BlockPos> loaded = new HashSet<>();
        loaded.add(CONTROLLER);
        world = hyperWorld(loaded);
        assertThat(HyperdimensionalStructure.validate(world, CONTROLLER))
                .isEqualTo(StructureValidationResult.DEFER_UNLOADED);
    }

    @Test
    void fullyLoadedStructureDistinguishesValidAndInvalid() {
        Set<BlockPos> loaded = new HashSet<>();
        loaded.add(CONTROLLER);
        for (BlockPos rel : HyperdimensionalStructure.ALL_SET) loaded.add(CONTROLLER.add(rel));
        World world = hyperWorld(loaded);
        assertThat(HyperdimensionalStructure.validate(world, CONTROLLER))
                .isEqualTo(StructureValidationResult.VALID);

        BlockPos broken = CONTROLLER.add(HyperdimensionalStructure.CASING_SET.iterator().next());
        World invalid = hyperWorld(loaded, broken);
        assertThat(HyperdimensionalStructure.validate(invalid, CONTROLLER))
                .isEqualTo(StructureValidationResult.INVALID);
    }

    @Test
    void deferredValidationDoesNotCreateAnotherTimedCheck() throws Exception {
        World world = mock(World.class);
        setProvider(world);
        when(world.isBlockLoaded(CONTROLLER)).thenReturn(false);
        StructureEventHandler.validateAndUpdate(world, CONTROLLER);
        StructureEventHandler.validateAndUpdate(world, CONTROLLER);

        Map<Integer, Map<BlockPos, Integer>> pending = staticMap("pendingChecks");
        Map<Integer, Set<BlockPos>> deferred = staticMap("deferredChecks");
        assertThat(pending).doesNotContainKey(0);
        assertThat(deferred.get(0)).containsOnly(CONTROLLER);
        deferred.clear();
        pending.clear();
    }

    @Test
    void automaticAssemblyAlsoDefersInsteadOfFormingPartialStructure() throws Exception {
        World world = hyperWorld(new HashSet<>());
        HyperdimensionalStructure.assemble(world, CONTROLLER);

        Map<Integer, Set<BlockPos>> deferred = staticMap("deferredChecks");
        assertThat(deferred.get(0)).containsOnly(CONTROLLER);
        deferred.clear();
    }
}
