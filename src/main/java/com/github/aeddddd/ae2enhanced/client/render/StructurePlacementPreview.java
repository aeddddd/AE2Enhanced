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
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.block.model.IBakedModel;
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
import java.util.List;
import java.util.Set;

/**
 * 多方块结构缺失方块幽灵投影渲染器。
 * 当玩家位于已放置的控制器 32 格范围内时，自动渲染该控制器对应结构中
 * 所有缺失方块的半透明模型，帮助玩家补全建造。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class StructurePlacementPreview {

    private static final double MAX_PREVIEW_DISTANCE = 32.0;
    private static final float GHOST_ALPHA = 0.35f;

    /** 用于幽灵方块渲染的伪世界：全亮光照、空气方块（不剔除任何面）。 */
    private static final IBlockAccess GHOST_WORLD = new GhostBlockAccess();

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        List<TileEntity> nearby = collectNearbyControllers(player);
        if (nearby.isEmpty()) return;

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
            BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
            BlockModelRenderer modelRenderer = dispatcher.getBlockModelRenderer();

            for (TileEntity te : nearby) {
                net.minecraft.world.World world = te.getWorld();
                BlockPos pos = te.getPos();
                IBlockState state = world.getBlockState(pos);

                if (te instanceof TileAssemblyController) {
                    EnumFacing facing = state.getValue(BlockAssemblyController.FACING);
                    renderAssemblyGhost(world, pos, facing, buffer, dispatcher, modelRenderer);
                } else if (te instanceof TileHyperdimensionalController) {
                    EnumFacing facing = state.getValue(BlockHyperdimensionalController.FACING);
                    renderHyperdimensionalGhost(world, pos, facing, buffer, dispatcher, modelRenderer);
                } else if (te instanceof TileComputationCore) {
                    EnumFacing facing = state.getValue(BlockComputationCore.FACING).getOpposite();
                    renderSupercausalGhost(world, pos, facing, buffer, dispatcher, modelRenderer);
                }
            }
        } finally {
            tessellator.draw();

            GlStateManager.enableDepth();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
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

    private static void renderAssemblyGhost(net.minecraft.world.World world, BlockPos controllerPos, EnumFacing facing,
                                            BufferBuilder buffer, BlockRendererDispatcher dispatcher,
                                            BlockModelRenderer modelRenderer) {
        BlockPos origin = controllerPos.add(rotate(new BlockPos(0, 0, 7), facing));
        renderGhostSet(world, origin, facing, AssemblyStructure.CORE_SET, ModBlocks.ASSEMBLY_CONTROLLER, buffer, dispatcher, modelRenderer);
        renderGhostSet(world, origin, facing, AssemblyStructure.PART1_SET, ModBlocks.ASSEMBLY_ME_INTERFACE, buffer, dispatcher, modelRenderer);
        renderGhostSet(world, origin, facing, AssemblyStructure.PART2_SET, ModBlocks.ASSEMBLY_CASING, buffer, dispatcher, modelRenderer);
        renderGhostSet(world, origin, facing, AssemblyStructure.PART3_SET, ModBlocks.ASSEMBLY_INNER_WALL, buffer, dispatcher, modelRenderer);
        renderGhostSet(world, origin, facing, AssemblyStructure.PART4_SET, ModBlocks.ASSEMBLY_STABILIZER, buffer, dispatcher, modelRenderer);
    }

    private static void renderHyperdimensionalGhost(net.minecraft.world.World world, BlockPos controllerPos, EnumFacing facing,
                                                    BufferBuilder buffer, BlockRendererDispatcher dispatcher,
                                                    BlockModelRenderer modelRenderer) {
        renderGhostSet(world, controllerPos, facing, HyperdimensionalStructure.CONTROLLER_SET, ModBlocks.HYPERDIMENSIONAL_CONTROLLER, buffer, dispatcher, modelRenderer);
        renderGhostSet(world, controllerPos, facing, HyperdimensionalStructure.ME_INTERFACE_SET, ModBlocks.HYPERDIMENSIONAL_ME_INTERFACE, buffer, dispatcher, modelRenderer);
        renderGhostSet(world, controllerPos, facing, HyperdimensionalStructure.CORE_SET, ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE, buffer, dispatcher, modelRenderer);
        renderGhostSet(world, controllerPos, facing, HyperdimensionalStructure.CASING_SET, ModBlocks.HYPERDIMENSIONAL_CASING, buffer, dispatcher, modelRenderer);
    }

    private static void renderSupercausalGhost(net.minecraft.world.World world, BlockPos controllerPos, EnumFacing facing,
                                               BufferBuilder buffer, BlockRendererDispatcher dispatcher,
                                               BlockModelRenderer modelRenderer) {
        // 控制器位置已由核心方块占据，跳过
        BlockPos meActual = controllerPos.add(rotate(SupercausalStructure.ME_INTERFACE_REL, facing));
        renderGhostBlock(world, meActual, ModBlocks.SUPER_CRAFTING_INTERFACE, buffer, dispatcher, modelRenderer);

        for (BlockPos rel : SupercausalStructure.TENSOR_CASING_SET) {
            if (rel.equals(SupercausalStructure.CONTROLLER_REL)) continue;
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            renderGhostBlock(world, actual, ModBlocks.CONSTANT_TENSOR_FIELD_CASING, buffer, dispatcher, modelRenderer);
        }

        for (BlockPos rel : SupercausalStructure.CAUSAL_ANCHOR_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            renderGhostBlock(world, actual, ModBlocks.CAUSAL_ANCHOR_CORE, buffer, dispatcher, modelRenderer);
        }

        for (BlockPos rel : SupercausalStructure.SPINOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            renderGhostBlock(world, actual, ModBlocks.CONSTANT_SPINOR_FIELD_CASING, buffer, dispatcher, modelRenderer);
        }
    }

    private static void renderGhostSet(net.minecraft.world.World world, BlockPos origin, EnumFacing facing,
                                       Set<BlockPos> relSet, Block expected,
                                       BufferBuilder buffer, BlockRendererDispatcher dispatcher,
                                       BlockModelRenderer modelRenderer) {
        for (BlockPos rel : relSet) {
            BlockPos actual = origin.add(rotate(rel, facing));
            renderGhostBlock(world, actual, expected, buffer, dispatcher, modelRenderer);
        }
    }

    private static void renderGhostBlock(net.minecraft.world.World world, BlockPos actual, Block expected,
                                         BufferBuilder buffer, BlockRendererDispatcher dispatcher,
                                         BlockModelRenderer modelRenderer) {
        IBlockState actualState = world.getBlockState(actual);
        if (actualState.getBlock() == expected) return; // 已有正确方块，无需投影

        IBlockState state = expected.getDefaultState();
        IBakedModel model = dispatcher.getModelForState(state);

        // 使用 BufferBuilder 的 translation 来偏移顶点位置，
        // 避免 renderModel 内部的 putPosition() 累积影响之前已写入的所有顶点。
        buffer.setTranslation(actual.getX(), actual.getY(), actual.getZ());
        modelRenderer.renderModel(GHOST_WORLD, model, state, BlockPos.ORIGIN, buffer, false);
        buffer.setTranslation(0, 0, 0);
    }

    /**
     * 与三个结构类完全一致的旋转逻辑。
     */
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
