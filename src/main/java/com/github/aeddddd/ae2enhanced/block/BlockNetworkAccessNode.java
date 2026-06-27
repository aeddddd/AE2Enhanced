package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileNetworkAccessNode;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 通用网络访问节点方块.
 * 同时桥接 RF / Mana / Starlight,根据相邻方块类型自动工作.
 */
public class BlockNetworkAccessNode extends Block {

    public BlockNetworkAccessNode() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "network_access_node");
        setTranslationKey(AE2Enhanced.MOD_ID + ".network_access_node");
        setHardness(3.0F);
        setResistance(8.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileNetworkAccessNode();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileNetworkAccessNode) {
            ((TileNetworkAccessNode) te).onBreak();
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
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        if (player.isSneaking() && !world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileNetworkAccessNode) {
                TileNetworkAccessNode node = (TileNetworkAccessNode) te;
                node.cycleMode();
                String key = node.isInputMode()
                        ? "message.ae2enhanced.network_access_node.mode.input"
                        : "message.ae2enhanced.network_access_node.mode.output";
                player.sendMessage(new TextComponentTranslation(key));
                return true;
            }
        }
        return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
    }
}
