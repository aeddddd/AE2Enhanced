package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalMeInterface;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class BlockHyperdimensionalMeInterface extends Block {

    public static final PropertyBool FORMED = PropertyBool.create("formed");

    public BlockHyperdimensionalMeInterface() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "hyperdimensional_me_interface");
        setTranslationKey(AE2Enhanced.MOD_ID + ".hyperdimensional_me_interface");
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setDefaultState(blockState.getBaseState().withProperty(FORMED, false));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileHyperdimensionalMeInterface();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FORMED);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FORMED) ? 1 : 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FORMED, meta == 1);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(FORMED) ? 10 : 0;
    }
}
