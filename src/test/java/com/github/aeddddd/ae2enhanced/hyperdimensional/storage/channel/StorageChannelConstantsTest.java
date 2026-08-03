package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StorageChannelConstants} 单元测试.
 */
class StorageChannelConstantsTest {

    @Test
    void testCapacityPerKeyValue() {
        // 容量上限固定为 10^36
        assertEquals(BigInteger.TEN.pow(36), StorageChannelConstants.CAPACITY_PER_KEY);
    }

    @Test
    void testCapacityExceedsLongMax() {
        // 容量必须远大于 Long.MAX_VALUE,确保 AE2 网络侧先触顶
        assertTrue(StorageChannelConstants.CAPACITY_PER_KEY.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }
}
