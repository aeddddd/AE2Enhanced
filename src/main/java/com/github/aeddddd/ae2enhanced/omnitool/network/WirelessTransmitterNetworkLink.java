package com.github.aeddddd.ae2enhanced.omnitool.network;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 通过潜行右键无线频道发射器绑定坐标，从而链接其所在 ME 网络。
 * 这是 Universal 模式 DROP_AE 所使用的链接方式。
 */
public class WirelessTransmitterNetworkLink implements IOmniToolNetworkLink {

    public static final String ID = "wireless_transmitter";
    public static final WirelessTransmitterNetworkLink INSTANCE = new WirelessTransmitterNetworkLink();

    private static final String NBT_AE_BOUND = "AEBound";
    private static final String NBT_AE_X = "AEX";
    private static final String NBT_AE_Y = "AEY";
    private static final String NBT_AE_Z = "AEZ";
    private static final String NBT_AE_DIM = "AEDim";

    private WirelessTransmitterNetworkLink() {}

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTooltipKey() {
        return "item.ae2enhanced.me_omni_tool.ae_bound";
    }

    @Override
    public boolean isLinked(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_AE_BOUND);
    }

    public static void setBound(ItemStack stack, BlockPos pos, int dim) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        tag.setBoolean(NBT_AE_BOUND, true);
        tag.setInteger(NBT_AE_X, pos.getX());
        tag.setInteger(NBT_AE_Y, pos.getY());
        tag.setInteger(NBT_AE_Z, pos.getZ());
        tag.setInteger(NBT_AE_DIM, dim);
    }

    @Nullable
    public static BlockPos getTransmitterPos(ItemStack stack) {
        if (!isLinkedStatic(stack)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return new BlockPos(tag.getInteger(NBT_AE_X), tag.getInteger(NBT_AE_Y), tag.getInteger(NBT_AE_Z));
    }

    public static int getTransmitterDim(ItemStack stack) {
        if (!isLinkedStatic(stack)) return Integer.MIN_VALUE;
        return stack.getTagCompound().getInteger(NBT_AE_DIM);
    }

    @Nullable
    public static TileWirelessChannelTransmitter getTransmitterTile(ItemStack stack, World world) {
        BlockPos pos = getTransmitterPos(stack);
        if (pos == null) return null;
        if (world.provider.getDimension() != getTransmitterDim(stack)) return null;
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileWirelessChannelTransmitter ? (TileWirelessChannelTransmitter) te : null;
    }

    @Override
    @Nullable
    public IGrid getLinkedGrid(ItemStack stack, World world, @Nullable EntityPlayer player) {
        TileWirelessChannelTransmitter transmitter = getTransmitterTile(stack, world);
        if (transmitter == null) return null;
        IGridNode node = transmitter.getGridNode(AEPartLocation.INTERNAL);
        return node != null ? node.getGrid() : null;
    }

    /**
     * 获取链接网络的物品存储监控器，供 DROP_AE 使用。
     */
    @Nullable
    public static IMEMonitor<IAEItemStack> getItemMonitor(ItemStack stack, World world) {
        IGrid grid = INSTANCE.getLinkedGrid(stack, world, null);
        if (grid == null) return null;
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        if (storage == null) return null;
        return storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
    }

    @Override
    public void clear(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            tag.removeTag(NBT_AE_BOUND);
            tag.removeTag(NBT_AE_X);
            tag.removeTag(NBT_AE_Y);
            tag.removeTag(NBT_AE_Z);
            tag.removeTag(NBT_AE_DIM);
            if (tag.getSize() == 0) {
                stack.setTagCompound(null);
            }
        }
    }

    // 静态辅助，供本类内部及旧调用方迁移期使用
    private static boolean isLinkedStatic(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_AE_BOUND);
    }
}
