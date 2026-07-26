package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;


import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.item.AssemblyUpgradeCardItem;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.item.ConformalInvariantChargeItem;
import com.github.aeddddd.ae2enhanced.item.GuideBookItem;
import com.github.aeddddd.ae2enhanced.item.MicroSingularityItem;
import com.github.aeddddd.ae2enhanced.item.PersonalDimensionCoreItem;
import com.github.aeddddd.ae2enhanced.item.SingularityConstrictorItem;
import com.github.aeddddd.ae2enhanced.item.DifferentialFormStabilizerItem;
import com.github.aeddddd.ae2enhanced.item.StableSpacetimeManifoldItem;

/**
 * 物品注册中心.
 */
public final class ModItems {
    public static final DeferredRegister<Item> DR = DeferredRegister.create(Registries.ITEM, AE2Enhanced.MOD_ID);

    // 模组指南书
    public static final RegistryObject<Item> GUIDE = DR.register("guide",
            () -> new GuideBookItem(new Item.Properties().stacksTo(1)));

    // Hyperdimensional Storage
    public static final RegistryObject<Item> HYPERDIMENSIONAL_CONTROLLER = DR.register("hyperdimensional_controller",
            () -> new BlockItem(ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> HYPERDIMENSIONAL_CASING = DR.register("hyperdimensional_casing",
            () -> new BlockItem(ModBlocks.HYPERDIMENSIONAL_CASING.get(), new Item.Properties()));

    public static final RegistryObject<Item> HYPERDIMENSIONAL_SINGULARITY_CORE = DR.register(
            "hyperdimensional_singularity_core",
            () -> new BlockItem(ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get(), new Item.Properties()));

    public static final RegistryObject<Item> MULTIBLOCK_ME_INTERFACE = DR.register("multiblock_me_interface",
            () -> new BlockItem(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), new Item.Properties()));

    // Black Hole advanced materials
    public static final RegistryObject<Item> STABLE_SPACETIME_MANIFOLD = DR.register("stable_spacetime_manifold",
            () -> new StableSpacetimeManifoldItem(new Item.Properties()));

    public static final RegistryObject<Item> DIFFERENTIAL_FORM_STABILIZER = DR.register("differential_form_stabilizer",
            () -> new DifferentialFormStabilizerItem(new Item.Properties()));

    public static final RegistryObject<Item> CONFORMAL_INVARIANT_CHARGE = DR.register("conformal_invariant_charge",
            () -> new ConformalInvariantChargeItem(new Item.Properties()));

    // 被约束的微型奇点（物品形态,NBT 携带寿命/永久标记,不可堆叠）
    public static final RegistryObject<Item> MICRO_SINGULARITY = DR.register("micro_singularity",
            () -> new MicroSingularityItem(new Item.Properties().stacksTo(1)));

    // 奇点约束器：右键微型奇点将其约束为物品形态
    public static final RegistryObject<Item> SINGULARITY_CONSTRICTOR = DR.register("singularity_constrictor",
            () -> new SingularityConstrictorItem(new Item.Properties().stacksTo(16)));

    // Assembly Hub
    public static final RegistryObject<Item> ASSEMBLY_CONTROLLER = DR.register("assembly_controller",
            () -> new BlockItem(ModBlocks.ASSEMBLY_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_CASING_1 = DR.register("assembly_casing_1",
            () -> new BlockItem(ModBlocks.ASSEMBLY_CASING_1.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_CASING_2 = DR.register("assembly_casing_2",
            () -> new BlockItem(ModBlocks.ASSEMBLY_CASING_2.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_CASING_3 = DR.register("assembly_casing_3",
            () -> new BlockItem(ModBlocks.ASSEMBLY_CASING_3.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_CASING_4 = DR.register("assembly_casing_4",
            () -> new BlockItem(ModBlocks.ASSEMBLY_CASING_4.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_INNER_WALL = DR.register("assembly_inner_wall",
            () -> new BlockItem(ModBlocks.ASSEMBLY_INNER_WALL.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_STABILIZER = DR.register("assembly_stabilizer",
            () -> new BlockItem(ModBlocks.ASSEMBLY_STABILIZER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_PARALLEL_UPGRADE = DR.register("assembly_parallel_upgrade",
            () -> new AssemblyUpgradeCardItem(new Item.Properties().stacksTo(5)));

    public static final RegistryObject<Item> ASSEMBLY_SPEED_UPGRADE = DR.register("assembly_speed_upgrade",
            () -> new AssemblyUpgradeCardItem(new Item.Properties().stacksTo(5)));

    public static final RegistryObject<Item> ASSEMBLY_CAPACITY_UPGRADE = DR.register("assembly_capacity_upgrade",
            () -> new AssemblyUpgradeCardItem(new Item.Properties().stacksTo(10)));

    public static final RegistryObject<Item> ASSEMBLY_AUTO_UPLOAD_UPGRADE = DR.register("assembly_auto_upload_upgrade",
            () -> new AssemblyUpgradeCardItem(new Item.Properties().stacksTo(1)));

    // Supercausal Computation Core
    public static final RegistryObject<Item> COMPUTATION_CONTROLLER = DR.register("computation_controller",
            () -> new BlockItem(ModBlocks.COMPUTATION_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> CONSTANT_TENSOR_FIELD_CASING = DR.register("constant_tensor_field_casing",
            () -> new BlockItem(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), new Item.Properties()));

    public static final RegistryObject<Item> CONSTANT_SPINOR_FIELD_CASING = DR.register("constant_spinor_field_casing",
            () -> new BlockItem(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), new Item.Properties()));

    public static final RegistryObject<Item> CAUSAL_ANCHOR_CORE = DR.register("causal_anchor_core",
            () -> new BlockItem(ModBlocks.CAUSAL_ANCHOR_CORE.get(), new Item.Properties()));

    // 个人维度核心（便携进出个人维度）
    public static final RegistryObject<Item> PERSONAL_DIMENSION = DR.register("personal_dimension",
            () -> new PersonalDimensionCoreItem(new Item.Properties().stacksTo(1)));

    // 个人维度管理器
    public static final RegistryObject<Item> PERSONAL_DIMENSION_MANAGER = DR.register("personal_dimension_manager",
            () -> new BlockItem(ModBlocks.PERSONAL_DIMENSION_MANAGER.get(), new Item.Properties()));

    // 警示方块(个人维度地板预设用)
    public static final RegistryObject<Item> YELLOW_STRIPES_BLOCK_B = DR.register("yellow_stripes_block_b",
            () -> new BlockItem(ModBlocks.YELLOW_STRIPES_BLOCK_B.get(), new Item.Properties()));

    // 先进 ME 全能工具
    public static final RegistryObject<Item> ME_OMNI_TOOL = DR.register("me_omni_tool",
            () -> new AdvancedMEOmniToolItem(new Item.Properties().stacksTo(1)));

    // 测试用单方块合成 CPU：所有环境注册(可指令获取),但隐藏于创造栏与 JEI
    public static final RegistryObject<Item> TEST_CRAFTING_CPU = DR.register("test_crafting_cpu",
            () -> new BlockItem(ModBlocks.TEST_CRAFTING_CPU.get(), new Item.Properties()));

    private ModItems() {
    }
}
