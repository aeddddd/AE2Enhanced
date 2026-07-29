package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;


import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyCasingBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.PersonalDimensionManagerBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.ComputationCasingBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.VirtualCraftingCpuBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.MultiblockMeInterfaceBlockEntity;

/**
 * 方块实体类型注册中心.
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

    public static final RegistryObject<BlockEntityType<ComputationCasingBlockEntity>> COMPUTATION_CASING = DR
            .register("computation_casing",
                    () -> BlockEntityType.Builder.of(ComputationCasingBlockEntity::new,
                            ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(),
                            ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(),
                            ModBlocks.CAUSAL_ANCHOR_CORE.get(),
                            ModBlocks.CASING_GLASS.get()).build(null));

    // 个人维度管理器
    public static final RegistryObject<BlockEntityType<PersonalDimensionManagerBlockEntity>> PERSONAL_DIMENSION_MANAGER = DR
            .register("personal_dimension_manager",
                    () -> BlockEntityType.Builder.of(PersonalDimensionManagerBlockEntity::new,
                            ModBlocks.PERSONAL_DIMENSION_MANAGER.get()).build(null));

    // 单方块虚拟合成 CPU,与 ModBlocks.VIRTUAL_CRAFTING_CPU 同步注册(所有环境)
    public static final RegistryObject<BlockEntityType<VirtualCraftingCpuBlockEntity>> VIRTUAL_CRAFTING_CPU = DR
            .register("virtual_crafting_cpu",
                    () -> BlockEntityType.Builder.of(VirtualCraftingCpuBlockEntity::new,
                            ModBlocks.VIRTUAL_CRAFTING_CPU.get()).build(null));

    private ModBlockEntities() {
    }
}
