package com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemHandlerUpgradeAdapter;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;

import appeng.api.implementations.tiles.ISegmentedInventory;
import appeng.tile.AEBaseTile;
import appeng.util.SettingsFrom;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 处理 AE2 TileEntity 的配置复制粘贴.
 * 配置与升级分离：paste 先应用升级,再用剩余 NBT 应用配置.
 */
public class AE2TileHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        return target instanceof AEBaseTile;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        AEBaseTile tile = (AEBaseTile) target;
        NBTTagCompound output = tile.downloadSettings(SettingsFrom.MEMORY_CARD);
        if (output == null) {
            output = new NBTTagCompound();
        }

        try {
            if (tile instanceof ISegmentedInventory) {
                IItemHandler upgrades = ((ISegmentedInventory) tile).getInventoryByName("upgrades");
                if (upgrades != null && upgrades.getSlots() > 0) {
                    NBTTagList upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(
                            new ItemHandlerUpgradeAdapter(upgrades));
                    if (!upgradeList.isEmpty()) {
                        output.setTag("ae2e:upgrades", upgradeList);
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy upgrades for {}", tile.getClass().getName(), e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        AEBaseTile tile = (AEBaseTile) target;

        // 1. 先处理升级(配置粘贴不应覆盖升级槽)
        if (data.hasKey("ae2e:upgrades") && tile instanceof ISegmentedInventory) {
            NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
            IItemHandler upgrades = ((ISegmentedInventory) tile).getInventoryByName("upgrades");
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
        tile.uploadSettings(SettingsFrom.MEMORY_CARD, settings, player);

        return PasteResult.SUCCESS;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof AEBaseTile) {
            return ((AEBaseTile) target).getClass().getSimpleName();
        }
        return target.getClass().getSimpleName();
    }
}
