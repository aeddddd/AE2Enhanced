package com.github.aeddddd.ae2enhanced.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * {@link HugeCount} 混合精度计数器测试.
 *
 * <p>重点：long 快路径语义、BigInteger 溢出升级、toByteArray 与 BigInteger.toByteArray
 * 的二进制兼容（磁盘格式不变式）、跨表示 equals/hashCode 一致性。</p>
 */
public class HugeCountTest {

    @Test
    public void longFastPathArithmetic() {
        HugeCount a = HugeCount.of(100);
        HugeCount b = HugeCount.of(250);
        assertThat(a.add(b).toLongSaturated()).isEqualTo(350L);
        assertThat(b.subtract(a).toLongSaturated()).isEqualTo(150L);
        assertThat(a.min(b).toLongSaturated()).isEqualTo(100L);
        assertThat(a.isZero()).isFalse();
        assertThat(HugeCount.ZERO.isZero()).isTrue();
        assertThat(HugeCount.of(5).subtract(5)).isSameAs(HugeCount.ZERO);
    }

    @Test
    public void subtractNegativeThrows() {
        assertThatThrownBy(() -> HugeCount.of(1).subtract(2))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HugeCount.of(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void overflowUpgradesToBigInteger() {
        HugeCount big = HugeCount.of(Long.MAX_VALUE).add(1);
        assertThat(big.isBig()).isTrue();
        assertThat(big.exceedsLong()).isTrue();
        assertThat(big.toLongSaturated()).isEqualTo(Long.MAX_VALUE);
        assertThat(big.toBigInteger()).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        // 大值回落到 long 范围后仍保持正确
        HugeCount back = big.subtract(HugeCount.of(Long.MAX_VALUE));
        assertThat(back.toLongSaturated()).isEqualTo(1L);
        assertThat(back.isBig()).isFalse();
    }

    /** 磁盘格式不变式：与 BigInteger.toByteArray 完全一致。 */
    @Test
    public void byteArrayCompatibleWithBigInteger() {
        long[] samples = {0L, 1L, 127L, 128L, 255L, 256L, 32767L, 32768L,
            Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 1, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long v : samples) {
            assertThat(HugeCount.of(v).toByteArray())
                .as("value %d", v)
                .isEqualTo(BigInteger.valueOf(v).toByteArray());
        }
        Random rng = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            long v = rng.nextLong() >>> 1; // 非负
            assertThat(HugeCount.of(v).toByteArray())
                .isEqualTo(BigInteger.valueOf(v).toByteArray());
        }
        // BigInteger 域
        BigInteger huge = new BigInteger("123456789012345678901234567890123456789");
        assertThat(HugeCount.of(huge).toByteArray()).isEqualTo(huge.toByteArray());
        // 往返
        assertThat(HugeCount.fromByteArray(huge.toByteArray()).toBigInteger()).isEqualTo(huge);
        assertThat(HugeCount.fromByteArray(BigInteger.valueOf(64).toByteArray()).toLongSaturated()).isEqualTo(64L);
    }

    @Test
    public void equalsHashCodeAcrossRepresentations() {
        // 同一数值的 long/BigInteger 表示必须相等且同 hash（否则 Map 语义破裂）
        HugeCount viaLong = HugeCount.of(Long.MAX_VALUE);
        HugeCount viaBig = HugeCount.of(BigInteger.valueOf(Long.MAX_VALUE));
        assertThat(viaLong).isEqualTo(viaBig);
        assertThat(viaLong.hashCode()).isEqualTo(viaBig.hashCode());
        assertThat(HugeCount.of(7)).isEqualTo(HugeCount.of(BigInteger.valueOf(7)));
        assertThat(HugeCount.of(7)).isNotEqualTo(HugeCount.of(8));
    }

    @Test
    public void compareToOrdering() {
        assertThat(HugeCount.of(1).compareTo(HugeCount.of(2))).isNegative();
        assertThat(HugeCount.of(Long.MAX_VALUE).add(1).compareTo(HugeCount.of(Long.MAX_VALUE))).isPositive();
        assertThat(HugeCount.ZERO.compareTo(HugeCount.ZERO)).isZero();
    }
}
