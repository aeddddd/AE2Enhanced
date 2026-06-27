package com.github.aeddddd.ae2enhanced.dimension.lighting;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;

/**
 * 个人维度光照修复工具。
 *
 * <p>由于个人维度是空世界，chunk 生成后光照数据可能不完整，
 * 玩家进入/登录/切换维度时调用本类方法对周围 chunk 执行补光。</p>
 */
public final class DimensionLightingFixer {

    private DimensionLightingFixer() {}

    /**
     * 安排一次延迟补光：在目标 chunk 加载完成后的下一 tick 执行。
     */
    public static void scheduleRelight(@Nullable MinecraftServer server, int dimId, BlockPos center) {
        if (server == null) return;
        server.addScheduledTask(() -> relightDimensionChunks(dimId, center));
    }

    /**
     * 对指定坐标周围 3×3 chunk 范围执行 checkLight 并标记已加载。
     * 个人维度仅为单层地板的空世界，chunk 生成时已初始化光照；此处只需覆盖玩家出生点周围即可。
     */
    public static void relightDimensionChunks(int dimId, BlockPos center) {
        WorldServer world = DimensionManager.getWorld(dimId);
        if (world == null) return;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                try {
                    chunk.checkLight();
                    chunk.setLightPopulated(true);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to relight chunk at {}, {}", cx + dx, cz + dz, e);
                }
            }
        }
    }
}
