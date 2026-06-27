package com.github.aeddddd.ae2enhanced.client.model;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.util.vector.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Starlight 假物品的 IModel / ICustomModelLoader 实现.
 * 固定使用 Astral Sorcery liquid_starlight 流体精灵图.
 */
public class StarlightDropModel implements IModel {

    public static final ResourceLocation MODEL_LOCATION = new ResourceLocation(AE2Enhanced.MOD_ID, "starlight_drop");
    public static final ResourceLocation FLUID_LOCATION = new ResourceLocation("astralsorcery", "blocks/fluid/starlight_still");

    protected static final ItemCameraTransforms CAMERA_TRANSFORMS = new ItemCameraTransforms(
            new ItemTransformVec3f(new Vector3f(0, 0, 0), new Vector3f(0, 0.1875f, 0.0625f), new Vector3f(0.55f, 0.55f, 0.55f)),
            new ItemTransformVec3f(new Vector3f(0, 0, 0), new Vector3f(0, 0.1875f, 0.0625f), new Vector3f(0.55f, 0.55f, 0.55f)),
            new ItemTransformVec3f(new Vector3f(0, -90, 25), new Vector3f(0.070625f, 0.2f, 0.070625f), new Vector3f(0.68f, 0.68f, 0.68f)),
            new ItemTransformVec3f(new Vector3f(0, -90, 25), new Vector3f(0.070625f, 0.2f, 0.070625f), new Vector3f(0.68f, 0.68f, 0.68f)),
            new ItemTransformVec3f(new Vector3f(0, 180, 0), new Vector3f(0, 0.8125f, 0.4375f), new Vector3f(1, 1, 1)),
            ItemTransformVec3f.DEFAULT,
            new ItemTransformVec3f(new Vector3f(0, 0, 0), new Vector3f(0, 0.125f, 0), new Vector3f(0.5f, 0.5f, 0.5f)),
            new ItemTransformVec3f(new Vector3f(0, 180, 0), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1))
    );

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return new BakedStarlightDropModel(state, format);
    }

    public static class Loader implements ICustomModelLoader {
        @Override
        public boolean accepts(ResourceLocation modelLocation) {
            String loc = modelLocation.toString();
            String expected = MODEL_LOCATION.toString();
            return loc.equals(expected) || loc.startsWith(expected + "#");
        }

        @Override
        public IModel loadModel(ResourceLocation modelLocation) {
            return new StarlightDropModel();
        }

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager) {
        }
    }

    protected static class BakedStarlightDropModel implements IBakedModel {
        protected final Optional<TRSRTransformation> modelTransform;
        protected final VertexFormat vertexFormat;
        protected final IBakedModel defaultModel;

        public BakedStarlightDropModel(IModelState modelState, VertexFormat vertexFormat) {
            this.modelTransform = modelState.apply(Optional.empty());
            this.vertexFormat = vertexFormat;
            this.defaultModel = buildModel(vertexFormat, modelTransform);
        }

        private static IBakedModel buildModel(VertexFormat vertexFormat, Optional<TRSRTransformation> modelTransform) {
            TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(FLUID_LOCATION.toString());
            if (sprite == null || "missingno".equals(sprite.getIconName())) {
                sprite = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
            }
            List<BakedQuad> quads = ItemLayerModel.getQuadsForSprite(1, sprite, vertexFormat, modelTransform);
            return new SimpleTextureBakedModel(sprite, quads, CAMERA_TRANSFORMS);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return defaultModel.getQuads(state, side, rand);
        }

        @Override
        public boolean isAmbientOcclusion() {
            return defaultModel.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return defaultModel.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return defaultModel.isBuiltInRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return defaultModel.getParticleTexture();
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return defaultModel.getItemCameraTransforms();
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType) {
            return defaultModel.handlePerspective(cameraTransformType);
        }
    }
}
