package com.github.aeddddd.ae2enhanced.client;

import javax.annotation.Nullable;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimRulesSyncPacket;

/**
 * 客户端缓存的"当前所在个人维度"规则.
 *
 * <p>飞行移动为客户端权威,无飞行惯性等需要在客户端执行的规则,
 * 通过 {@link PersonalDimRulesSyncPacket} 同步后缓存在此处;
 * 玩家不在任何个人维度内时缓存为 {@code null}.</p>
 */
public final class ClientPersonalDimRules {

    @Nullable
    private static PersonalDimensionRules currentRules;

    private ClientPersonalDimRules() {
    }

    /**
     * 获取当前所在个人维度的规则；不在个人维度内或尚未同步时返回 {@code null}.
     */
    @Nullable
    public static PersonalDimensionRules getCurrent() {
        return currentRules;
    }

    /**
     * 更新缓存；{@code rules} 为 {@code null} 表示玩家不在个人维度内.
     */
    public static void update(@Nullable PersonalDimensionRules rules) {
        currentRules = rules != null ? rules.copy() : null;
    }
}
