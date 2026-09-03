package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.util.Platform;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * CPU 状态 GUI(ContainerCraftingCPU/合成状态界面)发包节流.
 * <p>该容器每 tick 将选中 CPU 的变动条目分 3 个 PacketMEInventoryUpdate
 * (STORAGE/ACTIVE/PENDING,逐条带 NBT)发给每个观察者;合成状态列表界面还会
 * 自动订阅第一个忙碌 CPU——大单执行期每 tick 数千条目变动形成发包洪峰,
 * 挤占位置纠正包带宽,表现为玩家位置回弹.</p>
 * <p>实现同 MixinContainerMEMonitorable:包装 {@code list.isEmpty()} 判定,
 * 节流 tick 跳过冲刷块(含 resetStatus),标记累积不丢数据.</p>
 */
@Mixin(value = ContainerCraftingCPU.class, remap = false)
public abstract class MixinContainerCraftingCPUThrottle {

    @Shadow
    private IItemList<IAEItemStack> list;

    /** 洪流节流计数(待冲刷条目超阈值时启用). */
    private int ae2enhanced$floodSkip = 0;

    @WrapOperation(
        method = "func_75142_b",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/data/IItemList;isEmpty()Z"
        ),
        require = 0
    )
    private boolean ae2enhanced$throttleFloodFlush(IItemList<?> pending, Operation<Boolean> original) {
        int threshold = com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.guiSyncFloodThreshold;
        if (!Platform.isServer() || threshold <= 0 || pending.size() <= threshold) {
            this.ae2enhanced$floodSkip = 0;
            return original.call(pending);
        }
        this.ae2enhanced$floodSkip++;
        if (this.ae2enhanced$floodSkip
                >= com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.guiSyncFloodIntervalTicks) {
            this.ae2enhanced$floodSkip = 0;
            return original.call(pending);
        }
        return true;
    }
}
