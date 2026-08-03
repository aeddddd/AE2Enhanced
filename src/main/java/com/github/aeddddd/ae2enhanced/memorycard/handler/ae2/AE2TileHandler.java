package com.github.aeddddd.ae2enhanced.memorycard.handler.ae2;

import java.util.List;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.items.tools.MemoryCardItem;
import appeng.items.tools.SettingsCategory;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.memorycard.upgrade.AE2UpgradeInventoryAdapter;

/**
 * 处理 AE2 方块设备(AEBaseBlockEntity)的配置复制粘贴.
 * 配置与升级分离:paste 先应用升级,再导入剩余配置(同 AE2PartHandler).
 */
public class AE2TileHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        return target instanceof AEBaseBlockEntity;
    }

    @Override
    public CompoundTag copy(Object target) {
        AEBaseBlockEntity blockEntity = (AEBaseBlockEntity) target;
        CompoundTag output = new CompoundTag();

        // 1. 基础配置:设置项 / 优先级 / 配置库存(AE2 官方通用导出)
        MemoryCardItem.exportGenericSettings(blockEntity, output);
        // 官方导出会写入 "upgrades"(itemId->count),升级卡由本类按槽位单独处理,避免重复
        output.remove("upgrades");

        // 2. 升级槽(按槽位序列化,含 count)
        try {
            if (blockEntity instanceof IUpgradeableObject upgradeable) {
                ListTag upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(
                        new AE2UpgradeInventoryAdapter(upgradeable.getUpgrades()));
                if (!upgradeList.isEmpty()) {
                    output.put("ae2e:upgrades", upgradeList);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy upgrades for {}", blockEntity.getClass().getName(), e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, CompoundTag data, Player player) {
        AEBaseBlockEntity blockEntity = (AEBaseBlockEntity) target;

        // 1. 先处理升级(配置粘贴不应覆盖升级槽;缺少时向绑定网络发起合成请求)
        if (data.contains("ae2e:upgrades") && blockEntity instanceof IUpgradeableObject upgradeable) {
            ListTag upgradeList = data.getList("ae2e:upgrades", Tag.TAG_COMPOUND);
            List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgradeList);
            PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(
                    new AE2UpgradeInventoryAdapter(upgradeable.getUpgrades()), needed, player);
            if (result != PasteResult.SUCCESS) {
                return result;
            }
        }

        // 2. 用不含升级的 NBT 应用配置(player=null 时官方导入跳过升级槽)
        CompoundTag settings = data.copy();
        settings.remove("ae2e:upgrades");
        settings.remove("upgrades");
        Set<SettingsCategory> imported = MemoryCardItem.importGenericSettings(blockEntity, settings, null);
        if (imported.isEmpty()) {
            return PasteResult.INVALID_MACHINE;
        }

        return PasteResult.SUCCESS;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof AEBaseBlockEntity blockEntity) {
            return blockEntity.getBlockState().getBlock().getName().getString();
        }
        return target.getClass().getSimpleName();
    }
}
