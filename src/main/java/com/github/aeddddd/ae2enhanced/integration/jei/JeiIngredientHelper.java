package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;

/**
 * JEI 成分转换工具类.
 * 将 AE2Enhanced 的假物品(流体/气体/源质 drop)转换为 JEI 可识别的实际成分,
 * 从而支持在终端中直接按 R/U 查询对应流体/气体/源质的配方.
 *
 * 复刻 ae2fc CoreModHooks.wrapFluidPacket 的设计.
 */
public class JeiIngredientHelper {

    public static Object wrapIngredient(Object obj) {
        if (!(obj instanceof ItemStack)) {
            return obj;
        }
        ItemStack stack = (ItemStack) obj;
        if (stack.isEmpty()) {
            return obj;
        }

        if (!Ae2fcCompat.AE2FC_LOADED) {
            // 流体假物品 -> FluidStack
            if (stack.getItem() == ItemRegistry.FLUID_DROP) {
                FluidStack fluid = FakeItemRegister.getStack(stack);
                if (fluid != null) {
                    return fluid;
                }
            }

            // 气体假物品 -> GasStack
            if (ItemRegistry.GAS_DROP != null && stack.getItem() == ItemRegistry.GAS_DROP) {
                GasStack gas = FakeItemRegister.getStack(stack);
                if (gas != null) {
                    return gas;
                }
            }
        }

        // 源质假物品 -> Thaumcraft crystal essence ItemStack
        if (ItemRegistry.ESSENTIA_DROP != null && stack.getItem() == ItemRegistry.ESSENTIA_DROP) {
            String aspectTag = ItemEssentiaDrop.getAspectTag(stack);
            if (aspectTag != null) {
                Aspect aspect = Aspect.getAspect(aspectTag);
                if (aspect != null && ItemsTC.crystalEssence != null) {
                    ItemStack crystal = new ItemStack(ItemsTC.crystalEssence);
                    ((thaumcraft.api.items.ItemGenericEssentiaContainer) ItemsTC.crystalEssence)
                            .setAspects(crystal, new AspectList().add(aspect, 1));
                    return crystal;
                }
            }
        }

        return obj;
    }
}
