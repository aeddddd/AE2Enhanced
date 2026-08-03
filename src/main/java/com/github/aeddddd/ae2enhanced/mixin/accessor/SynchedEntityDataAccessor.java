package com.github.aeddddd.ae2enhanced.mixin.accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.network.syncher.SynchedEntityData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link SynchedEntityData} 私有成员访问器,替代 ForceKillHelper 中的运行时反射.
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {

    @Accessor("itemsById")
    Int2ObjectMap<SynchedEntityData.DataItem<?>> ae2e$getItemsById();
}
