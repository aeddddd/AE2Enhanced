package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChunkPowerNodeSync;
import com.github.aeddddd.ae2enhanced.platform.energy.EnergyAdapterRegistry;
import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** 需求退避 tick 数: 目标无能量需求时跳过探测的时长 */
    private static final int DEMAND_BACKOFF_TICKS = 10;

    /** 区块供电黑名单：这些方块不会被供能（避免自我循环或兼容问题） */
    protected static final Set<String> BLACKLIST = new HashSet<>();
    static {
        BLACKLIST.add("ae2enhanced:network_access_node");
    }

    private EnumFacing forward = EnumFacing.NORTH;
    private MachineSource machineSource;

    // 目标设备缓存(只存 BlockPos,每 tick 重新获取 TE 和 cap)
    protected final List<BlockPos> cachedTargets = new ArrayList<>();
    private int cacheRefreshCooldown = 0;

    // 目标附加缓存: 可用输入面 / 能量适配器 / 需求退避,随 refreshTargetCache 重建
    private final Map<BlockPos, EnumFacing> targetFaceCache = new HashMap<>();
    private final Map<BlockPos, IEnergyAdapter> targetAdapterCache = new HashMap<>();
    private final Map<BlockPos, Integer> demandBackoff = new HashMap<>();

    /** 排除名单：已解除绑定的设备位置，节点不再为其供电。持久化到 NBT */
    protected final Set<BlockPos> excludedTargets = new HashSet<>();

    // 每 tick 供电统计(瞬态,仅服务端有意义)
    private final Map<BlockPos, Long> lastTickDelivered = new HashMap<>();
    private long lastTickOutput = 0;

    // 客户端 GUI 同步缓存
    private final List<PacketChunkPowerNodeSync.TargetInfo> clientTargetList = new ArrayList<>();
    private long clientLastTickOutput = 0;

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void doPowerTick() {
        lastTickDelivered.clear();
        lastTickOutput = 0;

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

        // 经 EnergyChannelResolver 解析当前生效的能量通道（兼容 Flux_Applied 外部通道）
        IMEMonitor energyMonitor;
        try {
            energyMonitor = storageGrid.getInventory(EnergyChannelResolver.getChannel());
        } catch (NullPointerException e) {
            // 能量通道不可用
            return;
        }
        if (energyMonitor == null) return;
        MachineSource source = getMachineSource();

        for (BlockPos targetPos : cachedTargets) {
            if (excludedTargets.contains(targetPos)) continue;

            // 需求退避: 上轮探测无需求的目标跳过,降低 capability 模拟调用频率
            Integer backoff = demandBackoff.get(targetPos);
            if (backoff != null) {
                if (backoff > 1) {
                    demandBackoff.put(targetPos, backoff - 1);
                } else {
                    demandBackoff.remove(targetPos);
                }
                continue;
            }

            TileEntity te = world.getTileEntity(targetPos);
            if (te == null || te.isInvalid()) continue;

            // 优先使用缓存的输入面,失效时兜底重扫 6 个面
            IEnergyStorage cap = null;
            EnumFacing cachedFace = targetFaceCache.get(targetPos);
            if (cachedFace != null && te.hasCapability(CapabilityEnergy.ENERGY, cachedFace)) {
                IEnergyStorage c = te.getCapability(CapabilityEnergy.ENERGY, cachedFace);
                if (c != null && c.canReceive()) {
                    cap = c;
                }
            }
            if (cap == null) {
                for (EnumFacing facing : EnumFacing.values()) {
                    if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                        IEnergyStorage c = te.getCapability(CapabilityEnergy.ENERGY, facing);
                        if (c != null && c.canReceive()) {
                            cap = c;
                            targetFaceCache.put(targetPos, facing);
                            break;
                        }
                    }
                }
            }
            if (cap == null) continue;

            IEnergyAdapter adapter = targetAdapterCache.get(targetPos);
            if (adapter == null) {
                String blockId = world.getBlockState(targetPos).getBlock().getRegistryName().toString();
                adapter = EnergyAdapterRegistry.findAdapter(blockId);
                targetAdapterCache.put(targetPos, adapter);
            }

            long demand = adapter.getReceiveableEnergy(te, cap);
            if (demand <= 0) {
                demandBackoff.put(targetPos, DEMAND_BACKOFF_TICKS);
                continue;
            }

            IAEStack request = EnergyChannelResolver.createStack(demand);
            if (request == null) continue;
            IAEStack extracted = (IAEStack) energyMonitor.extractItems(request, Actionable.MODULATE, source);
            if (extracted == null || extracted.getStackSize() <= 0) continue;

            long toInject = extracted.getStackSize();
            long actual = adapter.injectEnergy(te, cap, toInject, false);

            if (actual > 0) {
                lastTickDelivered.merge(targetPos, actual, Long::sum);
                lastTickOutput += actual;
            }

            long leftover = extracted.getStackSize() - actual;
            if (leftover > 0) {
                IAEStack rest = EnergyChannelResolver.createStack(leftover);
                if (rest != null) {
                    energyMonitor.injectItems(rest, Actionable.MODULATE, source);
                }
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
        clearTargetCaches();
        Chunk chunk = world.getChunk(pos);
        if (chunk == null) return;

        for (TileEntity te : chunk.getTileEntityMap().values()) {
            registerTargetIfReceivable(te);
        }
    }

    /**
     * 清空目标缓存及其附加缓存(输入面/适配器/需求退避),供子类重扫前调用.
     */
    protected final void clearTargetCaches() {
        cachedTargets.clear();
        targetFaceCache.clear();
        targetAdapterCache.clear();
        demandBackoff.clear();
    }

    /**
     * 扫描 TE 各朝向的能量 capability,确认可接收后登记目标,
     * 同时记录可用输入面与能量适配器,供 tick 循环直接复用.
     */
    protected final void registerTargetIfReceivable(TileEntity te) {
        if (te == null || te.isInvalid()) return;
        if (te == this) return;

        BlockPos tp = te.getPos();
        EnumFacing receiveFace = null;
        for (EnumFacing facing : EnumFacing.values()) {
            if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                IEnergyStorage cap = te.getCapability(CapabilityEnergy.ENERGY, facing);
                if (cap != null && cap.canReceive()) {
                    receiveFace = facing;
                    break;
                }
            }
        }
        if (receiveFace == null) return;

        // 黑名单检查（仅对找到可接收面的目标才获取 blockId）
        String blockId = world.getBlockState(tp).getBlock().getRegistryName().toString();
        if (BLACKLIST.contains(blockId)) return;

        BlockPos immutable = tp.toImmutable();
        cachedTargets.add(immutable);
        targetFaceCache.put(immutable, receiveFace);
        targetAdapterCache.put(immutable, EnergyAdapterRegistry.findAdapter(blockId));
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

    // ---------- 排除名单与统计 ----------

    /**
     * 判断指定位置的设备是否已被排除（解除绑定）.
     */
    public boolean isTargetExcluded(BlockPos targetPos) {
        return excludedTargets.contains(targetPos);
    }

    /**
     * 设置指定位置设备的排除状态。排除后节点不再为其供电，可随时恢复.
     */
    public void setTargetExcluded(BlockPos targetPos, boolean excluded) {
        if (excluded) {
            excludedTargets.add(targetPos.toImmutable());
        } else {
            excludedTargets.remove(targetPos);
        }
        markDirty();
    }

    /**
     * 上一 tick 实际输出的总能量（FE）.
     */
    public long getLastTickOutput() {
        if (world != null && world.isRemote) {
            return clientLastTickOutput;
        }
        return lastTickOutput;
    }

    /**
     * 上一 tick 向指定目标实际交付的能量（FE，仅服务端有效）.
     */
    public long getLastTickDelivered(BlockPos targetPos) {
        return lastTickDelivered.getOrDefault(targetPos, 0L);
    }

    // ---------- GUI 同步 ----------

    /**
     * 构建 GUI 同步包：包含状态、每 tick 输出与目标列表（坐标/名称/排除状态/交付量）.
     */
    public PacketChunkPowerNodeSync buildSyncPacket() {
        List<PacketChunkPowerNodeSync.TargetInfo> targets = new ArrayList<>(cachedTargets.size());
        for (BlockPos targetPos : cachedTargets) {
            net.minecraft.block.Block block = world.getBlockState(targetPos).getBlock();
            targets.add(new PacketChunkPowerNodeSync.TargetInfo(
                    targetPos, new ItemStack(block), block.getTranslationKey(),
                    excludedTargets.contains(targetPos), getLastTickDelivered(targetPos)));
        }
        // 排序：已排除的排最前，其余按当前消耗 FE 从大到小
        targets.sort((a, b) -> {
            if (a.isExcluded() != b.isExcluded()) {
                return a.isExcluded() ? -1 : 1;
            }
            return Long.compare(b.getDelivered(), a.getDelivered());
        });
        return new PacketChunkPowerNodeSync(pos, isPowered(), isActive(), lastTickOutput, targets);
    }

    /**
     * 客户端处理 GUI 同步包.
     */
    public void handleSyncPacket(PacketChunkPowerNodeSync packet) {
        this.clientTargetList.clear();
        this.clientTargetList.addAll(packet.getTargets());
        this.clientLastTickOutput = packet.getLastTickOutput();
        int flags = this.clientFlags & ~3;
        if (packet.isPowered()) flags |= 1;
        if (packet.isActive()) flags |= 2;
        this.clientFlags = flags;
    }

    /**
     * 客户端获取最近一次同步的目标列表.
     */
    public List<PacketChunkPowerNodeSync.TargetInfo> getClientTargetList() {
        return clientTargetList;
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
        this.excludedTargets.clear();
        net.minecraft.nbt.NBTTagList excludedList = compound.getTagList("excludedTargets", 4); // 4 = NBTTagLong
        for (int i = 0; i < excludedList.tagCount(); i++) {
            this.excludedTargets.add(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong) excludedList.get(i)).getLong()));
        }
        // cachedTargets 不持久化,重新扫描即可
    }

    @Override
    public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("forward", this.forward.getIndex());
        compound.setInteger("clientFlags", this.clientFlags);
        net.minecraft.nbt.NBTTagList excludedList = new net.minecraft.nbt.NBTTagList();
        for (BlockPos p : this.excludedTargets) {
            excludedList.appendTag(new net.minecraft.nbt.NBTTagLong(p.toLong()));
        }
        compound.setTag("excludedTargets", excludedList);
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
