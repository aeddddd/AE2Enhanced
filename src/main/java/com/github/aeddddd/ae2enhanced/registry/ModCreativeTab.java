package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.DevEnvironment;

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
                        output.accept(new ItemStack(ModItems.MICRO_SINGULARITY.get()));

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

                        // Supercausal Computation Core —— 多方块功能异常,已临时下线并隐藏结构方块
                        // （保留注册以兼容既有存档,仅不出现在创造栏）

                        // 【仅开发环境】测试用单方块合成 CPU
                        if (DevEnvironment.isDev() && ModItems.TEST_CRAFTING_CPU != null) {
                            output.accept(new ItemStack(ModItems.TEST_CRAFTING_CPU.get()));
                        }
                    })
                    .build());

    private ModCreativeTab() {
    }
}
