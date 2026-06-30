package com.github.aeddddd.ae2enhanced.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.blockentity.grid.AENetworkBlockEntity;

/**
 * AE2Enhanced 网络方块实体基类，继承 AE2 的 AENetworkedBlockEntity。
 * <p>主网格节点直接通过父类的 {@link AENetworkBlockEntity#getMainNode()} 访问。</p>
 */
public class AE2ENetworkedBlockEntity extends AENetworkBlockEntity {

    public AE2ENetworkedBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }
}
