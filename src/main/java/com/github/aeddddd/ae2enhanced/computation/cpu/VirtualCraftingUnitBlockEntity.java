package com.github.aeddddd.ae2enhanced.computation.cpu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlockEntities;

/**
 * 供虚拟 CPU 使用的虚假 AE2 合成单元方块实体.
 * <p>不放入世界,仅用于给 {@link appeng.me.cluster.implementations.CraftingCPUCluster}
 * 提供一个指向实际通用 ME 接口节点的 {@link #getActionableNode()}.</p>
 */
public class VirtualCraftingUnitBlockEntity extends CraftingBlockEntity {

    private final IManagedGridNode interfaceNode;
    private final int parallel;
    private final long storageBytes;

    public VirtualCraftingUnitBlockEntity(Level level, BlockPos pos, BlockState state,
            IManagedGridNode interfaceNode, int parallel) {
        this(level, pos, state, interfaceNode, parallel, Long.MAX_VALUE);
    }

    public VirtualCraftingUnitBlockEntity(Level level, BlockPos pos, BlockState state,
            IManagedGridNode interfaceNode, int parallel, long storageBytes) {
        super(AEBlockEntities.CRAFTING_UNIT, pos, state);
        this.setLevel(level);
        this.interfaceNode = interfaceNode;
        this.parallel = parallel;
        this.storageBytes = storageBytes;
    }

    @Override
    public IGridNode getActionableNode() {
        return interfaceNode.getNode();
    }

    /**
     * 虚拟 CPU 的存储容量（字节）,默认为 Long.MAX_VALUE（无限）.
     * AE2 15.3.4 的字节格式化 bug（Tooltips.BYTE_NUMS 第 4 项错误）已由
     * MixinTooltips 修复并扩展档位,任意 long 值均可安全显示.
     * <p>注意：同一集群加入多个单元时存储会累加,多单元场景除首个单元外应传 0,
     * 防止累加溢出（见 {@link VirtualCraftingCPU}）.</p>
     */
    @Override
    public long getStorageBytes() {
        return storageBytes;
    }

    @Override
    public int getAcceleratorThreads() {
        return parallel;
    }

    @Override
    public void breakCluster() {
        // 虚拟方块不处于真实世界,取消默认的掉落行为.
        // MixinCraftingCPUCluster 同时会拦截 CraftingCPUCluster.breakCluster,
        // 此处保留空实现作为双重保险.
    }
}
