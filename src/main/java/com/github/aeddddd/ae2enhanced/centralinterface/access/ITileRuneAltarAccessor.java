package com.github.aeddddd.ae2enhanced.centralinterface.access;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import vazkii.botania.common.block.tile.TileRuneAltar;

/**
 * TileRuneAltar 方法访问器.
 *
 * <p>TileRuneAltar 声明了 renderHUD(Minecraft, ScaledResolution) 客户端方法,
 * 在 Dedicated Server 上任何 getMethod/getDeclaredMethod 反射都会因解析方法签名
 * 抛出 NoClassDefFoundError,因此必须通过 Invoker 访问.</p>
 *
 * <p>字段(currentRecipe/cooldown/mana/manaToGet)类型均不引用客户端类,
 * 继续由 BotaniaReflectionHelper 通过 getDeclaredField 反射访问.</p>
 */
@Mixin(value = TileRuneAltar.class, remap = false)
public interface ITileRuneAltarAccessor {

    @Invoker("isEmpty")
    boolean ae2e$isEmpty();

    @Invoker("addItem")
    boolean ae2e$addItem(EntityPlayer player, ItemStack stack, EnumHand hand);

    @Invoker("getCurrentMana")
    int ae2e$getCurrentMana();

    @Invoker("recieveMana")
    void ae2e$recieveMana(int mana);

    @Invoker("saveLastRecipe")
    void ae2e$saveLastRecipe();

    @Invoker("getSizeInventory")
    int ae2e$getSizeInventory();
}
