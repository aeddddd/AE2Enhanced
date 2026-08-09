package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 奇点处理仓 — 后期单方块高并行处理机器.
 * 右键打开 GUI；破坏时以 BlockEntityTag 保留能量/缓存/卡片/任务状态.
 */
public class BlockSingularityChamber extends Block {

    public BlockSingularityChamber() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "singularity_chamber");
        setTranslationKey(AE2Enhanced.MOD_ID + ".singularity_chamber");
        setHardness(5.0F);
        setResistance(10.0F);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileSingularityChamber();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_SINGULARITY_CHAMBER,
                    world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    /**
     * 破坏时保留 Tile 全部状态（能量/Long 缓存/卡片/进行中任务）到掉落物 NBT,
     * 放置时由 ItemBlock 的 BlockEntityTag 机制自动恢复.
     */
    @Override
    public void getDrops(NonNullList<ItemStack> drops, net.minecraft.world.IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        ItemStack stack = new ItemStack(this);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileSingularityChamber) {
            NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
            // 坐标由放置时重新写入,避免旧坐标污染
            tag.removeTag("x");
            tag.removeTag("y");
            tag.removeTag("z");
            stack.setTagInfo("BlockEntityTag", tag);
        }
        drops.add(stack);
    }

    /**
     * 防止创造模式破坏时 Tile 提前移除导致 NBT 丢失（保持默认行为即可,无需覆盖）,
     * 但需要确保 breakBlock 时不清除数据 — 默认 super.breakBlock 会移除 TileEntity,
     * getDrops 在其之前调用,数据已捕获.
     */
}
