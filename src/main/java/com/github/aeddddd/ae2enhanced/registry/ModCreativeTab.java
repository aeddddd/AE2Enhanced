package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 创造模式物品栏注册中心.
 */
public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> DR = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            AE2Enhanced.MOD_ID);

    public static final RegistryObject<CreativeModeTab> AE2E_TAB = DR.register("ae2enhanced",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.ae2enhanced"))
                    .icon(() -> new ItemStack(ModItems.HYPERDIMENSIONAL_CONTROLLER.get()))
                    .displayItems((params, output) -> {
                        // 指南书
                        output.accept(new ItemStack(ModItems.GUIDE.get()));

                        // Hyperdimensional Storage
                        output.accept(new ItemStack(ModItems.HYPERDIMENSIONAL_CONTROLLER.get()));
                        output.accept(new ItemStack(ModItems.MULTIBLOCK_ME_INTERFACE.get()));
                        output.accept(new ItemStack(ModItems.HYPERDIMENSIONAL_CASING.get()));
                        output.accept(new ItemStack(ModItems.HYPERDIMENSIONAL_SINGULARITY_CORE.get()));

                        // Black Hole
                        output.accept(new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD.get()));
                        output.accept(new ItemStack(ModItems.DIFFERENTIAL_FORM_STABILIZER.get()));
                        output.accept(new ItemStack(ModItems.CONFORMAL_INVARIANT_CHARGE.get()));
                        output.accept(new ItemStack(ModItems.SINGULARITY_CONSTRICTOR.get()));
                        output.accept(com.github.aeddddd.ae2enhanced.item.MicroSingularityItem.createStack(
                                com.github.aeddddd.ae2enhanced.blackhole.blockentity.MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS,
                                false));

                        // Assembly Hub
                        output.accept(new ItemStack(ModItems.ASSEMBLY_CONTROLLER.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_CASING_1.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_CASING_2.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_CASING_3.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_CASING_4.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_INNER_WALL.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_STABILIZER.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_PARALLEL_UPGRADE.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_SPEED_UPGRADE.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_CAPACITY_UPGRADE.get()));
                        output.accept(new ItemStack(ModItems.ASSEMBLY_AUTO_UPLOAD_UPGRADE.get()));

                        // 个人维度
                        output.accept(new ItemStack(ModItems.PERSONAL_DIMENSION.get()));
                        output.accept(new ItemStack(ModItems.PERSONAL_DIMENSION_MANAGER.get()));
                        output.accept(new ItemStack(ModItems.YELLOW_STRIPES_BLOCK_B.get()));

                        // 先进 ME 全能工具
                        output.accept(new ItemStack(ModItems.ME_OMNI_TOOL.get()));

                        // Supercausal Computation Core
                        output.accept(new ItemStack(ModItems.COMPUTATION_CONTROLLER.get()));
                        output.accept(new ItemStack(ModItems.CONSTANT_TENSOR_FIELD_CASING.get()));
                        output.accept(new ItemStack(ModItems.CONSTANT_SPINOR_FIELD_CASING.get()));
                        output.accept(new ItemStack(ModItems.CASING_GLASS.get()));
                        output.accept(new ItemStack(ModItems.CAUSAL_ANCHOR_CORE.get()));

                        // 测试用单方块合成 CPU 仅通过指令获取,不出现在创造栏(见 ModItems.TEST_CRAFTING_CPU)
                    })
                    .build());

    private ModCreativeTab() {
    }
}
