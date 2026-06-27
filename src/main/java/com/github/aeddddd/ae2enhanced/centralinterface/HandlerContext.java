package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Handler 执行时的上下文对象,聚合常用参数以减少接口方法签名复杂度.
 *
 * 各 handler 实现可在内部构造此对象以简化代码,不强制要求使用.
 */
public class HandlerContext {

    public final World world;
    public final BlockPos pos;
    public final TileEntity tileEntity;
    public final String blockId;

    public HandlerContext(World world, BlockPos pos, TileEntity tileEntity, String blockId) {
        this.world = world;
        this.pos = pos;
        this.tileEntity = tileEntity;
        this.blockId = blockId;
    }

    public static HandlerContext fromBinding(World world, TargetBinding binding) {
        if (world.provider.getDimension() != binding.dimension) {
            return null;
        }
        TileEntity te = world.getTileEntity(binding.pos);
        return new HandlerContext(world, binding.pos, te, binding.blockId);
    }
}
