package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import java.math.BigInteger;

/**
 * 各通道共享的常量。
 */
public final class StorageChannelConstants {

    /**
     * 每种 key 的容量上限。该值远大于 {@link Long#MAX_VALUE}，
     * 确保在 AE2 网络侧看到上限之前内部不会溢出。
     */
    public static final BigInteger CAPACITY_PER_KEY = BigInteger.TEN.pow(36);

    private StorageChannelConstants() {
    }
}
