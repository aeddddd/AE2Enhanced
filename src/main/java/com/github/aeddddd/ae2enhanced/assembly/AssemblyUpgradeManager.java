package com.github.aeddddd.ae2enhanced.assembly;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModItems;

/**
 * 负责读取装配枢纽的升级卡并计算有效属性。
 */
public class AssemblyUpgradeManager {

    private AssemblyPatternManager patternManager;

    public void setPatternManager(AssemblyPatternManager patternManager) {
        this.patternManager = patternManager;
    }

    private ItemStackHandler getItemHandler() {
        return patternManager != null ? patternManager.getItemHandler() : null;
    }

    /**
     * 当前并行上限。0 张并行升级卡 = 64，每多 1 张 ×32，5 张 = Long.MAX_VALUE。
     */
    public long getParallelCap() {
        ItemStackHandler handler = getItemHandler();
        if (handler == null) {
            return 64;
        }
        ItemStack stack = handler.getStackInSlot(0);
        if (stack.isEmpty()) {
            return 64;
        }
        if (stack.getItem() == ModItems.ASSEMBLY_PARALLEL_UPGRADE.get()) {
            int count = stack.getCount();
            if (count >= 5) {
                return Long.MAX_VALUE;
            }
            long cap = 64;
            for (int i = 0; i < count; i++) {
                cap = cap * 32;
                if (cap > 67108864) {
                    return 67108864;
                }
            }
            return cap;
        }
        return 64;
    }

    /**
     * 当前合成延迟 tick 数。0 张速度升级卡 = 20，每张减半，最低 1 tick。
     */
    public int getCraftingTicks() {
        ItemStackHandler handler = getItemHandler();
        if (handler == null) {
            return 20;
        }
        ItemStack stack = handler.getStackInSlot(1);
        if (stack.isEmpty()) {
            return 20;
        }
        if (stack.getItem() == ModItems.ASSEMBLY_SPEED_UPGRADE.get()) {
            int ticks = 20;
            int count = stack.getCount();
            for (int i = 0; i < count && ticks > 1; i++) {
                ticks = Math.max(ticks / 2, 1);
            }
            return ticks;
        }
        return 20;
    }

    /**
     * 当前可用样板页数。基础 5 页，每张扩容升级卡 +10 页，10 张即可达到上限 100 页。
     */
    public int getPatternPages() {
        ItemStackHandler handler = getItemHandler();
        int count = 0;
        if (handler != null) {
            ItemStack stack = handler.getStackInSlot(2);
            if (!stack.isEmpty() && stack.getItem() == ModItems.ASSEMBLY_CAPACITY_UPGRADE.get()) {
                count = stack.getCount();
            }
        }
        int pages = AssemblyControllerBlockEntity.PATTERN_PAGES_BASE
                + count * AssemblyControllerBlockEntity.PATTERN_PAGES_PER_CAPACITY;
        return Math.min(pages, AssemblyControllerBlockEntity.PATTERN_PAGES_MAX);
    }

    /**
     * 检查是否安装了样板自动上传模块升级（槽位 4）。
     */
    public boolean hasAutoUploadUpgrade() {
        ItemStackHandler handler = getItemHandler();
        if (handler == null) {
            return false;
        }
        ItemStack stack = handler.getStackInSlot(4);
        return !stack.isEmpty() && stack.getItem() == ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE.get();
    }
}
