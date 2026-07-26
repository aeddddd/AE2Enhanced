package com.github.aeddddd.ae2enhanced.mixin;

import java.util.concurrent.Future;

import net.minecraft.server.level.ServerPlayer;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;

import com.github.aeddddd.ae2enhanced.crafting.CraftConfirmMenuLongExt;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.SpecialPlanInfoPacket;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /**
     * 计划摘要构建后,向客户端发送特殊计划显示信息(自增殖/循环链轮次详情);
     * 普通计划发送空信息以清空客户端缓存.{@code require = 0} 防第三方改写时崩溃.
     */
    @Inject(method = "broadcastChanges",
            at = @At(value = "INVOKE",
                    target = "Lappeng/menu/me/crafting/CraftingPlanSummary;fromJob(Lappeng/api/networking/IGrid;Lappeng/api/networking/security/IActionSource;Lappeng/api/networking/crafting/ICraftingPlan;)Lappeng/menu/me/crafting/CraftingPlanSummary;",
                    shift = At.Shift.AFTER, remap = false),
            require = 0, remap = false)
    private void ae2e$sendSpecialPlanInfo(CallbackInfo ci) {
        if (this.result == null) {
            return;
        }
        var player = ((CraftConfirmMenu) (Object) this).getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            var info = SpecialPlanInfo.compute(this.result);
            ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SpecialPlanInfoPacket(info));
        }
    }
}
