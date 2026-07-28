package com.github.aeddddd.ae2enhanced.craftingplan.dag;

/**
 * 饱和长整数运算:溢出时钳制到 {@link Long#MAX_VALUE} 而非抛异常,
 * 由调用方按"超出可计划范围"语义回落.
 */
public final class SaturatedMath {

    private SaturatedMath() {
    }

    public static long add(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r; // 两正数相加溢出必绕回负数
    }

    public static long multiply(long a, long b) {
        if (a <= 0 || b <= 0) {
            return 0;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    /** 向上取整除法(a、b 为正). */
    public static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
