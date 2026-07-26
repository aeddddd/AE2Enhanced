package com.github.aeddddd.ae2enhanced.client.specialcrafting;

import org.jetbrains.annotations.Nullable;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;

/**
 * 客户端缓存:当前计划确认界面的特殊计划显示信息.
 * 由 SpecialPlanInfoPacket 更新;普通计划收到 EMPTY 自动清空.
 */
public final class SpecialPlanClientCache {

    private static volatile SpecialPlanInfo current = SpecialPlanInfo.EMPTY;

    private SpecialPlanClientCache() {
    }

    public static void set(SpecialPlanInfo info) {
        current = info == null ? SpecialPlanInfo.EMPTY : info;
    }

    @Nullable
    public static SpecialPlanInfo.Entry entryFor(appeng.api.stacks.AEKey key) {
        return current.entryFor(key);
    }
}
