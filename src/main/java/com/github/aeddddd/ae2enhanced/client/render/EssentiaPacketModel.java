package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;

import java.util.Collections;
import java.util.function.Function;

/**
 * EssentiaPacket 的模型占位符. 不通过 getQuads() 渲染, 该路径在 AE2 终端中异常会被静默吞掉,
 * 而是通过 isBuiltInRenderer()=true 让 RenderItem 走 TileEntityItemStackRenderer.
 */
public class EssentiaPacketModel implements IModel {

    public static final ResourceLocation MODEL_LOCATION = new ResourceLocation(AE2Enhanced.MOD_ID, "models/essentia_drop");

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return new BakedEssentiaPacketModel();
    }

    public static class Loader implements net.minecraftforge.client.model.ICustomModelLoader {
        @Override
        public boolean accepts(ResourceLocation modelLocation) {
            return modelLocation.compareTo(MODEL_LOCATION) == 0;
        }

        @Override
        public IModel loadModel(ResourceLocation modelLocation) {
            return new EssentiaPacketModel();
        }

        @Override
        public void onResourceManagerReload(net.minecraft.client.resources.IResourceManager resourceManager) {
        }
    }

    /**
     * BakedModel 占位: isBuiltInRenderer()=true, 其余全部返回空值.
     * RenderItem 发现后走 TileEntityItemStackRenderer, 进入 EssentiaItemRenderer.
     */
    public static class BakedEssentiaPacketModel implements IBakedModel {

        @Override
        public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
                net.minecraft.block.state.IBlockState state,
                net.minecraft.util.EnumFacing side,
                long rand) {
            return Collections.emptyList();
        }

        @Override
        public boolean isAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return true; // RenderItem 据此走 TileEntityItemStackRenderer 路径
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return null;
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return ItemCameraTransforms.DEFAULT;
        }
    }
}
