package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileChunkManaNode;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 区块魔力节点方块.
 *
 * <p>外观与区块供电节点一致,消耗 1 个 AE 频道.
 * 作为免费魔力源向所在区块内所有 Botania 魔力接收设施供魔,
 * 不从 ME 网络扣除 Mana.</p>
 */
public class BlockChunkManaNode extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyEnum<State> STATE = PropertyEnum.create("state", State.class);

    public BlockChunkManaNode() {
        this("chunk_mana_node");
    }

    protected BlockChunkManaNode(String name) {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, name);
        setTranslationKey(AE2Enhanced.MOD_ID + "." + name);
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setDefaultState(blockState.getBaseState()
            .withProperty(FACING, EnumFacing.NORTH)
            .withProperty(STATE, State.OFF));
        setLightOpacity(0);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, STATE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta));
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        State visualState = State.OFF;
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TileChunkManaNode) {
            TileChunkManaNode tile = (TileChunkManaNode) te;
            if (tile.isActive()) {
                visualState = State.HAS_CHANNEL;
            } else if (tile.isPowered()) {
                visualState = State.ON;
            }
        }
        return super.getActualState(state, worldIn, pos)
            .withProperty(STATE, visualState);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                             float hitX, float hitY, float hitZ, int meta,
                                             EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                 EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileChunkManaNode) {
            ((TileChunkManaNode) te).setForward(state.getValue(FACING));
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileChunkManaNode();
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    private static final AxisAlignedBB AABB_DOWN = new AxisAlignedBB(0.1875, 0.3125, 0.1875, 0.8125, 1.0, 0.8125);
    private static final AxisAlignedBB AABB_UP = new AxisAlignedBB(0.1875, 0.0, 0.1875, 0.8125, 0.6875, 0.8125);
    private static final AxisAlignedBB AABB_NORTH = new AxisAlignedBB(0.1875, 0.1875, 0.3125, 0.8125, 0.8125, 1.0);
    private static final AxisAlignedBB AABB_SOUTH = new AxisAlignedBB(0.1875, 0.1875, 0.0, 0.8125, 0.8125, 0.6875);
    private static final AxisAlignedBB AABB_WEST = new AxisAlignedBB(0.3125, 0.1875, 0.1875, 1.0, 0.8125, 0.8125);
    private static final AxisAlignedBB AABB_EAST = new AxisAlignedBB(0.0, 0.1875, 0.1875, 0.6875, 0.8125, 0.8125);

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return getAABB(state);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return getAABB(state);
    }

    private AxisAlignedBB getAABB(IBlockState state) {
        switch (state.getValue(FACING)) {
            case DOWN: return AABB_DOWN;
            case UP: return AABB_UP;
            case NORTH: return AABB_NORTH;
            case SOUTH: return AABB_SOUTH;
            case WEST: return AABB_WEST;
            case EAST: return AABB_EAST;
            default: return FULL_BLOCK_AABB;
        }
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        if (player.isSneaking() && !world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileChunkManaNode) {
                TileChunkManaNode node = (TileChunkManaNode) te;
                java.util.List<BlockPos> targets = node.getCachedTargets();
                com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendTo(
                        new com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerHighlight(targets, 100),
                        (EntityPlayerMP) player);
                return true;
            }
        }
        return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
    }

    public enum State implements IStringSerializable {
        OFF, ON, HAS_CHANNEL;

        @Override
        public String getName() {
            return this.name().toLowerCase();
        }
    }
}
