package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ValidationResult} 单元测试.
 */
class ValidationResultTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testOk() {
        ValidationResult result = ValidationResult.ok();
        assertTrue(result.passed);
        assertTrue(result.allChunksLoaded);
        assertTrue(result.missing.isEmpty());
    }

    @Test
    void testOkDefaults() {
        ValidationResult result = ValidationResult.ok();
        // 成功结果默认没有锚点统计与并行上限
        assertEquals(0, result.causalAnchorCount);
        assertEquals(0, result.parallelLimit);
        assertEquals(0, result.causalAnchorCount());
        assertEquals(0, result.parallelLimit());
    }

    @Test
    void testIncomplete() {
        Map<Block, Integer> missing = new LinkedHashMap<>();
        missing.put(Blocks.STONE, 3);
        ValidationResult result = ValidationResult.incomplete(missing, true);
        assertFalse(result.passed);
        assertTrue(result.allChunksLoaded);
        assertEquals(1, result.missing.size());
        assertEquals(3, result.missing.get(Blocks.STONE));
    }

    @Test
    void testIncompleteDefaults() {
        ValidationResult result = ValidationResult.incomplete(Collections.emptyMap(), true);
        // incomplete 工厂方法不携带锚点信息
        assertEquals(0, result.causalAnchorCount());
        assertEquals(0, result.parallelLimit());
    }

    @Test
    void testUnloadedChunks() {
        ValidationResult result = new ValidationResult(false, Collections.emptyMap(), false);
        assertFalse(result.passed);
        assertFalse(result.allChunksLoaded);
    }

    @Test
    void testFullConstructor() {
        Map<Block, Integer> missing = new LinkedHashMap<>();
        missing.put(Blocks.DIRT, 2);
        ValidationResult result = new ValidationResult(true, missing, true, 4, 16);
        assertTrue(result.passed());
        assertTrue(result.allChunksLoaded());
        assertEquals(2, result.missing.get(Blocks.DIRT));
        assertEquals(4, result.causalAnchorCount());
        assertEquals(16, result.parallelLimit());
    }

    @Test
    void testAccessorMethods() {
        ValidationResult result = new ValidationResult(false, Collections.emptyMap(), false, 1, 8);
        // 字段与访问器方法应保持一致
        assertEquals(result.passed, result.passed());
        assertEquals(result.allChunksLoaded, result.allChunksLoaded());
        assertEquals(result.causalAnchorCount, result.causalAnchorCount());
        assertEquals(result.parallelLimit, result.parallelLimit());
    }

    @Test
    void testMissingMapIsDefensiveCopy() {
        Map<Block, Integer> missing = new LinkedHashMap<>();
        missing.put(Blocks.STONE, 1);
        ValidationResult result = new ValidationResult(false, missing, true);
        // 修改源映射不应影响结果对象
        missing.put(Blocks.DIRT, 5);
        assertEquals(1, result.missing.size());
        assertFalse(result.missing.containsKey(Blocks.DIRT));
    }

    @Test
    void testMissingMapIsUnmodifiable() {
        Map<Block, Integer> missing = new LinkedHashMap<>();
        missing.put(Blocks.STONE, 1);
        ValidationResult result = new ValidationResult(false, missing, true);
        assertThrows(UnsupportedOperationException.class, () -> result.missing.put(Blocks.DIRT, 1));
        assertThrows(UnsupportedOperationException.class, result.missing::clear);
    }
}
