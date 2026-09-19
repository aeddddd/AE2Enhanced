package com.github.aeddddd.ae2enhanced.client.specialcrafting;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;

/**
 * 客户端特殊计划显示信息缓存.
 * <p>保存最近一次合成计算的结果,供 GuiCraftConfirm 的 tooltip mixin 查询.
 * 合成确认界面一次只对应一个计算结果,因此只缓存最新一条.</p>
 */
public final class SpecialPlanClientCache {

    private static SpecialPlanInfo info = SpecialPlanInfo.EMPTY;

    private SpecialPlanClientCache() {
    }

    public static void update(SpecialPlanInfo newInfo) {
        info = newInfo != null ? newInfo : SpecialPlanInfo.EMPTY;
    }

    public static void clear() {
        info = SpecialPlanInfo.EMPTY;
    }

    @Nullable
    public static SpecialPlanInfo infoFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        IAEItemStack ae = AEItemStack.fromItemStack(stack);
        if (ae == null) {
            return null;
        }
        return info.entryFor(ae) != null || info.callCountOf(ae) > 0 ? info : null;
    }
}
