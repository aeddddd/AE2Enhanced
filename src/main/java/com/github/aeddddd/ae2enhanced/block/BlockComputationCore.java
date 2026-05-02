package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.structure.ComputationCoreIndex;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class BlockComputationCore extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public BlockComputationCore() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "computation_core");
        setTranslationKey(AE2Enhanced.MOD_ID + ".computation_core");
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.BUILDING_BLOCKS);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileComputationCore();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) {
            ComputationCoreIndex index = ComputationCoreIndex.get(world);
            if (index != null) {
                index.add(pos);
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            ComputationCoreIndex index = ComputationCoreIndex.get(world);
            if (index != null) {
                index.remove(pos);
            }
            // 触发结构解体
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileComputationCore) {
                TileComputationCore tile = (TileComputationCore) te;
                if (tile.isFormed()) {
                    tile.disassemble();
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileComputationCore) {
                TileComputationCore tile = (TileComputationCore) te;
                if (!tile.isFormed()) {
                    SupercausalStructure.ValidationResult result = SupercausalStructure.validate(world, pos);
                    if (result.passed) {
                        SupercausalStructure.assemble(world, pos);
                    }
                    player.openGui(AE2Enhanced.instance, GuiHandler.GUI_COMPUTATION_UNFORMED, world, pos.getX(), pos.getY(), pos.getZ());
                } else {
                    player.openGui(AE2Enhanced.instance, GuiHandler.GUI_COMPUTATION_FORMED, world, pos.getX(), pos.getY(), pos.getZ());
                }
            }
        }
        return true;
    }
}
