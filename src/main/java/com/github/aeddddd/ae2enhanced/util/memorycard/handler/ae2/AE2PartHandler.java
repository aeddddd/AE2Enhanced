package com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemHandlerUpgradeAdapter;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;

import appeng.helpers.IPriorityHost;
import appeng.api.parts.PartItemStack;
import appeng.parts.AEBasePart;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.SettingsFrom;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalBusBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 处理 AE2 Part 的配置复制粘贴.
 * 不依赖反射调用 AEBasePart.downloadSettings,直接复制其基础逻辑并扩展本 mod Part 的自定义状态.
 */
public class AE2PartHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        return target instanceof AEBasePart;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        AEBasePart part = (AEBasePart) target;
        NBTTagCompound output = new NBTTagCompound();

        // 1. 基础配置：IConfigManager
        if (part.getConfigManager() != null) {
            part.getConfigManager().writeToNBT(output);
        }

        // 2. 基础配置：IPriorityHost
        if (part instanceof IPriorityHost) {
            output.setInteger("priority", ((IPriorityHost) part).getPriority());
        }

        // 3. 基础配置：config inventory
        IItemHandler configInv = part.getInventoryByName("config");
        if (configInv instanceof AppEngInternalAEInventory) {
            ((AppEngInternalAEInventory) configInv).writeToNBT(output, "config");
        }

        // 4. 本 mod Part 的自定义状态
        if (part instanceof PartStockingBus) {
            PartStockingBus stocking = (PartStockingBus) part;
            output.setInteger("stockingMode", stocking.getMode().ordinal());
            for (int i = 0; i < 9; i++) {
                output.setLong("targetAmount_" + i, stocking.getTargetAmount(i));
            }
        }
        if (part instanceof PartUniversalBusBase) {
            PartUniversalBusBase bus = (PartUniversalBusBase) part;
            output.setInteger("busMode", bus.getBusMode().ordinal());
            output.setInteger("roundRobinIndex", bus.getRoundRobinIndex());
        }

        // 5. 额外复制升级槽
        try {
            IItemHandler upgrades = part.getInventoryByName("upgrades");
            if (upgrades != null && upgrades.getSlots() > 0) {
                NBTTagList upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(
                        new ItemHandlerUpgradeAdapter(upgrades));
                if (!upgradeList.isEmpty()) {
                    output.setTag("ae2e:upgrades", upgradeList);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy upgrades for {}", part.getClass().getName(), e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        AEBasePart part = (AEBasePart) target;

        // 1. 先处理升级
        if (data.hasKey("ae2e:upgrades")) {
            NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
            IItemHandler upgrades = part.getInventoryByName("upgrades");
            if (upgrades != null) {
                List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgradeList);
                PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(
                        new ItemHandlerUpgradeAdapter(upgrades), needed, player);
                if (result != PasteResult.SUCCESS) return result;
            }
        }

        // 2. 用不含升级的 NBT 应用配置
        NBTTagCompound settings = data.copy();
        settings.removeTag("ae2e:upgrades");
        part.uploadSettings(SettingsFrom.MEMORY_CARD, settings, player);

        return PasteResult.SUCCESS;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof AEBasePart) {
            return ((AEBasePart) target).getItemStack(PartItemStack.NETWORK).getDisplayName();
        }
        return target.getClass().getSimpleName();
    }
}
