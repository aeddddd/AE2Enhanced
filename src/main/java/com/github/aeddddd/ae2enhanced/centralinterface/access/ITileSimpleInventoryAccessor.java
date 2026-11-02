package com.github.aeddddd.ae2enhanced.centralinterface.access;

import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import vazkii.botania.common.block.tile.TileSimpleInventory;

/**
 * TileSimpleInventory 的 getItemHandler 访问器.
 *
 * <p>getItemHandler 声明在 TileSimpleInventory(TileRuneAltar / TileAltar 的父类),
 * 对子类做 getMethod 反射同样会触发子类客户端方法签名解析导致服务端崩溃,
 * 因此对父类使用 Invoker,子类实例可直接强转本接口调用.</p>
 */
@Mixin(value = TileSimpleInventory.class, remap = false)
public interface ITileSimpleInventoryAccessor {

    @Invoker("getItemHandler")
    IItemHandlerModifiable ae2e$getItemHandler();
}
