package com.github.aeddddd.ae2enhanced.mixin.late.cellterminal;

import appeng.api.networking.IGrid;
import com.cellterminal.container.handler.NetworkToolActionHandler;
import com.github.aeddddd.ae2enhanced.integration.cellterminal.CellTerminalActor;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 网络批量工具(如"按内容分区存储总线")同样会改写 EMC 接口白名单,
 * 在入口记录执行者以便 {@code EMCInterfaceFilterHost} 做权限校验.
 *
 * cellterminal 自有方法名不参与重映射, remap=false.
 */
@Mixin(value = NetworkToolActionHandler.class, remap = false)
public abstract class MixinNetworkToolActionHandler {

    @Inject(method = "handleAction", at = @At("HEAD"))
    private static void ae2e$captureActor(String toolId, Map<?, ?> activeFilters, Map<?, ?> storageById,
                                          Map<?, ?> storageBusById, IGrid grid, EntityPlayer player,
                                          CallbackInfo ci) {
        CellTerminalActor.set(player);
    }

    @Inject(method = "handleAction", at = @At("RETURN"))
    private static void ae2e$clearActor(String toolId, Map<?, ?> activeFilters, Map<?, ?> storageById,
                                        Map<?, ?> storageBusById, IGrid grid, EntityPlayer player,
                                        CallbackInfo ci) {
        CellTerminalActor.clear();
    }
}
