package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 方块实体移除原因判断工具。
 * <p>用于区分 {@code setRemoved} 是由于方块被破坏（方块状态已改变），
 * 还是由于区块卸载 / 服务器关闭。仅在确认方块被破坏时才应触发完整拆解或
 * 资源释放，避免在区块卸载路径上执行多余 I/O 或状态变更。</p>
 */
public final class BlockEntityRemovalHelper {

    private BlockEntityRemovalHelper() {
    }

    /**
     * 判断当前方块实体是否因所在方块被破坏而移除。
     *
     * @param be 要判断的方块实体
     * @return 若方块状态已不再是原方块，则返回 true
     */
    public static boolean isBlockBeingBroken(@Nullable BlockEntity be) {
        if (be == null || be.getLevel() == null) {
            return false;
        }
        BlockState current = be.getLevel().getBlockState(be.getBlockPos());
        return current.getBlock() != be.getBlockState().getBlock();
    }
}
