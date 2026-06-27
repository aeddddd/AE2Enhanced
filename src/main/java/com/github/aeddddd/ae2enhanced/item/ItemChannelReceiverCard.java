package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * F1b：频道接收卡.
 *
 * <p>空白状态可通过无线频道发生器 GUI 绑定到特定发生器.
 * 已绑定状态插入 AE 设备的 Upgrade 槽后,设备节点会与发生器节点建立
 * 远程 {@code GridConnection},从而借用发生器的控制器路径.</p>
 */
public class ItemChannelReceiverCard extends Item {

    private static final String NBT_BOUND = "ae2e_channel_bound";
    private static final String NBT_X = "ae2e_tx";
    private static final String NBT_Y = "ae2e_ty";
    private static final String NBT_Z = "ae2e_tz";
    private static final String NBT_DIM = "ae2e_tdim";
    private static final String NBT_SIDE = "ae2e_tside";

    public ItemChannelReceiverCard() {
        this.setMaxStackSize(64);
        this.setTranslationKey("ae2enhanced.channel_receiver_card");
        this.setRegistryName("ae2enhanced", "channel_receiver_card");
        this.setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    public static boolean isBound(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_BOUND);
    }

    public static void bindToTransmitter(ItemStack stack, BlockPos pos, int dim, net.minecraft.util.EnumFacing side) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setBoolean(NBT_BOUND, true);
        tag.setInteger(NBT_X, pos.getX());
        tag.setInteger(NBT_Y, pos.getY());
        tag.setInteger(NBT_Z, pos.getZ());
        tag.setInteger(NBT_DIM, dim);
        tag.setInteger(NBT_SIDE, side.ordinal());
        stack.setTagCompound(tag);
    }

    public static BlockPos getTransmitterPos(ItemStack stack) {
        if (!isBound(stack)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return new BlockPos(tag.getInteger(NBT_X), tag.getInteger(NBT_Y), tag.getInteger(NBT_Z));
    }

    public static int getTransmitterDim(ItemStack stack) {
        if (!isBound(stack)) return Integer.MIN_VALUE;
        return stack.getTagCompound().getInteger(NBT_DIM);
    }

    public static net.minecraft.util.EnumFacing getTransmitterSide(ItemStack stack) {
        if (!isBound(stack)) return null;
        int ord = stack.getTagCompound().getInteger(NBT_SIDE);
        return net.minecraft.util.EnumFacing.byIndex(ord);
    }

    public static void clearBinding(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            tag.removeTag(NBT_BOUND);
            tag.removeTag(NBT_X);
            tag.removeTag(NBT_Y);
            tag.removeTag(NBT_Z);
            tag.removeTag(NBT_DIM);
            tag.removeTag(NBT_SIDE);
            if (tag.getSize() == 0) {
                stack.setTagCompound(null);
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        if (isBound(stack)) {
            BlockPos pos = getTransmitterPos(stack);
            int dim = getTransmitterDim(stack);
            tooltip.add(I18n.format("item.ae2enhanced.channel_receiver_card.tooltip.bound", pos.getX(), pos.getY(), pos.getZ(), dim));
        } else {
            tooltip.add(I18n.format("item.ae2enhanced.channel_receiver_card.tooltip.unbound"));
        }
    }
}
