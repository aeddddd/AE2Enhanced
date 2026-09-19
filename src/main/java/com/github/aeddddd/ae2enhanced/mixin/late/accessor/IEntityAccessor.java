package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Entity 受保护字段访问接口. MC 原生类, remap=true 使用 MCP 名.
 */
@Mixin(Entity.class)
public interface IEntityAccessor {

    @Accessor("isImmuneToFire")
    void ae2e$setImmuneToFire(boolean immune);
}
