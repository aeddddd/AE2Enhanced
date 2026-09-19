package com.github.aeddddd.ae2enhanced.storage;

import java.math.BigInteger;

/**
 * 混合精度非负计数器：long 快路径 + BigInteger 溢出升级.
 *
 * <p>设计参照 NeoECOAE 的 HugeAmount：绝大多数存储数量都在 long 范围内，
 * 此时加减法是零分配的原始运算；只有超过 {@link Long#MAX_VALUE} 才升级为 BigInteger。
 * 相比全量 BigInteger，消除了存取热路径上每次操作 1-2 次的 BigInteger 分配。</p>
 *
 * <p>不可变对象。语义约束：值域为非负整数（存储计数语义），{@link #subtract} 结果
 * 为负时抛 {@link IllegalArgumentException}（与 NeoECOAE HugeAmount 一致）。</p>
 *
 * <p>持久化编码通过 {@link #toByteArray()} 与 BigInteger 二进制兼容（二进制补码
 * 大端最小字节序），磁盘格式不因本类而改变。</p>
 */
public final class HugeCount implements Comparable<HugeCount> {

    public static final HugeCount ZERO = new HugeCount(0L, null);
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final long longValue;
    private final BigInteger bigValue; // 非 null 当且仅当值超过 Long.MAX_VALUE

    private HugeCount(long longValue, BigInteger bigValue) {
        this.longValue = longValue;
        this.bigValue = bigValue;
    }

    public static HugeCount of(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("HugeCount 不允许负数: " + value);
        }
        return value == 0L ? ZERO : new HugeCount(value, null);
    }

    public static HugeCount of(BigInteger value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("HugeCount 不允许负数: " + value);
        }
        if (value.signum() == 0) {
            return ZERO;
        }
        if (value.compareTo(BI_LONG_MAX) <= 0) {
            return of(value.longValue());
        }
        return new HugeCount(0L, value);
    }

    /** 从 BigInteger 二进制补码字节（含符号位）读取，与 BigInteger(byte[]) 语义一致。 */
    public static HugeCount fromByteArray(byte[] bytes) {
        return of(new BigInteger(bytes));
    }

    public boolean isZero() {
        return bigValue == null && longValue == 0L;
    }

    public boolean isBig() {
        return bigValue != null;
    }

    /** 兼容旧代码的 signum 语义：本类值域非负，只会返回 0 或 1。 */
    public int signum() {
        return isZero() ? 0 : 1;
    }

    public HugeCount add(HugeCount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (isZero()) {
            return other;
        }
        if (this.bigValue == null && other.bigValue == null && Long.MAX_VALUE - this.longValue >= other.longValue) {
            return of(this.longValue + other.longValue);
        }
        return of(this.toBigInteger().add(other.toBigInteger()));
    }

    public HugeCount add(long other) {
        if (other < 0L) {
            throw new IllegalArgumentException("add 参数必须非负: " + other);
        }
        if (other == 0L) {
            return this;
        }
        if (this.bigValue == null && Long.MAX_VALUE - this.longValue >= other) {
            return of(this.longValue + other);
        }
        return of(toBigInteger().add(BigInteger.valueOf(other)));
    }

    public HugeCount subtract(HugeCount other) {
        if (other == null || other.isZero()) {
            return this;
        }
        if (compareTo(other) < 0) {
            throw new IllegalArgumentException("HugeCount 减法结果为负: " + this + " - " + other);
        }
        if (this.bigValue == null && other.bigValue == null) {
            return of(this.longValue - other.longValue);
        }
        return of(toBigInteger().subtract(other.toBigInteger()));
    }

    public HugeCount subtract(long other) {
        if (other < 0L) {
            throw new IllegalArgumentException("subtract 参数必须非负: " + other);
        }
        if (other == 0L) {
            return this;
        }
        if (this.bigValue == null) {
            if (this.longValue < other) {
                throw new IllegalArgumentException("HugeCount 减法结果为负: " + this + " - " + other);
            }
            return of(this.longValue - other);
        }
        return of(bigValue.subtract(BigInteger.valueOf(other)));
    }

    public HugeCount min(HugeCount other) {
        return other == null || compareTo(other) <= 0 ? this : other;
    }

    /** 超过 Long.MAX_VALUE 时截断为 Long.MAX_VALUE（AE2 堆叠数量的接口上限）。 */
    public long toLongSaturated() {
        return bigValue != null ? Long.MAX_VALUE : longValue;
    }

    /** 是否超过 long 表示范围（替代旧代码的 compareTo(StorageConstants.LONG_MAX) > 0）。 */
    public boolean exceedsLong() {
        return bigValue != null;
    }

    public BigInteger toBigInteger() {
        return bigValue != null ? bigValue : BigInteger.valueOf(longValue);
    }

    /**
     * 序列化为 BigInteger 兼容的二进制补码大端字节数组.
     * 对同一数值，返回字节与 {@code new BigInteger(...).toByteArray()} 完全一致，
     * 保证磁盘格式（v1: sign 字节 + magnitude）前后兼容。
     */
    public byte[] toByteArray() {
        if (bigValue != null) {
            return bigValue.toByteArray();
        }
        long v = longValue;
        // 最小字节数：循环条件保证去掉前导零后最高字节的符号位仍为 0（正数语义）
        int len = 1;
        long tmp = v;
        while (tmp >>> 7 != 0) {
            tmp >>>= 8;
            len++;
        }
        byte[] out = new byte[len];
        for (int i = len - 1; i >= 0; i--) {
            out[i] = (byte) (v & 0xFF);
            v >>>= 8;
        }
        return out;
    }

    @Override
    public int compareTo(HugeCount other) {
        if (other == null) {
            return 1;
        }
        if (this.bigValue == null && other.bigValue == null) {
            return Long.compare(this.longValue, other.longValue);
        }
        return toBigInteger().compareTo(other.toBigInteger());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HugeCount && compareTo((HugeCount) obj) == 0;
    }

    @Override
    public int hashCode() {
        // 与 BigInteger.hashCode 保持一致，保证 long/BigInteger 两种表示同 hash
        return toBigInteger().hashCode();
    }

    @Override
    public String toString() {
        return bigValue != null ? bigValue.toString() : Long.toString(longValue);
    }
}
