package com.github.aeddddd.ae2enhanced.test.assembly;

import static org.mockito.Mockito.mock;

import net.minecraft.world.item.Item;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternManager;
import com.github.aeddddd.ae2enhanced.assembly.AssemblyUpgradeManager;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.testutil.AE2ItemTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;

/**
 * assembly 包测试共享工装:升级卡物品注入与控制器/管理器装配.
 * <p>单元测试环境没有注册事件,通过 {@link RegistryObjectTestInjector}
 * 将测试构造的物品实例注入 {@link ModItems} 的 RegistryObject;
 * 同时把升级卡实例注册进物品注册表,否则 Forge 补丁后的 ItemStack 构造器
 * 会因缺少 registry delegate 抛异常.</p>
 */
final class AssemblyTestFixtures {

    /** 槽位 0:并行升级卡(测试实例). */
    static final Item PARALLEL = new Item(new Item.Properties());
    /** 槽位 1:速度升级卡(测试实例). */
    static final Item SPEED = new Item(new Item.Properties());
    /** 槽位 2:扩容升级卡(测试实例). */
    static final Item CAPACITY = new Item(new Item.Properties());
    /** 槽位 4:样板自动上传升级(测试实例). */
    static final Item AUTO_UPLOAD = new Item(new Item.Properties());

    static {
        // 原版引导 + AE2 配置加载 + AE2 样板物品注册(内部幂等)
        AE2ItemTestBootstrap.bootstrap();
        RegistryObjectTestInjector.inject(ModItems.ASSEMBLY_PARALLEL_UPGRADE, PARALLEL);
        RegistryObjectTestInjector.inject(ModItems.ASSEMBLY_SPEED_UPGRADE, SPEED);
        RegistryObjectTestInjector.inject(ModItems.ASSEMBLY_CAPACITY_UPGRADE, CAPACITY);
        RegistryObjectTestInjector.inject(ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE, AUTO_UPLOAD);
        // 注册测试升级卡实例,ItemStack 构造器需要 registry delegate
        AE2ItemTestBootstrap.registerTestItem(ModItems.ASSEMBLY_PARALLEL_UPGRADE, PARALLEL);
        AE2ItemTestBootstrap.registerTestItem(ModItems.ASSEMBLY_SPEED_UPGRADE, SPEED);
        AE2ItemTestBootstrap.registerTestItem(ModItems.ASSEMBLY_CAPACITY_UPGRADE, CAPACITY);
        AE2ItemTestBootstrap.registerTestItem(ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE, AUTO_UPLOAD);
    }

    private AssemblyTestFixtures() {
    }

    /** 确保静态注入已执行. */
    static void init() {
        // 触发类加载即可
    }

    /**
     * 装配一套互相接线完成的管理器:controller 为 mock(level 默认 null),
     * upgradeManager 与 patternManager 与游戏内接线一致.
     */
    static AssemblyPair newPair() {
        return newPair(mock(AssemblyControllerBlockEntity.class));
    }

    static AssemblyPair newPair(AssemblyControllerBlockEntity controller) {
        init();
        var upgradeManager = new AssemblyUpgradeManager();
        var patternManager = new AssemblyPatternManager(controller, upgradeManager);
        upgradeManager.setPatternManager(patternManager);
        return new AssemblyPair(controller, upgradeManager, patternManager);
    }

    record AssemblyPair(AssemblyControllerBlockEntity controller, AssemblyUpgradeManager upgradeManager,
            AssemblyPatternManager patternManager) {
    }
}
