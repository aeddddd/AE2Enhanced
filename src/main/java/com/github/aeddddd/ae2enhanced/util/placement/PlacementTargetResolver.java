package com.github.aeddddd.ae2enhanced.util.placement;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartItemStack;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.items.parts.ItemPart;
import appeng.items.parts.PartType;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 解析当前应该放置的目标物品。
 *
 * 优先级：
 * 1. 副手物品（如果是方块/线缆/Part）。
 * 2. 当前径向预设。
 * 3. 批量模式下被点击的方块（建筑手杖模式：对着相同方块铺设）。
 */
public final class PlacementTargetResolver {

    private PlacementTargetResolver() {}

    /**
     * 解析单格/线缆放置的目标物品。
     *
     * @param player     玩家
     * @param config     工具配置
     * @param world      世界
     * @param clickedPos 被点击的方块位置
     * @return 目标物品，无则 EMPTY
     */
    public static ItemStack resolveSingleOrCable(EntityPlayer player, PlacementConfig config,
                                                  World world, BlockPos clickedPos) {
        // 1. 副手优先
        ItemStack off = player.getHeldItemOffhand();
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
     * @param world      世界
     * @param clickedPos 被点击的方块位置
     * @return 目标物品，无则 EMPTY
     */
    public static ItemStack resolveBulk(EntityPlayer player, World world, BlockPos clickedPos) {
        // 1. 副手优先
        ItemStack off = player.getHeldItemOffhand();
        if (off.getItem() instanceof ItemBlock) {
            return off.copy();
        }

        // 2. 被点击方块本身
        IBlockState state = world.getBlockState(clickedPos);
        Block block = state.getBlock();
        if (block.isAir(state, world, clickedPos)) return ItemStack.EMPTY;
        ItemStack pickStack = block.getItem(world, clickedPos, state);
        if (!pickStack.isEmpty() && pickStack.getItem() instanceof ItemBlock) {
            return pickStack;
        }

        return ItemStack.EMPTY;
    }

    /**
     * 从世界中拾取一个代表性的物品栈。
     * 对于 AE2 Part/线缆方块，优先从 IPartHost 获取中心 Part 的拾取栈，
     * 以避免 Block.getItem 返回“AE Cable and/or Bus”等不具体的物品。
     */
    public static ItemStack pickRepresentativeStack(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;
            IPart center = host.getPart(AEPartLocation.INTERNAL);
            if (center != null) {
                ItemStack pick = center.getItemStack(PartItemStack.PICK);
                if (!pick.isEmpty()) {
                    return pick;
                }
            }
        }

        IBlockState state = world.getBlockState(pos);
        ItemStack pick = state.getBlock().getItem(world, pos, state);
        return pick != null ? pick : ItemStack.EMPTY;
    }

    /**
     * 判断物品是否可放置（方块、AE2 Part、Facade）。
     */
    public static boolean isPlaceable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemBlock
                || stack.getItem() instanceof appeng.api.parts.IPartItem
                || stack.getItem() instanceof appeng.facade.IFacadeItem;
    }

    /**
     * 判断物品是否为 AE2 线缆。
     */
    public static boolean isCable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof ItemPart)) return false;
        PartType type = ((ItemPart) stack.getItem()).getTypeByStack(stack);
        return type != null && type.isCable();
    }

    /**
     * 获取线缆的基础类型（颜色视为 TRANSPARENT），用于忽略颜色进行比较。
     */
    public static PartType getCablePartType(ItemStack cable) {
        if (!isCable(cable)) return null;
        return ((ItemPart) cable.getItem()).getTypeByStack(cable);
    }

    /**
     * 判断两种线缆是否为同一类型（忽略颜色）。
     */
    public static boolean isSameCableType(ItemStack a, ItemStack b) {
        PartType typeA = getCablePartType(a);
        PartType typeB = getCablePartType(b);
        return typeA != null && typeA == typeB;
    }

    /**
     * 在 AE 网络中查找任意一种同类型线缆（忽略颜色）。
     *
     * @param monitor   网络物品存储
     * @param baseCable 参考线缆物品
     * @return 找到的网络栈，无则 null
     */
    @Nullable
    public static IAEItemStack findCableOfType(IMEMonitor<IAEItemStack> monitor, ItemStack baseCable) {
        if (!isCable(baseCable)) return null;
        PartType targetType = getCablePartType(baseCable);
        if (targetType == null) return null;

        for (IAEItemStack stack : monitor.getStorageList()) {
            ItemStack netStack = stack.getDefinition();
            if (isSameCableType(baseCable, netStack)) {
                return stack.copy();
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
        if (!isCable(baseCable)) return ItemStack.EMPTY;
        ItemPart itemPart = (ItemPart) baseCable.getItem();
        PartType type = itemPart.getTypeByStack(baseCable);
        if (type == null) return ItemStack.EMPTY;
        // 通过 PartType + AEColor 创建对应 meta 的 stack
        int damage = itemPart.getDamageByType(type);
        if (damage < 0) return ItemStack.EMPTY;
        // 基础 damage 对应 TRANSPARENT，需要加上颜色偏移
        int colorOffset = color.ordinal();
        return new ItemStack(itemPart, 1, damage + colorOffset);
    }

    /**
     * 获取线缆当前颜色。
     */
    public static AEColor getCableColor(ItemStack cable) {
        if (!isCable(cable)) return AEColor.TRANSPARENT;
        ItemPart itemPart = (ItemPart) cable.getItem();
        PartType type = itemPart.getTypeByStack(cable);
        if (type == null) return AEColor.TRANSPARENT;
        int baseDamage = itemPart.getDamageByType(type);
        int colorIndex = cable.getMetadata() - baseDamage;
        if (colorIndex < 0 || colorIndex >= AEColor.values().length) {
            return AEColor.TRANSPARENT;
        }
        return AEColor.values()[colorIndex];
    }
}
