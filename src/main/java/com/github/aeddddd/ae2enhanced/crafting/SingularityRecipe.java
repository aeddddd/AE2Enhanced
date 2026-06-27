package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑洞生成仪式配方.
 * 以目标方块为中心,扫描周围 5×5×5 区域内的物品实体,累加数量后匹配输入.
 * 输入支持三类：丢在世界中的物品、右键手持物品、右键目标方块.
 * 输出为微型奇点方块,可配置存在时间.
 */
public class SingularityRecipe {

    private final String id;
    private final List<ItemStack> droppedInputs;
    private final ItemStack heldItem;
    private final Block targetBlock;
    private final int lifetimeTicks;

    /**
     * 完整构造函数.
     *
     * @param id             配方唯一标识
     * @param droppedInputs  丢在世界中的物品输入
     * @param heldItem       右键手持物品(可为 null/Empty,表示不检查)
     * @param targetBlock    右键目标方块(可为 null,表示不检查)
     * @param lifetimeTicks  微型奇点存在时间(tick),<=0 使用默认值 6000
     */
    public SingularityRecipe(String id, List<ItemStack> droppedInputs,
                             ItemStack heldItem, Block targetBlock, int lifetimeTicks) {
        this.id = id;
        this.droppedInputs = droppedInputs != null ? droppedInputs : Collections.emptyList();
        this.heldItem = heldItem != null ? heldItem : ItemStack.EMPTY;
        this.targetBlock = targetBlock;
        this.lifetimeTicks = lifetimeTicks > 0 ? lifetimeTicks : TileMicroSingularity.DEFAULT_LIFE_TICKS;
    }

    /**
     * 简化构造函数(向后兼容)：仅支持 droppedInputs,其余使用默认值.
     */
    public SingularityRecipe(String id, List<ItemStack> droppedInputs) {
        this(id, droppedInputs, ItemStack.EMPTY, null, TileMicroSingularity.DEFAULT_LIFE_TICKS);
    }

    public String getId() {
        return id;
    }

    public List<ItemStack> getInputs() {
        return droppedInputs;
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    public Block getTargetBlock() {
        return targetBlock;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    private static String stackKey(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return stack.getItem().getRegistryName() + "#" + stack.getMetadata();
    }

    /**
     * 检测是否匹配：先检查目标方块和手持物品,再检查 droppedInputs.
     */
    public boolean matches(World world, BlockPos center, ItemStack held) {
        // 检查目标方块
        if (targetBlock != null) {
            Block actualBlock = world.getBlockState(center).getBlock();
            if (actualBlock != targetBlock) {
                return false;
            }
        }

        // 检查手持物品(忽略数量,只要类型匹配且数量>=1)
        if (!heldItem.isEmpty()) {
            if (held == null || held.isEmpty()) {
                return false;
            }
            if (held.getItem() != heldItem.getItem()) {
                return false;
            }
            if (held.getMetadata() != heldItem.getMetadata()) {
                return false;
            }
        }

        // 检查 droppedInputs
        AxisAlignedBB area = new AxisAlignedBB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3
        );
        Map<String, Integer> found = new HashMap<>();
        for (EntityItem entityItem : world.getEntitiesWithinAABB(EntityItem.class, area)) {
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;
            found.merge(stackKey(stack), stack.getCount(), Integer::sum);
        }
        for (ItemStack need : droppedInputs) {
            if (found.getOrDefault(stackKey(need), 0) < need.getCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行仪式：消耗区域内材料,在中心位置生成微型奇点方块.
     * 如果配方指定了 heldItem,则 held 参数中的物品也应被消耗.
     */
    public void craft(World world, BlockPos center, ItemStack held) {
        // 消耗 droppedInputs
        AxisAlignedBB area = new AxisAlignedBB(
                center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 3, center.getY() + 3, center.getZ() + 3
        );
        Map<String, Integer> remaining = new HashMap<>();
        for (ItemStack need : droppedInputs) {
            remaining.put(stackKey(need), need.getCount());
        }
        for (EntityItem entityItem : world.getEntitiesWithinAABB(EntityItem.class, area)) {
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;
            String key = stackKey(stack);
            int needed = remaining.getOrDefault(key, 0);
            if (needed > 0) {
                int consume = Math.min(needed, stack.getCount());
                stack.shrink(consume);
                remaining.put(key, needed - consume);
                if (stack.isEmpty()) {
                    entityItem.setDead();
                }
            }
        }

        // 生成微型奇点方块
        world.setBlockState(center, com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry.MICRO_SINGULARITY.getDefaultState());
        TileEntity te = world.getTileEntity(center);
        if (te instanceof TileMicroSingularity) {
            ((TileMicroSingularity) te).setLifetimeTicks(lifetimeTicks);
        }
    }
}
