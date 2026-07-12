package com.github.aeddddd.ae2enhanced.client.render;

import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;

/**
 * 结构放置投影渲染器：当玩家对未成形主方块 Shift+右键 开启投影后，
 * 在对应缺失位置渲染半透明幽灵方块。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class StructurePlacementPreviewRenderer {

    private static final double MAX_PREVIEW_DISTANCE = 64.0;
    private static final float GHOST_FILL_ALPHA = 0.16f;
    private static final float GHOST_EDGE_ALPHA = 0.55f;
    private static final float GHOST_SCALE = 0.85f;
    private static final int GHOST_COLOR = 0x66CCFF;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!AE2EnhancedConfig.CLIENT.enableAssemblyRenderer.get()
                && !AE2EnhancedConfig.CLIENT.enableHyperdimensionalRenderer.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        if (!(level instanceof ClientLevel) || player == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer fill = buffer.getBuffer(RenderHelper.TESR_TRANSLUCENT);
        VertexConsumer edge = buffer.getBuffer(RenderHelper.TESR_LINES);

        int chunkRadius = (int) Math.ceil(MAX_PREVIEW_DISTANCE / 16.0);
        ChunkPos playerChunk = player.chunkPosition();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunkAccess = level.getChunk(playerChunk.x + dx, playerChunk.z + dz);
                if (!(chunkAccess instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof IMultiblockController controller)) {
                        continue;
                    }
                    if (controller.isFormed()) {
                        continue;
                    }
                    if (!controller.isShowingStructureProjection()) {
                        continue;
                    }

                    BlockPos controllerPos = be.getBlockPos();
                    if (controllerPos.distToCenterSqr(camera) > MAX_PREVIEW_DISTANCE * MAX_PREVIEW_DISTANCE) {
                        continue;
                    }

                    IMultiblockStructure structure = getStructureFor(be);
                    if (structure == null) {
                        continue;
                    }

                    Set<Map.Entry<BlockPos, Block>> expected = structure.getExpectedBlocks(level, controllerPos);
                    for (Map.Entry<BlockPos, Block> entry : expected) {
                        BlockPos actualPos = controllerPos.offset(entry.getKey());
                        Block expectedBlock = entry.getValue();
                        if (level.getBlockState(actualPos).getBlock() != expectedBlock) {
                            renderGhostBlock(poseStack, fill, edge, actualPos, expectedBlock);
                        }
                    }
                }
            }
        }

        buffer.endBatch();
        poseStack.popPose();
    }

    private static IMultiblockStructure getStructureFor(BlockEntity be) {
        if (be instanceof AssemblyControllerBlockEntity) {
            return AssemblyStructure.getInstance();
        }
        if (be instanceof HyperdimensionalControllerBlockEntity) {
            return HyperdimensionalStructure.getInstance();
        }
        return null;
    }

    private static void renderGhostBlock(PoseStack poseStack, VertexConsumer fill, VertexConsumer edge,
            BlockPos pos, Block expectedBlock) {
        poseStack.pushPose();
        poseStack.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        poseStack.scale(GHOST_SCALE, GHOST_SCALE, GHOST_SCALE);

        // 填充
        RenderHelper.drawCube(fill, poseStack, 0.5f, GHOST_COLOR, GHOST_FILL_ALPHA);

        // 线框
        RenderHelper.drawCubeWireframe(edge, poseStack, 0.5f, GHOST_COLOR, GHOST_EDGE_ALPHA);

        poseStack.popPose();
    }
}
