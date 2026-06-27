package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEnergies;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentias;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeMana;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeStarlight;
import appeng.api.config.Upgrades;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

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

        if (!Ae2fcCompat.AE2FC_LOADED && Loader.isModLoaded("mekanism") && Loader.isModLoaded("mekeng")) {
            FakeGases.init();
        }
        if (Loader.isModLoaded("thaumcraft") && Loader.isModLoaded("thaumicenergistics")) {
            FakeEssentias.init();
        }
        if (Loader.isModLoaded("botania")) {
            FakeMana.init();
        }
        if (Loader.isModLoaded("astralsorcery")) {
            FakeStarlight.init();
        }

        com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardHandlerRegistry.init();
        com.github.aeddddd.ae2enhanced.centralinterface.HandlerRegistry.init();
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

        // 中枢 ME 接口升级卡
        if (BlockRegistry.CENTRAL_ME_INTERFACE != null) {
            ItemStack centralInterface = new ItemStack(BlockRegistry.CENTRAL_ME_INTERFACE);
            Upgrades.PATTERN_EXPANSION.registerItem(centralInterface, 3);
        }

        // 物质炮弹药：共形不变荷(weight 1E8 → 伤害 5,000,000)
        appeng.api.AEApi.instance().registries().matterCannon().registerAmmo(
                new ItemStack(ItemRegistry.CONFORMAL_CHARGE), 100_000_000.0);

        // 智能样板垃圾回收器
        com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector.init();
    }
}
