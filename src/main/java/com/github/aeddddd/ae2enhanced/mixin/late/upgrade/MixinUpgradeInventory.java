package com.github.aeddddd.ae2enhanced.mixin.late.upgrade;

import appeng.parts.automation.UpgradeInventory;
import appeng.util.inv.IAEAppEngInventory;
import com.github.aeddddd.ae2enhanced.util.network.WirelessChannelConnectionHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F1b：在任意 {@link UpgradeInventory} 内容变化时,如果其 parent 是 {@link appeng.parts.automation.PartUpgradeable},
 * 且其中包含绑定的频道接收卡,则自动创建/销毁到对应无线频道发生器的远程 {@link appeng.api.networking.IGridConnection}.
 *
 * <p>直接注入 {@link UpgradeInventory#onChangeInventory} 的 TAIL,而不是
 * {@link appeng.parts.automation.PartUpgradeable#onChangeInventory},因为后者在大量子类中被覆盖且不调用 super,
 * 导致注入 {@code PartUpgradeable.onChangeInventory} 的 Mixin 实际上永远不会执行.</p>
 */
@Mixin(value = UpgradeInventory.class, remap = false)
public class MixinUpgradeInventory {

    @Shadow
    @Final
    private IAEAppEngInventory parent;

    @Inject(method = "onChangeInventory", at = @At("TAIL"), remap = false)
    private void ae2e$onUpgradeInventoryChanged(IItemHandler inv, int slot, appeng.util.inv.InvOperation mc,
                                                 ItemStack removedStack, ItemStack newStack, CallbackInfo ci) {
        if (!appeng.util.Platform.isServer()) {
            return;
        }
        WirelessChannelConnectionHelper.destroyConnection(this.parent);
        WirelessChannelConnectionHelper.tryConnect(this.parent);
    }
}
