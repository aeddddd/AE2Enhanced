package com.github.aeddddd.ae2enhanced.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link LivingEntity} 受保护成员访问器,供 ForceKillHelper 直接读写底层血量数据.
 * {@code ae2e$actuallyHurt} 对应 1.12 的 {@code ae2e$damageEntity} invoker.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> ae2e$getDataHealthId() {
        throw new UnsupportedOperationException();
    }

    @Invoker("actuallyHurt")
    void ae2e$actuallyHurt(DamageSource source, float amount);
}
