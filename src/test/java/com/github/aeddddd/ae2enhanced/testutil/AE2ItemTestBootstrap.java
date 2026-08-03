package com.github.aeddddd.ae2enhanced.testutil;

import java.io.IOException;
import java.nio.file.Files;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.ItemDefinition;

/**
 * 单元测试环境下的 AE2 物品引导工具.
 * <p>解决两类问题:
 * <ol>
 * <li>{@link AEItems} 静态初始化会构造全部 AE2 物品,其中充能工具(如 EntropyManipulatorItem)
 * 在构造器中读取 {@code AEConfig.instance()},未加载时抛 NPE,导致整个类初始化失败
 * (后续引用表现为 {@code NoClassDefFoundError}).这里先用临时目录加载 AE2 配置.</li>
 * <li>Forge 补丁后的 {@code ItemStack} 构造器要求物品在 {@code ForgeRegistries.ITEMS}
 * 中存在 registry delegate,未注册物品会抛 {@code IllegalArgumentException: No delegate exists}.
 * 这里把测试用到的 AE2 样板物品注册进物品注册表,并提供模组测试物品的注册辅助.</li>
 * </ol>
 * 整个过程幂等,可安全重复调用.</p>
 */
public final class AE2ItemTestBootstrap {

    private static volatile boolean bootstrapped;

    private AE2ItemTestBootstrap() {
    }

    /**
     * 完成 AE2 配置加载与样板物品注册.幂等,可重复调用.
     */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (AE2ItemTestBootstrap.class) {
            if (bootstrapped) {
                return;
            }
            doBootstrap();
            bootstrapped = true;
        }
    }

    /**
     * 把模组测试物品以 {@link RegistryObject} 的注册名写入物品注册表,
     * 使测试代码可以安全地构造该物品的 {@code ItemStack}.
     */
    public static void registerTestItem(RegistryObject<Item> registryObject, Item item) {
        ForgeRegistries.ITEMS.register(registryObject.getId(), item);
    }

    private static void doBootstrap() {
        // 物品注册表解冻由原版引导完成
        MinecraftTestBootstrap.bootstrap();
        loadAE2Config();
        // 装配枢纽相关测试用到的 AE2 样板物品
        register(AEItems.CRAFTING_PATTERN);
        register(AEItems.SMITHING_TABLE_PATTERN);
        register(AEItems.STONECUTTING_PATTERN);
        register(AEItems.PROCESSING_PATTERN);
    }

    private static void loadAE2Config() {
        if (AEConfig.instance() != null) {
            return;
        }
        try {
            AEConfig.load(Files.createTempDirectory("ae2enhanced-test-ae2config"));
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 AE2 测试配置目录", e);
        }
    }

    private static void register(ItemDefinition<?> definition) {
        ForgeRegistries.ITEMS.register(definition.id(), definition.asItem());
    }
}
