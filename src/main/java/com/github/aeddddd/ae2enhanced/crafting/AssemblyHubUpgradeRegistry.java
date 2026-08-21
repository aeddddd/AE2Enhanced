package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assembly Hub 升级卡注册表.
 * 允许 CraftTweaker 或其他系统注册新的升级卡物品,限定类型为并行(Parallel)或速度(Speed).
 */
public class AssemblyHubUpgradeRegistry {

    public enum UpgradeType {
        PARALLEL, SPEED
    }

    public static class UpgradeDefinition {
        public final ItemStack item;
        public final UpgradeType type;
        public final int maxStack;
        public final long[] values; // 索引 0 对应 1 张卡,依此类推

        public UpgradeDefinition(ItemStack item, UpgradeType type, int maxStack, long[] values) {
            this.item = item.copy();
            this.item.setCount(1);
            this.type = type;
            this.maxStack = maxStack;
            this.values = values != null ? values.clone() : new long[0];
        }
    }

    private static final Map<String, UpgradeDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    /** 注册表修订号:register/removeById 时递增,供 TileAssemblyController 缓存失效判断. */
    private static volatile long revision;

    public static long getRevision() {
        return revision;
    }

    public static String keyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        net.minecraft.util.ResourceLocation reg = stack.getItem().getRegistryName();
        if (reg != null) {
            return reg.toString() + "#" + stack.getMetadata();
        }
        // 无注册名：附加 Item 实例的稳定标识,避免不同未注册物品碰撞
        net.minecraft.item.Item item = stack.getItem();
        return "unknown#" + stack.getMetadata() + "#" + item.getClass().getName()
                + "@" + System.identityHashCode(item);
    }

    public static void register(UpgradeDefinition def) {
        DEFINITIONS.put(keyOf(def.item), def);
        revision++;
    }

    /**
     * 按 key 移除升级定义.
     *
     * @param id 注册表 key,即 {@link #keyOf(ItemStack)} 的返回值
     * @return 是否实际移除了条目
     */
    public static boolean removeById(String id) {
        if (id == null || id.isEmpty()) return false;
        boolean removed = DEFINITIONS.remove(id) != null;
        if (removed) {
            revision++;
        }
        return removed;
    }

    @Nullable
    public static UpgradeDefinition findFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        // 先精确匹配 item+meta
        UpgradeDefinition def = DEFINITIONS.get(keyOf(stack));
        if (def != null) return def;
        return null;
    }

    public static boolean isParallelUpgrade(ItemStack stack) {
        if (stack.getItem() instanceof ItemUpgradeCard && stack.getMetadata() == ItemUpgradeCard.META_PARALLEL) {
            return true;
        }
        UpgradeDefinition def = findFor(stack);
        return def != null && def.type == UpgradeType.PARALLEL;
    }

    public static boolean isSpeedUpgrade(ItemStack stack) {
        if (stack.getItem() instanceof ItemUpgradeCard && stack.getMetadata() == ItemUpgradeCard.META_SPEED) {
            return true;
        }
        UpgradeDefinition def = findFor(stack);
        return def != null && def.type == UpgradeType.SPEED;
    }

    /**
     * 获取某物品的自定义最大堆叠(仅对注册表中的定义有效).
     * 原生 ItemUpgradeCard 的堆叠限制不通过此处返回.
     */
    public static int getCustomMaxStack(ItemStack stack) {
        UpgradeDefinition def = findFor(stack);
        return def != null ? def.maxStack : -1;
    }

    /**
     * 获取并行上限值.
     *
     * @param stack 升级卡物品
     * @param count 实际安装数量
     * @return 并行上限,若未注册则返回 -1(调用方应回退到默认逻辑)
     */
    public static long getParallelValue(ItemStack stack, int count) {
        UpgradeDefinition def = findFor(stack);
        if (def == null || def.type != UpgradeType.PARALLEL) return -1;
        if (count <= 0) return 64;
        int idx = Math.min(count, def.values.length) - 1;
        if (idx < 0) return 64;
        return def.values[idx];
    }

    /**
     * 获取速度冷却值(tick).
     *
     * @param stack 升级卡物品
     * @param count 实际安装数量
     * @return 冷却 tick 数,若未注册则返回 -1(调用方应回退到默认逻辑)
     */
    public static int getSpeedValue(ItemStack stack, int count) {
        UpgradeDefinition def = findFor(stack);
        if (def == null || def.type != UpgradeType.SPEED) return -1;
        if (count <= 0) return 20;
        int idx = Math.min(count, def.values.length) - 1;
        if (idx < 0) return 20;
        long val = def.values[idx];
        return (int) Math.max(val, 1);
    }
}
