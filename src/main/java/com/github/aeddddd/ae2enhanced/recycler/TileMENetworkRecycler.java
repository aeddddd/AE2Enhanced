package com.github.aeddddd.ae2enhanced.recycler;

import com.github.aeddddd.ae2enhanced.tile.TileAENetworkBase;

import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.recycler.packet.PacketRecyclerSync;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerBindingRegistry;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerFluidNetworkHandler;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerNetworkHandler;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager.TargetRef;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * ME 网络回收节点的 TileEntity.
 *
 * <p>接入 AE2 网络,管理远程/跨维度回收目标,通过单一 RecyclerNetworkHandler
 * 向网络暴露存储视图,实际产物直接写入超维度仓储中枢.</p>
 */
public class TileMENetworkRecycler extends TileAENetworkBase
        implements ITickable, ICellContainer, com.github.aeddddd.ae2enhanced.item.memorycard.api.RecyclerBindingTarget {

    private static final String OUTBOX_KEY = "RecyclerOutbox";
    private static final int OUTBOX_VERSION = 1;
    private final TargetManager targetManager = new TargetManager();
    private final RecyclerNetworkHandler networkHandler = new RecyclerNetworkHandler(this);
    private final RecyclerFluidNetworkHandler fluidNetworkHandler = new RecyclerFluidNetworkHandler(this);
    private boolean lastPowered = false;
    private boolean lastActive = false;
    private int clientFlags = 0;
    private int tickCounter = 0;
    // 客户端同步字段
    private int clientTargetCount = 0;
    private long clientLastRecycledCount = 0;
    private boolean clientActive = false;

    // ---- TileAENetworkBase ----
    private boolean clientPowered = false;
    private NBTTagCompound preservedOutbox;

    public TileMENetworkRecycler() {
    }

    @Override
    protected String getProxyName() {
        return "me_network_recycler";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(getBlockType());
    }

    @Override
    public void disassemble() {
        if (world != null && !world.isRemote) {
            dropContents();
        }
    }

    @Override
    public void securityBreak() {
        if (world != null && !world.isRemote) {
            world.destroyBlock(pos, false);
        }
    }

    // ---- ICellContainer ----

    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        return getProxy().getNode();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        if (channel instanceof IItemStorageChannel) {
            return Collections.singletonList(networkHandler);
        }
        if (channel instanceof IFluidStorageChannel) {
            return Collections.singletonList(fluidNetworkHandler);
        }
        return Collections.emptyList();
    }

    // ---- 生命周期 ----

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void blinkCell(int slot) {
    }

    @Override
    public void saveChanges(ICellInventory<?> inv) {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote) {
            networkHandler.onLoad();
            fluidNetworkHandler.onLoad();
            registerAllBindings();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (networkHandler != null) {
            networkHandler.onInvalidate();
        }
        if (fluidNetworkHandler != null) {
            fluidNetworkHandler.onInvalidate();
        }
        if (world != null && !world.isRemote && RecyclerBindingRegistry.isActive()) {
            unregisterAllBindings();
        }
    }

    // ---- AE 网络事件 ----

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (networkHandler != null) {
            networkHandler.onInvalidate();
        }
        if (fluidNetworkHandler != null) {
            fluidNetworkHandler.onInvalidate();
        }
        if (world != null && !world.isRemote && RecyclerBindingRegistry.isActive()) {
            unregisterAllBindings();
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady()) {
            clearNeedsReady();
            getProxy().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            getProxy().setIdlePowerUsage(com.github.aeddddd.ae2enhanced.config.ServerConfig.current().recycler.idlePower);
            getProxy().onReady();
        }

        syncClientState();

        tickCounter++;
        if (isActive()) {
            networkHandler.tick(tickCounter);
            fluidNetworkHandler.tick(tickCounter);
        }

        if (tickCounter % 10 == 0) {
            syncToWatchingPlayers();
        }
    }

    // ---- 目标管理 ----

    private void syncClientState() {
        boolean powered = isPowered();
        boolean active = isActive();
        if (powered != lastPowered || active != lastActive) {
            lastPowered = powered;
            lastActive = active;
            int flags = 0;
            if (powered) flags |= 1;
            if (active) flags |= 2;
            this.clientFlags = flags;
            if (world != null) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 2);
            }
        }
    }

    @MENetworkEventSubscribe
    public void chanRender(MENetworkChannelsChanged c) {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @MENetworkEventSubscribe
    public void powerRender(MENetworkPowerStatusChange c) {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    public TargetManager getTargetManager() {
        return targetManager;
    }

    public boolean isPowered() {
        return getProxy().isPowered();
    }

    public boolean isActive() {
        return getProxy().isActive();
    }

    public int getClientTargetCount() {
        return world.isRemote ? clientTargetCount : targetManager.getTargetCount();
    }

    public long getClientLastRecycledCount() {
        return world.isRemote ? clientLastRecycledCount : networkHandler.getLastRecycledCount();
    }

    public boolean isClientActive() {
        return world.isRemote ? clientActive : isActive();
    }

    public boolean isClientPowered() {
        return world.isRemote ? clientPowered : isPowered();
    }

    // ---- 绑定管理 ----

    public void handleSyncPacket(PacketRecyclerSync packet) {
        this.clientTargetCount = packet.getTargetCount();
        this.clientLastRecycledCount = packet.getLastRecycledCount();
        this.clientActive = packet.isActive();
        this.clientPowered = packet.isPowered();
    }

    private void syncToWatchingPlayers() {
        if (world == null || world.isRemote) return;
        PacketRecyclerSync packet = new PacketRecyclerSync(
                pos,
                targetManager.getTargetCount(),
                networkHandler.getLastRecycledCount(),
                isActive(),
                isPowered()
        );
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
            if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                double distSq = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (distSq < 64 * 64) {
                    AE2Enhanced.network.sendTo(packet, (net.minecraft.entity.player.EntityPlayerMP) player);
                }
            }
        }
    }

    public void dropContents() {
        if (world == null || world.isRemote) return;
        networkHandler.dropBufferedContents(world, pos);
        fluidNetworkHandler.dropBufferedContents(world, pos);
    }

    public boolean tryBindTarget(@Nonnull TargetRef target) {
        if (targetManager.getTargetCount() >= com.github.aeddddd.ae2enhanced.config.ServerConfig.current().recycler.maxTargets) {
            return false;
        }
        if (targetManager.addTarget(target)) {
            RecyclerBindingRegistry.getInstance().register(target, networkHandler, fluidNetworkHandler);
            markDirty();
            return true;
        }
        return false;
    }

    public boolean tryUnbindTarget(@Nonnull TargetRef target) {
        if (targetManager.removeTarget(target)) {
            RecyclerBindingRegistry.getInstance().unregister(target);
            markDirty();
            return true;
        }
        return false;
    }

    public void clearTargets() {
        RecyclerBindingRegistry.getInstance().unregisterAll(networkHandler);
        RecyclerBindingRegistry.getInstance().unregisterAll(fluidNetworkHandler);
        targetManager.clear();
        markDirty();
    }

    // ---- NBT ----

    @Override
    public int bindSelections(net.minecraft.world.World world,
            java.util.List<com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard.SelectionEntry> selections) {
        int bound = 0;
        for (com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard.SelectionEntry entry : selections) {
            TargetRef target = new TargetRef(entry.dim, entry.pos,
                    entry.side >= 0 ? EnumFacing.values()[entry.side] : EnumFacing.UP);
            if (tryBindTarget(target)) {
                bound++;
            }
        }
        markDirty();
        return bound;
    }

    @Override
    public int clearBindingSelections() {
        int count = targetManager.getTargetCount();
        clearTargets();
        return count;
    }

    private void unregisterAllBindings() {
        RecyclerBindingRegistry.getInstance().unregisterAll(networkHandler);
        RecyclerBindingRegistry.getInstance().unregisterAll(fluidNetworkHandler);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Targets", Constants.NBT.TAG_LIST)) {
            targetManager.deserializeNBT(compound.getTagList("Targets", Constants.NBT.TAG_COMPOUND));
        }
        preservedOutbox = null;
        if (compound.hasKey(OUTBOX_KEY, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound outbox = compound.getCompoundTag(OUTBOX_KEY);
            if (outbox.getInteger("Version") == OUTBOX_VERSION) {
                preservedOutbox = outbox.copy();
                networkHandler.readOutbox(outbox);
                fluidNetworkHandler.readOutbox(outbox);
            } else {
                preservedOutbox = outbox.copy();
            }
        }
    }

    private void registerAllBindings() {
        for (TargetRef target : targetManager.getTargets()) {
            RecyclerBindingRegistry.getInstance().register(target, networkHandler, fluidNetworkHandler);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        NBTTagList list = targetManager.serializeNBT();
        if (list != null && list.tagCount() > 0) {
            compound.setTag("Targets", list);
        }
        if (preservedOutbox != null && preservedOutbox.getInteger("Version") != OUTBOX_VERSION) {
            compound.setTag(OUTBOX_KEY, preservedOutbox.copy());
            return compound;
        }
        NBTTagCompound outbox = new NBTTagCompound();
        outbox.setInteger("Version", OUTBOX_VERSION);
        networkHandler.writeOutbox(outbox);
        fluidNetworkHandler.writeOutbox(outbox);
        outbox = com.github.aeddddd.ae2enhanced.storage.PreservedNbt.mergeRoot(
                outbox, preservedOutbox, outbox.getKeySet());
        compound.setTag(OUTBOX_KEY, outbox);
        return compound;
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("ClientFlags", clientFlags);
        return tag;
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        if (pkt.getNbtCompound() != null) {
            this.clientFlags = pkt.getNbtCompound().getInteger("ClientFlags");
            if (world != null) {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        }
    }
}
