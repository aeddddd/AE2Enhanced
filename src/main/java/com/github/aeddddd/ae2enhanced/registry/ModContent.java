package com.github.aeddddd.ae2enhanced.registry;

import appeng.api.config.Upgrades;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.block.BlockRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.item.fakeitem.FakeEnergies;
import com.github.aeddddd.ae2enhanced.item.fakeitem.FakeFluids;
import com.github.aeddddd.ae2enhanced.part.PartRegistry;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import net.minecraft.item.ItemStack;

/**
 * 内容注册中心：假物品、升级卡、物质炮弹药、各类初始化注册表
 */
public final class ModContent {

    private ModContent() {}

    public static void preInit() {
        if (!Ae2fcCompat.AE2FC_LOADED) {
            FakeFluids.init();
        }
        FakeEnergies.init();

        // 第三方 Mod 对应的假物品 handler 通过反射加载，避免出现在 ModContent 的常量池中。
        // 某些 ClassLoader（如 Cleanroom/Forge 在开启 eager verification 时）会在方法入口处
        // 解析所有直接引用的类，若对应 Mod 未安装或其 API 类不存在，会立即抛出 NoClassDefFoundError。
        if (!Ae2fcCompat.AE2FC_LOADED) {
            tryInitFakeWithExtraCheck(
                    new String[]{"mekanism", "mekeng"},
                    "com.github.aeddddd.ae2enhanced.item.fakeitem.FakeGases",
                    new String[]{"com.mekeng.github.common.me.storage.IGasStorageChannel"});
        }
        tryInitFakeWithExtraCheck(
                new String[]{"thaumcraft", "thaumicenergistics"},
                "com.github.aeddddd.ae2enhanced.item.fakeitem.FakeEssentias",
                new String[]{"thaumicenergistics.api.storage.IAEEssentiaStack"});
        tryInitFake(new String[]{"botania"}, "com.github.aeddddd.ae2enhanced.item.fakeitem.FakeMana");
        tryInitFake(new String[]{"astralsorcery"}, "com.github.aeddddd.ae2enhanced.item.fakeitem.FakeStarlight");

        com.github.aeddddd.ae2enhanced.item.memorycard.core.MemoryCardHandlerRegistry.init();
    }

    /**
     * 通过反射调用指定假物品 handler 的 init()，避免在 ModContent 常量池中保留对
     * 可选第三方类的直接引用。
     *
     * @param modIds    必须全部已加载的 mod id 列表；任一未加载时跳过
     * @param className 假物品 handler 全限定名
     */
    private static void tryInitFake(String[] modIds, String className) {
        if (!allModsLoaded(modIds)) {
            return;
        }
        try {
            com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.invokeStatic(className, "init");
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize fake item handler {} (required mods not fully available)", className, e);
        }
    }

    /**
     * 在加载假物品 handler 前，先校验第三方 API 类是否存在。
     * 用于解决 mod id 已加载但对应 API 类（如 Thaumic Energistics 的 IAEEssentiaStack）
     * 缺失或重命名导致的启动崩溃。
     */
    private static void tryInitFakeWithExtraCheck(String[] modIds, String className, String[] requiredApiClasses) {
        if (!allModsLoaded(modIds)) {
            return;
        }
        if (requiredApiClasses != null) {
            for (String apiClass : requiredApiClasses) {
                try {
                    com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.classForName(apiClass);
                } catch (ClassNotFoundException e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] API class {} not found, skipping fake item handler {}", apiClass, className);
                    return;
                }
            }
        }
        tryInitFake(modIds, className);
    }

    private static boolean allModsLoaded(String[] modIds) {
        if (modIds == null) return true;
        for (String modId : modIds) {
            if (!com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.isModLoaded(modId)) {
                return false;
            }
        }
        return true;
    }

    public static void init() {
        // E1a：通用输入总线升级卡
        if (PartRegistry.PART_UNIVERSAL_IMPORT_BUS != null) {
            ItemStack busStack = new ItemStack(PartRegistry.PART_UNIVERSAL_IMPORT_BUS);
            Upgrades.SPEED.registerItem(busStack, 4);
            Upgrades.CAPACITY.registerItem(busStack, 5);
            Upgrades.REDSTONE.registerItem(busStack, 1);
            Upgrades.FUZZY.registerItem(busStack, 1);
            Upgrades.CRAFTING.registerItem(busStack, 1);
        }

        // E1b：通用输出总线升级卡
        if (PartRegistry.PART_UNIVERSAL_EXPORT_BUS != null) {
            ItemStack busStack = new ItemStack(PartRegistry.PART_UNIVERSAL_EXPORT_BUS);
            Upgrades.SPEED.registerItem(busStack, 4);
            Upgrades.CAPACITY.registerItem(busStack, 5);
            Upgrades.REDSTONE.registerItem(busStack, 1);
            Upgrades.FUZZY.registerItem(busStack, 1);
            Upgrades.CRAFTING.registerItem(busStack, 1);
        }

        // Stocking 总线升级卡
        if (PartRegistry.PART_STOCKING_BUS != null) {
            ItemStack busStack = new ItemStack(PartRegistry.PART_STOCKING_BUS);
            Upgrades.SPEED.registerItem(busStack, 4);
            Upgrades.CAPACITY.registerItem(busStack, 4);
            Upgrades.REDSTONE.registerItem(busStack, 1);
            Upgrades.FUZZY.registerItem(busStack, 1);
            Upgrades.CRAFTING.registerItem(busStack, 1);
        }

        // 能源存储总线升级卡:双向过滤卡(反转黑白名单) + 容量卡(解锁过滤槽)
        if (PartRegistry.PART_ENERGY_STORAGE_BUS != null) {
            ItemStack busStack = new ItemStack(PartRegistry.PART_ENERGY_STORAGE_BUS);
            Upgrades.INVERTER.registerItem(busStack, 1);
            Upgrades.CAPACITY.registerItem(busStack, 5);
        }

        // 中枢 ME 接口升级卡
        if (BlockRegistry.CENTRAL_ME_INTERFACE != null) {
            ItemStack centralInterface = new ItemStack(BlockRegistry.CENTRAL_ME_INTERFACE);
            Upgrades.PATTERN_EXPANSION.registerItem(centralInterface, 3);
        }

        // 物质炮弹药：共形不变荷(weight 1E8 → 伤害 5,000,000)
        appeng.api.AEApi.instance().registries().matterCannon().registerAmmo(
                new ItemStack(ItemRegistry.CONFORMAL_CHARGE), 100_000_000.0);
    }
}
