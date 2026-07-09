package com.github.aeddddd.ae2enhanced.multiblock;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;

import com.github.aeddddd.ae2enhanced.block.AE2EBaseEntityBlock;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.util.BlockEntityRemovalHelper;

/**
 * 通用多方块 ME 接口方块实体。
 * <p>作为 grid node 宿主，向 AE2 网络提供 IStorageProvider、ICraftingProvider
 * 与 IVirtualCpuProvider 服务，并委托给背后的控制器。</p>
 */
public class MultiblockMeInterfaceBlockEntity extends AENetworkBlockEntity
        implements IStorageProvider, ICraftingProvider, IVirtualCpuProvider {

    @Nullable
    private BlockPos controllerPos = null;
    private final IControllerLocator controllerLocator = IControllerLocator.defaultLocator();

    public MultiblockMeInterfaceBlockEntity(BlockPos pos, BlockState state) {
        this(com.github.aeddddd.ae2enhanced.registry.ModBlockEntities.MULTIBLOCK_ME_INTERFACE.get(), pos, state);
    }

    public MultiblockMeInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(ModItems.MULTIBLOCK_ME_INTERFACE.get())
                .addService(IStorageProvider.class, this)
                .addService(ICraftingProvider.class, this)
                .addService(IVirtualCpuProvider.class, this);
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void setControllerPos(@Nullable BlockPos controllerPos) {
        if (java.util.Objects.equals(this.controllerPos, controllerPos)) {
            return;
        }

        // 先通知旧控制器解绑
        if (this.controllerPos != null && level != null
                && level.getBlockEntity(this.controllerPos) instanceof IMultiblockController oldController) {
            oldController.detachInterface(worldPosition);
        }

        this.controllerPos = controllerPos;
        setChanged();

        if (level == null || level.isClientSide()) {
            return;
        }

        // 通知新控制器绑定
        if (controllerPos != null
                && level.getBlockEntity(controllerPos) instanceof IMultiblockController newController) {
            newController.attachInterface(worldPosition);
        }

        // 同步更新方块状态（成形/未成形）
        BlockState state = getBlockState();
        boolean formed = controllerPos != null;
        if (state.getValue(AE2EBaseEntityBlock.FORMED) != formed) {
            level.setBlock(worldPosition, state.setValue(AE2EBaseEntityBlock.FORMED, formed), Block.UPDATE_ALL);
        }

        // 刷新网络服务
        requestNetworkUpdate();
    }

    @Nullable
    public IMultiblockController getController() {
        if (controllerPos == null || level == null) {
            return null;
        }
        if (level.getBlockEntity(controllerPos) instanceof IMultiblockController controller) {
            return controller;
        }
        return null;
    }

    /**
     * 尝试使用定位器恢复控制器坐标。
     */
    public void recoverController() {
        if (level == null || level.isClientSide() || controllerPos != null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.getValue(AE2EBaseEntityBlock.FORMED)) {
            return;
        }
        BlockPos located = controllerLocator.locateController(level, worldPosition);
        if (located != null) {
            setControllerPos(located);
        }
    }

    public void requestNetworkUpdate() {
        IManagedGridNode node = getMainNode();
        IStorageProvider.requestUpdate(node);
        ICraftingProvider.requestUpdate(node);
    }

    @Override
    public void onReady() {
        // 节点始终 ready；由控制器是否成形决定实际提供哪些服务。
        super.onReady();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 服务端加载后若控制器坐标丢失但方块状态显示成形，尝试容错恢复。
        if (level != null && !level.isClientSide() && controllerPos == null) {
            recoverController();
        }
    }

    @Override
    public void setRemoved() {
        if (controllerPos != null && level != null && !level.isClientSide()
                && BlockEntityRemovalHelper.isBlockBeingBroken(this)
                && level.getBlockEntity(controllerPos) instanceof IMultiblockController controller) {
            controller.detachInterface(worldPosition);
        }
        super.setRemoved();
    }

    // ---- IStorageProvider ----

    @Override
    public void mountInventories(IStorageMounts mounts) {
        IMultiblockController controller = getController();
        if (controller instanceof IStorageHost host && host.isFormed()) {
            MEStorage storage = host.getStorage();
            if (storage != null) {
                mounts.mount(storage);
            }
        }
    }

    // ---- ICraftingProvider ----

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        IMultiblockController controller = getController();
        if (controller instanceof IPatternProviderHost host && host.isFormed()) {
            return host.getAvailablePatterns();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        IMultiblockController controller = getController();
        if (controller instanceof IPatternProviderHost host && host.isFormed()) {
            return host.pushPattern(pattern, inputs);
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        IMultiblockController controller = getController();
        if (controller instanceof IPatternProviderHost host && host.isFormed()) {
            return host.isBusy();
        }
        return false;
    }

    @Override
    public int getPatternPriority() {
        return 0;
    }

    // ---- IVirtualCpuProvider ----

    @Override
    public boolean isVirtualCpuAvailable() {
        IMultiblockController controller = getController();
        return controller != null && controller.isVirtualCpuAvailable();
    }

    @Override
    public int getVirtualCpuParallelLimit() {
        IMultiblockController controller = getController();
        return controller != null ? controller.getVirtualCpuParallelLimit() : 0;
    }

    @Override
    public AECableType getCableConnectionType(net.minecraft.core.Direction dir) {
        return AECableType.SMART;
    }

    // ---- NBT ----

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        long encoded = data.getLong("controllerPos");
        controllerPos = encoded != 0 ? BlockPos.of(encoded) : null;
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        if (controllerPos != null) {
            data.putLong("controllerPos", controllerPos.asLong());
        }
    }
}
