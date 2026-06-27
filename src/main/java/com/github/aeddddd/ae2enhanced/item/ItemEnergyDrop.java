package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * RF 能量假物品.
 * 用于在标准 AE2 物品终端中显示 RF 能量存储量.
 *
 * 设计：RF 能量无子类型,ItemStack 的 count 固定为 1(作为模板),
 * 实际数量通过 IAEItemStack.stackSize 表示.
 */
public class ItemEnergyDrop extends AbstractNbtDrop {

    private static final String AMOUNT_KEY = "Amount";

    public ItemEnergyDrop() {
        super("energy_drop");
    }

    /**
     * 创建 RF 假物品模板(count = 1).
     * 实际数量通过 AEItemStack.setStackSize() 设置.
     */
    public static ItemStack createStack() {
        return new ItemStack(ItemRegistry.ENERGY_DROP, 1);
    }

    /**
     * 创建携带具体数量的 RF 假物品.
     * <p>
     * count 限制在 [1,64] 用于显示,真实数量通过 NBT {@code Amount} 保存.
     * </p>
     */
    public static ItemStack createStack(long amount) {
        int count = (int) Math.min(Math.max(amount, 1), 64);
        ItemStack stack = new ItemStack(ItemRegistry.ENERGY_DROP, count);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(AMOUNT_KEY, amount);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 获取该 RF 假物品代表的数量.
     * 优先读取 NBT,其次使用 count.
     */
    public static long getAmount(ItemStack stack) {
        if (!isEnergyDrop(stack)) {
            return 0;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(AMOUNT_KEY)) {
            return tag.getLong(AMOUNT_KEY);
        }
        return stack.getCount();
    }

    /**
     * 判断 ItemStack 是否是能量假物品.
     */
    public static boolean isEnergyDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemEnergyDrop;
    }


}
