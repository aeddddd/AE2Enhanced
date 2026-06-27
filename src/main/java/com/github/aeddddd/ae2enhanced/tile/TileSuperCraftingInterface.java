package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.util.math.BlockPos;

/**
 * 超因果合成接口 TileEntity.
 * 本身不创建独立的 AE 网络节点,而是作为 TileComputationCore 的物理网络接入点.
 * ME 线缆连接到本方块时,实际上接入的是控制器的网格节点.
 */
public class TileSuperCraftingInterface extends TileDelegatedProxyBase<TileComputationCore> {

    @Override
    public void setControllerPos(BlockPos pos) {
        super.setControllerPos(pos);
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(this.pos, world.getBlockState(this.pos), world.getBlockState(this.pos), 2);
        }
    }

    @Override
    protected Class<TileComputationCore> getControllerClass() {
        return TileComputationCore.class;
    }

    @Override
    protected boolean isControllerFormed(TileComputationCore controller) {
        return controller.isFormed();
    }
}
