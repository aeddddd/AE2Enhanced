package com.github.aeddddd.ae2enhanced.mixin.bridge;

import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;

/**
 * PatternHelper 内部状态访问接口（配方返还物识别用）.
 * <p>可合成样板构造时已匹配好 {@code standardRecipe} 并持有编码输入的 {@code crafting}
 * 物品栏,暴露二者即可零注册表扫描地计算 {@code getRemainingItems}——
 * 这是识别 CraftTweaker {@code .reuse()} 等不消耗物品配方的关键
 * （原生 {@code Item.hasContainerItem} 路径覆盖不到它们）.</p>
 */
public interface IPatternHelperAccess {

    /** 构造时匹配的原版配方（processing 样板为 null）. */
    @Nullable
    IRecipe ae2enhanced$standardRecipe();

    /** 编码输入模板物品栏（只读使用,调用方不得直接修改其内容）. */
    @Nullable
    InventoryCrafting ae2enhanced$craftingTemplate();
}
