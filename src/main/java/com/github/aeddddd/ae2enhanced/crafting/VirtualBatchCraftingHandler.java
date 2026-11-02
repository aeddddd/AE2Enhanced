package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;

/** Central 虚拟批量执行端口，由中央接口实现并由 Mixin 调用方装配。 */
public interface VirtualBatchCraftingHandler {

    void setNextVirtualBatchLimit(long limit);
    void setVirtualItemSource(IMEInventory<IAEItemStack> source);
    long getLastVirtualBatchSize();
}
