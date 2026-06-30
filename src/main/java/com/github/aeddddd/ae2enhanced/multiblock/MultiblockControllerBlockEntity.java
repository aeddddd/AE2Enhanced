package com.github.aeddddd.ae2enhanced.multiblock;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.blockentity.AE2EBaseBlockEntity;

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

    public void setFormed(boolean formed) {
        if (this.formed != formed) {
            this.formed = formed;
            setChanged();
            markForUpdate();
        }
    }

    @Override
    public BlockPos getControllerPos() {
        return worldPosition;
    }

    @Override
    public void attachInterface(BlockPos interfacePos) {
        if (interfaces.add(interfacePos)) {
            setChanged();
        }
    }

    @Override
    public void detachInterface(BlockPos interfacePos) {
        if (interfaces.remove(interfacePos)) {
            setChanged();
        }
    }

    public Set<BlockPos> getInterfaces() {
        return new HashSet<>(interfaces);
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
