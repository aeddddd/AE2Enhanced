package com.github.aeddddd.ae2enhanced.mixin.accessor;

import net.minecraft.network.syncher.SynchedEntityData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link SynchedEntityData.DataItem} 私有方法调用器,供 ForceKillHelper 直接改写底层数据项.
 */
@Mixin(SynchedEntityData.DataItem.class)
public interface SynchedDataItemInvoker {

    @Invoker("setValue")
    void ae2e$setValue(Object value);

    @Invoker("setDirty")
    void ae2e$setDirty(boolean dirty);
}
