package com.github.aeddddd.ae2enhanced.crafting;

import net.minecraft.item.ItemStack;

/**
 * 微型奇点燃料配方, 代码注册, CraftTweaker 可增删.
 * 手持匹配物品右键微型奇点时消耗 1 个: 增加 {@code ticks} 存在时间, 或当 permanent 时使奇点永久存在.
 * 移植自 1.20.1 分支的 JSON 配方; 1.12.2 无数据驱动配方体系, 改为注册表实现, 匹配语义为物品 + metadata.
 */
public class SingularityFuelRecipe {

    private final String id;
    private final ItemStack fuelItem;
    private final int ticks;
    private final boolean permanent;

    /**
     * @param id        配方唯一标识
     * @param fuelItem  燃料物品, 匹配物品与 metadata, 忽略 NBT 与数量
     * @param ticks     喂入后追加的存在时间, 单位 tick, permanent 为 true 时忽略
     * @param permanent true 时喂入后奇点永久存在
     */
    public SingularityFuelRecipe(String id, ItemStack fuelItem, int ticks, boolean permanent) {
        this.id = id;
        // 防御性拷贝: 避免调用方后续修改传入栈影响配方; null 归一化为空栈
        this.fuelItem = fuelItem != null ? fuelItem.copy() : ItemStack.EMPTY;
        this.ticks = ticks;
        this.permanent = permanent;
    }

    public String getId() {
        return id;
    }

    public ItemStack getFuelItem() {
        return fuelItem.copy();
    }

    public int getTicks() {
        return ticks;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == fuelItem.getItem()
                && stack.getMetadata() == fuelItem.getMetadata();
    }
}
