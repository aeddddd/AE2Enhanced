package com.github.aeddddd.ae2enhanced.data.client;

import java.util.function.Supplier;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.CustomLoaderBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.model.ConnectedTextureModel;
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
        connectedBlock(ModBlocks.HYPERDIMENSIONAL_CASING);
        simpleBlock(ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get());
        simpleBlock(ModBlocks.MULTIBLOCK_ME_INTERFACE.get());
        connectedBlock(ModBlocks.ASSEMBLY_CASING_1);
        connectedBlock(ModBlocks.ASSEMBLY_CASING_2);
        connectedBlock(ModBlocks.ASSEMBLY_CASING_3);
        connectedBlock(ModBlocks.ASSEMBLY_CASING_4);
        connectedBlock(ModBlocks.ASSEMBLY_INNER_WALL);
        connectedBlock(ModBlocks.ASSEMBLY_STABILIZER);
        simpleBlock(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get());
        simpleBlock(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get());
        simpleBlock(ModBlocks.CAUSAL_ANCHOR_CORE.get());

        horizontalBlock(ModBlocks.ASSEMBLY_CONTROLLER.get(), models().getExistingFile(modLoc("block/assembly_controller")));
        horizontalBlock(ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get(), models().getExistingFile(modLoc("block/hyperdimensional_controller")));
        horizontalBlock(ModBlocks.COMPUTATION_CONTROLLER.get(), models().getExistingFile(modLoc("block/computation_controller")));
    }

    /**
     * 生成使用 {@code ae2enhanced:connected} 连接纹理模型的方块状态与模型。
     */
    private void connectedBlock(Supplier<Block> block) {
        Block b = block.get();
        String name = ForgeRegistries.BLOCKS.getKey(b).getPath();
        BlockModelBuilder model = models().getBuilder("block/" + name)
                .customLoader(ConnectedLoaderBuilder::new)
                .end();
        model.texture("texture", modLoc("block/" + name + "_ctm"));
        model.texture("particle", modLoc("block/" + name));
        simpleBlock(b, model);
    }

    private void simpleBlock(Supplier<Block> block) {
        simpleBlock(block.get());
    }

    private static class ConnectedLoaderBuilder extends CustomLoaderBuilder<BlockModelBuilder> {
        ConnectedLoaderBuilder(BlockModelBuilder parent, ExistingFileHelper existingFileHelper) {
            super(ConnectedTextureModel.LOADER_ID, parent, existingFileHelper);
        }
    }
}
