package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.parts.AEBasePart;
import appeng.parts.automation.PartUpgradeable;
import appeng.util.inv.IAEAppEngInventory;
import com.github.aeddddd.ae2enhanced.util.network.WirelessChannelConnectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F1b-fix：在 Part 加载到世界时(addToWorld),自动检查升级槽中的频道接收卡,
 * 并尝试建立无线网格连接.解决退出重进游戏后连接丢失的问题.
 */
@Mixin(value = AEBasePart.class, remap = false)
public class MixinAEBasePart {

    @Inject(method = "addToWorld", at = @At("TAIL"), remap = false)
    private void ae2e$onPartAddToWorld(CallbackInfo ci) {
        if (!appeng.util.Platform.isServer()) return;
        if ((Object) this instanceof IAEAppEngInventory) {
            WirelessChannelConnectionHelper.tryConnect((IAEAppEngInventory) this);
        }
    }
}
