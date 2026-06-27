package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;

import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 超维度仓储中枢各类存储适配器的公共接口.
 * <p>
 * 用于在 {@link TileHyperdimensionalController} 中统一处理 AE2E 内部适配器
 * ({@link AbstractStorageAdapter}) 与外部通道适配器
 * ({@link com.github.aeddddd.ae2enhanced.storage.external.ExternalStorageAdapter}),
 * 避免在控制器中依赖具体实现类型.
 * </p>
 */
public interface IStorageAdapter {

    /**
     * 获取内部存储 Map(Key 为描述符,Value 为数量).
     */
    Map<?, BigInteger> getStorageMap();

    /**
     * 获取总数量.
     */
    BigInteger getTotalCount();

    /**
     * 判断当前是否处于安全模式(只读).
     */
    boolean isSafeMode();

    /**
     * 重新计算总数量.
     */
    void recalcTotal();

    /**
     * 设置任意变更回调.
     */
    void setOnChangeCallback(Runnable callback);
}
