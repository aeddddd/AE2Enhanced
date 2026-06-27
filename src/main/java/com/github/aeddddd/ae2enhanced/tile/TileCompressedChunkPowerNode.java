package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 压缩区块供电节点的 TileEntity.
 *
 * <p>与区块供电节点一致,但供电范围扩展为 3×3 区块(以自身所在区块为中心).</p>
 */
public class TileCompressedChunkPowerNode extends TileChunkPowerNode {

    public TileCompressedChunkPowerNode() {
    }

    /**
     * 扫描 3×3 区块范围内所有可接收能量的 TileEntity,缓存其位置.
     *
     * <p>优化：直接读取已加载 chunk 的 {@code tileEntities} 映射,避免每 20 tick
     * 遍历全图 {@code world.loadedTileEntityList}.</p>
     */
    @Override
    protected void refreshTargetCache() {
        cachedTargets.clear();
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
                    boolean canReceive = false;
                    for (EnumFacing facing : EnumFacing.values()) {
                        if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                            IEnergyStorage cap = te.getCapability(CapabilityEnergy.ENERGY, facing);
                            if (cap != null && cap.canReceive()) {
                                canReceive = true;
                                break;
                            }
                        }
                    }
                    if (!canReceive) continue;

                    cachedTargets.add(tp.toImmutable());
                }
            }
        }
    }
}
