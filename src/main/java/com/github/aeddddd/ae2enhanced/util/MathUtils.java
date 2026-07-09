package com.github.aeddddd.ae2enhanced.util;

/**
 * 数学相关工具方法。
 */
public final class MathUtils {

    private MathUtils() {
    }

    /**
     * 安全乘法，溢出时返回 {@link Long#MAX_VALUE}。
     *
     * @param a 被乘数
     * @param b 乘数
     * @return 乘积或溢出时的 {@link Long#MAX_VALUE}
     */
    public static long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
