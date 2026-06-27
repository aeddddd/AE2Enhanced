package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.tile.misc.TileInterface;
import com.github.aeddddd.ae2enhanced.util.network.WirelessChannelConnectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F1b-fix：在 ME 接口加载到世界时(onReady),自动检查升级槽中的频道接收卡,
 * 并尝试建立无线网格连接.解决退出重进游戏后连接丢失的问题.
 *
 * <p>注意：必须直接注入 {@link TileInterface#onReady()} 而不是父类
 * {@link appeng.tile.grid.AENetworkTile#onReady()},因为
 * {@link appeng.tile.grid.AENetworkInvTile} 覆盖了 {@code onReady()} 且没有调用
 * {@code super.onReady()}(父类 AENetworkTile 的版本),导致对 AENetworkTile.onReady
 * 的 Mixin 注入实际上永远不会在 TileInterface 实例上执行.</p>
 */
@Mixin(value = TileInterface.class, remap = false)
public class MixinAENetworkTile {

    @Inject(method = "onReady", at = @At("TAIL"), remap = false)
    private void ae2e$onTileReady(CallbackInfo ci) {
        if (!appeng.util.Platform.isServer()) return;
        if ((Object) this instanceof IInterfaceHost) {
            DualityInterface duality = ((IInterfaceHost) (Object) this).getInterfaceDuality();
            if (duality != null) {
                WirelessChannelConnectionHelper.tryConnect(duality);
            }
        }
    }
}
