package com.github.aeddddd.ae2enhanced.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.api.storage.ISubMenuHost;

import com.github.aeddddd.ae2enhanced.crafting.CraftAmountMenuLongExt;
import com.github.aeddddd.ae2enhanced.crafting.CraftConfirmMenuLongExt;
import com.github.aeddddd.ae2enhanced.mixin.accessor.AEBaseMenuAccessor;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 为 {@link CraftAmountMenu} 增加 long 型下单确认入口,突破原生 confirm 的 int 上限.
 * 方法体复刻原生 confirm 的服务端分支,仅数量以 long 运算.
 */
@Mixin(value = CraftAmountMenu.class, remap = false)
public abstract class MixinCraftAmountMenu implements CraftAmountMenuLongExt {

    @Shadow
    private AEKey whatToCraft;

    @Shadow
    @Final
    private ISubMenuHost host;

    @Unique
    @Override
    public void ae2e$confirmLong(long amount, boolean craftMissingAmount, boolean autoStart) {
        CraftAmountMenu self = (CraftAmountMenu) (Object) this;
        if (this.whatToCraft == null) {
            return;
        }

        if (craftMissingAmount) {
            IActionHost actionHost = ((AEBaseMenuAccessor) self).invokeGetActionHost();
            if (actionHost != null) {
                var node = actionHost.getActionableNode();
                if (node != null) {
                    var storage = node.getGrid().getStorageService();
                    long existingAmount = storage.getCachedInventory().get(this.whatToCraft);
                    amount = existingAmount > amount ? 0 : amount - existingAmount;
                }
            }
        }

        var locator = self.getLocator();
        if (locator == null) {
            return;
        }
        var player = self.getPlayer();
        if (amount > 0) {
            MenuOpener.open(CraftConfirmMenu.TYPE, player, locator);
            if (player.containerMenu instanceof CraftConfirmMenu ccc) {
                ccc.setAutoStart(autoStart);
                ((CraftConfirmMenuLongExt) ccc).ae2e$planJobLong(this.whatToCraft, amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
                self.broadcastChanges();
            }
        } else {
            // 数量为 0：与原生一致返回主菜单
            this.host.returnToMainMenu(player, self);
        }
    }
}
