package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyCasingBlock;
import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyInnerWallBlock;
import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyStabilizerBlock;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalCasingBlock;
import com.github.aeddddd.ae2enhanced.computation.block.CausalAnchorCoreBlock;
import com.github.aeddddd.ae2enhanced.computation.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.computation.block.ConstantSpinorFieldCasingBlock;
import com.github.aeddddd.ae2enhanced.computation.block.ConstantTensorFieldCasingBlock;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalSingularityCoreBlock;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlock;

/**
 * 方块注册中心。
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> DR = DeferredRegister.create(Registries.BLOCK, AE2Enhanced.MOD_ID);

    // Hyperdimensional Storage
    public static final RegistryObject<Block> HYPERDIMENSIONAL_CONTROLLER = DR.register("hyperdimensional_controller",
            () -> new HyperdimensionalControllerBlock(metalProperties()));

    public static final RegistryObject<Block> HYPERDIMENSIONAL_CASING = DR.register("hyperdimensional_casing",
            () -> new HyperdimensionalCasingBlock(metalProperties()));

    public static final RegistryObject<Block> HYPERDIMENSIONAL_SINGULARITY_CORE = DR.register(
            "hyperdimensional_singularity_core",
            () -> new HyperdimensionalSingularityCoreBlock(metalProperties().lightLevel(state -> 8)));

    // Common multiblock ME interface
    public static final RegistryObject<Block> MULTIBLOCK_ME_INTERFACE = DR.register("multiblock_me_interface",
            () -> new MultiblockMeInterfaceBlock(metalProperties()));

    // Assembly Hub
    public static final RegistryObject<Block> ASSEMBLY_CONTROLLER = DR.register("assembly_controller",
            () -> new AssemblyControllerBlock(metalProperties()));

    public static final RegistryObject<Block> ASSEMBLY_CASING_1 = DR.register("assembly_casing_1",
            () -> new AssemblyCasingBlock(metalProperties()));

    public static final RegistryObject<Block> ASSEMBLY_CASING_2 = DR.register("assembly_casing_2",
            () -> new AssemblyCasingBlock(metalProperties()));

    public static final RegistryObject<Block> ASSEMBLY_CASING_3 = DR.register("assembly_casing_3",
            () -> new AssemblyCasingBlock(metalProperties()));

    public static final RegistryObject<Block> ASSEMBLY_CASING_4 = DR.register("assembly_casing_4",
            () -> new AssemblyCasingBlock(metalProperties()));

    public static final RegistryObject<Block> ASSEMBLY_INNER_WALL = DR.register("assembly_inner_wall",
            () -> new AssemblyInnerWallBlock(metalProperties()));

    public static final RegistryObject<Block> ASSEMBLY_STABILIZER = DR.register("assembly_stabilizer",
            () -> new AssemblyStabilizerBlock(metalProperties()));

    // Supercausal Computation Core
    public static final RegistryObject<Block> COMPUTATION_CONTROLLER = DR.register("computation_controller",
            () -> new ComputationControllerBlock(metalProperties()));

    public static final RegistryObject<Block> CONSTANT_TENSOR_FIELD_CASING = DR.register("constant_tensor_field_casing",
            () -> new ConstantTensorFieldCasingBlock(metalProperties()));

    public static final RegistryObject<Block> CONSTANT_SPINOR_FIELD_CASING = DR.register("constant_spinor_field_casing",
            () -> new ConstantSpinorFieldCasingBlock(metalProperties()));

    public static final RegistryObject<Block> CAUSAL_ANCHOR_CORE = DR.register("causal_anchor_core",
            () -> new CausalAnchorCoreBlock(metalProperties().lightLevel(state -> 8)));

    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(5.0F, 10.0F)
                .requiresCorrectToolForDrops();
    }

    private ModBlocks() {
    }
}
