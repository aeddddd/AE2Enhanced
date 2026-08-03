package com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * Mekanism 反射隔离助手(目标版本: Mekanism 1.20.1, 10.4.x).
 *
 * <p>所有对 Mekanism 类的反射访问集中于此,handler 通过 static import 使用.
 * 仅当 mekanism 已安装时本类才会被 MemoryCardHandlerRegistry 触碰;
 * 反射点基于 Mekanism 1.20.x 分支源码核对,若 Mekanism 版本 API 变动导致初始化失败,
 * {@link #AVAILABLE} 为 false,handler 自动失效(复制/粘贴按"不支持"处理),不影响游戏运行.</p>
 *
 * <p>反射访问点:
 * <ul>
 * <li>mekanism.common.capabilities.Capabilities#CONFIG_CARD — IConfigCardAccess 能力</li>
 * <li>mekanism.api.IConfigCardAccess — 原生配置卡接口(getConfigurationData/setConfigurationData/
 * configurationDataSet/getConfigurationDataType/isConfigurationDataCompatible)</li>
 * <li>mekanism.common.tile.interfaces.IUpgradeTile#getComponent / #recalculateUpgrades</li>
 * <li>mekanism.common.tile.component.TileComponentUpgrade — 私有字段 upgrades(EnumMap) 与 supports(Upgrade)</li>
 * <li>mekanism.api.Upgrade — 升级枚举(getMax)</li>
 * <li>mekanism.common.util.UpgradeUtils#getStack(Upgrade, int) — 升级对应的物品</li>
 * </ul></p>
 */
public class MekanismReflectionHelper {

    public static final boolean AVAILABLE;

    /** Capability&lt;IConfigCardAccess&gt;,原始 Object 持有,使用时强转 raw Capability */
    public static Object CONFIG_CARD_CAPABILITY;

    public static final Class<?> CONFIG_CARD_ACCESS_CLASS;
    public static final Method GET_CONFIGURATION_DATA;
    public static final Method SET_CONFIGURATION_DATA;
    public static final Method CONFIGURATION_DATA_SET;
    public static final Method GET_CONFIGURATION_DATA_TYPE;
    public static final Method IS_CONFIGURATION_DATA_COMPATIBLE;

    public static final Class<?> UPGRADE_TILE_CLASS;
    public static final Method GET_COMPONENT;
    public static final Method RECALCULATE_UPGRADES;

    public static final Class<?> COMPONENT_UPGRADE_CLASS;
    /** TileComponentUpgrade 内部的 Map&lt;Upgrade, Integer&gt;(直接读写,避免 removeUpgrade 把拆下的升级塞进机器输出槽) */
    public static final Field UPGRADES_FIELD;
    public static final Method COMPONENT_SUPPORTS;

    public static final Class<?> UPGRADE_CLASS;
    public static final Object[] UPGRADE_VALUES;
    public static final Method UPGRADE_GET_MAX;

    public static final Method UPGRADE_UTILS_GET_STACK;

    static {
        boolean available = false;
        Object configCardCapability = null;
        Class<?> configCardAccessClass = null;
        Method getConfigurationData = null;
        Method setConfigurationData = null;
        Method configurationDataSet = null;
        Method getConfigurationDataType = null;
        Method isConfigurationDataCompatible = null;
        Class<?> upgradeTileClass = null;
        Method getComponent = null;
        Method recalculateUpgrades = null;
        Class<?> componentUpgradeClass = null;
        Field upgradesField = null;
        Method componentSupports = null;
        Class<?> upgradeClass = null;
        Object[] upgradeValues = null;
        Method upgradeGetMax = null;
        Method upgradeUtilsGetStack = null;

        try {
            Class<?> capabilitiesClass = Class.forName("mekanism.common.capabilities.Capabilities");
            configCardCapability = capabilitiesClass.getField("CONFIG_CARD").get(null);

            configCardAccessClass = Class.forName("mekanism.api.IConfigCardAccess");
            getConfigurationData = configCardAccessClass.getMethod("getConfigurationData", Player.class);
            setConfigurationData = configCardAccessClass.getMethod("setConfigurationData", Player.class, CompoundTag.class);
            configurationDataSet = configCardAccessClass.getMethod("configurationDataSet");
            getConfigurationDataType = configCardAccessClass.getMethod("getConfigurationDataType");
            isConfigurationDataCompatible = configCardAccessClass.getMethod("isConfigurationDataCompatible", BlockEntityType.class);

            upgradeClass = Class.forName("mekanism.api.Upgrade");
            upgradeValues = upgradeClass.getEnumConstants();
            upgradeGetMax = upgradeClass.getMethod("getMax");

            upgradeTileClass = Class.forName("mekanism.common.tile.interfaces.IUpgradeTile");
            getComponent = upgradeTileClass.getMethod("getComponent");
            recalculateUpgrades = upgradeTileClass.getMethod("recalculateUpgrades", upgradeClass);

            componentUpgradeClass = Class.forName("mekanism.common.tile.component.TileComponentUpgrade");
            upgradesField = componentUpgradeClass.getDeclaredField("upgrades");
            upgradesField.setAccessible(true);
            componentSupports = componentUpgradeClass.getMethod("supports", upgradeClass);

            Class<?> upgradeUtilsClass = Class.forName("mekanism.common.util.UpgradeUtils");
            upgradeUtilsGetStack = upgradeUtilsClass.getMethod("getStack", upgradeClass, int.class);

            available = true;
        } catch (Throwable t) {
            // 反射点失效(Mekanism 版本变动): 静默降级,handler 不生效
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Mekanism reflection for UMC", t);
        }

        AVAILABLE = available;
        CONFIG_CARD_CAPABILITY = configCardCapability;
        CONFIG_CARD_ACCESS_CLASS = configCardAccessClass;
        GET_CONFIGURATION_DATA = getConfigurationData;
        SET_CONFIGURATION_DATA = setConfigurationData;
        CONFIGURATION_DATA_SET = configurationDataSet;
        GET_CONFIGURATION_DATA_TYPE = getConfigurationDataType;
        IS_CONFIGURATION_DATA_COMPATIBLE = isConfigurationDataCompatible;
        UPGRADE_TILE_CLASS = upgradeTileClass;
        GET_COMPONENT = getComponent;
        RECALCULATE_UPGRADES = recalculateUpgrades;
        COMPONENT_UPGRADE_CLASS = componentUpgradeClass;
        UPGRADES_FIELD = upgradesField;
        COMPONENT_SUPPORTS = componentSupports;
        UPGRADE_CLASS = upgradeClass;
        UPGRADE_VALUES = upgradeValues;
        UPGRADE_GET_MAX = upgradeGetMax;
        UPGRADE_UTILS_GET_STACK = upgradeUtilsGetStack;
    }
}
