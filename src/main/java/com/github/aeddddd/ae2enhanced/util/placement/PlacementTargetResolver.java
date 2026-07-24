package com.github.aeddddd.ae2enhanced.util.placement;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.implementations.items.IFacadeItem;
import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.ColoredItemDefinition;
import appeng.items.parts.ColoredPartItem;

/**
 * 解析当前应该放置的目标物品。
 *
 * 优先级：
 * 1. 副手物品（如果是方块/线缆/Part）。
 * 2. 当前径向预设。
 * 3. 批量模式下被点击的方块（建筑手杖模式：对着相同方块铺设）。
 *
 * <p>1.20.1 移植说明：AE2 15.x 的 Part 系统已重构，1.12 的 ItemPart/PartType 内部枚举
 * 不复存在。线缆识别改用 {@link IPartItem#getPartClass()} 与
 * {@link ICablePart}（五种线缆 Part 均实现该接口），颜色识别改用
 * {@link ColoredPartItem#getColor()}，换色放置通过 {@link AEParts} 中的
 * 五个 {@link ColoredItemDefinition} 按 Part 类匹配生成。</p>
 */
public final class PlacementTargetResolver {

    private PlacementTargetResolver() {}

    /**
     * 解析单格/线缆放置的目标物品。
     *
     * @param player     玩家
     * @param config     工具配置
     * @param level      世界
     * @param clickedPos 被点击的方块位置
     * @return 目标物品，无则 EMPTY
     */
    public static ItemStack resolveSingleOrCable(Player player, PlacementConfig config,
            Level level, BlockPos clickedPos) {
        // 1. 副手优先
        ItemStack off = player.getOffhandItem();
        if (isPlaceable(off)) {
            return off.copy();
        }

        // 2. 当前预设
        ItemStack preset = config.getSelectedStack();
        if (!preset.isEmpty()) {
            return preset.copy();
        }

        // 3. 批量模式下的被点击方块（如果玩家配置了点击同材质铺设）
        // 单格模式不走这条，返回空
        return ItemStack.EMPTY;
    }

    /**
     * 解析批量放置的目标物品（Construction Wand 规则）。
     *
     * 优先级：
     * 1. 副手物品（Construction Wand：Having blocks in your offhand will place them instead）。
     * 2. 被点击方块本身（同类型向外延伸）。
     * 注意：批量模式不使用径向预设。
     *
     * @param player     玩家
     * @param level      世界
     * @param clickedPos 被点击的方块位置
     * @return 目标物品，无则 EMPTY
     */
    public static ItemStack resolveBulk(Player player, Level level, BlockPos clickedPos) {
        // 1. 副手优先
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof BlockItem) {
            return off.copy();
        }

        // 2. 被点击方块本身
        BlockState state = level.getBlockState(clickedPos);
        Block block = state.getBlock();
        if (state.isAir()) return ItemStack.EMPTY;
        ItemStack pickStack = block.getCloneItemStack(level, clickedPos, state);
        if (!pickStack.isEmpty() && pickStack.getItem() instanceof BlockItem) {
            return pickStack;
        }

        return ItemStack.EMPTY;
    }

    /**
     * 从世界中拾取一个代表性的物品栈。
     * 对于 AE2 Part/线缆方块，优先从 IPartHost 获取中心 Part 的物品栈，
     * 以避免 getCloneItemStack 返回不具体的物品。
     */
    public static ItemStack pickRepresentativeStack(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IPartHost host) {
            IPart center = host.getPart(null);
            if (center != null) {
                ItemStack pick = new ItemStack(center.getPartItem());
                if (!pick.isEmpty()) {
                    return pick;
                }
            }
        }

        BlockState state = level.getBlockState(pos);
        ItemStack pick = state.getBlock().getCloneItemStack(level, pos, state);
        return pick != null ? pick : ItemStack.EMPTY;
    }

    /**
     * 判断物品是否可放置（方块、AE2 Part、Facade）。
     */
    public static boolean isPlaceable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem
                || stack.getItem() instanceof IPartItem
                || stack.getItem() instanceof IFacadeItem;
    }

    /**
     * 判断物品是否为 AE2 线缆。
     */
    public static boolean isCable(ItemStack stack) {
        return getCablePartClass(stack) != null;
    }

    /**
     * 获取线缆的 Part 类（颜色无关），用于忽略颜色进行比较。
     */
    @Nullable
    public static Class<?> getCablePartClass(ItemStack cable) {
        if (cable.isEmpty() || !(cable.getItem() instanceof IPartItem<?> partItem)) {
            return null;
        }
        Class<?> partClass = partItem.getPartClass();
        return ICablePart.class.isAssignableFrom(partClass) ? partClass : null;
    }

    /**
     * 判断两种线缆是否为同一类型（忽略颜色）。
     */
    public static boolean isSameCableType(ItemStack a, ItemStack b) {
        Class<?> typeA = getCablePartClass(a);
        Class<?> typeB = getCablePartClass(b);
        return typeA != null && typeA == typeB;
    }

    /**
     * 在 AE 网络中查找任意一种同类型线缆（忽略颜色）。
     *
     * @param storage   网络物品存储
     * @param baseCable 参考线缆物品
     * @return 找到的网络键，无则 null
     */
    @Nullable
    public static AEItemKey findCableOfType(MEStorage storage, ItemStack baseCable) {
        if (!isCable(baseCable)) return null;
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                ItemStack netStack = itemKey.toStack();
                if (isSameCableType(baseCable, netStack)) {
                    return itemKey;
                }
            }
        }
        return null;
    }

    /**
     * 创建指定颜色的线缆物品。
     *
     * @param baseCable 原始线缆物品（用于确定线缆类型）
     * @param color     目标颜色
     * @return 对应颜色的线缆 stack，失败返回 EMPTY
     */
    public static ItemStack createCableOfColor(ItemStack baseCable, AEColor color) {
        Class<?> partClass = getCablePartClass(baseCable);
        if (partClass == null) return ItemStack.EMPTY;
        for (ColoredItemDefinition<?> def : cableDefinitions()) {
            Item sample = def.item(color);
            if (sample instanceof IPartItem<?> partItem && partItem.getPartClass() == partClass) {
                return def.stack(color);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 获取线缆当前颜色。
     */
    public static AEColor getCableColor(ItemStack cable) {
        if (cable.getItem() instanceof ColoredPartItem<?> colored) {
            return colored.getColor();
        }
        return AEColor.TRANSPARENT;
    }

    /**
     * AE2 全部五种线缆定义。延迟初始化，避免过早触发 AE2 注册表加载。
     */
    private static List<ColoredItemDefinition<?>> cableDefinitions() {
        return List.of(
                AEParts.GLASS_CABLE,
                AEParts.COVERED_CABLE,
                AEParts.SMART_CABLE,
                AEParts.COVERED_DENSE_CABLE,
                AEParts.SMART_DENSE_CABLE);
    }
}
