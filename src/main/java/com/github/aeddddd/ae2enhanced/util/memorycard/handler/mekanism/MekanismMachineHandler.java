package com.github.aeddddd.ae2enhanced.util.memorycard.handler.mekanism;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.MekanismUpgradeProvider;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.aeddddd.ae2enhanced.util.memorycard.handler.mekanism.MekanismReflectionHelper.*;

/**
 * Mekanism 机器的配置复制粘贴 Handler.
 * 使用反射访问 Mekanism API,避免硬依赖.
 */
public class MekanismMachineHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) return false;
        try {
            // 延迟获取 capability(Forge @CapabilityInject 可能在 init 之后才注入)
            if (CONFIG_CARD_CAPABILITY == null) {
                Class<?> capabilitiesClass = Class.forName("mekanism.common.capabilities.Capabilities");
                CONFIG_CARD_CAPABILITY = capabilitiesClass.getField("CONFIG_CARD_CAPABILITY").get(null);
            }
            if (CONFIG_CARD_CAPABILITY == null) {
                // 回退：按类名前缀匹配
                return target.getClass().getName().startsWith("mekanism.common.tile.");
            }
            // 尝试 null facing 和所有 6 个面
            for (EnumFacing face : EnumFacing.values()) {
                if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, target, CONFIG_CARD_CAPABILITY, face)) {
                    return true;
                }
            }
            return (Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, target, CONFIG_CARD_CAPABILITY, null);
        } catch (Exception e) {
            return target.getClass().getName().startsWith("mekanism.common.tile.");
        }
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            // 1. 红石控制
            if (REDSTONE_CONTROL_CLASS.isInstance(tile)) {
                Object controlType = REDSTONE_GET_CONTROL_TYPE.invoke(tile);
                output.setInteger("controlType", ((Enum<?>) controlType).ordinal()); // enum ordinal
            }

            // 2. 侧面配置
            if (SIDE_CONFIGURATION_CLASS.isInstance(tile)) {
                Object config = SIDE_CONFIG_GET_CONFIG.invoke(tile);
                Object ejector = SIDE_CONFIG_GET_EJECTOR.invoke(tile);
                TILE_COMPONENT_CONFIG_WRITE.invoke(config, output);
                TILE_COMPONENT_EJECTOR_WRITE.invoke(ejector, output);
            }

            // 3. 特殊配置数据
            if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null)) {
                Object special = CAPABILITY_UTILS_GET_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null);
                Method getConfigurationData = SPECIAL_CONFIG_DATA_CLASS.getMethod("getConfigurationData", NBTTagCompound.class);
                NBTTagCompound specialData = (NBTTagCompound) getConfigurationData.invoke(special, new NBTTagCompound());
                if (specialData != null) {
                    output.merge(specialData);
                }

                Method getDataType = SPECIAL_CONFIG_DATA_CLASS.getMethod("getDataType");
                String dataType = (String) getDataType.invoke(special);
                output.setString("dataType", dataType);
            } else {
                // 没有特殊配置数据,使用 block registry name + fullName
                if (TILE_ENTITY_CONTAINER_BLOCK_CLASS.isInstance(tile)) {
                    String fullName = (String) CONTAINER_BLOCK_FULL_NAME.get(tile);
                    String blockName = tile.getWorld().getBlockState(tile.getPos()).getBlock().getRegistryName().toString();
                    output.setString("dataType", blockName + "." + fullName + ".name");
                }
            }

            // 4. 升级
            if (UPGRADE_TILE_CLASS.isInstance(tile)) {
                Object component = UPGRADE_TILE_GET_COMPONENT.invoke(tile);
                NBTTagCompound upgradeNbt = new NBTTagCompound();
                TILE_COMPONENT_UPGRADE_WRITE.invoke(component, upgradeNbt);
                if (!upgradeNbt.isEmpty()) {
                    output.setTag("mekanism:upgrades", upgradeNbt);

                    // 同时存储 ae2e:upgrades 格式,供 MISSING_UPGRADES 消息显示
                    NBTTagList ae2eUpgrades = new NBTTagList();
                    @SuppressWarnings("unchecked")
                    Map<Object, Integer> upgrades = (Map<Object, Integer>) UPGRADE_BUILD_MAP.invoke(null, upgradeNbt);
                    for (Map.Entry<Object, Integer> entry : upgrades.entrySet()) {
                        ItemStack upgStack = ((ItemStack) UPGRADE_GET_STACK.invoke(entry.getKey())).copy();
                        upgStack.setCount(entry.getValue());
                        ae2eUpgrades.appendTag(upgStack.writeToNBT(new NBTTagCompound()));
                    }
                    if (!ae2eUpgrades.isEmpty()) {
                        output.setTag("ae2e:upgrades", ae2eUpgrades);
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy Mekanism machine config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            // 验证 dataType
            String sourceDataType = data.getString("dataType");
            String targetDataType = getDataType(tile);
            if (!isCompatible(sourceDataType, targetDataType)) {
                return PasteResult.INVALID_MACHINE;
            }

            // 0. 处理工厂 tier 升级(需要在其他配置之前)
            PasteResult tierResult = applyTierUpgrade(tile, data, player);
            if (tierResult == PasteResult.INVALID_MACHINE || tierResult == PasteResult.FAILED) {
                return tierResult;
            }
            if (tierResult == PasteResult.MISSING_UPGRADES) {
                return tierResult;
            }
            if (tierResult == PasteResult.SUCCESS) {
                // upgrade 可能替换了 tile,重新获取
                TileEntity newTile = player.world.getTileEntity(tile.getPos());
                if (newTile != null) {
                    tile = newTile;
                }
            }

            // 1. 红石控制
            if (data.hasKey("controlType") && REDSTONE_CONTROL_CLASS.isInstance(tile)) {
                int ordinal = data.getInteger("controlType");
                if (ordinal >= 0 && ordinal < REDSTONE_CONTROL_VALUES.length) {
                    REDSTONE_SET_CONTROL_TYPE.invoke(tile, REDSTONE_CONTROL_VALUES[ordinal]);
                }
            }

            // 2. 侧面配置
            if (SIDE_CONFIGURATION_CLASS.isInstance(tile)) {
                Object config = SIDE_CONFIG_GET_CONFIG.invoke(tile);
                Object ejector = SIDE_CONFIG_GET_EJECTOR.invoke(tile);
                NBTTagCompound configData = data.copy();
                // 移除非配置键,避免误写入
                configData.removeTag("dataType");
                configData.removeTag("mekanism:upgrades");
                configData.removeTag("ae2e:upgrades");
                TILE_COMPONENT_CONFIG_READ.invoke(config, configData);
                TILE_COMPONENT_EJECTOR_READ.invoke(ejector, configData);
            }

            // 3. 特殊配置数据
            if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null)) {
                Object special = CAPABILITY_UTILS_GET_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null);
                Method setConfigurationData = SPECIAL_CONFIG_DATA_CLASS.getMethod("setConfigurationData", NBTTagCompound.class);
                NBTTagCompound specialData = data.copy();
                specialData.removeTag("dataType");
                specialData.removeTag("mekanism:upgrades");
                specialData.removeTag("ae2e:upgrades");
                specialData.removeTag("controlType");
                setConfigurationData.invoke(special, specialData);
            }

            // 4. 升级处理(通过 IUpgradeProvider 统一流程)
            if (data.hasKey("mekanism:upgrades") && UPGRADE_TILE_CLASS.isInstance(tile)) {
                Object component = UPGRADE_TILE_GET_COMPONENT.invoke(tile);
                Object[] allTypes = UPGRADE_CLASS.getEnumConstants();
                MekanismUpgradeProvider provider = new MekanismUpgradeProvider(
                        component, allTypes,
                        TILE_COMPONENT_UPGRADE_GET_UPGRADES,
                        TILE_COMPONENT_UPGRADE_ADD_UPGRADE,
                        TILE_COMPONENT_UPGRADE_REMOVE_UPGRADE,
                        UPGRADE_GET_STACK
                );

                NBTTagCompound upgradeNbt = data.getCompoundTag("mekanism:upgrades");
                @SuppressWarnings("unchecked")
                Map<Object, Integer> sourceUpgrades = (Map<Object, Integer>) UPGRADE_BUILD_MAP.invoke(null, upgradeNbt);

                List<ItemStack> needed = new ArrayList<>();
                for (Object type : allTypes) {
                    int count = sourceUpgrades.getOrDefault(type, 0);
                    if (count > 0) {
                        ItemStack stack = ((ItemStack) UPGRADE_GET_STACK.invoke(type)).copy();
                        stack.setCount(count);
                        needed.add(stack);
                    }
                }

                PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(provider, needed, player);
                if (result != PasteResult.SUCCESS) return result;
            }

            // 5. 显式处理 sorting 字段(双重保险)
            if (data.hasKey("sorting")) {
                applySorting(tile, data.getBoolean("sorting"));
            }

            // 6. 更新 tile
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(), tile.getWorld().getBlockState(tile.getPos()), tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste Mekanism machine config", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }

    /**
     * 尝试应用工厂 tier 升级.如果不是工厂或不需要升级,返回 SUCCESS.
     */
    private PasteResult applyTierUpgrade(TileEntity tile, NBTTagCompound data, EntityPlayer player) {
        if (TIER_UPGRADEABLE_CLASS == null || BASE_TIER_VALUES == null) {
            return PasteResult.SUCCESS; // 反射不可用
        }
        try {
            String sourceDataType = data.getString("dataType");
            String targetDataType = getDataType(tile);

            // 源不是工厂：不需要升级
            if (!sourceDataType.contains("Factory")) {
                return PasteResult.SUCCESS;
            }

            // 提取 tier(普通机器 tier 视为 -1)
            Object sourceTier = extractBaseTier(sourceDataType);
            int sourceOrdinal = sourceTier != null ? ((Enum<?>) sourceTier).ordinal() : -1;
            if (sourceOrdinal < 0) {
                return PasteResult.SUCCESS;
            }

            Object targetTier = extractBaseTier(targetDataType);
            int targetOrdinal = targetTier != null ? ((Enum<?>) targetTier).ordinal() : -1;

            if (sourceOrdinal <= targetOrdinal) {
                return PasteResult.SUCCESS; // 无需升级或降级
            }

            if (!TIER_UPGRADEABLE_CLASS.isInstance(tile)) {
                return PasteResult.INVALID_MACHINE;
            }

            // 逐级升级(从 targetOrdinal+1 到 sourceOrdinal)
            for (int tierOrd = targetOrdinal + 1; tierOrd <= sourceOrdinal; tierOrd++) {
                Object tier = BASE_TIER_VALUES[tierOrd];
                ItemStack installer = getTierInstaller(tierOrd);
                if (installer.isEmpty()) {
                    return PasteResult.FAILED;
                }
                if (MemoryCardUpgradeHelper.countInInventory(player, installer) < 1) {
                    List<ItemStack> missing = new ArrayList<>();
                    missing.add(installer.copy());
                    MemoryCardUpgradeHelper.NetworkPullResult result = MemoryCardUpgradeHelper.tryPullFromNetwork(player, missing);
                    if (result == MemoryCardUpgradeHelper.NetworkPullResult.FAILED
                            || MemoryCardUpgradeHelper.countInInventory(player, installer) < 1) {
                        return PasteResult.MISSING_UPGRADES;
                    }
                }
                MemoryCardUpgradeHelper.consumeFromInventory(player, installer);
                Object upgradeResult = TIER_UPGRADEABLE_UPGRADE.invoke(tile, tier);
                if (!(upgradeResult instanceof Boolean) || !(Boolean) upgradeResult) {
                    // 升级失败,返还 installer
                    if (!player.addItemStackToInventory(installer.copy())) {
                        player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, installer.copy()));
                    }
                    return PasteResult.FAILED;
                }

                // 升级后 block 被替换,重新获取 tile
                // 注意：TileEntityFactory 没有显式实现 ITierUpgradeable,因此不能检查 isInstance
                TileEntity newTile = player.world.getTileEntity(tile.getPos());
                if (newTile == null) {
                    return PasteResult.FAILED;
                }
                tile = newTile;
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to apply Mekanism tier upgrade", e);
            return PasteResult.FAILED;
        }
    }

    private Object extractBaseTier(String dataType) {
        // dataType 示例: "Basic Smelting Factory", "Elite Enriching Factory"
        String[] parts = dataType.split(" ");
        if (parts.length == 0) return null;
        String tierName = parts[0];
        for (Object tier : BASE_TIER_VALUES) {
            if (((Enum<?>) tier).name().equalsIgnoreCase(tierName)) {
                return tier;
            }
        }
        return null;
    }

    private ItemStack getTierInstaller(int meta) {
        if (meta >= 0 && meta < TIER_INSTALLER_CACHE.length && TIER_INSTALLER_CACHE[meta] != null) {
            return TIER_INSTALLER_CACHE[meta].copy();
        }
        try {
            net.minecraft.util.ResourceLocation rl = new net.minecraft.util.ResourceLocation("mekanism", "tierinstaller");
            net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(rl);
            if (item != null) {
                ItemStack stack = new ItemStack(item, 1, meta);
                if (meta >= 0 && meta < TIER_INSTALLER_CACHE.length) {
                    TIER_INSTALLER_CACHE[meta] = stack.copy();
                }
                return stack;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not get Mekanism tier installer", e);
        }
        return ItemStack.EMPTY;
    }

    private void applySorting(TileEntity tile, boolean sorting) {
        try {
            Class<?> current = tile.getClass();
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField("sorting");
                    if (field.getType() == boolean.class) {
                        field.setAccessible(true);
                        field.setBoolean(tile, sorting);
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                }
                current = current.getSuperclass();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not set sorting field on {}", tile.getClass().getName());
        }
    }

    private String getDataType(TileEntity tile) throws Exception {
        if ((Boolean) CAPABILITY_UTILS_HAS_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null)) {
            Object special = CAPABILITY_UTILS_GET_CAPABILITY.invoke(null, tile, SPECIAL_CONFIG_DATA_CAPABILITY, null);
            Method getDataType = SPECIAL_CONFIG_DATA_CLASS.getMethod("getDataType");
            return (String) getDataType.invoke(special);
        }
        if (TILE_ENTITY_CONTAINER_BLOCK_CLASS.isInstance(tile)) {
            String fullName = (String) CONTAINER_BLOCK_FULL_NAME.get(tile);
            String blockName = tile.getWorld().getBlockState(tile.getPos()).getBlock().getRegistryName().toString();
            return blockName + "." + fullName + ".name";
        }
        return tile.getClass().getName();
    }

    /**
     * 验证源机器和目标机器是否兼容.
     * 允许工厂跨 tier 粘贴(如 Basic Factory → Elite Factory).
     */
    private boolean isCompatible(String sourceType, String targetType) {
        if (sourceType.equals(targetType)) return true;

        // 都是工厂：比较 recipe type
        if (sourceType.contains("Factory") && targetType.contains("Factory")) {
            return recipeTypesMatch(sourceType, targetType);
        }

        // 源或目标是工厂：允许跨类型粘贴(普通机器 ↔ 工厂)
        // 升级由 applyTierUpgrade 处理,配置由 paste 处理
        if (sourceType.contains("Factory") || targetType.contains("Factory")) {
            return true;
        }

        // 都不是工厂
        String sourceNormalized = sourceType.replaceAll("(?i)basic|advanced|elite|ultimate", "").trim();
        String targetNormalized = targetType.replaceAll("(?i)basic|advanced|elite|ultimate", "").trim();
        return sourceNormalized.equalsIgnoreCase(targetNormalized);
    }

    private boolean recipeTypesMatch(String sourceType, String targetType) {
        String[] sourceParts = sourceType.split(" ");
        String[] targetParts = targetType.split(" ");
        if (sourceParts.length >= 2 && targetParts.length >= 2) {
            String sourceRecipe = sourceParts[sourceParts.length - 2];
            String targetRecipe = targetParts[targetParts.length - 2];
            if (sourceRecipe.equals(targetRecipe)) {
                return true;
            }
        }
        String sourceNormalized = sourceType.replaceAll("(?i)basic|advanced|elite|ultimate", "").trim();
        String targetNormalized = targetType.replaceAll("(?i)basic|advanced|elite|ultimate", "").trim();
        return sourceNormalized.equalsIgnoreCase(targetNormalized);
    }
}
