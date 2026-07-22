package com.github.aeddddd.ae2enhanced.mixin;

import java.util.concurrent.Future;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;

import com.github.aeddddd.ae2enhanced.crafting.CraftConfirmMenuLongExt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 为 {@link CraftConfirmMenu} 增加 long 型计划提交入口,突破原生 planJob 的 int 上限.
 * 方法体与原生 {@code planJob} 完全一致,仅数量参数为 long；
 * 原生 int 字段 amount 仅用于返回上级菜单与 CRAFT_LESS 重算,按 int 上限截断不影响本次计算.
 */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class MixinCraftConfirmMenu implements CraftConfirmMenuLongExt {

    @Shadow
    private Future<ICraftingPlan> job;

    @Shadow
    private ICraftingPlan result;

    @Shadow
    private AEKey whatToCraft;

    @Shadow
    private int amount;

    @Shadow
    public abstract void clearError();

    @Shadow
    protected abstract IGrid getGrid();

    @Shadow
    protected abstract IActionSource getActionSrc();

    @Unique
    @Override
    public boolean ae2e$planJobLong(AEKey what, long amountLong, CalculationStrategy strategy) {
        if (this.job != null) {
            this.job.cancel(true);
        }
        this.result = null;
        this.clearError();

        this.whatToCraft = what;
        this.amount = (int) Math.min(amountLong, Integer.MAX_VALUE);

        var player = ((CraftConfirmMenu) (Object) this).getPlayer();
        var grid = this.getGrid();
        if (grid == null) {
            return false;
        }

        var cg = grid.getCraftingService();
        this.job = cg.beginCraftingCalculation(
                player.level(),
                this::getActionSrc,
                what,
                amountLong,
                strategy);
        return true;
    }
}
