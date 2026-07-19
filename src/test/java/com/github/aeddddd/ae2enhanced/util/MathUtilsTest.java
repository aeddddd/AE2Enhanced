package com.github.aeddddd.ae2enhanced.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MathUtils} 单元测试。
 */
class MathUtilsTest {

    @Test
    void testSafeMultiplyNormal() {
        assertEquals(12L, MathUtils.safeMultiply(3L, 4L));
        assertEquals(0L, MathUtils.safeMultiply(0L, 12345L));
        assertEquals(0L, MathUtils.safeMultiply(12345L, 0L));
        assertEquals(1L, MathUtils.safeMultiply(1L, 1L));
    }

    @Test
    void testSafeMultiplyNegative() {
        assertEquals(-30L, MathUtils.safeMultiply(-5L, 6L));
        assertEquals(-30L, MathUtils.safeMultiply(5L, -6L));
        assertEquals(30L, MathUtils.safeMultiply(-5L, -6L));
    }

    @Test
    void testSafeMultiplyBoundaryValues() {
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MAX_VALUE, 1L));
        assertEquals(Long.MIN_VALUE, MathUtils.safeMultiply(Long.MIN_VALUE, 1L));
        assertEquals(Long.MAX_VALUE - 1L, MathUtils.safeMultiply(Long.MAX_VALUE - 1L, 1L));
    }

    @Test
    void testSafeMultiplyOverflowReturnsMaxValue() {
        // 正溢出统一钳制到 Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MAX_VALUE, 2L));
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MAX_VALUE / 2 + 1, 2L));
    }

    @Test
    void testSafeMultiplyNegativeOverflowReturnsMaxValue() {
        // 负溢出同样返回 Long.MAX_VALUE（设计上表示“无限大”）
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MIN_VALUE, 2L));
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MIN_VALUE, -1L));
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, MathUtils.safeMultiply(Long.MAX_VALUE, -2L));
    }

    @Test
    void testSafeMultiplyLargeButNotOverflow() {
        // 刚好不溢出：MAX_VALUE = 3037000499 * 3037000499 + 余数
        assertEquals(3037000499L * 3037000499L, MathUtils.safeMultiply(3037000499L, 3037000499L));
    }
}
