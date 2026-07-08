package com.github.aeddddd.ae2enhanced.multiblock;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.security.IActionSource;

import com.github.aeddddd.ae2enhanced.blockentity.AE2EBaseBlockEntity;
import com.github.aeddddd.ae2enhanced.util.BlockEntityRemovalHelper;

/**
 * 多方块控制器方块实体基类。
 * <p>维护成形状态与所属接口位置列表，本身不作为 AE2 网络节点。</p>
 */
public abstract class MultiblockControllerBlockEntity extends AE2EBaseBlockEntity implements IMultiblockController {

    private boolean formed = false;
    private final Set<BlockPos> interfaces = new HashSet<>();

    protected MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean isFormed() {
        return formed;
    }

    /**
     * 直接设置成形状态并触发方块更新。
     * <p>外部代码应优先使用 {@link #assemble()} / {@link #disassemble()}，
     * 以便统一调用 {@link #onAssemble()} / {@link #onDisassemble()} 钩子。</p>
     */
    public void setFormed(boolean formed) {
        if (this.formed != formed) {
            this.formed = formed;
            setChanged();
            markForUpdate();
        }
    }

    /**
     * 装配结构：先调用子类钩子，再置为成形，最后刷新接口服务。
     */
    public void assemble() {
        if (isFormed()) {
            return;
        }
        onAssemble();
        setFormed(true);
        refreshInterfaceServices();
    }

    /**
     * 拆解结构：先调用子类钩子，再置为未成形，最后刷新接口服务。
     */
    public void disassemble() {
        if (!isFormed()) {
            return;
        }
        onDisassemble();
        setFormed(false);
        refreshInterfaceServices();
    }

    @Override
    public BlockPos getControllerPos() {
        return worldPosition;
    }

    @Override
    public void attachInterface(BlockPos interfacePos) {
        if (interfaces.add(interfacePos.immutable())) {
            setChanged();
        }
    }

    @Override
    public void detachInterface(BlockPos interfacePos) {
        if (interfaces.remove(interfacePos.immutable())) {
            setChanged();
        }
    }

    public Set<BlockPos> getInterfaces() {
        return new HashSet<>(interfaces);
    }

    @Override
    public IActionSource getActionSource() {
        if (level == null || level.isClientSide()) {
            return IActionSource.empty();
        }
        for (BlockPos pos : interfaces) {
            if (level.getBlockEntity(pos) instanceof MultiblockMeInterfaceBlockEntity me) {
                if (me.getActionableNode() != null) {
                    return IActionSource.ofMachine(me);
                }
            }
        }
        return IActionSource.empty();
    }

    /**
     * 通知所有已记录的接口节点刷新网络服务。
     */
    protected void refreshInterfaceServices() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (BlockPos pos : interfaces) {
            if (level.getBlockEntity(pos) instanceof MultiblockMeInterfaceBlockEntity interfaceBe) {
                interfaceBe.requestNetworkUpdate();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 服务端加载后，若已成形则安排一次服务刷新，确保网络能重新发现控制器。
        if (level != null && !level.isClientSide() && isFormed()) {
            refreshInterfaceServices();
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide() && isFormed()
                && BlockEntityRemovalHelper.isBlockBeingBroken(this)) {
            // 仅在控制器方块真正被破坏时解散；
            // 区块卸载或关服时触发 setRemoved 不应执行完整拆解，避免额外 IO 与状态异常。
            disassemble();
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // 子类可覆写以在区块卸载时释放临时资源，但不应执行完整拆解。
    }

    /**
     * 服务端每 tick 调用入口。子类覆写以处理验证、保存等逻辑。
     */
    public void serverTick() {
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("formed", formed);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("formed", Tag.TAG_BYTE)) {
            this.formed = tag.getBoolean("formed");
        }
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        formed = data.getBoolean("formed");
        interfaces.clear();
        if (data.contains("interfaces", Tag.TAG_LIST)) {
            ListTag list = data.getList("interfaces", Tag.TAG_LONG);
            for (Tag tag : list) {
                interfaces.add(BlockPos.of(((net.minecraft.nbt.LongTag) tag).getAsLong()));
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putBoolean("formed", formed);
        ListTag list = new ListTag();
        for (BlockPos pos : interfaces) {
            list.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        }
        data.put("interfaces", list);
    }
}
