package com.github.aeddddd.ae2enhanced.util.memorycard.handler.mekanism;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Mekanism 反射隔离助手.
 *
 * <p>所有对 Mekanism 类的反射访问集中于此,handler 通过 static import 使用.
 * 当 Mekanism 未安装时,所有字段为 null,{@link #AVAILABLE} 为 false.</p>
 */
public class MekanismReflectionHelper {

    public static final boolean AVAILABLE;
    public static final Class<?> CONFIG_CARD_CAPABILITY_CLASS;
    public static final Class<?> SPECIAL_CONFIG_DATA_CLASS;
    public static Object CONFIG_CARD_CAPABILITY;
    public static Object SPECIAL_CONFIG_DATA_CAPABILITY;

    public static final Class<?> REDSTONE_CONTROL_CLASS;
    public static final Method REDSTONE_GET_CONTROL_TYPE;
    public static final Method REDSTONE_SET_CONTROL_TYPE;
    public static final Object[] REDSTONE_CONTROL_VALUES;

    public static final Class<?> SIDE_CONFIGURATION_CLASS;
    public static final Method SIDE_CONFIG_GET_CONFIG;
    public static final Method SIDE_CONFIG_GET_EJECTOR;
    public static final Method SIDE_CONFIG_GET_ORIENTATION;

    public static final Class<?> TILE_COMPONENT_CONFIG_CLASS;
    public static final Method TILE_COMPONENT_CONFIG_WRITE;
    public static final Method TILE_COMPONENT_CONFIG_READ;

    public static final Class<?> TILE_COMPONENT_EJECTOR_CLASS;
    public static final Method TILE_COMPONENT_EJECTOR_WRITE;
    public static final Method TILE_COMPONENT_EJECTOR_READ;

    public static final Class<?> UPGRADE_TILE_CLASS;
    public static final Method UPGRADE_TILE_GET_COMPONENT;

    public static final Class<?> TILE_COMPONENT_UPGRADE_CLASS;
    public static final Method TILE_COMPONENT_UPGRADE_READ;
    public static final Method TILE_COMPONENT_UPGRADE_WRITE;
    public static final Method TILE_COMPONENT_UPGRADE_GET_UPGRADES;
    public static final Method TILE_COMPONENT_UPGRADE_ADD_UPGRADE;
    public static final Method TILE_COMPONENT_UPGRADE_REMOVE_UPGRADE;
    public static final Method TILE_COMPONENT_UPGRADE_GET_INSTALLED_TYPES;

    public static final Class<?> UPGRADE_CLASS;
    public static final Method UPGRADE_GET_STACK;
    public static final Method UPGRADE_SAVE_MAP;
    public static final Method UPGRADE_BUILD_MAP;

    public static final Class<?> CAPABILITY_UTILS_CLASS;
    public static final Method CAPABILITY_UTILS_HAS_CAPABILITY;
    public static final Method CAPABILITY_UTILS_GET_CAPABILITY;

    public static final Class<?> TILE_ENTITY_CONTAINER_BLOCK_CLASS;
    public static final Field CONTAINER_BLOCK_FULL_NAME;

    public static final Class<?> TIER_UPGRADEABLE_CLASS;
    public static final Method TIER_UPGRADEABLE_UPGRADE;
    public static final Class<?> BASE_TIER_CLASS;
    public static final Object[] BASE_TIER_VALUES;
    public static final ItemStack[] TIER_INSTALLER_CACHE = new ItemStack[4];

    static {
        boolean available = false;
        Class<?> configCardCapabilityClass = null;
        Class<?> specialConfigDataClass = null;
        Object configCardCapability = null;
        Object specialConfigDataCapability = null;

        Class<?> redstoneControlClass = null;
        Method redstoneGetControlType = null;
        Method redstoneSetControlType = null;
        Object[] redstoneControlValues = null;

        Class<?> sideConfigurationClass = null;
        Method sideConfigGetConfig = null;
        Method sideConfigGetEjector = null;
        Method sideConfigGetOrientation = null;

        Class<?> tileComponentConfigClass = null;
        Method tileComponentConfigWrite = null;
        Method tileComponentConfigRead = null;

        Class<?> tileComponentEjectorClass = null;
        Method tileComponentEjectorWrite = null;
        Method tileComponentEjectorRead = null;

        Class<?> upgradeTileClass = null;
        Method upgradeTileGetComponent = null;

        Class<?> tileComponentUpgradeClass = null;
        Method tileComponentUpgradeRead = null;
        Method tileComponentUpgradeWrite = null;
        Method tileComponentUpgradeGetUpgrades = null;
        Method tileComponentUpgradeAddUpgrade = null;
        Method tileComponentUpgradeRemoveUpgrade = null;
        Method tileComponentUpgradeGetInstalledTypes = null;

        Class<?> upgradeClass = null;
        Method upgradeGetStack = null;
        Method upgradeSaveMap = null;
        Method upgradeBuildMap = null;

        Class<?> capabilityUtilsClass = null;
        Method capabilityUtilsHasCapability = null;
        Method capabilityUtilsGetCapability = null;

        Class<?> tileEntityContainerBlockClass = null;
        Field containerBlockFullName = null;

        Class<?> tierUpgradeableClass = null;
        Method tierUpgradeableUpgrade = null;
        Class<?> baseTierClass = null;
        Object[] baseTierValues = null;

        try {
            if (Loader.isModLoaded("mekanism")) {
                configCardCapabilityClass = Class.forName("mekanism.api.IConfigCardAccess");
                specialConfigDataClass = Class.forName("mekanism.api.IConfigCardAccess$ISpecialConfigData");

                Class<?> capabilitiesClass = Class.forName("mekanism.common.capabilities.Capabilities");
                configCardCapability = capabilitiesClass.getField("CONFIG_CARD_CAPABILITY").get(null);
                specialConfigDataCapability = capabilitiesClass.getField("SPECIAL_CONFIG_DATA_CAPABILITY").get(null);

                redstoneControlClass = Class.forName("mekanism.common.base.IRedstoneControl");
                redstoneGetControlType = redstoneControlClass.getMethod("getControlType");
                redstoneSetControlType = redstoneControlClass.getMethod("setControlType", redstoneGetControlType.getReturnType());
                redstoneControlValues = redstoneGetControlType.getReturnType().getEnumConstants();

                sideConfigurationClass = Class.forName("mekanism.common.base.ISideConfiguration");
                sideConfigGetConfig = sideConfigurationClass.getMethod("getConfig");
                sideConfigGetEjector = sideConfigurationClass.getMethod("getEjector");
                sideConfigGetOrientation = sideConfigurationClass.getMethod("getOrientation");

                tileComponentConfigClass = sideConfigGetConfig.getReturnType();
                tileComponentConfigWrite = tileComponentConfigClass.getMethod("write", NBTTagCompound.class);
                tileComponentConfigRead = tileComponentConfigClass.getMethod("read", NBTTagCompound.class);

                tileComponentEjectorClass = sideConfigGetEjector.getReturnType();
                tileComponentEjectorWrite = tileComponentEjectorClass.getMethod("write", NBTTagCompound.class);
                tileComponentEjectorRead = tileComponentEjectorClass.getMethod("read", NBTTagCompound.class);

                upgradeTileClass = Class.forName("mekanism.common.base.IUpgradeTile");
                upgradeTileGetComponent = upgradeTileClass.getMethod("getComponent");

                tileComponentUpgradeClass = upgradeTileGetComponent.getReturnType();
                tileComponentUpgradeRead = tileComponentUpgradeClass.getMethod("read", NBTTagCompound.class);
                tileComponentUpgradeWrite = tileComponentUpgradeClass.getMethod("write", NBTTagCompound.class);
                tileComponentUpgradeGetUpgrades = tileComponentUpgradeClass.getMethod("getUpgrades", Class.forName("mekanism.common.Upgrade"));
                tileComponentUpgradeAddUpgrade = tileComponentUpgradeClass.getMethod("addUpgrade", Class.forName("mekanism.common.Upgrade"));
                tileComponentUpgradeRemoveUpgrade = tileComponentUpgradeClass.getMethod("removeUpgrade", Class.forName("mekanism.common.Upgrade"));
                tileComponentUpgradeGetInstalledTypes = tileComponentUpgradeClass.getMethod("getInstalledTypes");

                upgradeClass = Class.forName("mekanism.common.Upgrade");
                upgradeGetStack = upgradeClass.getMethod("getStack");
                upgradeSaveMap = upgradeClass.getMethod("saveMap", java.util.Map.class, NBTTagCompound.class);
                upgradeBuildMap = upgradeClass.getMethod("buildMap", NBTTagCompound.class);

                capabilityUtilsClass = Class.forName("mekanism.common.util.CapabilityUtils");
                capabilityUtilsHasCapability = capabilityUtilsClass.getMethod("hasCapability", ICapabilityProvider.class, net.minecraftforge.common.capabilities.Capability.class, EnumFacing.class);
                capabilityUtilsGetCapability = capabilityUtilsClass.getMethod("getCapability", ICapabilityProvider.class, net.minecraftforge.common.capabilities.Capability.class, EnumFacing.class);

                tileEntityContainerBlockClass = Class.forName("mekanism.common.tile.prefab.TileEntityContainerBlock");
                containerBlockFullName = tileEntityContainerBlockClass.getField("fullName");

                tierUpgradeableClass = Class.forName("mekanism.common.base.ITierUpgradeable");
                tierUpgradeableUpgrade = tierUpgradeableClass.getMethod("upgrade", Class.forName("mekanism.common.tier.BaseTier"));
                baseTierClass = Class.forName("mekanism.common.tier.BaseTier");
                baseTierValues = baseTierClass.getEnumConstants();

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Mekanism reflection for UMC", e);
        }

        AVAILABLE = available;
        CONFIG_CARD_CAPABILITY_CLASS = configCardCapabilityClass;
        SPECIAL_CONFIG_DATA_CLASS = specialConfigDataClass;
        CONFIG_CARD_CAPABILITY = configCardCapability;
        SPECIAL_CONFIG_DATA_CAPABILITY = specialConfigDataCapability;

        REDSTONE_CONTROL_CLASS = redstoneControlClass;
        REDSTONE_GET_CONTROL_TYPE = redstoneGetControlType;
        REDSTONE_SET_CONTROL_TYPE = redstoneSetControlType;
        REDSTONE_CONTROL_VALUES = redstoneControlValues;

        SIDE_CONFIGURATION_CLASS = sideConfigurationClass;
        SIDE_CONFIG_GET_CONFIG = sideConfigGetConfig;
        SIDE_CONFIG_GET_EJECTOR = sideConfigGetEjector;
        SIDE_CONFIG_GET_ORIENTATION = sideConfigGetOrientation;

        TILE_COMPONENT_CONFIG_CLASS = tileComponentConfigClass;
        TILE_COMPONENT_CONFIG_WRITE = tileComponentConfigWrite;
        TILE_COMPONENT_CONFIG_READ = tileComponentConfigRead;

        TILE_COMPONENT_EJECTOR_CLASS = tileComponentEjectorClass;
        TILE_COMPONENT_EJECTOR_WRITE = tileComponentEjectorWrite;
        TILE_COMPONENT_EJECTOR_READ = tileComponentEjectorRead;

        UPGRADE_TILE_CLASS = upgradeTileClass;
        UPGRADE_TILE_GET_COMPONENT = upgradeTileGetComponent;

        TILE_COMPONENT_UPGRADE_CLASS = tileComponentUpgradeClass;
        TILE_COMPONENT_UPGRADE_READ = tileComponentUpgradeRead;
        TILE_COMPONENT_UPGRADE_WRITE = tileComponentUpgradeWrite;
        TILE_COMPONENT_UPGRADE_GET_UPGRADES = tileComponentUpgradeGetUpgrades;
        TILE_COMPONENT_UPGRADE_ADD_UPGRADE = tileComponentUpgradeAddUpgrade;
        TILE_COMPONENT_UPGRADE_REMOVE_UPGRADE = tileComponentUpgradeRemoveUpgrade;
        TILE_COMPONENT_UPGRADE_GET_INSTALLED_TYPES = tileComponentUpgradeGetInstalledTypes;

        UPGRADE_CLASS = upgradeClass;
        UPGRADE_GET_STACK = upgradeGetStack;
        UPGRADE_SAVE_MAP = upgradeSaveMap;
        UPGRADE_BUILD_MAP = upgradeBuildMap;

        CAPABILITY_UTILS_CLASS = capabilityUtilsClass;
        CAPABILITY_UTILS_HAS_CAPABILITY = capabilityUtilsHasCapability;
        CAPABILITY_UTILS_GET_CAPABILITY = capabilityUtilsGetCapability;

        TILE_ENTITY_CONTAINER_BLOCK_CLASS = tileEntityContainerBlockClass;
        CONTAINER_BLOCK_FULL_NAME = containerBlockFullName;

        TIER_UPGRADEABLE_CLASS = tierUpgradeableClass;
        TIER_UPGRADEABLE_UPGRADE = tierUpgradeableUpgrade;
        BASE_TIER_CLASS = baseTierClass;
        BASE_TIER_VALUES = baseTierValues;
    }
}
