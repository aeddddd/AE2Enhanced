package com.github.aeddddd.ae2enhanced.mixin.accessor;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link Entity} 私有成员访问器,替代 ForceKillHelper 中的运行时反射.
 */
@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("entityData")
    SynchedEntityData ae2e$getEntityData();
}
