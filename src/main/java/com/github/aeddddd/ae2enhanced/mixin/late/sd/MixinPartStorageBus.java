package com.github.aeddddd.ae2enhanced.mixin.late.sd;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.storage.ITickingMonitor;
import appeng.parts.misc.PartStorageBus;
import com.github.aeddddd.ae2enhanced.integration.drawer.DrawerMonitorWrapper;
import com.github.aeddddd.ae2enhanced.integration.drawer.sd.StorageDrawersAdapter;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 替换 StorageDrawers 的 ItemRepositoryAdapter 为 Hash 索引优化版本,
 * 并在存储总线因区块加载顺序导致未连接时自动重试.
 *
 * <p>在 {@link PartStorageBus#getInventoryWrapper} 返回 ItemRepositoryAdapter 时,
 * 使用 {@link StorageDrawersAdapter} + {@link DrawerMonitorWrapper} 替代,
 * 使 getAvailableItems() 利用 TileEntityController.drawerPrimaryLookup Hash 索引.</p>
 *
 * <p>额外增加重连逻辑：存储总线加入世界后的一段时间内,如果仍未连接到有效容器,
 * 会自动重新扫描相邻方块,避免重进存档后因相邻方块未加载而必须手动破坏重放.</p>
 */
@Mixin(value = PartStorageBus.class, remap = false)
public class MixinPartStorageBus {

    @Shadow
    protected ITickingMonitor monitor;

    @Shadow
    protected void resetCache(boolean fullReset) {
    }

    // 加入世界后尝试重连的剩余次数
    private int ae2enhanced$reconnectAttempts = 0;

    @Inject(method = "getInventoryWrapper", at = @At("RETURN"), cancellable = true)
    private void ae2enhanced$replaceItemRepositoryAdapter(TileEntity target, CallbackInfoReturnable<IMEInventory<IAEItemStack>> cir) {
        IMEInventory<IAEItemStack> result = cir.getReturnValue();
        // 只有当返回的是 ItemRepositoryAdapter 时才替换
        if (result != null && result.getClass().getName().equals("appeng.parts.misc.ItemRepositoryAdapter")) {
            try {
                EnumFacing targetSide = ((PartStorageBus) (Object) this).getSide().getFacing().getOpposite();
                if (PartStorageBus.ITEM_REPOSITORY_CAPABILITY != null
                        && target.hasCapability(PartStorageBus.ITEM_REPOSITORY_CAPABILITY, targetSide)) {
                    IItemRepository repo = target.getCapability(PartStorageBus.ITEM_REPOSITORY_CAPABILITY, targetSide);
                    if (repo != null) {
                        appeng.api.storage.channels.IItemStorageChannel channel =
                                appeng.api.AEApi.instance().storage().getStorageChannel(
                                        appeng.api.storage.channels.IItemStorageChannel.class);
                        StorageDrawersAdapter adapter = new StorageDrawersAdapter(repo, target, channel);
                        DrawerMonitorWrapper monitor = new DrawerMonitorWrapper(adapter, channel);
                        cir.setReturnValue(monitor);
                    }
                }
            } catch (Exception ignored) {
                // 任何异常保持原有行为
            }
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true)
    private void ae2enhanced$reconnectIfDisconnected(IGridNode node, int ticksSinceLastCall,
                                                     CallbackInfoReturnable<TickRateModulation> cir) {
        // 首次 tick 且未连接时,只对 StorageDrawers 容器启动重连窗口
        // 避免普通箱子/机器等非 SD 容器在每次加载时都被迫执行 8 次 full cache reset
        if (this.ae2enhanced$reconnectAttempts == 0 && this.monitor == null) {
            TileEntity target = ae2enhanced$getNeighborTile();
            if (ae2enhanced$isStorageDrawersTarget(target)) {
                this.ae2enhanced$reconnectAttempts = 8;
            }
        }

        if (this.ae2enhanced$reconnectAttempts > 0) {
            this.ae2enhanced$reconnectAttempts--;
            if (this.monitor == null) {
                TileEntity target = ae2enhanced$getNeighborTile();
                if (ae2enhanced$isStorageDrawersTarget(target)) {
                    this.resetCache(true);
                } else {
                    // 目标不是 SD 容器,继续重试无意义,立即停止
                    this.ae2enhanced$reconnectAttempts = 0;
                }
            }
            // 仍未连接且还有剩余次数时保持唤醒,继续重试
            if (this.monitor == null && this.ae2enhanced$reconnectAttempts > 0) {
                cir.setReturnValue(TickRateModulation.URGENT);
                return;
            }
        }
    }

    private TileEntity ae2enhanced$getNeighborTile() {
        try {
            PartStorageBus self = (PartStorageBus) (Object) this;
            TileEntity hostTile = self.getHost().getTile();
            EnumFacing facing = self.getSide().getFacing();
            BlockPos neighbor = hostTile.getPos().offset(facing);
            return hostTile.getWorld().getTileEntity(neighbor);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean ae2enhanced$isStorageDrawersTarget(TileEntity target) {
        if (target == null || PartStorageBus.ITEM_REPOSITORY_CAPABILITY == null) {
            return false;
        }
        try {
            PartStorageBus self = (PartStorageBus) (Object) this;
            EnumFacing targetSide = self.getSide().getFacing().getOpposite();
            return target.hasCapability(PartStorageBus.ITEM_REPOSITORY_CAPABILITY, targetSide);
        } catch (Exception ignored) {
            return false;
        }
    }
}
