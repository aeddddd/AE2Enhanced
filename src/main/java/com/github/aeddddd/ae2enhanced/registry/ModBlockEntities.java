package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyCasingBlockEntity;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.blackhole.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;

/**
 * 方块实体类型注册中心。
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> DR = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            AE2Enhanced.MOD_ID);

    public static final RegistryObject<BlockEntityType<HyperdimensionalControllerBlockEntity>> HYPERDIMENSIONAL_CONTROLLER = DR
            .register("hyperdimensional_controller",
                    () -> BlockEntityType.Builder.of(HyperdimensionalControllerBlockEntity::new,
                            ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<MultiblockMeInterfaceBlockEntity>> MULTIBLOCK_ME_INTERFACE = DR
            .register("multiblock_me_interface",
                    () -> BlockEntityType.Builder.of(MultiblockMeInterfaceBlockEntity::new,
                            ModBlocks.MULTIBLOCK_ME_INTERFACE.get()).build(null));

    public static final RegistryObject<BlockEntityType<MicroSingularityBlockEntity>> MICRO_SINGULARITY = DR.register(
            "micro_singularity",
            () -> BlockEntityType.Builder.of(MicroSingularityBlockEntity::new, ModBlocks.MICRO_SINGULARITY.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<AssemblyControllerBlockEntity>> ASSEMBLY_CONTROLLER = DR
            .register("assembly_controller",
                    () -> BlockEntityType.Builder.of(AssemblyControllerBlockEntity::new,
                            ModBlocks.ASSEMBLY_CONTROLLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<AssemblyCasingBlockEntity>> ASSEMBLY_CASING = DR
            .register("assembly_casing",
                    () -> BlockEntityType.Builder.of(AssemblyCasingBlockEntity::new,
                            ModBlocks.ASSEMBLY_CASING_1.get(), ModBlocks.ASSEMBLY_CASING_2.get(),
                            ModBlocks.ASSEMBLY_CASING_3.get(), ModBlocks.ASSEMBLY_CASING_4.get()).build(null));

    public static final RegistryObject<BlockEntityType<ComputationCoreBlockEntity>> COMPUTATION_CONTROLLER = DR
            .register("computation_controller",
                    () -> BlockEntityType.Builder.of(ComputationCoreBlockEntity::new,
                            ModBlocks.COMPUTATION_CONTROLLER.get()).build(null));

    private ModBlockEntities() {
    }
}
