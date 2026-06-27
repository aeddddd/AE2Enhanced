package com.github.aeddddd.ae2enhanced.centralinterface;

import java.util.EnumSet;

/**
 * 远程目标处理器的能力标记.
 *
 * <p>一个 handler 可以同时支持物理发配和虚拟批量合成，也可以只支持其一。
 * {@link DualityCentralInterface} 根据能力集合决定对该目标使用哪条路径。</p>
 */
public enum HandlerCapabilities {
    /**
     * 支持物理发配：实际推送材料到目标机器并在 tick 中收集产物。
     */
    PHYSICAL,

    /**
     * 支持批量虚拟合成：不占用机器，直接从网络扣除资源并返回产物。
     */
    VIRTUAL_BATCH;

    /**
     * 常用组合：只支持物理。
     */
    public static EnumSet<HandlerCapabilities> physicalOnly() {
        return EnumSet.of(PHYSICAL);
    }

    /**
     * 常用组合：只支持虚拟批量。
     */
    public static EnumSet<HandlerCapabilities> virtualOnly() {
        return EnumSet.of(VIRTUAL_BATCH);
    }

    /**
     * 常用组合：物理和虚拟批量都支持。
     */
    public static EnumSet<HandlerCapabilities> all() {
        return EnumSet.of(PHYSICAL, VIRTUAL_BATCH);
    }
}
