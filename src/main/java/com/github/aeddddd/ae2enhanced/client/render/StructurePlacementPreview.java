package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.block.BlockAssemblyController;
import com.github.aeddddd.ae2enhanced.block.BlockComputationCore;
import com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多方块结构缺失方块幽灵投影渲染器。
 * 当玩家位于已放置的控制器 32 格范围内时，自动渲染该控制器对应结构中
 * 所有缺失方块的半透明缩小模型，帮助玩家补全建造。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class StructurePlacementPreview {

    private static final double MAX_PREVIEW_DISTANCE = 32.0;
    private static final float GHOST_ALPHA = 0.18f;
    private static final float GHOST_SCALE = 0.82f;

    private static final IBlockAccess GHOST_WORLD = new GhostBlockAccess();
    private static final Map<Block, IBakedModel> SCALED_MODEL_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        List<TileEntity> nearby = collectNearbyControllers(player);
        if (nearby.isEmpty()) return;

        // 阶段 1：收集所有缺失方块
        List<GhostBlock> ghosts = new ArrayList<>();
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();

        for (TileEntity te : nearby) {
            net.minecraft.world.World world = te.getWorld();
            BlockPos pos = te.getPos();
            IBlockState state = world.getBlockState(pos);

            if (te instanceof TileAssemblyController) {
                EnumFacing facing = state.getValue(BlockAssemblyController.FACING);
                collectAssemblyGhosts(world, pos, facing, ghosts, dispatcher);
            } else if (te instanceof TileHyperdimensionalController) {
                EnumFacing facing = state.getValue(BlockHyperdimensionalController.FACING);
                collectHyperdimensionalGhosts(world, pos, facing, ghosts, dispatcher);
            } else if (te instanceof TileComputationCore) {
                EnumFacing facing = state.getValue(BlockComputationCore.FACING).getOpposite();
                collectSupercausalGhosts(world, pos, facing, ghosts, dispatcher);
            }
        }

        if (ghosts.isEmpty()) return;

        // 阶段 2：按到相机的距离从远到近排序
        double camX = player.posX;
        double camY = player.posY + player.getEyeHeight();
        double camZ = player.posZ;
        Collections.sort(ghosts, (a, b) -> {
            double da = distanceSqTo(camX, camY, camZ, a.pos);
            double db = distanceSqTo(camX, camY, camZ, b.pos);
            return Double.compare(db, da); // 远的在前
        });

        // 阶段 3：统一渲染
        double rx = mc.getRenderManager().viewerPosX;
        double ry = mc.getRenderManager().viewerPosY;
        double rz = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-rx, -ry, -rz);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1f, 1f, 1f, GHOST_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        try {
            BlockModelRenderer modelRenderer = dispatcher.getBlockModelRenderer();
            for (GhostBlock ghost : ghosts) {
                buffer.setTranslation(ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ());
                modelRenderer.renderModel(GHOST_WORLD, ghost.model, ghost.state, BlockPos.ORIGIN, buffer, false);
            }
        } finally {
            buffer.setTranslation(0, 0, 0);
            tessellator.draw();

            GlStateManager.depthMask(true);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private static double distanceSqTo(double x, double y, double z, BlockPos pos) {
        double dx = pos.getX() + 0.5 - x;
        double dy = pos.getY() + 0.5 - y;
        double dz = pos.getZ() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static List<TileEntity> collectNearbyControllers(EntityPlayer player) {
        List<TileEntity> result = new ArrayList<>();
        double maxDistSq = MAX_PREVIEW_DISTANCE * MAX_PREVIEW_DISTANCE;
        net.minecraft.world.World world = player.world;

        for (TileEntity te : world.loadedTileEntityList) {
            if (te.isInvalid()) continue;
            if (!(te instanceof TileAssemblyController)
                && !(te instanceof TileHyperdimensionalController)
                && !(te instanceof TileComputationCore)) {
                continue;
            }
            BlockPos pos = te.getPos();
            double distSq = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq <= maxDistSq) {
                result.add(te);
            }
        }
        return result;
    }

    private static void collectAssemblyGhosts(net.minecraft.world.World world, BlockPos controllerPos, EnumFacing facing,
                                               List<GhostBlock> out, BlockRendererDispatcher dispatcher) {
        BlockPos origin = controllerPos.add(rotate(new BlockPos(0, 0, 7), facing));
        collectGhostSet(world, origin, facing, AssemblyStructure.CORE_SET, ModBlocks.ASSEMBLY_CONTROLLER, out, dispatcher);
        collectGhostSet(world, origin, facing, AssemblyStructure.PART1_SET, ModBlocks.ASSEMBLY_ME_INTERFACE, out, dispatcher);
        collectGhostSet(world, origin, facing, AssemblyStructure.PART2_SET, ModBlocks.ASSEMBLY_CASING, out, dispatcher);
        collectGhostSet(world, origin, facing, AssemblyStructure.PART3_SET, ModBlocks.ASSEMBLY_INNER_WALL, out, dispatcher);
        collectGhostSet(world, origin, facing, AssemblyStructure.PART4_SET, ModBlocks.ASSEMBLY_STABILIZER, out, dispatcher);
    }

    private static void collectHyperdimensionalGhosts(net.minecraft.world.World world, BlockPos controllerPos, EnumFacing facing,
                                                       List<GhostBlock> out, BlockRendererDispatcher dispatcher) {
        collectGhostSet(world, controllerPos, facing, HyperdimensionalStructure.CONTROLLER_SET, ModBlocks.HYPERDIMENSIONAL_CONTROLLER, out, dispatcher);
        collectGhostSet(world, controllerPos, facing, HyperdimensionalStructure.ME_INTERFACE_SET, ModBlocks.HYPERDIMENSIONAL_ME_INTERFACE, out, dispatcher);
        collectGhostSet(world, controllerPos, facing, HyperdimensionalStructure.CORE_SET, ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE, out, dispatcher);
        collectGhostSet(world, controllerPos, facing, HyperdimensionalStructure.CASING_SET, ModBlocks.HYPERDIMENSIONAL_CASING, out, dispatcher);
    }

    private static void collectSupercausalGhosts(net.minecraft.world.World world, BlockPos controllerPos, EnumFacing facing,
                                                  List<GhostBlock> out, BlockRendererDispatcher dispatcher) {
        BlockPos meActual = controllerPos.add(rotate(SupercausalStructure.ME_INTERFACE_REL, facing));
        collectGhostBlock(world, meActual, ModBlocks.SUPER_CRAFTING_INTERFACE, out, dispatcher);

        for (BlockPos rel : SupercausalStructure.TENSOR_CASING_SET) {
            if (rel.equals(SupercausalStructure.CONTROLLER_REL)) continue;
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            collectGhostBlock(world, actual, ModBlocks.CONSTANT_TENSOR_FIELD_CASING, out, dispatcher);
        }

        for (BlockPos rel : SupercausalStructure.CAUSAL_ANCHOR_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            collectGhostBlock(world, actual, ModBlocks.CAUSAL_ANCHOR_CORE, out, dispatcher);
        }

        for (BlockPos rel : SupercausalStructure.SPINOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            collectGhostBlock(world, actual, ModBlocks.CONSTANT_SPINOR_FIELD_CASING, out, dispatcher);
        }
    }

    private static void collectGhostSet(net.minecraft.world.World world, BlockPos origin, EnumFacing facing,
                                        Set<BlockPos> relSet, Block expected,
                                        List<GhostBlock> out, BlockRendererDispatcher dispatcher) {
        for (BlockPos rel : relSet) {
            BlockPos actual = origin.add(rotate(rel, facing));
            collectGhostBlock(world, actual, expected, out, dispatcher);
        }
    }

    private static void collectGhostBlock(net.minecraft.world.World world, BlockPos actual, Block expected,
                                          List<GhostBlock> out, BlockRendererDispatcher dispatcher) {
        IBlockState actualState = world.getBlockState(actual);
        if (actualState.getBlock() == expected) return;

        IBlockState state = expected.getDefaultState();
        IBakedModel scaledModel = getScaledModel(dispatcher, expected);
        out.add(new GhostBlock(actual, state, scaledModel));
    }

    private static IBakedModel getScaledModel(BlockRendererDispatcher dispatcher, Block block) {
        return SCALED_MODEL_CACHE.computeIfAbsent(block, b ->
            new ScaledBakedModel(dispatcher.getModelForState(b.getDefaultState()), GHOST_SCALE)
        );
    }

    private static BlockPos rotate(BlockPos rel, EnumFacing facing) {
        if (facing == EnumFacing.NORTH) return rel;
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        switch (facing) {
            case SOUTH: return new BlockPos(-x, y, -z);
            case EAST:  return new BlockPos(-z, y, x);
            case WEST:  return new BlockPos(z, y, -x);
            default:    return rel;
        }
    }

    /**
     * 代表一个待渲染的幽灵方块。
     */
    private static class GhostBlock {
        final BlockPos pos;
        final IBlockState state;
        final IBakedModel model;

        GhostBlock(BlockPos pos, IBlockState state, IBakedModel model) {
            this.pos = pos;
            this.state = state;
            this.model = model;
        }
    }

    /**
     * 缩放 BakedModel：将所有 quad 顶点向方块中心 (0.5, 0.5, 0.5) 收缩。
     */
    private static class ScaledBakedModel implements IBakedModel {
        private final IBakedModel original;
        private final float scale;

        ScaledBakedModel(IBakedModel original, float scale) {
            this.original = original;
            this.scale = scale;
        }

        @Override
        public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            List<BakedQuad> quads = original.getQuads(state, side, rand);
            if (quads.isEmpty()) return quads;
            List<BakedQuad> result = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                result.add(scaleQuad(quad));
            }
            return result;
        }

        private BakedQuad scaleQuad(BakedQuad quad) {
            int[] data = quad.getVertexData().clone();
            int stride = 7; // BLOCK format: 3f pos + 4b color + 2f uv + 2s lightmap = 28 bytes = 7 ints
            for (int i = 0; i < 4; i++) {
                int idx = i * stride;
                float x = Float.intBitsToFloat(data[idx]);
                float y = Float.intBitsToFloat(data[idx + 1]);
                float z = Float.intBitsToFloat(data[idx + 2]);
                data[idx]     = Float.floatToRawIntBits(0.5f + (x - 0.5f) * scale);
                data[idx + 1] = Float.floatToRawIntBits(0.5f + (y - 0.5f) * scale);
                data[idx + 2] = Float.floatToRawIntBits(0.5f + (z - 0.5f) * scale);
            }
            return new BakedQuad(data, quad.getTintIndex(), quad.getFace(), quad.getSprite(), quad.shouldApplyDiffuseLighting(), quad.getFormat());
        }

        @Override public boolean isAmbientOcclusion() { return original.isAmbientOcclusion(); }
        @Override public boolean isGui3d() { return original.isGui3d(); }
        @Override public boolean isBuiltInRenderer() { return original.isBuiltInRenderer(); }
        @Override public TextureAtlasSprite getParticleTexture() { return original.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return original.getItemCameraTransforms(); }
        @Override public ItemOverrideList getOverrides() { return original.getOverrides(); }
    }

    /**
     * 伪 IBlockAccess，用于幽灵方块渲染。
     * 所有位置返回空气（不触发面剔除）与全亮光照。
     */
    private static class GhostBlockAccess implements IBlockAccess {
        @Override
        public net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos) { return null; }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) { return 0xF000F0; }

        @Override
        public IBlockState getBlockState(BlockPos pos) { return Blocks.AIR.getDefaultState(); }

        @Override
        public boolean isAirBlock(BlockPos pos) { return true; }

        @Override
        public Biome getBiome(BlockPos pos) { return Biomes.PLAINS; }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) { return 0; }

        @Override
        public WorldType getWorldType() { return WorldType.DEFAULT; }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) { return false; }
    }
}
