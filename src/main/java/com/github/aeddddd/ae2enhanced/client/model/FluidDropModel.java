package com.github.aeddddd.ae2enhanced.client.model;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister;
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
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.util.vector.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 流体假物品的 IModel / ICustomModelLoader 实现.
 * 通过 ItemOverrideList 根据 ItemStack 的 NBT 动态返回带正确流体纹理的 BakedModel.
 *
 * 设计参考 ae2fc 的 FluidPacketModel.
 */
public class FluidDropModel implements IModel {

    public static final ResourceLocation MODEL_LOCATION = new ResourceLocation(AE2Enhanced.MOD_ID, "fluid_drop");

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
        return new BakedFluidDropModel(state, format);
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
            return new FluidDropModel();
        }

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager) {
        }
    }

    protected static class BakedFluidDropModel implements IBakedModel {
        protected final Optional<TRSRTransformation> modelTransform;
        protected final VertexFormat vertexFormat;
        protected final ItemOverrideList overrides;
        protected final IBakedModel defaultOverride;

        public BakedFluidDropModel(IModelState modelState, VertexFormat vertexFormat) {
            this.modelTransform = modelState.apply(Optional.empty());
            this.vertexFormat = vertexFormat;
            this.overrides = genOverrides();
            this.defaultOverride = genDefaultOverrides();
        }

        protected ItemOverrideList genOverrides() {
            return new OverrideCache(this, vertexFormat);
        }

        protected IBakedModel genDefaultOverrides() {
            return ((OverrideCache) this.overrides).resolve(new FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return defaultOverride.getQuads(state, side, rand);
        }

        @Override
        public boolean isAmbientOcclusion() {
            return defaultOverride.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return defaultOverride.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return defaultOverride.isBuiltInRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return defaultOverride.getParticleTexture();
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return defaultOverride.getItemCameraTransforms();
        }

        @Override
        public ItemOverrideList getOverrides() {
            return overrides;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType) {
            return defaultOverride.handlePerspective(cameraTransformType);
        }
    }

    protected static class OverrideCache extends ItemOverrideList {
        private final BakedFluidDropModel parent;
        private final VertexFormat vertexFormat;
        private final Map<String, IBakedModel> cache = new HashMap<>();

        public OverrideCache(BakedFluidDropModel parent, VertexFormat vertexFormat) {
            super(Collections.emptyList());
            this.parent = parent;
            this.vertexFormat = vertexFormat;
        }

        @Nonnull
        @Override
        public IBakedModel handleItemState(@Nonnull IBakedModel originalModel, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
            if (!ItemFluidDrop.isFluidDrop(stack)) {
                return originalModel;
            }
            FluidStack fluid = FakeItemRegister.getStack(stack);
            return fluid != null ? resolve(fluid) : originalModel;
        }

        public IBakedModel resolve(FluidStack fluid) {
            String key = fluid.getFluid().getName();
            return cache.computeIfAbsent(key, k -> buildModel(fluid, parent.modelTransform, vertexFormat));
        }

        private static IBakedModel buildModel(FluidStack fluidStack, Optional<TRSRTransformation> modelTransform, VertexFormat vertexFormat) {
            net.minecraftforge.fluids.Fluid fluid = fluidStack.getFluid();
            TextureAtlasSprite sprite = null;
            if (fluid.getStill(fluidStack) != null) {
                sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(fluid.getStill(fluidStack).toString());
            }
            // fallback: 如果 still texture 缺失,尝试 flowing texture
            if (sprite == null || "missingno".equals(sprite.getIconName())) {
                if (fluid.getFlowing(fluidStack) != null) {
                    TextureAtlasSprite flowing = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(fluid.getFlowing(fluidStack).toString());
                    if (flowing != null && !"missingno".equals(flowing.getIconName())) {
                        sprite = flowing;
                    }
                }
            }
            if (sprite == null) {
                sprite = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
            }
            List<BakedQuad> quads = ItemLayerModel.getQuadsForSprite(1, sprite, vertexFormat, modelTransform);
            return new SimpleTextureBakedModel(sprite, quads, CAMERA_TRANSFORMS);
        }
    }
}
