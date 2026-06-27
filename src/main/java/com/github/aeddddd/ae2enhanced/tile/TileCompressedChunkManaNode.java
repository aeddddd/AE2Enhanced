package com.github.aeddddd.ae2enhanced.tile;

import com.github.aeddddd.ae2enhanced.util.compat.botania.BotaniaManaHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

/**
 * 压缩区块魔力节点的 TileEntity.
 *
 * <p>与区块魔力节点一致,从 ME 网络 Mana 存储通道提取 Mana,
 * 但供魔范围扩展为 3×3 区块(以自身所在区块为中心).</p>
 */
public class TileCompressedChunkManaNode extends TileChunkManaNode {

    public TileCompressedChunkManaNode() {
    }

    /**
     * 扫描 3×3 区块范围内所有 Botania 魔力接收设施,缓存其位置.
     */
    @Override
    protected void refreshTargetCache() {
        cachedTargets.clear();
        if (!BotaniaManaHelper.isAvailable()) return;

        int centerChunkX = pos.getX() >> 4;
        int centerChunkZ = pos.getZ() >> 4;

        ChunkProviderServer provider = world.getChunkProvider() instanceof ChunkProviderServer
                ? (ChunkProviderServer) world.getChunkProvider() : null;
        if (provider == null) return;

        for (int cx = centerChunkX - 1; cx <= centerChunkX + 1; cx++) {
            for (int cz = centerChunkZ - 1; cz <= centerChunkZ + 1; cz++) {
                Chunk chunk = provider.getLoadedChunk(cx, cz);
                if (chunk == null) continue;

                for (TileEntity te : chunk.getTileEntityMap().values()) {
                    if (te == null || te.isInvalid()) continue;
                    if (te == this) continue;

                    BlockPos tp = te.getPos();
                    if (BotaniaManaHelper.isManaVoid(world, tp)) continue;
                    if (BotaniaManaHelper.isGeneratingFlower(te)) continue;
                    if (!BotaniaManaHelper.isManaReceiver(te)) continue;

                    cachedTargets.add(tp.toImmutable());
                }
            }
        }
    }
}
