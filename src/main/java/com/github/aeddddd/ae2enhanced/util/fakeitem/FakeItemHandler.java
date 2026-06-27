package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 假物品处理器接口.
 * 将特定类型的 AE2 存储对象(FluidStack/GasStack)与 ItemStack 互相转换.
 *
 * V: 实际存储类型(如 FluidStack, GasStack)
 * A: AE2 栈类型(如 IAEFluidStack, IAEGasStack)
 */
public interface FakeItemHandler<V, A> {

    /** 从 ItemStack 解析实际存储对象. */
    V getStack(ItemStack stack);

    /** 从 IAEItemStack 解析实际存储对象. */
    V getStack(@Nullable IAEItemStack stack);

    /** 从 ItemStack 解析 AE2 栈. */
    A getAEStack(ItemStack stack);

    /** 从 IAEItemStack 解析 AE2 栈. */
    A getAEStack(@Nullable IAEItemStack stack);

    /** 将实际存储对象打包为 ItemStack(含 NBT). */
    ItemStack packStack(V value);

    /** 将实际存储对象打包为 IAEItemStack(含 NBT,数量正确). */
    IAEItemStack packAEStack(V value);

    /** 将 AE2 长栈打包为 IAEItemStack(含 NBT,数量正确). */
    IAEItemStack packAEStackLong(A aeStack);
}
