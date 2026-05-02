package com.github.aeddddd.ae2enhanced.crafting;

import java.math.BigInteger;

/**
 * 批次管理器：将超大订单（> Long.MAX_VALUE 或超出并行限制）拆分为可处理的子批次。
 * P1 骨架 —— 大数字拆分逻辑已可用，与 AE2-UEL 的 long 接口桥接将在 P1 完善。
 */
public class BatchManager {

    private BatchManager() {}

    /**
     * 将 BigInteger 总量拆分为每个不超过 Long.MAX_VALUE 的批次。
     */
    public static long[] splitToLongBatches(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return new long[0];
        }
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return new long[]{ amount.longValueExact() };
        }

        BigInteger[] divRem = amount.divideAndRemainder(BigInteger.valueOf(Long.MAX_VALUE));
        long fullBatches = divRem[0].longValueExact();
        long remainder = divRem[1].longValueExact();

        int size = (int) fullBatches + (remainder > 0 ? 1 : 0);
        long[] result = new long[size];
        for (int i = 0; i < fullBatches; i++) {
            result[i] = Long.MAX_VALUE;
        }
        if (remainder > 0) {
            result[(int) fullBatches] = remainder;
        }
        return result;
    }

    /**
     * 将 BigInteger 总量拆分为每个不超过 parallelLimit 的批次。
     */
    public static long[] splitToParallelBatches(BigInteger amount, int parallelLimit) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0 || parallelLimit <= 0) {
            return new long[0];
        }
        if (amount.compareTo(BigInteger.valueOf(parallelLimit)) <= 0) {
            return new long[]{ amount.longValueExact() };
        }

        BigInteger bigParallel = BigInteger.valueOf(parallelLimit);
        BigInteger[] divRem = amount.divideAndRemainder(bigParallel);
        long fullBatches = divRem[0].longValueExact();
        long remainder = divRem[1].longValueExact();

        int size = (int) fullBatches + (remainder > 0 ? 1 : 0);
        long[] result = new long[size];
        for (int i = 0; i < fullBatches; i++) {
            result[i] = parallelLimit;
        }
        if (remainder > 0) {
            result[(int) fullBatches] = remainder;
        }
        return result;
    }
}
