package com.github.aeddddd.ae2enhanced.assembly.blockentity;

import java.util.BitSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.crafting.inv.ListCraftingInventory;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyCraftingProcessor;
import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternInventory;
import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternManager;
import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternSavedData;
import com.github.aeddddd.ae2enhanced.assembly.AssemblyUpgradeManager;
import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
import com.github.aeddddd.ae2enhanced.client.render.AbstractMultiblockRenderer;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
import com.github.aeddddd.ae2enhanced.multiblock.IPatternProviderHost;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;
import com.github.aeddddd.ae2enhanced.util.BlockEntityRemovalHelper;

/**
 * 装配枢纽控制器方块实体。
 * <p>自身作为 AE2 网络节点，向网络提供 Long 级别的并行虚拟样板合成，支持升级卡、样板分页与产物缓冲。
 * 核心逻辑已拆分到 {@link AssemblyPatternManager}、{@link AssemblyUpgradeManager} 与 {@link AssemblyCraftingProcessor}。</p>
 */
public class AssemblyControllerBlockEntity extends AENetworkBlockEntity
        implements IPatternProviderHost, ICraftingProvider, PatternContainer, IMultiblockController {

    public static final int UPGRADE_SLOTS = 6;
    public static final int PATTERN_SLOTS_PER_PAGE = 102; // 17×6
    public static final int PATTERN_PAGES_BASE = 5;
    public static final int PATTERN_PAGES_PER_CAPACITY = 10; // 10 张扩容卡即可完整解锁 100 页
    public static final int PATTERN_PAGES_MAX = 100;
    public static final int TOTAL_SLOTS_MAX = UPGRADE_SLOTS + PATTERN_SLOTS_PER_PAGE * PATTERN_PAGES_MAX;
    public static final int TOTAL_SLOTS_BASE = UPGRADE_SLOTS + PATTERN_SLOTS_PER_PAGE * PATTERN_PAGES_BASE;

    private final AssemblyUpgradeManager upgradeManager = new AssemblyUpgradeManager();
    private final AssemblyPatternManager patternManager;
    private final AssemblyCraftingProcessor craftingProcessor;

    private int validationCooldown = 0;
    private int statusTick = 0;
    private boolean networkActive = false;
    private boolean networkPowered = false;
    private boolean formed = false;
    private boolean showingStructureProjection = false;

    /**
     * 真实合成 batch 信息缓存：配方、催化剂槽位、槽位物品模板。
     * <p>保留在此类中作为公开 API 供 Mixin 使用。</p>
     */
    public static class PatternBatchInfo {
        public CraftingRecipe recipe;
        public BitSet catalystSlots; // 真催化剂：remaining 与 input 完全一致(NBT 不变)
        public BitSet transformSlots; // 消耗性转换：remaining 与 input 同一物品但 NBT 不同
        public AEKey[] slotTemplates; // 每个槽位实际提取的物品模板
    }

    public AssemblyControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_CONTROLLER.get(), pos, state);
        this.patternManager = new AssemblyPatternManager(this, this.upgradeManager);
        this.upgradeManager.setPatternManager(this.patternManager);
        this.craftingProcessor = new AssemblyCraftingProcessor(this, this.upgradeManager, this.patternManager);
    }

    public ItemStackHandler getItemHandler() {
        return patternManager.getItemHandler();
    }

    public int getPatternSlotCount() {
        return patternManager.getPatternSlotCount();
    }

    /**
     * 当前并行上限。0 张并行升级卡 = 64，每多 1 张 ×32，5 张 = Long.MAX_VALUE。
     */
    public long getParallelCap() {
        return upgradeManager.getParallelCap();
    }

    /**
     * 设置当前执行样板的动作来源。由 Mixin 在批量处理前设置，确保 AE2 网络操作归因正确。
     */
    public void setCurrentActionSource(@Nullable IActionSource source) {
        this.craftingProcessor.setCurrentActionSource(source);
    }

    /**
     * 获取实际应使用的动作来源。优先使用 Mixin 设置的临时来源，否则回退到机器源。
     */
    public IActionSource getEffectiveActionSource() {
        return this.craftingProcessor.getEffectiveActionSource();
    }

    /**
     * 当前 tick 是否还能接受新的 batch。batchBusy 会在每个 tick 的服务器端刷新。
     */
    public boolean canBatch() {
        return this.craftingProcessor.canBatch();
    }

    /**
     * 标记 batch 忙碌状态。由 Mixin 在批量处理前后调用。
     */
    public void setBatchBusy(boolean busy) {
        this.craftingProcessor.setBatchBusy(busy);
    }

    public void resetBatchCooldown() {
        this.craftingProcessor.resetBatchCooldown();
    }

    /**
     * 当前合成延迟 tick 数。0 张速度升级卡 = 20，每张减半，最低 1 tick。
     */
    public int getCraftingTicks() {
        return upgradeManager.getCraftingTicks();
    }

    /**
     * 当前可用样板页数。基础 5 页，每张扩容升级卡 +5 页，上限 30 页。
     */
    public int getPatternPages() {
        return upgradeManager.getPatternPages();
    }

    /**
     * 检查是否安装了样板自动上传模块升级（槽位 4）。
     */
    public boolean hasAutoUploadUpgrade() {
        return upgradeManager.hasAutoUploadUpgrade();
    }

    // ---- Pattern Batch Info (real vs virtual crafting) ----

    /**
     * 供 Mixin 调用：检查指定样板是否已被缓存为纯虚拟合成(无剩余物品或加工样板)。
     */
    public boolean isVirtualPattern(IPatternDetails details) {
        return this.craftingProcessor.isVirtualPattern(details);
    }

    /**
     * 供 Mixin 调用：检查 pendingOutputs 是否还能接受指定数量的 stack。
     */
    public boolean canAcceptRealBatch(int stackCount) {
        return this.craftingProcessor.canAcceptRealBatch(stackCount);
    }

    /**
     * 供 Mixin 调用：安全地将产物加入 pendingOutputs。
     */
    public void addPendingOutput(ItemStack stack) {
        this.craftingProcessor.addPendingOutput(stack);
    }

    /**
     * 安全地将 GenericStack 产物加入 pendingOutputs。
     */
    public void addPendingOutput(GenericStack stack) {
        this.craftingProcessor.addPendingOutput(stack);
    }

    /**
     * 供 Mixin 调用：获取或创建 PatternBatchInfo(含催化剂识别)。
     */
    public PatternBatchInfo getPatternBatchInfo(IPatternDetails details, ListCraftingInventory meInv,
            IActionSource source) {
        return this.craftingProcessor.getPatternBatchInfo(details, meInv, source);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(ModItems.ASSEMBLY_CONTROLLER.get())
                .addService(ICraftingProvider.class, this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

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

        patternManager.ensurePatternCapacity();
        patternManager.tickRefresh();

        craftingProcessor.tryInjectPendingOutputs();
        craftingProcessor.tickJobTimers();

        // 黑洞事件视界逻辑
        if (AE2EnhancedConfig.COMMON.enableBlackHole.get() && isFormed()) {
            craftingProcessor.tickBlackHole();
        }

        if (++statusTick >= 20) {
            statusTick = 0;
            refreshNetworkStatus();
        }
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
        if (networkActive != active || networkPowered != powered) {
            networkActive = active;
            networkPowered = powered;
            setChanged();
            markForUpdate();
        }
    }

    public boolean isNetworkActive() {
        return networkActive;
    }

    public boolean isNetworkPowered() {
        return networkPowered;
    }

    public int getJobCount() {
        return this.craftingProcessor.getJobCount();
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
        refreshInterfaceServices();
    }

    @Override
    public void disassemble() {
        if (!isFormed()) {
            return;
        }
        onDisassemble();
        setFormed(false);
        craftingProcessor.clearState();
        refreshInterfaceServices();
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
        if (state.getBlock() instanceof MultiblockControllerBlock controllerBlock) {
            return controllerBlock.getStructure();
        }
        return null;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        Direction facing = Direction.NORTH;
        if (level != null) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(MultiblockControllerBlock.FACING)) {
                facing = state.getValue(MultiblockControllerBlock.FACING);
            }
        }
        IMultiblockStructure structure = getStructure();
        java.util.Set<BlockPos> positions = structure != null ? structure.getAllPositions() : java.util.Set.of();
        float[] bounds = AbstractMultiblockRenderer.computeBounds(positions, facing);
        Vec3 center = AbstractMultiblockRenderer.computeCenterOffset(bounds);
        double radius = AbstractMultiblockRenderer.computeRadius(bounds) + 15.0;
        Vec3 worldCenter = new Vec3(pos.getX() + center.x, pos.getY() + center.y, pos.getZ() + center.z);
        return new net.minecraft.world.phys.AABB(worldCenter, worldCenter).inflate(radius);
    }

    @Override
    public void attachInterface(BlockPos interfacePos) {
        // 装配枢纽采用任意结构方块接入网络方案，不依赖通用 ME 接口方块。
    }

    @Override
    public void detachInterface(BlockPos interfacePos) {
        // 装配枢纽采用任意结构方块接入网络方案，不依赖通用 ME 接口方块。
    }

    @Override
    public IActionSource getActionSource() {
        return IActionSource.ofMachine(this);
    }

    /**
     * 刷新控制器在 AE2 网络中的样板列表。
     * <p>采用任意结构方块接入网络的方案，控制器自身即为网络节点，直接请求更新即可。</p>
     */
    public void refreshInterfaceServices() {
        if (level == null || level.isClientSide()) {
            return;
        }
        IManagedGridNode node = getMainNode();
        if (node != null) {
            ICraftingProvider.requestUpdate(node);
        }
    }

    @Override
    public int getPatternPriority() {
        return 0;
    }

    @Override
    public void onReady() {
        super.onReady();
        if (level != null && !level.isClientSide() && isFormed()) {
            refreshInterfaceServices();
        }
    }

    @Override
    public IGrid getGrid() {
        IManagedGridNode node = getMainNode();
        return node != null && node.isReady() ? node.getGrid() : null;
    }

    @Override
    public boolean isVisibleInTerminal() {
        return isFormed();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return new AssemblyPatternInventory(patternManager);
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var icon = AEItemKey.of(new ItemStack(ModBlocks.ASSEMBLY_CONTROLLER.get()));
        var name = Component.translatable("block.ae2enhanced.assembly_controller");
        var tooltip = List.<Component>of(Component.translatable(
                "gui.ae2enhanced.assembly.pattern_pages",
                upgradeManager.getPatternPages()));
        return new PatternContainerGroup(icon, name, tooltip);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide() && isFormed()
                && BlockEntityRemovalHelper.isBlockBeingBroken(this)) {
            disassemble();
        }
        if (level != null && !level.isClientSide() && BlockEntityRemovalHelper.isBlockBeingBroken(this)) {
            AssemblyPatternSavedData savedData = AssemblyPatternSavedData.get(level);
            if (savedData != null) {
                savedData.removePatterns(worldPosition);
            }
        }
        super.setRemoved();
    }

    // ---- IPatternProviderHost ----

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patternManager.getAvailablePatterns();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return this.craftingProcessor.pushPattern(pattern, inputs);
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, @Nullable IManagedGridNode node) {
        return this.craftingProcessor.pushPattern(pattern, inputs, node);
    }

    /**
     * 批量执行样板任务，一次性处理 {@code batchSize} 个副本。
     */
    public boolean pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputs, @Nullable IManagedGridNode node, long batchSize) {
        return this.craftingProcessor.pushPatternBatch(pattern, inputs, node, batchSize);
    }

    @Nullable
    public IManagedGridNode resolveNode(@Nullable IManagedGridNode preferred) {
        if (preferred != null && preferred.isReady()) {
            return preferred;
        }
        IManagedGridNode node = getMainNode();
        return node != null && node.isReady() ? node : null;
    }

    @Override
    public boolean isBusy() {
        return this.craftingProcessor.isBusy();
    }

    // ---- NBT ----

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        patternManager.load(data);
        craftingProcessor.load(data);
        formed = data.getBoolean("formed");
        showingStructureProjection = data.getBoolean("showProjection");
        networkActive = data.getBoolean("networkActive");
        networkPowered = data.getBoolean("networkPowered");
        patternManager.ensurePatternCapacity();
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        patternManager.save(data);
        craftingProcessor.save(data);
        data.putBoolean("formed", formed);
        data.putBoolean("showProjection", showingStructureProjection);
        data.putBoolean("networkActive", networkActive);
        data.putBoolean("networkPowered", networkPowered);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("formed", formed);
        tag.putBoolean("showProjection", showingStructureProjection);
        tag.putBoolean("networkActive", networkActive);
        tag.putBoolean("networkPowered", networkPowered);
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
        if (tag.contains("networkActive", Tag.TAG_BYTE)) {
            networkActive = tag.getBoolean("networkActive");
        }
        if (tag.contains("networkPowered", Tag.TAG_BYTE)) {
            networkPowered = tag.getBoolean("networkPowered");
        }
    }
}
