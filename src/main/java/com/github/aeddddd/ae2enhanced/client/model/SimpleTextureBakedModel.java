package com.github.aeddddd.ae2enhanced.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 极简的纹理 BakedModel：只显示单层 sprite 的 2D 物品模型.
 * 用于 FluidDrop / GasDrop / EssentiaDrop 等假物品的动态模型渲染.
 *
 * 消除 FluidDropModel.OverrideModel 与 GasDropModel.OverrideModel 的重复 IBakedModel 实现.
 */
public class SimpleTextureBakedModel implements IBakedModel {

    private final TextureAtlasSprite texture;
    private final List<BakedQuad> quads;
    private final ItemCameraTransforms transforms;

    public SimpleTextureBakedModel(TextureAtlasSprite texture, List<BakedQuad> quads, ItemCameraTransforms transforms) {
        this.texture = texture;
        this.quads = quads;
        this.transforms = transforms;
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return quads;
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
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return texture;
    }

    @Nonnull
    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return transforms;
    }

    @Nonnull
    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }
}
