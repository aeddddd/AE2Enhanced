package com.github.aeddddd.ae2enhanced.structure;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StructureDefinition} 单元测试.
 */
class StructureDefinitionTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private static final BlockPos P1 = new BlockPos(0, 0, 0);
    private static final BlockPos P2 = new BlockPos(1, 0, 0);
    private static final BlockPos P3 = new BlockPos(0, 1, 0);

    @Test
    void testBuilderAdd() {
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, P1)
                .add(Blocks.STONE, P2)
                .add(Blocks.DIRT, P3)
                .build();

        assertEquals(2, def.getBlockSets().size());
        assertEquals(Set.of(P1, P2), def.getBlockSets().get(Blocks.STONE));
        assertEquals(Set.of(P3), def.getBlockSets().get(Blocks.DIRT));
        assertEquals(3, def.getAllPositions().size());
    }

    @Test
    void testBuilderAddAll() {
        StructureDefinition def = StructureDefinition.builder()
                .addAll(Blocks.STONE, Set.of(P1, P2, P3))
                .build();

        assertEquals(1, def.getBlockSets().size());
        assertEquals(3, def.getBlockSets().get(Blocks.STONE).size());
        assertEquals(3, def.getAllPositions().size());
    }

    @Test
    void testDuplicatePositionForSameBlock() {
        // 同一方块同一位置重复添加只计一次
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, P1)
                .add(Blocks.STONE, P1)
                .build();
        assertEquals(1, def.getBlockSets().get(Blocks.STONE).size());
        assertEquals(1, def.getAllPositions().size());
    }

    @Test
    void testSamePositionDifferentBlocks() {
        // 不同方块可占用同一相对坐标（验证时各自统计）
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, P1)
                .add(Blocks.DIRT, P1)
                .build();
        assertEquals(1, def.getAllPositions().size());
        assertEquals(2, def.getExpectedBlocks().size());
    }

    @Test
    void testGetExpectedBlocks() {
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, P1)
                .add(Blocks.DIRT, P2)
                .build();

        Set<Map.Entry<BlockPos, Block>> expected = def.getExpectedBlocks();
        assertEquals(2, expected.size());
        assertTrue(expected.stream().anyMatch(e -> e.getKey().equals(P1) && e.getValue() == Blocks.STONE));
        assertTrue(expected.stream().anyMatch(e -> e.getKey().equals(P2) && e.getValue() == Blocks.DIRT));
    }

    @Test
    void testOfDefensiveCopy() {
        Set<BlockPos> positions = new HashSet<>();
        positions.add(P1);
        StructureDefinition def = StructureDefinition.of(Map.of(Blocks.STONE, positions));
        // 修改源集合不应影响定义
        positions.add(P2);
        assertEquals(1, def.getAllPositions().size());
        assertEquals(1, def.getBlockSets().get(Blocks.STONE).size());
    }

    @Test
    void testReturnedCollectionsAreUnmodifiable() {
        StructureDefinition def = StructureDefinition.builder()
                .add(Blocks.STONE, P1)
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> def.getBlockSets().put(Blocks.DIRT, Set.of(P2)));
        assertThrows(UnsupportedOperationException.class,
                () -> def.getAllPositions().add(P2));
        assertThrows(UnsupportedOperationException.class,
                () -> def.getBlockSets().get(Blocks.STONE).add(P2));
    }

    @Test
    void testBuilderChainingReturnsBuilder() {
        StructureDefinition.Builder builder = StructureDefinition.builder();
        assertNotNull(builder.add(Blocks.STONE, P1));
        assertNotNull(builder.addAll(Blocks.DIRT, Set.of(P2)));
        assertNotNull(builder.build());
    }

    @Test
    void testEmptyDefinition() {
        StructureDefinition def = StructureDefinition.builder().build();
        assertTrue(def.getBlockSets().isEmpty());
        assertTrue(def.getAllPositions().isEmpty());
        assertTrue(def.getExpectedBlocks().isEmpty());
        assertFalse(def.getAllPositions().contains(P1));
    }
}
