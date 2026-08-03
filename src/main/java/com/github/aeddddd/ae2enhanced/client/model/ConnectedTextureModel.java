package com.github.aeddddd.ae2enhanced.client.model;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 内置的连接纹理模型（不依赖 CTM/OptiFine,类似 AE2 合成存储器的做法）.
 * <p>
 * 纹理为 16x16 = 256 格的 CTM 表（每格 16x16,动画时为多帧 256x256 帧）,
 * 格索引即该面 8 邻接位掩码（与生成工具约定一致）：
 * <ul>
 * <li>bit0 = 上边,bit1 = 下边,bit2 = 左边,bit3 = 右边</li>
 * <li>bit4 = 左上对角,bit5 = 右上对角,bit6 = 左下对角,bit7 = 右下对角</li>
 * <li>对角位仅在相邻两边均连接时才有意义（否则恒为 0）</li>
 * </ul>
 * 模型 JSON 格式：
 *
 * <pre>{@code
 * {
 *     "loader": "ae2enhanced:connected",
 *     "connect": "class",          // 可选："class"（同类方块,默认）或 "block"（仅同种方块）
 *     "render_type": "solid",      // 可选："solid"（默认）、"cutout" 或 "translucent"（含透明像素时使用）
 *     "textures": {
 *         "texture": "<命名空间:block/xxx_ctm>",
 *         "particle": "<命名空间:block/xxx>"
 *     }
 * }
 * }</pre>
 */
public final class ConnectedTextureModel {

    public static final ResourceLocation LOADER_ID = new ResourceLocation(AE2Enhanced.MOD_ID, "connected");

    private ConnectedTextureModel() {
    }

    public static final class Loader implements IGeometryLoader<Unbaked> {
        @Override
        public Unbaked read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            boolean connectByClass = !"block".equals(GsonHelper.getAsString(json, "connect", "class"));
            boolean ambientOcclusion = GsonHelper.getAsBoolean(json, "ambient_occlusion", true);
            String renderTypeName = GsonHelper.getAsString(json, "render_type", "solid");
            net.minecraft.client.renderer.RenderType renderType = switch (renderTypeName) {
                case "solid" -> net.minecraft.client.renderer.RenderType.solid();
                case "cutout" -> net.minecraft.client.renderer.RenderType.cutout();
                case "translucent" -> net.minecraft.client.renderer.RenderType.translucent();
                default -> throw new JsonParseException("Unknown render_type: " + renderTypeName);
            };
            return new Unbaked(connectByClass, ambientOcclusion, renderType);
        }
    }

    public record Unbaked(boolean connectByClass, boolean ambientOcclusion,
            net.minecraft.client.renderer.RenderType renderType) implements IUnbakedGeometry<Unbaked> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                ItemOverrides overrides, ResourceLocation modelLocation) {
            TextureAtlasSprite texture = spriteGetter.apply(context.getMaterial("texture"));
            Material particleMaterial = context.hasMaterial("particle")
                    ? context.getMaterial("particle")
                    : context.getMaterial("texture");
            TextureAtlasSprite particle = spriteGetter.apply(particleMaterial);
            return new Baked(texture, particle, connectByClass, ambientOcclusion, renderType, context.useBlockLight(),
                    modelState, modelLocation);
        }
    }

    public static final class Baked implements IDynamicBakedModel {
        /** 每个面 8 bit（4 边 + 4 对角）,6 个面共 48 bit */
        public static final ModelProperty<Long> CONNECTIONS = new ModelProperty<>();

        /**
         * 各面纹理平面内的 4 个相邻方向：{上, 下, 左, 右},与原版 cube 纹理朝向一致
         * （侧面 上=UP；顶面 上=NORTH；底面 上=SOUTH）.按 Direction.get3DDataValue() 索引.
         */
        private static final Direction[][] FACE_EDGES = {
                { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST }, // DOWN
                { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST }, // UP
                { Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST }, // NORTH
                { Direction.UP, Direction.DOWN, Direction.WEST, Direction.EAST }, // SOUTH
                { Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH }, // WEST
                { Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH }, // EAST
        };

        // 复刻原版 block/block 的物品展示变换（translation 单位已按原版换算为 1/16）
        private static final ItemTransforms TRANSFORMS = new ItemTransforms(
                new ItemTransform(new Vector3f(75, 45, 0), new Vector3f(0, 2.5f / 16f, 0), new Vector3f(0.375f)),
                new ItemTransform(new Vector3f(75, 45, 0), new Vector3f(0, 2.5f / 16f, 0), new Vector3f(0.375f)),
                new ItemTransform(new Vector3f(0, 225, 0), new Vector3f(), new Vector3f(0.4f)),
                new ItemTransform(new Vector3f(0, 45, 0), new Vector3f(), new Vector3f(0.4f)),
                ItemTransform.NO_TRANSFORM,
                new ItemTransform(new Vector3f(30, 225, 0), new Vector3f(), new Vector3f(0.625f)),
                new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 3f / 16f, 0), new Vector3f(0.25f)),
                new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(), new Vector3f(0.5f)));

        private final EnumMap<Direction, List<BakedQuad>[]> quads = new EnumMap<>(Direction.class);
        private final TextureAtlasSprite particle;
        private final boolean connectByClass;
        private final boolean ambientOcclusion;
        private final net.minecraft.client.renderer.RenderType renderType;
        private final boolean useBlockLight;

        @SuppressWarnings("unchecked")
        public Baked(TextureAtlasSprite texture, TextureAtlasSprite particle, boolean connectByClass,
                boolean ambientOcclusion, net.minecraft.client.renderer.RenderType renderType, boolean useBlockLight,
                ModelState modelState, ResourceLocation modelLocation) {
            this.particle = particle;
            this.connectByClass = connectByClass;
            this.ambientOcclusion = ambientOcclusion;
            this.renderType = renderType;
            this.useBlockLight = useBlockLight;

            FaceBakery faceBakery = new FaceBakery();
            for (Direction dir : Direction.values()) {
                BlockElementFace face = new BlockElementFace(dir, -1, "#texture",
                        new BlockFaceUV(new float[] { 0, 0, 16, 16 }, 0));
                BakedQuad base = faceBakery.bakeQuad(new Vector3f(0, 0, 0), new Vector3f(16, 16, 16), face, texture,
                        dir, modelState, null, true, modelLocation);
                List<BakedQuad>[] variants = new List[256];
                for (int mask = 0; mask < 256; mask++) {
                    variants[mask] = List.of(remapUv(base, texture, mask & 15, mask >> 4));
                }
                quads.put(dir, variants);
            }
        }

        /** 将整面 UV 平移缩放至 16x16 格 CTM 表中 (tileX, tileY) 对应的 16x16 子格 */
        private static BakedQuad remapUv(BakedQuad quad, TextureAtlasSprite sprite, int tileX, int tileY) {
            int[] vertices = quad.getVertices().clone();
            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float spanU = sprite.getU(16) - u0;
            float spanV = sprite.getV(16) - v0;
            for (int i = 0; i < 4; i++) {
                int offset = i * 8;
                float u = Float.intBitsToFloat(vertices[offset + 4]);
                float v = Float.intBitsToFloat(vertices[offset + 5]);
                float fracU = spanU != 0 ? (u - u0) / spanU : 0;
                float fracV = spanV != 0 ? (v - v0) / spanV : 0;
                vertices[offset + 4] = Float.floatToIntBits(sprite.getU(tileX + fracU));
                vertices[offset + 5] = Float.floatToIntBits(sprite.getV(tileY + fracV));
            }
            return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                ModelData extraData, @Nullable net.minecraft.client.renderer.RenderType renderType) {
            if (side == null) {
                return List.of();
            }
            int mask = 0;
            Long packed = extraData.get(CONNECTIONS);
            if (packed != null) {
                mask = (int) ((packed >> (side.get3DDataValue() * 8)) & 0xFF);
            }
            return quads.get(side)[mask];
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            long packed = 0;
            for (Direction face : Direction.values()) {
                Direction[] edges = FACE_EDGES[face.get3DDataValue()];
                boolean top = connects(level, pos, state, edges[0]);
                boolean bottom = connects(level, pos, state, edges[1]);
                boolean left = connects(level, pos, state, edges[2]);
                boolean right = connects(level, pos, state, edges[3]);
                int mask = (top ? 1 : 0) | (bottom ? 2 : 0) | (left ? 4 : 0) | (right ? 8 : 0);
                // 对角仅在相邻两边均连接时参与（用于内角渲染）
                if (top && left && connects(level, pos.relative(edges[0]).relative(edges[2]), state)) {
                    mask |= 16;
                }
                if (top && right && connects(level, pos.relative(edges[0]).relative(edges[3]), state)) {
                    mask |= 32;
                }
                if (bottom && left && connects(level, pos.relative(edges[1]).relative(edges[2]), state)) {
                    mask |= 64;
                }
                if (bottom && right && connects(level, pos.relative(edges[1]).relative(edges[3]), state)) {
                    mask |= 128;
                }
                packed |= (long) mask << (face.get3DDataValue() * 8);
            }
            return modelData.derive().with(CONNECTIONS, packed).build();
        }

        private boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction dir) {
            return connects(level, pos.relative(dir), state);
        }

        private boolean connects(BlockAndTintGetter level, BlockPos neighborPos, BlockState state) {
            Block neighbor = level.getBlockState(neighborPos).getBlock();
            Block self = state.getBlock();
            return connectByClass ? neighbor.getClass() == self.getClass() : neighbor == self;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ambientOcclusion;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return useBlockLight;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        public ItemTransforms getTransforms() {
            return TRANSFORMS;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            return ChunkRenderTypeSet.of(renderType);
        }
    }
}
