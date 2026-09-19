package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * EntityLivingBase 私有/受保护成员访问接口. MC 原生类, remap=true 使用 MCP 名.
 */
@Mixin(EntityLivingBase.class)
public interface IEntityLivingBaseAccessor {

    @Accessor("attackingPlayer")
    void ae2e$setAttackingPlayer(EntityPlayer player);

    @Accessor("recentlyHit")
    void ae2e$setRecentlyHit(int value);

    @Accessor("HEALTH")
    static DataParameter<Float> ae2e$getHEALTH() {
        throw new UnsupportedOperationException();
    }

    @Invoker("damageEntity")
    void ae2e$damageEntity(DamageSource source, float amount);
}
