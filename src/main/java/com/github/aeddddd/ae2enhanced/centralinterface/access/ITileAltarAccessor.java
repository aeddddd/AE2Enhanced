package com.github.aeddddd.ae2enhanced.centralinterface.access;

import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import vazkii.botania.common.block.tile.TileAltar;

/**
 * TileAltar (花药台) 方法访问器.
 *
 * <p>TileAltar 声明了 renderHUD(Minecraft, ScaledResolution) 客户端方法,
 * 在 Dedicated Server 上任何 getMethod/getDeclaredMethod 反射都会因解析方法签名
 * 抛出 NoClassDefFoundError,因此必须通过 Invoker 访问.</p>
 */
@Mixin(value = TileAltar.class, remap = false)
public interface ITileAltarAccessor {

    @Invoker("isEmpty")
    boolean ae2e$isEmpty();

    @Invoker("hasLava")
    boolean ae2e$hasLava();

    @Invoker("hasWater")
    boolean ae2e$hasWater();

    @Invoker("setWater")
    void ae2e$setWater(boolean water);

    @Invoker("collideEntityItem")
    boolean ae2e$collideEntityItem(EntityItem item);
}
