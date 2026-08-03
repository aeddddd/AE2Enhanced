package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.craftingplan.dag.SaturatedMath;

/**
 * {@link SaturatedMath} 饱和运算的边界测试.
 * <p>纯函数,无需 Minecraft 引导.契约:输入为非负数量,
 * 加法/乘法溢出时钳制到 {@link Long#MAX_VALUE},非正乘数返回 0.</p>
 */
public class SaturatedMathTest {

    // ===== add =====

    @Test
    public void testAddNormal() {
        assertThat(SaturatedMath.add(1, 2)).isEqualTo(3);
        assertThat(SaturatedMath.add(0, 0)).isEqualTo(0);
        assertThat(SaturatedMath.add(Long.MAX_VALUE, 0)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void testAddBoundaryNotSaturated() {
        // MAX - 1 + 1 恰好不溢出
        assertThat(SaturatedMath.add(Long.MAX_VALUE - 1, 1)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void testAddOverflowSaturates() {
        assertThat(SaturatedMath.add(Long.MAX_VALUE, 1)).isEqualTo(Long.MAX_VALUE);
        assertThat(SaturatedMath.add(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
        assertThat(SaturatedMath.add(Long.MAX_VALUE - 1, 2)).isEqualTo(Long.MAX_VALUE);
    }

    // ===== multiply =====

    @Test
    public void testMultiplyNormal() {
        assertThat(SaturatedMath.multiply(3, 4)).isEqualTo(12);
        assertThat(SaturatedMath.multiply(1, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void testMultiplyZeroOrNegativeReturnsZero() {
        assertThat(SaturatedMath.multiply(0, 5)).isEqualTo(0);
        assertThat(SaturatedMath.multiply(5, 0)).isEqualTo(0);
        assertThat(SaturatedMath.multiply(0, 0)).isEqualTo(0);
        assertThat(SaturatedMath.multiply(-1, 5)).isEqualTo(0);
        assertThat(SaturatedMath.multiply(5, -1)).isEqualTo(0);
        assertThat(SaturatedMath.multiply(-3, -7)).isEqualTo(0);
    }

    @Test
    public void testMultiplyBoundaryNotSaturated() {
        // MAX / 2 * 2 恰好不溢出
        long half = Long.MAX_VALUE / 2;
        assertThat(SaturatedMath.multiply(half, 2)).isEqualTo(half * 2);
    }

    @Test
    public void testMultiplyOverflowSaturates() {
        assertThat(SaturatedMath.multiply(Long.MAX_VALUE, 2)).isEqualTo(Long.MAX_VALUE);
        assertThat(SaturatedMath.multiply(Long.MAX_VALUE / 2 + 1, 2)).isEqualTo(Long.MAX_VALUE);
        assertThat(SaturatedMath.multiply(Long.MAX_VALUE, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
    }

    // ===== ceilDiv =====

    @Test
    public void testCeilDivExact() {
        assertThat(SaturatedMath.ceilDiv(4, 2)).isEqualTo(2);
        assertThat(SaturatedMath.ceilDiv(1, 1)).isEqualTo(1);
        assertThat(SaturatedMath.ceilDiv(100, 10)).isEqualTo(10);
    }

    @Test
    public void testCeilDivRoundsUp() {
        assertThat(SaturatedMath.ceilDiv(5, 2)).isEqualTo(3);
        assertThat(SaturatedMath.ceilDiv(1, 3)).isEqualTo(1);
        assertThat(SaturatedMath.ceilDiv(101, 10)).isEqualTo(11);
    }
}
