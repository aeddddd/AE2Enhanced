package com.github.aeddddd.ae2enhanced.blockentity;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.testutil.AE2ItemTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.ForgeConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;

/**
 * blockentity 包测试共享工装：注册表引导、方块实体类型与物品实例注入.
 * <p>真实构造方块实体需要两类前置条件:
 * <ol>
 * <li>{@link ModBlockEntities} 的 RegistryObject 中有可用的 {@link BlockEntityType},
 * 这里注入不依赖 DataFixer 的哑类型（构造器不校验 validBlocks）.</li>
 * <li>网络方块实体在构造期就会经 createMainNode 调用 {@code ModItems.X.get()},
 * 且 Forge 补丁后的 ItemStack 构造器要求物品已注册,这里注入并注册测试物品实例.</li>
 * </ol>
 * 升级卡注册在测试专用 id 下（{@code test_*}）,避免与 test.assembly 包工装
 * 对同一注册名的重复注册冲突；每次调用都会重新注入 RegistryObject,
 * 保证本包测试内物品实例身份一致.整个过程幂等,可安全重复调用.</p>
 */
final class BlockEntityTestSupport {

    /** 槽位 0:并行升级卡(测试实例). */
    static Item parallelUpgrade;
    /** 槽位 1:速度升级卡(测试实例). */
    static Item speedUpgrade;
    /** 槽位 2:扩容升级卡(测试实例). */
    static Item capacityUpgrade;
    /** 槽位 4:样板自动上传升级(测试实例). */
    static Item autoUploadUpgrade;

    private BlockEntityTestSupport() {
    }

    /**
     * 完成全部引导与注入.幂等,可重复调用.
     */
    static void bootstrap() {
        // 原版引导 + AE2 配置/样板物品 + AE2 key type 注册表 + 模组 COMMON 配置(均为幂等)
        AE2ItemTestBootstrap.bootstrap();
        AE2KeyTypeTestBootstrap.bootstrap();
        ForgeConfigTestBootstrap.bootstrap();
        injectBlockEntityTypes();
        injectControllerItems();
        injectUpgradeItems();
    }

    /**
     * 构造不依赖注册事件的哑 {@link BlockEntityType}.BlockEntity 构造器只保存引用,不做校验.
     */
    private static <T extends BlockEntity> BlockEntityType<T> dummyType() {
        return new BlockEntityType<>((pos, state) -> null, Set.of(Blocks.STONE), null);
    }

    private static void injectBlockEntityTypes() {
        RegistryObjectTestInjector.inject(ModBlockEntities.ASSEMBLY_CONTROLLER, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.ASSEMBLY_CASING, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.HYPERDIMENSIONAL_CONTROLLER, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.HYPERDIMENSIONAL_CASING, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.COMPUTATION_CONTROLLER, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.COMPUTATION_CASING, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.VIRTUAL_CRAFTING_CPU, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.MICRO_SINGULARITY, dummyType());
        RegistryObjectTestInjector.inject(ModBlockEntities.PERSONAL_DIMENSION_MANAGER, dummyType());
    }

    /**
     * 注入控制器/单方块物品：按真实注册名注册（无其他工装占用这些 id）,已注册则复用.
     */
    private static void injectControllerItems() {
        injectItemAtId(ModItems.ASSEMBLY_CONTROLLER, ModItems.ASSEMBLY_CONTROLLER.getId());
        injectItemAtId(ModItems.HYPERDIMENSIONAL_CONTROLLER, ModItems.HYPERDIMENSIONAL_CONTROLLER.getId());
        injectItemAtId(ModItems.COMPUTATION_CONTROLLER, ModItems.COMPUTATION_CONTROLLER.getId());
        injectItemAtId(ModItems.VIRTUAL_CRAFTING_CPU, ModItems.VIRTUAL_CRAFTING_CPU.getId());
    }

    /**
     * 注入升级卡：注册到测试专用 id,避免与其他测试工装对同一注册名重复注册.
     */
    private static void injectUpgradeItems() {
        parallelUpgrade = injectItemAtId(ModItems.ASSEMBLY_PARALLEL_UPGRADE, testId("test_parallel_upgrade"));
        speedUpgrade = injectItemAtId(ModItems.ASSEMBLY_SPEED_UPGRADE, testId("test_speed_upgrade"));
        capacityUpgrade = injectItemAtId(ModItems.ASSEMBLY_CAPACITY_UPGRADE, testId("test_capacity_upgrade"));
        autoUploadUpgrade = injectItemAtId(ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE, testId("test_auto_upload_upgrade"));
    }

    private static ResourceLocation testId(String path) {
        return new ResourceLocation("ae2enhanced", path);
    }

    /**
     * 确保指定注册名下有可用物品实例并注入 RegistryObject；已注册则复用现有实例.
     */
    private static Item injectItemAtId(RegistryObject<Item> registryObject, ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        if (item == null) {
            item = new Item(new Item.Properties());
            ForgeRegistries.ITEMS.register(id, item);
        }
        RegistryObjectTestInjector.inject(registryObject, item);
        return item;
    }
}
