package com.github.aeddddd.ae2enhanced.storage;

import java.math.BigInteger;

/**
 * 存储模块常用常量，避免热路径重复创建对象。
 */
public final class StorageConstants {

    private StorageConstants() {
    }

    /**
     * 预缓存的 {@link Long#MAX_VALUE} 对应 BigInteger，
     * 用于把超大数量 clamp 到 long 时避免每次 new BigInteger。
     */
    public static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
}
