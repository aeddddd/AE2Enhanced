package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.tile.TileCompressedChunkManaNode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 压缩区块魔力节点方块.
 *
 * <p>外观与区块魔力节点完全一致,消耗 1 个 AE 频道.
 * 作为免费魔力源向 3×3 区块范围内所有 Botania 魔力接收设施供魔.</p>
 */
public class BlockCompressedChunkManaNode extends BlockChunkManaNode {

    public BlockCompressedChunkManaNode() {
        super("compressed_chunk_mana_node");
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileCompressedChunkManaNode();
    }
}
