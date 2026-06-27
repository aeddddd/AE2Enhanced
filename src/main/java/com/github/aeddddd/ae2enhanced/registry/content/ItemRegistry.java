package com.github.aeddddd.ae2enhanced.registry.content;

import com.github.aeddddd.ae2enhanced.item.*;
import net.minecraft.item.Item;

/**
 * 物品注册表 —— 仅声明普通 Item 实例字段(不含 Part).
 */
public final class ItemRegistry {

    private ItemRegistry() {}

    public static ItemUpgradeCard UPGRADE_CARD;
    public static ItemConformalCharge CONFORMAL_CHARGE;
    public static ItemDifferentialFormStabilizer DIFFERENTIAL_FORM_STABILIZER;
    public static ItemStableSpacetimeManifold STABLE_SPACETIME_MANIFOLD;
    public static Item ESSENTIA_DROP;
    public static ItemFluidDrop FLUID_DROP;
    public static Item GAS_DROP;
    public static ItemChannelReceiverCard CHANNEL_RECEIVER_CARD;
    public static ItemUniversalMemoryCard UNIVERSAL_MEMORY_CARD;
    public static ItemOmniWirelessTerminal OMNI_WIRELESS_TERMINAL;
    public static ItemOmniUpgradeCard OMNI_UPGRADE_CARD;
    public static ItemSmartBlankPattern SMART_BLANK_PATTERN;
    public static ItemSmartPattern SMART_PATTERN;

    // RF 能量假物品
    public static ItemEnergyDrop ENERGY_DROP;

    // Botania Mana 假物品(条件加载)
    public static Item MANA_DROP;

    // Astral Sorcery Starlight 假物品(条件加载)
    public static Item STARLIGHT_DROP;

    // 先进ME工具
    public static ItemAdvancedMEOmniTool ME_OMNI_TOOL;

    // ME 放置工具
    public static ItemMEPlacementTool ME_PLACEMENT_TOOL;

    // 中枢接口虚拟并行卡
    public static ItemVirtualParallelCard VIRTUAL_PARALLEL_CARD;

    // 个人维度核心
    public static ItemPersonalDimension PERSONAL_DIMENSION;
}
