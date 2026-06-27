package com.github.aeddddd.ae2enhanced.client;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;

/**
 * 客户端缓存的个人维度规则。
 *
 * 个人维度配置 GUI 在客户端打开时无法读取服务端 WorldSavedData，因此通过
 * {@link com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesSync}
 * 同步后缓存在此处。
 */
public final class ClientPersonalDimensionRules {

    private static PersonalDimensionRules cachedRules;

    private ClientPersonalDimensionRules() {}

    /**
     * 获取当前缓存的规则；若尚未同步则返回默认值。
     */
    public static PersonalDimensionRules get() {
        if (cachedRules == null) {
            cachedRules = new PersonalDimensionRules();
        }
        return cachedRules;
    }

    public static void update(PersonalDimensionRules rules) {
        cachedRules = rules != null ? rules.copy() : new PersonalDimensionRules();
    }
}
