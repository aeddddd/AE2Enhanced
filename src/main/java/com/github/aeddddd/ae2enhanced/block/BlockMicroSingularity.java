package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 微型奇点 —�?仪式召唤的临时黑洞方块�?
 * 不可破坏，发光，有较小的碰撞箱，300 秒后自动坍缩�?
 * 玩家右键可主动触发黑洞合成（配方不匹配时不销毁物品）�?
 */
public class BlockMicroSingularity extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);

    public BlockMicroSingularity() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "micro_singularity");
        setTranslationKey(AE2Enhanced.MOD_ID + ".micro_singularity");
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setLightLevel(1.0F);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileMicroSingularity();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileMicroSingularity) {
                ((TileMicroSingularity) te).activateCrafting();
            }
        }
        return true;
    }
}
