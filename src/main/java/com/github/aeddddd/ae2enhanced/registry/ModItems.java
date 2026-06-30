package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 物品注册中心。
 */
public final class ModItems {
    public static final DeferredRegister<Item> DR = DeferredRegister.create(Registries.ITEM, AE2Enhanced.MOD_ID);

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

    // Assembly Hub
    public static final RegistryObject<Item> ASSEMBLY_CONTROLLER = DR.register("assembly_controller",
            () -> new BlockItem(ModBlocks.ASSEMBLY_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_CASING = DR.register("assembly_casing",
            () -> new BlockItem(ModBlocks.ASSEMBLY_CASING.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_INNER_WALL = DR.register("assembly_inner_wall",
            () -> new BlockItem(ModBlocks.ASSEMBLY_INNER_WALL.get(), new Item.Properties()));

    public static final RegistryObject<Item> ASSEMBLY_STABILIZER = DR.register("assembly_stabilizer",
            () -> new BlockItem(ModBlocks.ASSEMBLY_STABILIZER.get(), new Item.Properties()));

    // Supercausal Computation Core
    public static final RegistryObject<Item> COMPUTATION_CONTROLLER = DR.register("computation_controller",
            () -> new BlockItem(ModBlocks.COMPUTATION_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> CONSTANT_TENSOR_FIELD_CASING = DR.register("constant_tensor_field_casing",
            () -> new BlockItem(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), new Item.Properties()));

    public static final RegistryObject<Item> CONSTANT_SPINOR_FIELD_CASING = DR.register("constant_spinor_field_casing",
            () -> new BlockItem(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), new Item.Properties()));

    public static final RegistryObject<Item> CAUSAL_ANCHOR_CORE = DR.register("causal_anchor_core",
            () -> new BlockItem(ModBlocks.CAUSAL_ANCHOR_CORE.get(), new Item.Properties()));

    private ModItems() {
    }
}
