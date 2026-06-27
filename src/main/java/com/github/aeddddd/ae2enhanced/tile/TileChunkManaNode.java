package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import com.github.aeddddd.ae2enhanced.util.compat.botania.BotaniaManaHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 区块魔力节点的 TileEntity.
 *
 * <p>消耗 1 个 AE 频道,从连接的 ME 网络 Mana 存储通道提取 Mana,
 * 向所在区块(16×16)内所有 Botania 魔力接收设施供魔.</p>
 *
 * <p>供魔目标排除：</p>
 * <ul>
 *   <li>Mana Void 方块(避免无意义填充)</li>
 *   <li>产能花(generating flowers,避免破坏 Botania 产能逻辑)</li>
 * </ul>
 */
public class TileChunkManaNode extends TileAENetworkBase implements ITickable, IActionHost {

    private static final int CACHE_REFRESH_INTERVAL = 20;

    private EnumFacing forward = EnumFacing.NORTH;
    private MachineSource machineSource;

    // 目标设备缓存(只存 BlockPos,每 tick 重新获取 TE 和 cap)
    protected final List<BlockPos> cachedTargets = new ArrayList<>();
    private int cacheRefreshCooldown = 0;

    // 客户端同步
    private int clientFlags = 0;
    private boolean lastPowered = false;
    private boolean lastActive = false;

    public TileChunkManaNode() {
    }

    /**
     * 获取当前缓存的供魔目标位置列表（副本）.
     */
    public List<BlockPos> getCachedTargets() {
        return new ArrayList<>(cachedTargets);
    }

    // ---------- 朝向与代理 ----------

    public void setForward(EnumFacing facing) {
        this.forward = facing != null ? facing : EnumFacing.NORTH;
        if (getProxy() != null) {
            getProxy().setValidSides(EnumSet.of(this.forward.getOpposite()));
        }
        markDirty();
    }

    public EnumFacing getForward() {
        return this.forward;
    }

    @Override
    protected String getProxyName() {
        return "chunk_mana_node";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.CHUNK_MANA_NODE);
    }

    @Override
    public void disassemble() {
        // 无需额外清理
    }

    @Override
    public void securityBreak() {
        if (world != null && !world.isRemote) {
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        if (this.forward != null && dir.getFacing() == this.forward.getOpposite()) {
            return AECableType.SMART;
        }
        return AECableType.NONE;
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        if (this.forward != null && dir.getFacing() == this.forward.getOpposite()) {
            return getProxy().getNode();
        }
        return null;
    }

    // ---------- 初始化与 tick ----------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady()) {
            clearNeedsReady();
            getProxy().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            getProxy().setIdlePowerUsage(32);
            getProxy().onReady();
        }

        if (!isActive()) return;

        doManaTick();
        syncClientState();
    }

    private void doManaTick() {
        if (!BotaniaManaHelper.isAvailable()) return;

        IMEMonitor<IAEManaStack> manaMonitor = getManaMonitor();
        if (manaMonitor == null) return;

        if (cacheRefreshCooldown <= 0) {
            refreshTargetCache();
            cacheRefreshCooldown = CACHE_REFRESH_INTERVAL;
        } else {
            cacheRefreshCooldown--;
        }

        MachineSource source = getMachineSource();

        for (BlockPos targetPos : cachedTargets) {
            TileEntity te = world.getTileEntity(targetPos);
            if (te == null || te.isInvalid()) continue;
            if (!BotaniaManaHelper.isManaReceiver(te)) continue;
            if (BotaniaManaHelper.isFull(te)) continue;

            int current = BotaniaManaHelper.getCurrentMana(te);
            int capacity = BotaniaManaHelper.getManaCapacity(te);
            if (capacity <= current) continue;

            int demand = capacity - current;
            if (demand <= 0) continue;

            IAEManaStack request = AEManaStack.create(demand);
            IAEManaStack extracted = manaMonitor.extractItems(request, Actionable.MODULATE, source);
            if (extracted == null || extracted.getStackSize() <= 0) continue;

            int toReceive = (int) Math.min(extracted.getStackSize(), Integer.MAX_VALUE);
            BotaniaManaHelper.receiveMana(te, toReceive);

            // 计算实际接受了多少,未注入部分返还 ME 网络
            int after = BotaniaManaHelper.getCurrentMana(te);
            int accepted = Math.max(0, after - current);
            long leftover = extracted.getStackSize() - accepted;
            if (leftover > 0) {
                manaMonitor.injectItems(AEManaStack.create(leftover), Actionable.MODULATE, source);
            }
        }
    }

    /**
     * 扫描本区块内所有 Botania 魔力接收设施,缓存其位置.
     *
     * <p>排除 Mana Void 方块与产能花.</p>
     */
    protected void refreshTargetCache() {
        cachedTargets.clear();
        if (!BotaniaManaHelper.isAvailable()) return;

        Chunk chunk = world.getChunk(pos);
        if (chunk == null) return;

        for (TileEntity te : chunk.getTileEntityMap().values()) {
            if (te == null || te.isInvalid()) continue;
            if (te == this) continue;

            BlockPos tp = te.getPos();
            if (BotaniaManaHelper.isManaVoid(world, tp)) continue;
            if (BotaniaManaHelper.isGeneratingFlower(te)) continue;
            if (!BotaniaManaHelper.isManaReceiver(te)) continue;

            cachedTargets.add(tp.toImmutable());
        }
    }

    // ---------- 状态同步 ----------

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
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @MENetworkEventSubscribe
    public void chanRender(MENetworkChannelsChanged c) {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @MENetworkEventSubscribe
    public void powerRender(MENetworkPowerStatusChange c) {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    public boolean isPowered() {
        if (world != null && world.isRemote) {
            return (this.clientFlags & 1) == 1;
        }
        try {
            return getProxy().getEnergy().isNetworkPowered();
        } catch (GridAccessException e) {
            return false;
        }
    }

    public boolean isActive() {
        if (world != null && world.isRemote) {
            return isPowered() && (this.clientFlags & 2) == 2;
        }
        try {
            return getProxy().isActive();
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 辅助 ----------

    private IMEMonitor<IAEManaStack> getManaMonitor() {
        try {
            IStorageGrid storageGrid = getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return null;
            return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    private MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }

    // ---------- NBT ----------

    @Override
    public void readFromNBT(net.minecraft.nbt.NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.forward = EnumFacing.byIndex(compound.getInteger("forward"));
        this.clientFlags = compound.getInteger("clientFlags");
        // cachedTargets 不持久化,重新扫描即可
    }

    @Override
    public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("forward", this.forward.getIndex());
        compound.setInteger("clientFlags", this.clientFlags);
        return compound;
    }

    // ---------- 网络同步 ----------

    @Override
    public net.minecraft.nbt.NBTTagCompound getUpdateTag() {
        net.minecraft.nbt.NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("forward", this.forward.getIndex());
        tag.setInteger("clientFlags", this.clientFlags);
        return tag;
    }

    @Override
    public void handleUpdateTag(net.minecraft.nbt.NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        this.forward = EnumFacing.byIndex(tag.getInteger("forward"));
        this.clientFlags = tag.getInteger("clientFlags");
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }
}
