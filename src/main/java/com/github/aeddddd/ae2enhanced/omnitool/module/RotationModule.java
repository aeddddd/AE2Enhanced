package com.github.aeddddd.ae2enhanced.omnitool.module;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;

/**
 * 旋转模式：右键方块时旋转其朝向。
 */
public class RotationModule implements IOmniToolModule {

    @Override
    public int getMode() {
        return AdvancedMEOmniToolItem.MODE_ROTATE;
    }

    @Override
    public InteractionResult onItemUse(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        Direction facing = context.getClickedFace();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // 优先使用原版 Block#rotate（1.12 rotateBlock 的对应物）
        BlockState rotated = block.rotate(state, level, pos, Rotation.CLOCKWISE_90);
        if (rotated != state) {
            level.setBlock(pos, rotated, Block.UPDATE_ALL);
            if (player != null) player.swing(context.getHand());
            return InteractionResult.SUCCESS;
        }

        // DirectionProperty 手动循环回退
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dirProp) {
                Direction current = state.getValue(dirProp);
                Direction next = getNextFacing(current, facing, dirProp);
                if (next != null && next != current && dirProp.getPossibleValues().contains(next)) {
                    level.setBlock(pos, state.setValue(dirProp, next), Block.UPDATE_ALL);
                    if (player != null) player.swing(context.getHand());
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    private Direction getNextFacing(Direction current, Direction clickFace, DirectionProperty dirProp) {
        if (clickFace.getAxis() == Direction.Axis.Y) {
            // 点击顶面/底面：先尝试绕 X 轴旋转，再绕 Z 轴，再取反
            Direction next = current.getClockWise(Direction.Axis.X);
            if (dirProp.getPossibleValues().contains(next)) return next;
            next = current.getClockWise(Direction.Axis.Z);
            if (dirProp.getPossibleValues().contains(next)) return next;
            next = current.getOpposite();
            if (dirProp.getPossibleValues().contains(next)) return next;
        } else {
            // 点击侧面：绕 Y 轴旋转
            Direction next = current.getClockWise();
            if (dirProp.getPossibleValues().contains(next)) return next;
            next = current.getCounterClockWise();
            if (dirProp.getPossibleValues().contains(next)) return next;
        }
        return null;
    }
}
