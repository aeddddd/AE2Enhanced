package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.tile.inventory.AppEngInternalInventory;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 样板列表管理.
 *
 * <p>维护 patterns 库存与 {@link ICraftingPatternDetails} 集合的同步
 * （含 {@link ItemSmartPattern} 智能样板展开）、配方优先级、向 AE2 CPU 提供合成选项。</p>
 */
class CraftingPatternList {

    private Set<ICraftingPatternDetails> craftingList = null;
    private int priority = 0;

    boolean isInitialized() {
        return this.craftingList != null;
    }

    boolean contains(ICraftingPatternDetails details) {
        return this.craftingList != null && this.craftingList.contains(details);
    }

    int getPriority() {
        return this.priority;
    }

    void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * 向 AE2 CPU 注册全部配方（每个配方只注册一次；CPU 会对同一 pattern
     * 多次调用 pushPattern 实现并行）。
     */
    void provideCrafting(ICraftingProviderHelper craftingTracker, ICentralInterfaceHost host,
                         boolean gridActive, boolean hasBindings) {
        if (gridActive && this.craftingList != null && hasBindings) {
            for (ICraftingPatternDetails details : this.craftingList) {
                details.setPriority(this.priority);
                craftingTracker.addCraftingOption(host, details);
            }
        }
    }

    /**
     * 同步 patterns 库存与配方集合；发生增删时调用 {@code changeNotifier}
     * （由调用方负责向网格发送 MENetworkCraftingPatternChange）。
     *
     * <p>注意：网络未就绪时配方集合仍完成重建，等网络恢复后
     * CraftingGridCache 的 provideCrafting 扫描即可发现配方。</p>
     */
    void refresh(AppEngInternalInventory patterns, World world, Runnable changeNotifier) {
        boolean removed = false;
        boolean newPattern = false;

        if (this.craftingList == null) {
            this.craftingList = new HashSet<>();
        }

        boolean[] accountedFor = new boolean[patterns.getSlots()];

        Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
        while (i.hasNext()) {
            ICraftingPatternDetails details = i.next();
            boolean found = false;
            for (int x = 0; x < accountedFor.length; x++) {
                ItemStack stackInSlot = patterns.getStackInSlot(x);
                if (details.getPattern() == stackInSlot) {
                    found = true;
                    accountedFor[x] = true;
                    break;
                }
                // SmartPatternSubDetails: match by parent ItemSmartPattern
                if (details instanceof SmartPatternSubDetails) {
                    ItemStack parent = ((SmartPatternSubDetails) details).getPattern();
                    if (parent == stackInSlot) {
                        found = true;
                        accountedFor[x] = true;
                        break;
                    }
                }
            }
            if (!found) {
                removed = true;
                i.remove();
            }
        }

        for (int x = 0; x < accountedFor.length; x++) {
            if (!accountedFor[x]) {
                newPattern = true;
                addToCraftingList(patterns.getStackInSlot(x), world);
            }
        }

        if (newPattern || removed) {
            changeNotifier.run();
        }
    }

    private void addToCraftingList(ItemStack stack, World world) {
        if (stack.isEmpty()) return;
        // 智能样板展开
        if (stack.getItem() instanceof ItemSmartPattern) {
            List<SmartPatternSubDetails> subs = ItemSmartPattern.expandPatterns(stack, world);
            if (subs != null) {
                this.craftingList.addAll(subs);
            }
            return;
        }
        if (!(stack.getItem() instanceof ICraftingPatternItem)) return;
        ICraftingPatternItem patternItem = (ICraftingPatternItem) stack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(stack, world);
        if (details != null) {
            this.craftingList.add(details);
        }
    }
}
