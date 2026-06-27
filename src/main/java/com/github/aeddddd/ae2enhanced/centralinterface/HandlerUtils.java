package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Handler 层通用工具类.
 *
 * <p>提供安全的反射初始化、通用物品 IO、配方缓存 key 等，减少各 handler 的重复代码。</p>
 */
public final class HandlerUtils {

    private HandlerUtils() {
    }

    /**
     * 安全的反射初始化模板.
     *
     * <p>把 {@code init} 中的异常捕获并记录，返回 false 表示该 handler 应被禁用，
     * 而不是直接抛 RuntimeException 把服务端崩掉。</p>
     *
     * @param handlerName handler 名称（用于日志）
     * @param init        反射初始化逻辑
     * @return true 表示初始化成功
     */
    public static boolean safeInit(String handlerName, Runnable init) {
        try {
            init.run();
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] {} reflection init failed, handler disabled: {}", handlerName, e.toString());
            return false;
        }
    }

    /**
     * 带维度的方块位置 + blockId 配方缓存 key.
     */
    public static final class RecipeCacheKey {
        public final int dimension;
        public final BlockPos pos;
        public final String blockId;

        public RecipeCacheKey(int dimension, BlockPos pos, String blockId) {
            this.dimension = dimension;
            this.pos = pos;
            this.blockId = blockId != null ? blockId : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RecipeCacheKey)) return false;
            RecipeCacheKey that = (RecipeCacheKey) o;
            return dimension == that.dimension &&
                    pos.equals(that.pos) &&
                    blockId.equals(that.blockId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, pos, blockId);
        }

        @Override
        public String toString() {
            return "RecipeCacheKey{dim=" + dimension + ", pos=" + pos + ", blockId='" + blockId + "'}";
        }
    }

    /**
     * 把物品列表推入目标 TileEntity 的 IItemHandler（所有面依次尝试）。
     *
     * @param target 目标 TileEntity
     * @param stacks 要推送的物品
     * @return 剩余未能推入的物品；全部成功则返回空列表
     */
    public static List<ItemStack> pushItemsToTile(TileEntity target, List<ItemStack> stacks) {
        if (target == null || stacks == null || stacks.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = pushItemToTile(target, stack.copy());
            if (!remaining.isEmpty()) {
                leftovers.add(remaining);
            }
        }
        return leftovers;
    }

    /**
     * 把单个物品推入目标 TileEntity 的 IItemHandler。
     *
     * @param target 目标 TileEntity
     * @param stack  要推送的物品（会被修改）
     * @return 剩余未能推入的物品
     */
    public static ItemStack pushItemToTile(TileEntity target, ItemStack stack) {
        if (target == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    /**
     * 从目标 TileEntity 的所有 IItemHandler 中抽取全部可抽取物品。
     *
     * @param target 目标 TileEntity
     * @return 抽取到的物品列表
     */
    public static List<ItemStack> extractAllItems(TileEntity target) {
        if (target == null) {
            return Collections.emptyList();
        }
        List<ItemStack> result = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                while (true) {
                    ItemStack current = handler.getStackInSlot(slot);
                    if (current.isEmpty()) break;
                    ItemStack extracted = handler.extractItem(slot, current.getCount(), false);
                    if (extracted.isEmpty()) break;
                    result.add(extracted);
                }
            }
        }
        return result;
    }

    /**
     * 判断物品是否在给定的输入快照中。
     */
    public static boolean isInputMaterial(ItemStack stack, @Nullable List<ItemStack> inputs) {
        if (stack.isEmpty() || inputs == null || inputs.isEmpty()) {
            return false;
        }
        for (ItemStack input : inputs) {
            if (ItemStack.areItemsEqual(stack, input) && ItemStack.areItemStackTagsEqual(stack, input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 宽松匹配：item + metadata 相同；若 expected 有 NBT 则要求 NBT 也相同。
     */
    public static boolean matchesLoosely(ItemStack actual, ItemStack expected) {
        if (!ItemStack.areItemsEqual(actual, expected)) {
            return false;
        }
        if (!expected.hasTagCompound()) {
            return true;
        }
        return ItemStack.areItemStackTagsEqual(actual, expected);
    }

    /**
     * 创建 AE 动作源（简单包装，避免 handler 中重复 new）。
     */
    @Nonnull
    public static appeng.me.helpers.MachineSource machineSource(ICentralInterfaceHost host) {
        return new appeng.me.helpers.MachineSource(host);
    }
}
