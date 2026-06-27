package com.github.aeddddd.ae2enhanced.registry.content;

import com.github.aeddddd.ae2enhanced.item.*;

/**
 * Part 物品注册表 —— 仅声明 AE2 Part 相关的 Item 实例字段.
 */
public final class PartRegistry {

    private PartRegistry() {}

    public static ItemPartUniversalImportBus PART_UNIVERSAL_IMPORT_BUS;
    public static ItemPartUniversalExportBus PART_UNIVERSAL_EXPORT_BUS;
    public static ItemPartStockingBus PART_STOCKING_BUS;
}
