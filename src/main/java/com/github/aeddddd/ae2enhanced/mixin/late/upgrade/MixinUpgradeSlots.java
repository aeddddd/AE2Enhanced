package com.github.aeddddd.ae2enhanced.mixin.late.upgrade;

import appeng.parts.automation.PartAbstractFormationPlane;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.misc.PartStorageBus;
import appeng.fluids.parts.PartFluidStorageBus;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * F1：为所有 AE2 Part 增加额外的升级槽位数量.
 *
 * <p>由于频道接收卡会占用一个升级槽,通过配置 {@code extraUpgradeSlots}
 * 为所有设备统一增加槽位(默认 +2).</p>
 */
@Mixin(value = {
    PartUpgradeable.class,
    PartAbstractFormationPlane.class,
    PartStorageBus.class,
    PartFluidStorageBus.class
}, remap = false)
public class MixinUpgradeSlots {

    @ModifyVariable(method = "getUpgradeSlots", at = @At("RETURN"), ordinal = 0)
    private int ae2e$addExtraUpgradeSlots(int original) {
        return original + AE2EnhancedConfig.wirelessChannel.extraUpgradeSlots;
    }
}
