package com.github.aeddddd.ae2enhanced.memorycard.upgrade;

import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.COMPONENT_SUPPORTS;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.RECALCULATE_UPGRADES;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.UPGRADES_FIELD;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.UPGRADE_GET_MAX;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.UPGRADE_UTILS_GET_STACK;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.UPGRADE_VALUES;

import java.util.Map;

import net.minecraft.world.item.ItemStack;

/**
 * 将 Mekanism 的 TileComponentUpgrade 适配为 IUpgradeProvider(目标版本: Mekanism 1.20.1, 10.4.x).
 *
 * <p>Mekanism 的升级系统是基于 mekanism.api.Upgrade 枚举的 EnumMap(类型 → 数量),
 * 槽位索引映射到 Upgrade 枚举常量数组的索引.</p>
 *
 * <p>注意: 1.20.1 的 TileComponentUpgrade#removeUpgrade 会把拆下的升级塞进机器的
 * 升级输出槽(而非返还玩家),直接使用会与 applyUpgrades 的"旧升级返还玩家"流程造成物品复制.
 * 因此本适配器直接读写组件内部的 upgrades EnumMap,并手动调用
 * IUpgradeTile#recalculateUpgrades 触发重算,与原生 addUpgrades 的效果一致.</p>
 */
public class MekanismUpgradeProvider implements IUpgradeProvider {

    private final Object tile;
    private final Object component;
    /** Upgrade 类型 → 代表物品(用于从物品反查类型;applyUpgrades 按列表顺序写槽位,槽位索引与类型不保证对应) */
    private final ItemStack[] typeStacks = new ItemStack[getSlotCount()];

    public MekanismUpgradeProvider(Object tile, Object component) {
        this.tile = tile;
        this.component = component;
    }

    @Override
    public int getSlotCount() {
        return UPGRADE_VALUES != null ? UPGRADE_VALUES.length : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (UPGRADE_VALUES == null || slot < 0 || slot >= UPGRADE_VALUES.length) {
            return ItemStack.EMPTY;
        }
        try {
            Object type = UPGRADE_VALUES[slot];
            int count = getUpgradesMap().getOrDefault(type, 0);
            if (count <= 0) {
                return ItemStack.EMPTY;
            }
            return ((ItemStack) UPGRADE_UTILS_GET_STACK.invoke(null, type, count)).copy();
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        // 清空走 clearSlots;此处仅处理安装,安装时按物品反查 Upgrade 类型(slot 索引无意义)
        if (stack.isEmpty()) {
            return;
        }
        try {
            Object type = findTypeForStack(stack);
            if (type == null) {
                return;
            }
            // 目标机器不支持的升级类型直接跳过(正常同机型粘贴不会发生)
            if (!(Boolean) COMPONENT_SUPPORTS.invoke(component, type)) {
                return;
            }
            int target = Math.min(stack.getCount(), (Integer) UPGRADE_GET_MAX.invoke(type));
            getUpgradesMap().put(type, target);
            RECALCULATE_UPGRADES.invoke(tile, type);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void clearSlots() {
        try {
            // 先快照 keySet,避免边遍历边修改
            for (Object type : getUpgradesMap().keySet().toArray()) {
                getUpgradesMap().remove(type);
                RECALCULATE_UPGRADES.invoke(tile, type);
            }
        } catch (Exception ignored) {
        }
    }

    private Object findTypeForStack(ItemStack stack) throws Exception {
        for (int i = 0; i < UPGRADE_VALUES.length; i++) {
            if (typeStacks[i] == null) {
                typeStacks[i] = ((ItemStack) UPGRADE_UTILS_GET_STACK.invoke(null, UPGRADE_VALUES[i], 1)).copy();
            }
            if (ItemStack.isSameItem(typeStacks[i], stack)) {
                return UPGRADE_VALUES[i];
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Integer> getUpgradesMap() throws Exception {
        return (Map<Object, Integer>) UPGRADES_FIELD.get(component);
    }
}
