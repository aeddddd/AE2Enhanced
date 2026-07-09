package com.github.aeddddd.ae2enhanced.data.client;

import java.util.function.Supplier;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

/**
 * 方块状态数据生成器。
 * <p>为所有注册方块生成默认方块状态与模型引用。</p>
 */
public class AE2EBlockStateProvider extends BlockStateProvider {

    public AE2EBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AE2Enhanced.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlock(ModBlocks.HYPERDIMENSIONAL_CASING.get());
        simpleBlock(ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get());
        simpleBlock(ModBlocks.MULTIBLOCK_ME_INTERFACE.get());
        simpleBlock(ModBlocks.ASSEMBLY_CASING_1.get());
        simpleBlock(ModBlocks.ASSEMBLY_CASING_2.get());
        simpleBlock(ModBlocks.ASSEMBLY_CASING_3.get());
        simpleBlock(ModBlocks.ASSEMBLY_CASING_4.get());
        simpleBlock(ModBlocks.ASSEMBLY_INNER_WALL.get());
        simpleBlock(ModBlocks.ASSEMBLY_STABILIZER.get());
        simpleBlock(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get());
        simpleBlock(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get());
        simpleBlock(ModBlocks.CAUSAL_ANCHOR_CORE.get());

        horizontalBlock(ModBlocks.ASSEMBLY_CONTROLLER.get(), models().getExistingFile(modLoc("block/assembly_controller")));
        horizontalBlock(ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get(), models().getExistingFile(modLoc("block/hyperdimensional_controller")));
        horizontalBlock(ModBlocks.COMPUTATION_CONTROLLER.get(), models().getExistingFile(modLoc("block/computation_controller")));
    }

    private void simpleBlock(Supplier<Block> block) {
        simpleBlock(block.get());
    }
}
