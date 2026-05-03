package com.github.aeddddd.ae2enhanced;

import com.github.aeddddd.ae2enhanced.block.*;
import com.github.aeddddd.ae2enhanced.item.ItemBlockMicroSingularity;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalMeInterface;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileSuperCraftingInterface;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class ModBlocks {

    public static BlockAssemblyController ASSEMBLY_CONTROLLER;
    public static BlockAssemblyMeInterface ASSEMBLY_ME_INTERFACE;
    public static BlockAssemblyCasing ASSEMBLY_CASING;
    public static BlockAssemblyInnerWall ASSEMBLY_INNER_WALL;
    public static BlockAssemblyStabilizer ASSEMBLY_STABILIZER;
    public static BlockMicroSingularity MICRO_SINGULARITY;

    public static BlockHyperdimensionalController HYPERDIMENSIONAL_CONTROLLER;
    public static BlockHyperdimensionalMeInterface HYPERDIMENSIONAL_ME_INTERFACE;
    public static BlockHyperdimensionalCasing HYPERDIMENSIONAL_CASING;
    public static BlockHyperdimensionalSingularityCore HYPERDIMENSIONAL_SINGULARITY_CORE;

    // 第三阶段：超因果计算核心
    public static BlockComputationCore COMPUTATION_CORE;
    public static BlockConstantTensorFieldCasing CONSTANT_TENSOR_FIELD_CASING;
    public static BlockConstantSpinorFieldCasing CONSTANT_SPINOR_FIELD_CASING;
    public static BlockCausalAnchorCore CAUSAL_ANCHOR_CORE;
    public static BlockSuperCraftingInterface SUPER_CRAFTING_INTERFACE;

    public static void init() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(
            ASSEMBLY_CONTROLLER = new BlockAssemblyController(),
            ASSEMBLY_ME_INTERFACE = new BlockAssemblyMeInterface(),
            ASSEMBLY_CASING = new BlockAssemblyCasing(),
            ASSEMBLY_INNER_WALL = new BlockAssemblyInnerWall(),
            ASSEMBLY_STABILIZER = new BlockAssemblyStabilizer(),
            MICRO_SINGULARITY = new BlockMicroSingularity(),
            HYPERDIMENSIONAL_CONTROLLER = new BlockHyperdimensionalController(),
            HYPERDIMENSIONAL_ME_INTERFACE = new BlockHyperdimensionalMeInterface(),
            HYPERDIMENSIONAL_CASING = new BlockHyperdimensionalCasing(),
            HYPERDIMENSIONAL_SINGULARITY_CORE = new BlockHyperdimensionalSingularityCore(),
            COMPUTATION_CORE = new BlockComputationCore(),
            CONSTANT_TENSOR_FIELD_CASING = new BlockConstantTensorFieldCasing(),
            CONSTANT_SPINOR_FIELD_CASING = new BlockConstantSpinorFieldCasing(),
            CAUSAL_ANCHOR_CORE = new BlockCausalAnchorCore(),
            SUPER_CRAFTING_INTERFACE = new BlockSuperCraftingInterface()
        );

        GameRegistry.registerTileEntity(TileAssemblyController.class, AE2Enhanced.MOD_ID + ":assembly_controller");
        GameRegistry.registerTileEntity(TileAssemblyMeInterface.class, AE2Enhanced.MOD_ID + ":assembly_me_interface");
        GameRegistry.registerTileEntity(TileMicroSingularity.class, AE2Enhanced.MOD_ID + ":micro_singularity");
        GameRegistry.registerTileEntity(TileHyperdimensionalController.class, AE2Enhanced.MOD_ID + ":hyperdimensional_controller");
        GameRegistry.registerTileEntity(TileHyperdimensionalMeInterface.class, AE2Enhanced.MOD_ID + ":hyperdimensional_me_interface");
        GameRegistry.registerTileEntity(TileComputationCore.class, AE2Enhanced.MOD_ID + ":computation_core");
        GameRegistry.registerTileEntity(TileSuperCraftingInterface.class, AE2Enhanced.MOD_ID + ":super_crafting_interface");
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(
            new ItemBlock(ASSEMBLY_CONTROLLER).setRegistryName(ASSEMBLY_CONTROLLER.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(ASSEMBLY_ME_INTERFACE).setRegistryName(ASSEMBLY_ME_INTERFACE.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(ASSEMBLY_CASING).setRegistryName(ASSEMBLY_CASING.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(ASSEMBLY_INNER_WALL).setRegistryName(ASSEMBLY_INNER_WALL.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(ASSEMBLY_STABILIZER).setRegistryName(ASSEMBLY_STABILIZER.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlockMicroSingularity(MICRO_SINGULARITY).setRegistryName(MICRO_SINGULARITY.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(HYPERDIMENSIONAL_CONTROLLER).setRegistryName(HYPERDIMENSIONAL_CONTROLLER.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(HYPERDIMENSIONAL_ME_INTERFACE).setRegistryName(HYPERDIMENSIONAL_ME_INTERFACE.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(HYPERDIMENSIONAL_CASING).setRegistryName(HYPERDIMENSIONAL_CASING.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(HYPERDIMENSIONAL_SINGULARITY_CORE).setRegistryName(HYPERDIMENSIONAL_SINGULARITY_CORE.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(COMPUTATION_CORE).setRegistryName(COMPUTATION_CORE.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(CONSTANT_TENSOR_FIELD_CASING).setRegistryName(CONSTANT_TENSOR_FIELD_CASING.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(CONSTANT_SPINOR_FIELD_CASING).setRegistryName(CONSTANT_SPINOR_FIELD_CASING.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(CAUSAL_ANCHOR_CORE).setRegistryName(CAUSAL_ANCHOR_CORE.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS),
            new ItemBlock(SUPER_CRAFTING_INTERFACE).setRegistryName(SUPER_CRAFTING_INTERFACE.getRegistryName()).setCreativeTab(CreativeTabs.BUILDING_BLOCKS)
        );
    }
}
