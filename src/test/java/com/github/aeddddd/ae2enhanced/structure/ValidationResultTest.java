package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ValidationResult} 单元测试。
 */
class ValidationResultTest {

    @Test
    void testOk() {
        ValidationResult result = ValidationResult.ok();
        assertTrue(result.passed);
        assertTrue(result.allChunksLoaded);
        assertTrue(result.missing.isEmpty());
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
    void testUnloadedChunks() {
        ValidationResult result = new ValidationResult(false, Collections.emptyMap(), false);
        assertFalse(result.passed);
        assertFalse(result.allChunksLoaded);
    }
}
