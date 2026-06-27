package com.github.aeddddd.ae2enhanced.mixin.late.nuclearcraft;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import nc.radiation.RadiationHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NuclearCraft 辐射适配：玩家位于个人维度时完全跳过辐射更新。
 *
 * <p>注入 {@link RadiationHandler#updatePlayerRadiation(TickEvent.PlayerTickEvent)} 的 HEAD，
 * 当玩家处于个人维度时直接 cancel，使其不受物品栏/环境辐射影响，也不会累积 totalRads。</p>
 */
@Mixin(value = RadiationHandler.class, remap = false)
public class MixinRadiationHandler {

    @Inject(
            method = "updatePlayerRadiation",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ae2e$skipRadiationInPersonalDim(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (event.player != null && PersonalDimensionManager.isPersonalDimension(event.player.dimension)) {
            ci.cancel();
        }
    }
}
