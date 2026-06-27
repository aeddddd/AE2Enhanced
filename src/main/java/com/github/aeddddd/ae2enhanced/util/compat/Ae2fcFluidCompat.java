package com.github.aeddddd.ae2enhanced.util.compat;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;

/**
 * ae2fc 流体假物品兼容层.
 *
 * <p>当 ae2fc 已安装时,所有流体假物品的创建与解析均走 ae2fc 的 {@code ItemFluidDrop},
 * 让 ae2fc 的 FakeMonitor 体系接管注入/提取,避免 AE2E 自己维护一套流体回收逻辑.</p>
 *
 * <p>ae2fc 未安装时,回退到 AE2E 自己的 {@link ItemFluidDrop}.</p>
 */
public final class Ae2fcFluidCompat {

    private static final boolean AE2FC_LOADED;
    private static Method PACK_FLUID_TO_AE_DROP;
    private static Method GET_FLUID_FROM_ITEM;

    private static final String AE2FC_ITEM_FLUID_DROP = "com.glodblock.github.common.item.ItemFluidDrop";
    private static final String AE2FC_ITEM_FLUID_PACKET = "com.glodblock.github.common.item.ItemFluidPacket";

    static {
        boolean loaded = false;
        if (Loader.isModLoaded("ae2fc")) {
            try {
                Class<?> fakeFluidsClass = Class.forName("com.glodblock.github.common.item.fake.FakeFluids");
                PACK_FLUID_TO_AE_DROP = fakeFluidsClass.getMethod("packFluid2AEDrops", FluidStack.class);
                Class<?> utilClass = Class.forName("com.glodblock.github.util.Util");
                GET_FLUID_FROM_ITEM = utilClass.getMethod("getFluidFromItem", ItemStack.class);
                loaded = true;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize ae2fc fluid compat, falling back to AE2E ItemFluidDrop", e);
            }
        }
        AE2FC_LOADED = loaded;
    }

    private Ae2fcFluidCompat() {
    }

    /**
     * 判断 ItemStack 是否为 ae2fc 的流体假物品（ItemFluidDrop / ItemFluidPacket）。
     */
    public static boolean isAe2fcFluidFakeItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().getClass().getName();
        return AE2FC_ITEM_FLUID_DROP.equals(name) || AE2FC_ITEM_FLUID_PACKET.equals(name);
    }

    /**
     * 判断 ItemStack 是否为 AE2E 或 ae2fc 的流体假物品。
     */
    public static boolean isAnyFluidFakeItem(ItemStack stack) {
        return ItemFluidDrop.isFluidDrop(stack) || isAe2fcFluidFakeItem(stack);
    }

    /**
     * 创建表示指定流体的假物品堆叠.
     *
     * @param fluid 流体及数量
     * @return 流体假物品；若失败则返回空栈
     */
    public static ItemStack createFluidDrop(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (!AE2FC_LOADED) {
            return ItemFluidDrop.createStack(fluid);
        }
        try {
            IAEItemStack result = (IAEItemStack) PACK_FLUID_TO_AE_DROP.invoke(null, fluid);
            if (result != null) {
                ItemStack stack = result.createItemStack();
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to create ae2fc fluid drop, falling back", e);
        }
        return ItemFluidDrop.createStack(fluid);
    }

    /**
     * 从 ItemStack 中提取 FluidStack.
     *
     * <p>优先使用 ae2fc 的 {@code Util.getFluidFromItem} 处理流体容器；
     * 对 ae2fc 的 {@code ItemFluidDrop}/{@code ItemFluidPacket} 使用 AE2E 的
     * {@link FakeFluids#unpackAe2fcFluid} 解析（ae2fc 的 FluidDrop 没有
     * {@code FLUID_HANDLER_ITEM_CAPABILITY}，且 NBT 键与 AE2E 不同）。</p>
     *
     * @param stack 可能是流体假物品或流体容器的 ItemStack
     * @return 提取出的 FluidStack；若无法解析则返回 null
     */
    public static FluidStack getFluidStack(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (isAe2fcFluidFakeItem(stack)) {
            return FakeFluids.unpackAe2fcFluid(stack);
        }
        if (ItemFluidDrop.isFluidDrop(stack)) {
            return ItemFluidDrop.getFluidStack(stack);
        }
        if (AE2FC_LOADED) {
            try {
                FluidStack fluid = (FluidStack) GET_FLUID_FROM_ITEM.invoke(null, stack);
                if (fluid != null && fluid.getFluid() != null && fluid.amount > 0) {
                    return fluid;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to get fluid from ae2fc container, falling back", e);
            }
        }
        return null;
    }

    /**
     * 判断 ItemStack 是否为流体假物品(兼容 ae2fc 与 AE2E 自己的 drop).
     */
    public static boolean isFluidDrop(ItemStack stack) {
        return isAnyFluidFakeItem(stack);
    }
}
