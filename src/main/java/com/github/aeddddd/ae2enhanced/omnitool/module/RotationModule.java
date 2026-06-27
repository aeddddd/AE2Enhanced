package com.github.aeddddd.ae2enhanced.omnitool.module;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 旋转模式：右键方块时旋转其朝向。
 */
public class RotationModule implements IOmniToolModule {

    @Override
    public int getMode() {
        return ItemAdvancedMEOmniTool.MODE_ROTATE;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;

        IBlockState state = world.getBlockState(pos);
        net.minecraft.block.Block block = state.getBlock();
        boolean rotated = block.rotateBlock(world, pos, facing);
        if (rotated) {
            player.swingArm(hand);
            return EnumActionResult.SUCCESS;
        }

        // PropertyDirection 手动循环回退
        for (IProperty<?> prop : state.getPropertyKeys()) {
            if (prop instanceof PropertyDirection) {
                PropertyDirection dirProp = (PropertyDirection) prop;
                EnumFacing current = state.getValue(dirProp);
                EnumFacing next = getNextFacing(current, facing, dirProp);
                if (next != null && next != current && dirProp.getAllowedValues().contains(next)) {
                    world.setBlockState(pos, state.withProperty(dirProp, next));
                    player.swingArm(hand);
                    return EnumActionResult.SUCCESS;
                }
            }
        }
        return EnumActionResult.PASS;
    }

    private EnumFacing getNextFacing(EnumFacing current, EnumFacing clickFace, PropertyDirection dirProp) {
        if (clickFace.getAxis() == EnumFacing.Axis.Y) {
            // 点击顶面/底面：先尝试绕 X 轴旋转，再绕 Z 轴，再取反
            EnumFacing next = current.rotateAround(EnumFacing.Axis.X);
            if (dirProp.getAllowedValues().contains(next)) return next;
            next = current.rotateAround(EnumFacing.Axis.Z);
            if (dirProp.getAllowedValues().contains(next)) return next;
            next = current.getOpposite();
            if (dirProp.getAllowedValues().contains(next)) return next;
        } else {
            // 点击侧面：绕 Y 轴旋转
            EnumFacing next = current.rotateY();
            if (dirProp.getAllowedValues().contains(next)) return next;
            next = current.rotateYCCW();
            if (dirProp.getAllowedValues().contains(next)) return next;
        }
        return null;
    }
}
