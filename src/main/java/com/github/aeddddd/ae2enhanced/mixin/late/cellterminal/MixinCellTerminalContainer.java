package com.github.aeddddd.ae2enhanced.mixin.late.cellterminal;

import com.cellterminal.container.ContainerCellTerminalBase;
import com.cellterminal.network.PacketStorageBusPartitionAction;
import com.github.aeddddd.ae2enhanced.integration.cellterminal.CellTerminalActor;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在元件终端的存储总线分区编辑入口记录当前操作玩家,
 * 供 {@code EMCInterfaceFilterHost} 执行 EMC 接口的 canManage 权限校验.
 *
 * cellterminal 自有方法名不参与重映射, remap=false.
 */
@Mixin(value = ContainerCellTerminalBase.class, remap = false)
public abstract class MixinCellTerminalContainer {

    @Inject(method = "handleStorageBusPartitionAction", at = @At("HEAD"))
    private void ae2e$captureActor(long storageBusId, PacketStorageBusPartitionAction.Action action,
                                   int partitionSlot, ItemStack itemStack, CallbackInfo ci) {
        CellTerminalActor.set(((ContainerCellTerminalBase) (Object) this).getPlayerInv().player);
    }

    @Inject(method = "handleStorageBusPartitionAction", at = @At("RETURN"))
    private void ae2e$clearActor(long storageBusId, PacketStorageBusPartitionAction.Action action,
                                 int partitionSlot, ItemStack itemStack, CallbackInfo ci) {
        CellTerminalActor.clear();
    }
}
