package com.github.aeddddd.ae2enhanced.integration.drawer;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/**
 * 抽屉模组 Hash 索引适配器统一接口.
 *
 * <p>封装 StorageDrawers 和 FunctionalStorageLegacy 的底层实现差异,
 * 对外提供 O(同物品槽位数) 的索引访问.所有反射和 NPE 风险由实现类内部承担.</p>
 */
public interface IDrawerIndexAdapter {

    IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src);

    IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src);

    IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out);

    AccessRestriction getAccess();

    boolean isPrioritized(IAEItemStack input);

    boolean canAccept(IAEItemStack input);

    int getPriority();

    int getSlot();

    boolean validForPass(int pass);
}
