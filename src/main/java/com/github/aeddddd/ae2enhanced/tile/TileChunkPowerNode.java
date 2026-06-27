package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.storage.IMEMonitor;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.platform.energy.EnergyAdapterRegistry;
import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 区块供电节点的 TileEntity.
 *
 * <p>消耗 1 个 AE 频道,从连接的 ME 网络 RF 存储通道提取能量,
 * 向所在区块(16×16)内所有可接收 Forge Energy 的设备供能.</p>
 *
 * <p>供能策略：</p>
 * <ul>
 *   <li>每 {@link #CACHE_REFRESH_INTERVAL} tick 重新扫描本区块目标设备并缓存位置</li>
 *   <li>每 tick 遍历缓存,按需从 ME 网络提取并注入(支持 {@link IEnergyAdapter} 模组优化)</li>
 *   <li>未用完的能量立即返还 ME 网络</li>
 * </ul>
 */
public class TileChunkPowerNode extends TileAENetworkBase implements ITickable, IActionHost {

    private static final int CACHE_REFRESH_INTERVAL = 20;

    /** 区块供电黑名单：这些方块不会被供能（避免自我循环或兼容问题） */
    private static final Set<String> BLACKLIST = new HashSet<>();
    static {
        BLACKLIST.add("ae2enhanced:network_access_node");
    }

    private EnumFacing forward = EnumFacing.NORTH;
    private MachineSource machineSource;

    // 目标设备缓存(只存 BlockPos,每 tick 重新获取 TE 和 cap)
    protected final List<BlockPos> cachedTargets = new ArrayList<>();
    private int cacheRefreshCooldown = 0;

    /**
     * 获取当前缓存的供电目标位置列表（副本）.
     */
    public List<BlockPos> getCachedTargets() {
        return new ArrayList<>(cachedTargets);
    }

    // 客户端同步
    private int clientFlags = 0;
    private boolean lastPowered = false;
    private boolean lastActive = false;

    public TileChunkPowerNode() {
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
        return "chunk_power_node";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.CHUNK_POWER_NODE);
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

        doPowerTick();
        syncClientState();
    }

    private void doPowerTick() {
        if (cacheRefreshCooldown <= 0) {
            refreshTargetCache();
            cacheRefreshCooldown = CACHE_REFRESH_INTERVAL;
        } else {
            cacheRefreshCooldown--;
        }

        if (cachedTargets.isEmpty()) return;

        appeng.api.networking.storage.IStorageGrid storageGrid;
        try {
            storageGrid = getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
        } catch (GridAccessException e) {
            return;
        }

        IMEMonitor<IAEEnergyStack> energyMonitor = storageGrid.getInventory(
                AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class));
        MachineSource source = getMachineSource();

        for (BlockPos targetPos : cachedTargets) {
            TileEntity te = world.getTileEntity(targetPos);
            if (te == null || te.isInvalid()) continue;

            IEnergyStorage cap = null;
            for (EnumFacing facing : EnumFacing.values()) {
                if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                    IEnergyStorage c = te.getCapability(CapabilityEnergy.ENERGY, facing);
                    if (c != null && c.canReceive()) {
                        cap = c;
                        break;
                    }
                }
            }
            if (cap == null) continue;

            String blockId = world.getBlockState(targetPos).getBlock().getRegistryName().toString();
            IEnergyAdapter adapter = EnergyAdapterRegistry.findAdapter(blockId);

            long demand = adapter.getReceiveableEnergy(te, cap);
            if (demand <= 0) continue;

            IAEEnergyStack request = AEEnergyStack.create(demand);
            IAEEnergyStack extracted = energyMonitor.extractItems(request, Actionable.MODULATE, source);
            if (extracted == null || extracted.getStackSize() <= 0) continue;

            long toInject = extracted.getStackSize();
            long actual = adapter.injectEnergy(te, cap, toInject, false);

            long leftover = extracted.getStackSize() - actual;
            if (leftover > 0) {
                energyMonitor.injectItems(AEEnergyStack.create(leftover), Actionable.MODULATE, source);
            }
        }
    }

    /**
     * 扫描本区块内所有可接收能量的 TileEntity,缓存其位置.
     *
     * <p>优化：直接读取当前 chunk 的 {@code tileEntities} 映射,避免每 20 tick
     * 遍历全图 {@code world.loadedTileEntityList}.</p>
     *
     * <p>某些模组(如 Mekanism)的 {@code IEnergyStorage} capability 只在特定朝向
     * 上暴露为可接收({@code canReceive() == true}).因此需要遍历 6 个面查找有效输入面,
     * 而非直接传 {@code null}.</p>
     */
    protected void refreshTargetCache() {
        cachedTargets.clear();
        Chunk chunk = world.getChunk(pos);
        if (chunk == null) return;

        for (TileEntity te : chunk.getTileEntityMap().values()) {
            if (te == null || te.isInvalid()) continue;
            if (te == this) continue;

            BlockPos tp = te.getPos();
            boolean canReceive = false;
            for (EnumFacing facing : EnumFacing.values()) {
                if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                    IEnergyStorage cap = te.getCapability(CapabilityEnergy.ENERGY, facing);
                    if (cap != null && cap.canReceive()) {
                        canReceive = true;
                        break;
                    }
                }
            }
            if (!canReceive) continue;

            // 黑名单检查（仅对找到可接收面的目标才获取 blockId）
            String blockId = world.getBlockState(tp).getBlock().getRegistryName().toString();
            if (BLACKLIST.contains(blockId)) continue;

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
