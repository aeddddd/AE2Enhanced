package com.github.aeddddd.ae2enhanced.blockentity;

import java.math.BigInteger;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalMEStorage;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorage;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;
import com.github.aeddddd.ae2enhanced.util.BlockEntityRemovalHelper;

/**
 * 超维度仓储中枢控制器方块实体.
 * <p>自身作为 AE2 网络节点（任意结构方块均可并网）,成形后通过自身节点向网络提供
 * IStorageProvider 服务,挂载 Nexus UUID 对应的 BigInteger 外部存储.</p>
 */
public class HyperdimensionalControllerBlockEntity extends AENetworkBlockEntity
        implements IMultiblockController, IStorageProvider {

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

    private boolean formed = false;
    private boolean showingStructureProjection = false;

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

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(ModItems.HYPERDIMENSIONAL_CONTROLLER.get())
                .addService(IStorageProvider.class, this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
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
        // 特效中心：结构中心 (0,0,2) 上方 4.0 格(与渲染器一致,以方块中心为旋转基准)
        Vec3 localCenter = new Vec3(0.0, 3.5, 2.0);
        Vec3 rotatedCenter = rotateOffset(localCenter, facing);
        Vec3 worldCenter = new Vec3(pos.getX() + 0.5 + rotatedCenter.x, pos.getY() + 0.5 + rotatedCenter.y,
                pos.getZ() + 0.5 + rotatedCenter.z);
        return new AABB(worldCenter, worldCenter).inflate(5.5);
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

    // ---- IMultiblockController ----

    @Override
    public boolean isFormed() {
        return formed;
    }

    @Override
    public boolean isShowingStructureProjection() {
        return showingStructureProjection;
    }

    @Override
    public void toggleStructureProjection() {
        if (formed) {
            showingStructureProjection = false;
            return;
        }
        showingStructureProjection = !showingStructureProjection;
        setChanged();
        markForUpdate();
    }

    @Override
    public void setFormed(boolean formed) {
        if (this.formed != formed) {
            this.formed = formed;
            setChanged();
            markForUpdate();
        }
    }

    @Override
    public void assemble() {
        if (isFormed()) {
            return;
        }
        onAssemble();
        setFormed(true);
        requestStorageUpdate();
    }

    @Override
    public void disassemble() {
        if (!isFormed()) {
            return;
        }
        onDisassemble();
        setFormed(false);
        requestStorageUpdate();
    }

    @Override
    public void onAssemble() {
        initStorage();
    }

    @Override
    public void onDisassemble() {
        flushStorage();
        networkActive = false;
        networkPowered = false;
        storageTypes = 0;
        storageTotal = 0;
    }

    @Override
    public BlockPos getControllerPos() {
        return worldPosition;
    }

    @Override
    @Nullable
    public IMultiblockStructure getStructure() {
        if (level == null) {
            return null;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock controllerBlock) {
            return controllerBlock.getStructure();
        }
        return null;
    }

    @Override
    public IActionSource getActionSource() {
        return IActionSource.ofMachine(this);
    }

    // ---- 存储生命周期 ----

    @Override
    public void onLoad() {
        super.onLoad();
        // 区块 NBT 反序列化阶段 level 为 null,无法初始化存储;
        // 延迟到 onLoad 重建 storage/meStorage 运行时对象,避免重进存档后网络识别不到仓储.
        if (level != null && !level.isClientSide() && isFormed() && nexusId != null) {
            initStorage();
        }
    }

    @Override
    public void onChunkUnloaded() {
        // 区块卸载时方块实体被丢弃,先 flush 未持久化的内容,防止重载后从文件读到旧数据.
        flushStorage();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide() && isFormed()
                && BlockEntityRemovalHelper.isBlockBeingBroken(this)) {
            // 仅在控制器方块真正被破坏时解散；
            // 区块卸载或关服时触发 setRemoved 不应执行完整拆解,避免额外 IO 与状态异常.
            disassemble();
        }
        super.setRemoved();
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
        // 后续 GUI 可在此注册监听器以实时刷新；网络统计每 20 tick 刷新一次.
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
     * 当内部存储变化时通知 AE2 网络刷新.
     * <p>为避免高频写入时反复调用 requestUpdate,这里仅标记 pending；
     * 由 {@link #serverTick()} 以最低 5 tick 的间隔统一触发一次.</p>
     */
    private void onStorageContentChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        pendingNetworkUpdate = true;
    }

    /**
     * 通知网络重新挂载存储（成形状态变化或内容变化时调用）.
     */
    private void requestStorageUpdate() {
        IManagedGridNode node = getMainNode();
        if (node != null) {
            IStorageProvider.requestUpdate(node);
        }
    }

    public void flushStorage() {
        if (storage == null || level == null || level.isClientSide()) {
            return;
        }
        storage.persist();
    }

    // ---- IStorageProvider ----

    @Override
    public void mountInventories(IStorageMounts mounts) {
        if (isFormed() && meStorage != null) {
            mounts.mount(meStorage);
        }
    }

    // ---- Tick ----

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            IMultiblockStructure structure = getStructure();
            if (structure != null && isFormed() && !structure.validate(level, worldPosition)) {
                structure.disassemble(level, worldPosition);
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
            requestStorageUpdate();
        }
    }

    /**
     * 客户端 tick：移植自 1.12 TileHyperdimensionalController#update 的客户端分支.
     * <p>成形且网络活跃时,生成向结构中心汇聚的附魔粒子(能量流动效果).</p>
     */
    public void clientTick() {
        if (level == null || !level.isClientSide()) {
            return;
        }
        if (!isFormed() || !networkActive) {
            return;
        }
        if (level.random.nextInt(6) != 0) {
            return;
        }

        Direction facing = Direction.NORTH;
        BlockState state = getBlockState();
        if (state.hasProperty(com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock.FACING)) {
            facing = state.getValue(com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock.FACING);
        }
        Vec3 off = switch (facing) {
            case SOUTH -> new Vec3(0, 0, -2.0);
            case EAST -> new Vec3(-2.0, 0, 0);
            case WEST -> new Vec3(2.0, 0, 0);
            default -> new Vec3(0, 0, 2.0);
        };
        double cx = worldPosition.getX() + 0.5 + off.x;
        double cy = worldPosition.getY() + 1.5;
        double cz = worldPosition.getZ() + 0.5 + off.z;
        double px = cx + (level.random.nextDouble() - 0.5) * 4.0;
        double py = cy + (level.random.nextDouble() - 0.5) * 2.0;
        double pz = cz + (level.random.nextDouble() - 0.5) * 4.0;
        level.addParticle(ParticleTypes.ENCHANT, px, py, pz,
                (cx - px) * 0.05, (cy - py) * 0.05, (cz - pz) * 0.05);
    }

    private void refreshNetworkStatus() {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean active = false;
        boolean powered = false;
        IManagedGridNode node = getMainNode();
        if (node != null) {
            active = node.isActive();
            IGrid grid = node.getGrid();
            if (grid != null) {
                IEnergyService energy = grid.getEnergyService();
                powered = energy != null && energy.isNetworkPowered();
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

    // ---- NBT / 客户端同步 ----

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("formed", formed);
        tag.putBoolean("showProjection", showingStructureProjection);
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
        if (tag.contains("formed", Tag.TAG_BYTE)) {
            this.formed = tag.getBoolean("formed");
        }
        if (tag.contains("showProjection", Tag.TAG_BYTE)) {
            this.showingStructureProjection = tag.getBoolean("showProjection");
        }
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
        formed = data.getBoolean("formed");
        showingStructureProjection = data.getBoolean("showProjection");
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
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putBoolean("formed", formed);
        data.putBoolean("showProjection", showingStructureProjection);
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
