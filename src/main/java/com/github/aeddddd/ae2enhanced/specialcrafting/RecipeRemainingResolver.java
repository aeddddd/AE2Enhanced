package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;

import com.github.aeddddd.ae2enhanced.mixin.bridge.IPatternHelperAccess;

/**
 * 样板配方返还物解析：以样板构造时匹配的 {@code standardRecipe} 计算
 * {@code getRemainingItems}，得到"每次合成的返还表"(canon 键 → 数量).
 * <p>覆盖原生 {@code Item.hasContainerItem} 路径识别不到的不消耗配方——
 * CraftTweaker {@code .reuse()}/自定义 {@code getRemainingItems} 实现
 * （容器语义只走 Item API，而 CrT 通过配方级剩余物钩子返回物品）.</p>
 * <p>结果按样板实例缓存（弱键，样板随网络缓存重建而更换即自动失效）;
 * 计划层(DagExecutor/DagCompiler)与执行层(分子装配室/CPU 集群)共用同一张表,
 * 保证"计划按返还记账、执行按返还交付"的端到端一致.</p>
 */
public final class RecipeRemainingResolver {

    /** 样板实例 → 返还表；Optional.empty = 已确认不适用（调用方回退 Item 容器逻辑）. */
    private static final Map<ICraftingPatternDetails, Optional<Map<IAEItemStack, Long>>> CACHE = Collections
            .synchronizedMap(new WeakHashMap<>());

    private RecipeRemainingResolver() {
    }

    /**
     * 每次合成的返还表（canon 键 → 数量）.
     *
     * @return null = 不适用（processing 样板/非 PatternHelper 实现/配方异常）,
     *         调用方应回退到 {@code Item.hasContainerItem} 旧逻辑;
     *         空 map = 确认无任何返还（纯消耗配方）.
     */
    @Nullable
    public static Map<IAEItemStack, Long> remainingPerCraft(ICraftingPatternDetails pattern) {
        if (!pattern.isCraftable() || !(pattern instanceof IPatternHelperAccess)) {
            return null;
        }
        Optional<Map<IAEItemStack, Long>> cached = CACHE.get(pattern);
        if (cached != null) {
            return cached.orElse(null);
        }
        Optional<Map<IAEItemStack, Long>> resolved = Optional.ofNullable(resolve(pattern));
        CACHE.put(pattern, resolved);
        return resolved.orElse(null);
    }

    @Nullable
    private static Map<IAEItemStack, Long> resolve(ICraftingPatternDetails pattern) {
        IPatternHelperAccess access = (IPatternHelperAccess) pattern;
        IRecipe recipe = access.ae2enhanced$standardRecipe();
        InventoryCrafting template = access.ae2enhanced$craftingTemplate();
        if (recipe == null || template == null) {
            return null;
        }
        // 模板栏内容为编码输入,直接拷贝填充;getRemainingItems 会 shrink 传入栏位,
        // 必须在拷贝上调用,不得触碰样板持有的模板
        InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < template.getSizeInventory() && i < ic.getSizeInventory(); i++) {
            ItemStack stack = template.getStackInSlot(i);
            ic.setInventorySlotContents(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        NonNullList<ItemStack> remaining;
        try {
            remaining = recipe.getRemainingItems(ic);
        } catch (Throwable t) {
            return null; // 配方实现异常 → 回退 Item 容器逻辑(保守)
        }
        Map<IAEItemStack, Long> table = new LinkedHashMap<>();
        for (int i = 0; i < remaining.size() && i < ic.getSizeInventory(); i++) {
            ItemStack rem = remaining.get(i);
            if (rem.isEmpty()) {
                continue;
            }
            IAEItemStack ae = appeng.util.item.AEItemStack.fromItemStack(rem);
            if (ae == null) {
                continue;
            }
            IAEItemStack key = RecursiveCraftingHelper.canon(ae);
            table.merge(key, (long) rem.getCount(), Long::sum);
        }
        return table;
    }
}
