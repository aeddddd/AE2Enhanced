package com.github.aeddddd.ae2enhanced.util.memorycard.core;

import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * UMC 粘贴数据过滤器.
 *
 * <p>在调用 handler.paste() 之前,根据内存卡的复制内容模式(CopyMode)与粘贴选项
 * (升级/朝向/侧面/红石)对配置 NBT 进行键级过滤.
 * 各 handler 对缺失键均有 hasKey 保护,因此"过滤"等价于"不粘贴该部分".</p>
 *
 * <p>键名约定覆盖所有现有 handler(AE2/Mekanism/EnderIO/NuclearCraft/TechReborn/
 * ThermalExpansion/IndustrialForegoing/ExU2/QuantumThings).</p>
 */
public final class UMCDataFilter {

    /** 升级类键(消耗物品的升级卡/套件/插件). */
    private static final Set<String> UPGRADE_KEYS = new HashSet<>(Arrays.asList(
            "ae2e:upgrades",        // AE2 / NuclearCraft / TechReborn / IF(转换后) / ExU2(转换后)
            "eio:upgrades",         // EnderIO
            "mekanism:upgrades",    // Mekanism
            "Augments",             // ThermalExpansion
            "Level",                // ThermalExpansion(等级即升级套件)
            "upgrades",             // ExU2 原始键(防御性,正常流程已转换)
            "addonItems",           // IndustrialForegoing 原始键(防御性,正常流程已转换)
            "xu2:filter",           // ExU2 过滤器物品(消耗品)
            "qt:filter"             // QuantumThings 过滤器物品(消耗品)
            // 注意:QT 实体检测器的标量配置键 "filter"(int 枚举)不属于升级键,不得加入
    ));

    /** 朝向键. */
    private static final Set<String> FACING_KEYS = new HashSet<>(Arrays.asList(
            "Facing",               // ThermalExpansion
            "facing"                // EnderIO / TechReborn
    ));

    /** 侧面配置键. */
    private static final Set<String> SIDE_KEYS = new HashSet<>(Arrays.asList(
            "SideCache",            // ThermalExpansion
            "faceModes",            // EnderIO
            "slotConfig",           // TechReborn
            "fluidConfig",          // TechReborn
            "slotSettings",         // NuclearCraft
            "tankSettings",         // NuclearCraft
            "inventoryConnections", // NuclearCraft
            "fluidConnections",     // NuclearCraft
            "side_config",          // IndustrialForegoing(TeslaCoreLib)
            "Sides"                 // LazyAE2(libnine SideAlloc)
    ));

    /** 红石控制键. */
    private static final Set<String> REDSTONE_KEYS = new HashSet<>(Arrays.asList(
            "RSControl",            // ThermalExpansion
            "redstoneMode",         // EnderIO / TechReborn
            "controlType",          // Mekanism
            "redstoneControl",      // NuclearCraft
            "alternateComparator",  // NuclearCraft(比较器模式,归类为红石行为)
            "redstone",             // IndustrialForegoing / ExU2
            "rsMode"                // RFTools(McJtyLib 通用红石模式)
    ));

    /** 元数据键(类型校验用,任何模式下都保留). */
    private static final Set<String> META_KEYS = new HashSet<>(Arrays.asList(
            "dataType", "infoName", "xu2:type"
    ));

    private UMCDataFilter() {
    }

    /**
     * 生成粘贴用的过滤后配置数据(兼容旧调用：仅使用全局键清单).
     */
    public static NBTTagCompound filterForPaste(NBTTagCompound data,
                                                ItemUniversalMemoryCard.CopyMode mode,
                                                int options) {
        return filterForPaste(data, mode, options, null);
    }

    /**
     * 生成粘贴用的过滤后配置数据.
     *
     * <p>键分类 = 全局清单 ∪ 来源 handler 声明的键(经 {@code sourceHandlerId} 反查).
     * handler 声明的键与其复制代码同文件共处,新增配置键不会漏进过滤分类.</p>
     *
     * @param data           内存卡中保存的完整配置
     * @param mode           复制内容模式
     * @param options        粘贴选项位掩码(ItemUniversalMemoryCard.OPT_*)
     * @param sourceHandlerId 来源 handler 的稳定 ID(可为 null,退化为仅全局清单)
     * @return 过滤后的新 NBT(不修改入参)
     */
    public static NBTTagCompound filterForPaste(NBTTagCompound data,
                                                ItemUniversalMemoryCard.CopyMode mode,
                                                int options,
                                                String sourceHandlerId) {
        Set<String> upgradeKeys = UPGRADE_KEYS;
        Set<String> facingKeys = FACING_KEYS;
        Set<String> sideKeys = SIDE_KEYS;
        Set<String> redstoneKeys = REDSTONE_KEYS;

        IMemoryCardHandler sourceHandler = MemoryCardHandlerRegistry.findById(sourceHandlerId);
        if (sourceHandler != null) {
            upgradeKeys = union(UPGRADE_KEYS, sourceHandler.getUpgradeKeys());
            facingKeys = union(FACING_KEYS, sourceHandler.getFacingKeys());
            sideKeys = union(SIDE_KEYS, sourceHandler.getSideKeys());
            redstoneKeys = union(REDSTONE_KEYS, sourceHandler.getRedstoneKeys());
        }

        NBTTagCompound result = new NBTTagCompound();

        boolean keepUpgrades = mode != ItemUniversalMemoryCard.CopyMode.CONFIG_ONLY
                && (options & ItemUniversalMemoryCard.OPT_UPGRADES) != 0;
        boolean keepConfig = mode != ItemUniversalMemoryCard.CopyMode.UPGRADES_ONLY;

        for (String key : data.getKeySet()) {
            if (META_KEYS.contains(key)) {
                result.setTag(key, data.getTag(key).copy());
                continue;
            }
            if (upgradeKeys.contains(key)) {
                if (keepUpgrades) result.setTag(key, data.getTag(key).copy());
                continue;
            }
            if (!keepConfig) continue;
            if (facingKeys.contains(key) && (options & ItemUniversalMemoryCard.OPT_FACING) == 0) continue;
            if (sideKeys.contains(key) && (options & ItemUniversalMemoryCard.OPT_SIDES) == 0) continue;
            if (redstoneKeys.contains(key) && (options & ItemUniversalMemoryCard.OPT_REDSTONE) == 0) continue;
            result.setTag(key, data.getTag(key).copy());
        }
        return result;
    }

    private static Set<String> union(Set<String> base, Set<String> extra) {
        if (extra == null || extra.isEmpty()) return base;
        Set<String> merged = new HashSet<>(base);
        merged.addAll(extra);
        return merged;
    }
}
