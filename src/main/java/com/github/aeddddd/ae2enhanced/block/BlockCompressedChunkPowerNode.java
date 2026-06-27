package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileCompressedChunkPowerNode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 压缩区块供电节点方块.
 *
 * <p>外观与区块供电节点完全一致,消耗 1 个 AE 频道.
 * 从连接的 ME 网络 RF 存储通道提取能量,向 3×3 区块范围内所有可接收能量的设备供能.</p>
 */
public class BlockCompressedChunkPowerNode extends BlockChunkPowerNode {

    public BlockCompressedChunkPowerNode() {
        super("compressed_chunk_power_node");
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileCompressedChunkPowerNode();
    }
}
