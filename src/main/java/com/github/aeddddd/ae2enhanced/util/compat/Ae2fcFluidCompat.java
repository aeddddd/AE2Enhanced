package com.github.aeddddd.ae2enhanced.util.compat;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Method;

/**
 * ae2fc 流体假物品兼容层.
 *
 * <p>当 ae2fc 已安装时,所有流体假物品的创建与解析均走 ae2fc 的 {@code ItemFluidDrop},
 * 让 ae2fc 的 FakeMonitor 体系接管注入/提取,避免 AE2E 自己维护一套流体回收逻辑.</p>
 *
 * <p>ae2fc 未安装时,回退到 AE2E 自己的 {@link ItemFluidDrop}.</p>
 *
 * <p><b>安装检测不得使用 {@code Loader.isModLoaded}.</b> 该方法要求 FML {@code Loader}
 * 已装载;在未装载 Loader 的 JVM(无头单测/离线工具)里,{@code Loader.instance()} 为 null,
 * {@code isModLoaded} 直接抛 NPE。静态初始化块一旦抛异常,类初始化即告失败,JVM 不会重试
 * ——本类随后在整个 JVM 生命周期内每次访问都只会抛 {@code NoClassDefFoundError},
 * 兼容层的所有判据(含 {@link #isAnyFluidFakeItem(ItemStack)} 这类纯字符串比较)
 * 一并失效,并波及所有经过流体路径的用例。因此这里沿用 {@link Ae2fcCompat} 的类加载器
 * 资源探测判据:它只依赖 classpath,Loader 未装载时自然返回"未安装"。</p>
 *
 * <p>初始化本身也被完整保护:{@code ae2fc} 存在但反射失败时,退化为
 * "未加载 → 全部能力为 null/false / 回退 {@link ItemFluidDrop}",绝不抛出。</p>
 */
public final class Ae2fcFluidCompat {

    private static final boolean AE2FC_LOADED;
    private static Method PACK_FLUID_TO_AE_DROP;
    private static Method GET_FLUID_FROM_ITEM;

    private static final String AE2FC_ITEM_FLUID_DROP = "com.glodblock.github.common.item.ItemFluidDrop";
    private static final String AE2FC_ITEM_FLUID_PACKET = "com.glodblock.github.common.item.ItemFluidPacket";

    static {
        boolean loaded = false;
        Throwable failure = null;
        // 用 Ae2fcCompat 的资源探测结果判定 ae2fc 是否存在(不用 Loader.isModLoaded,理由见类注释)
        if (Ae2fcCompat.AE2FC_LOADED) {
            try {
                Class<?> fakeFluidsClass = Class.forName("com.glodblock.github.common.item.fake.FakeFluids");
                PACK_FLUID_TO_AE_DROP = fakeFluidsClass.getMethod("packFluid2AEDrops", FluidStack.class);
                Class<?> utilClass = Class.forName("com.glodblock.github.util.Util");
                GET_FLUID_FROM_ITEM = utilClass.getMethod("getFluidFromItem", ItemStack.class);
                loaded = true;
            } catch (Throwable e) {
                // 捕获 Throwable(而非 Exception):类加载/链接错误同样必须降级为"未安装",
                // 不能把本兼容层的初始化拖垮成永久性的 NoClassDefFoundError
                failure = e;
            }
        }
        AE2FC_LOADED = loaded;
        if (failure != null) {
            warnCompatInitFailure(failure);
        }
    }

    /**
     * ae2fc 存在但反射初始化失败时的降级日志.
     *
     * <p>日志本身不得成为静态初始化的失败点:调用方的类加载状态未知
     * ({@code AE2Enhanced.LOGGER} 可能同样不可用),因此这里再兜一层。</p>
     */
    private static void warnCompatInitFailure(Throwable failure) {
        try {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize ae2fc fluid compat, falling back to AE2E ItemFluidDrop", failure);
        } catch (Throwable ignored) {
            // 日志不可用时静默降级:兼容层保持"未加载"语义优先于留下日志
        }
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
     * <p>ae2fc 未安装时回退到 AE2E 自己的 {@link ItemFluidDrop}；
     * ae2fc 已安装时优先使用 ae2fc 的 ItemFluidDrop，若反射失败则返回空栈，
     * 避免 AE2E 的 ItemFluidDrop 在 ae2fc 加载时残留在物品网络。</p>
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
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to create ae2fc fluid drop, returning empty to avoid AE2E drop in item network", e);
        }
        return ItemStack.EMPTY;
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
}
