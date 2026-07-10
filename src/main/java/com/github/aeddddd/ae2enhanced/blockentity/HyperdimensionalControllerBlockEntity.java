package com.github.aeddddd.ae2enhanced.blockentity;

import java.math.BigInteger;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalMEStorage;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorage;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.multiblock.IStorageHost;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;

/**
 * 超维度仓储中枢控制器方块实体。
 * <p>持有 Nexus UUID 与 BigInteger 外部存储，通过通用 ME 接口挂载到 AE2 网络。</p>
 */
public class HyperdimensionalControllerBlockEntity extends MultiblockControllerBlockEntity
        implements IStorageHost {

    private static final String TAG_NEXUS_ID = "nexusId";
    private static final String TAG_NETWORK_ACTIVE = "networkActive";
    private static final String TAG_NETWORK_POWERED = "networkPowered";
    private static final String TAG_STORAGE_TYPES = "storageTypes";
    private static final String TAG_STORAGE_TOTAL = "storageTotal";
    private static final String TAG_SAFE_MODE = "safeMode";

    @Nullable
    private UUID nexusId;
    @Nullable
    private HyperdimensionalStorage storage;
    @Nullable
    private HyperdimensionalMEStorage meStorage;

    private int validationCooldown = 0;
    private int saveCooldown = 0;
    private int statusCooldown = 0;
    private int networkUpdateCooldown = 0;
    private boolean pendingNetworkUpdate = false;

    // 客户端同步字段
    private boolean networkActive = false;
    private boolean networkPowered = false;
    private int storageTypes = 0;
    private long storageTotal = 0;
    private boolean safeMode = false;

    public HyperdimensionalControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HYPERDIMENSIONAL_CONTROLLER.get(), pos, blockState);
    }

    @Nullable
    public UUID getNexusId() {
        return nexusId;
    }

    public void setNexusId(UUID nexusId) {
        this.nexusId = nexusId;
        setChanged();
    }

    public boolean isNetworkActive() {
        return networkActive;
    }

    public boolean isNetworkPowered() {
        return networkPowered;
    }

    public int getStorageTypes() {
        return storageTypes;
    }

    public long getStorageTotal() {
        return storageTotal;
    }

    public boolean isSafeMode() {
        return safeMode;
    }

    public String getStorageTotalRaw() {
        return String.valueOf(storageTotal);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        Direction facing = Direction.NORTH;
        if (level != null) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock.FACING)) {
                facing = state.getValue(com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock.FACING);
            }
        }
        // 特效中心：结构中心 (0,0,2)，抬高 2.5
        Vec3 localCenter = new Vec3(0.0, 2.5, 2.0);
        Vec3 rotatedCenter = rotateOffset(localCenter, facing);
        Vec3 worldCenter = new Vec3(pos.getX() + rotatedCenter.x, pos.getY() + rotatedCenter.y,
                pos.getZ() + rotatedCenter.z);
        return new AABB(worldCenter, worldCenter).inflate(8.0);
    }

    private static Vec3 rotateOffset(Vec3 local, Direction facing) {
        double x = local.x;
        double y = local.y;
        double z = local.z;
        return switch (facing) {
            case SOUTH -> new Vec3(-x, y, -z);
            case EAST -> new Vec3(-z, y, x);
            case WEST -> new Vec3(z, y, -x);
            default -> new Vec3(x, y, z);
        };
    }

    @Override
    public void onAssemble() {
        initStorage();
        updateCableConnections();
    }

    @Override
    public void onDisassemble() {
        flushStorage();
        networkActive = false;
        networkPowered = false;
        storageTypes = 0;
        storageTotal = 0;
    }

    private void initStorage() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (nexusId == null) {
            nexusId = UUID.randomUUID();
            setChanged();
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        if (storage == null) {
            storage = HyperdimensionalStorageFile.loadOrCreate(server, nexusId, s -> onStorageContentChanged());
        }
        refreshMeStorageSource();
        // 后续 GUI 可在此注册监听器以实时刷新；网络统计每 20 tick 刷新一次。
    }

    private void refreshMeStorageSource() {
        if (storage == null) {
            return;
        }
        IActionSource source = getActionSource();
        if (meStorage == null || !source.equals(meStorage.getSource())) {
            meStorage = new HyperdimensionalMEStorage(storage, source);
        }
    }

    /**
     * 当内部存储变化时通知 AE2 网络刷新。
     * <p>为避免高频写入时反复调用 requestNetworkUpdate，这里仅标记 pending；
     * 由 {@link #serverTick()} 以最低 5 tick 的间隔统一触发一次。</p>
     */
    private void onStorageContentChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        pendingNetworkUpdate = true;
    }

    @Override
    public void attachInterface(BlockPos interfacePos) {
        super.attachInterface(interfacePos);
        refreshMeStorageSource();
    }

    @Override
    public void detachInterface(BlockPos interfacePos) {
        super.detachInterface(interfacePos);
        refreshMeStorageSource();
    }

    public void flushStorage() {
        if (storage == null || level == null || level.isClientSide()) {
            return;
        }
        storage.persist();
    }


    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            if (isFormed() && !HyperdimensionalStructure.validate(level, worldPosition)) {
                HyperdimensionalStructure.disassemble(level, worldPosition);
            }
        }

        if (saveCooldown-- <= 0) {
            saveCooldown = AE2EnhancedConfig.COMMON.hyperdimensionalFlushIntervalSeconds.get() * 20;
            if (storage != null) {
                storage.flush();
            }
        }

        if (statusCooldown-- <= 0) {
            statusCooldown = 20;
            refreshNetworkStatus();
        }

        if (networkUpdateCooldown-- <= 0 && pendingNetworkUpdate) {
            networkUpdateCooldown = 5;
            pendingNetworkUpdate = false;
            for (BlockPos pos : getInterfaces()) {
                if (level.getBlockEntity(pos) instanceof MultiblockMeInterfaceBlockEntity me) {
                    me.requestNetworkUpdate();
                }
            }
        }
    }

    /**
     * 强制刷新接口相邻位置的 AE2 线缆连接，修复线缆连接时序问题。
     */
    private void updateCableConnections() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (BlockPos pos : getInterfaces()) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof appeng.blockentity.networking.CableBusBlockEntity cableBe) {
                    appeng.parts.CableBusContainer cbc = cableBe.getCableBus();
                    if (cbc != null) {
                        cbc.updateConnections();
                    }
                }
            }
        }
    }

    private void refreshNetworkStatus() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean active = false;
        boolean powered = false;
        for (BlockPos pos : getInterfaces()) {
            if (level.getBlockEntity(pos) instanceof MultiblockMeInterfaceBlockEntity me) {
                IManagedGridNode node = me.getMainNode();
                if (node != null) {
                    active = node.isActive();
                    IGrid grid = node.getGrid();
                    if (grid != null) {
                        IEnergyService energy = grid.getEnergyService();
                        powered = energy != null && energy.isNetworkPowered();
                    }
                    break;
                }
            }
        }
        boolean changed = networkActive != active || networkPowered != powered;
        networkActive = active;
        networkPowered = powered;
        refreshStats();
        if (changed) {
            markForUpdate();
        }
    }

    private void refreshStats() {
        if (level == null || level.isClientSide()) {
            return;
        }
        int types = storage == null ? 0 : storage.getContents().size();
        long total = 0;
        if (storage != null) {
            BigInteger sum = BigInteger.ZERO;
            for (BigInteger amount : storage.getContents().values()) {
                sum = sum.add(amount);
            }
            total = sum.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }
        boolean sm = storage != null && storage.isSafeMode();
        boolean changed = storageTypes != types || storageTotal != total || safeMode != sm;
        storageTypes = types;
        storageTotal = total;
        safeMode = sm;
        if (changed) {
            markForUpdate();
        }
    }

    // ---- IStorageHost ----

    @Nullable
    @Override
    public MEStorage getStorage() {
        if (!isFormed() || meStorage == null) {
            return null;
        }
        return meStorage;
    }

    // ---- NBT / 客户端同步 ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_NETWORK_ACTIVE, networkActive);
        tag.putBoolean(TAG_NETWORK_POWERED, networkPowered);
        tag.putInt(TAG_STORAGE_TYPES, storageTypes);
        tag.putLong(TAG_STORAGE_TOTAL, storageTotal);
        tag.putBoolean(TAG_SAFE_MODE, safeMode);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains(TAG_NETWORK_ACTIVE, Tag.TAG_BYTE)) {
            networkActive = tag.getBoolean(TAG_NETWORK_ACTIVE);
        }
        if (tag.contains(TAG_NETWORK_POWERED, Tag.TAG_BYTE)) {
            networkPowered = tag.getBoolean(TAG_NETWORK_POWERED);
        }
        if (tag.contains(TAG_STORAGE_TYPES, Tag.TAG_INT)) {
            storageTypes = tag.getInt(TAG_STORAGE_TYPES);
        }
        if (tag.contains(TAG_STORAGE_TOTAL, Tag.TAG_LONG)) {
            storageTotal = tag.getLong(TAG_STORAGE_TOTAL);
        }
        if (tag.contains(TAG_SAFE_MODE, Tag.TAG_BYTE)) {
            safeMode = tag.getBoolean(TAG_SAFE_MODE);
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.hasUUID(TAG_NEXUS_ID)) {
            nexusId = data.getUUID(TAG_NEXUS_ID);
        } else {
            nexusId = null;
        }
        networkActive = data.getBoolean(TAG_NETWORK_ACTIVE);
        networkPowered = data.getBoolean(TAG_NETWORK_POWERED);
        storageTypes = data.getInt(TAG_STORAGE_TYPES);
        storageTotal = data.getLong(TAG_STORAGE_TOTAL);
        safeMode = data.getBoolean(TAG_SAFE_MODE);
        if (isFormed() && nexusId != null && level != null && !level.isClientSide()) {
            initStorage();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        if (nexusId != null) {
            data.putUUID(TAG_NEXUS_ID, nexusId);
        }
        data.putBoolean(TAG_NETWORK_ACTIVE, networkActive);
        data.putBoolean(TAG_NETWORK_POWERED, networkPowered);
        data.putInt(TAG_STORAGE_TYPES, storageTypes);
        data.putLong(TAG_STORAGE_TOTAL, storageTotal);
        data.putBoolean(TAG_SAFE_MODE, safeMode);
    }
}
