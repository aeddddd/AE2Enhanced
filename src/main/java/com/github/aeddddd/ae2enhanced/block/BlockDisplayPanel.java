package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.network.packet.PacketDisplayAction;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
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

/**
 * 趋势显示幕墙面板方块.
 *
 * <p>水平朝向决定显示面; 同朝向面板组成 2~16 × 2~9 的实心矩形即成型.
 * 右键打开配置 GUI; 潜行+右键循环切换图表类型.</p>
 */
public class BlockDisplayPanel extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public BlockDisplayPanel() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "display_panel");
        setTranslationKey(AE2Enhanced.MOD_ID + ".display_panel");
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
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
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileDisplayPanel();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileDisplayPanel)) return false;
        TileDisplayPanel panel = (TileDisplayPanel) te;
        if (world.isRemote) {
            // 潜行+右键: 循环切换图表类型, 无需打开 GUI
            if (player.isSneaking() && panel.isFormed()) {
                AE2Enhanced.network.sendToServer(
                        new PacketDisplayAction(panel.getMasterPos() != null ? panel.getMasterPos() : pos,
                                PacketDisplayAction.ACTION_CYCLE_CHART, 0));
            }
            return true;
        }
        if (player.isSneaking()) return true;
        // 触发即时扫描, 保证刚摆好的屏幕立即可交互
        panel.requestRescan();
        panel.update();
        if (panel.isFormed() && panel.getMasterPos() != null) {
            BlockPos master = panel.getMasterPos();
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_DISPLAY_WALL,
                    world, master.getX(), master.getY(), master.getZ());
        } else {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                    "gui.ae2enhanced.display_wall.not_formed"));
        }
        return true;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileDisplayPanel) {
            ((TileDisplayPanel) te).requestRescan();
        }
    }

}
