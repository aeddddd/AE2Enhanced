package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 先进 ME 收集器方块.
 *
 * <p>右键打开 GUI 配置范围与过滤,破坏时掉落内部升级卡与过滤物品.</p>
 */
public class BlockAdvancedMECollector extends Block {

    public static final PropertyEnum<State> STATE = PropertyEnum.create("state", State.class);

    private static final AxisAlignedBB AABB = new AxisAlignedBB(0.1875, 0.0, 0.1875, 0.8125, 0.75, 0.8125);

    public BlockAdvancedMECollector() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "advanced_me_collector");
        setTranslationKey(AE2Enhanced.MOD_ID + ".advanced_me_collector");
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setDefaultState(blockState.getBaseState().withProperty(STATE, State.OFF));
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, STATE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        State visualState = State.OFF;
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TileAdvancedMECollector) {
            TileAdvancedMECollector tile = (TileAdvancedMECollector) te;
            if (tile.isActive()) {
                visualState = State.HAS_CHANNEL;
            } else if (tile.isPowered()) {
                visualState = State.ON;
            }
        }
        return super.getActualState(state, worldIn, pos).withProperty(STATE, visualState);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAdvancedMECollector();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileAdvancedMECollector) {
                player.openGui(AE2Enhanced.instance, GuiHandler.GUI_ADVANCED_ME_COLLECTOR,
                        world, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAdvancedMECollector) {
            ((TileAdvancedMECollector) te).dropContents();
        }
        super.breakBlock(world, pos, state);
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
        return AABB;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return AABB;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public enum State implements IStringSerializable {
        OFF, ON, HAS_CHANNEL;

        @Override
        public String getName() {
            return this.name().toLowerCase();
        }
    }
}
