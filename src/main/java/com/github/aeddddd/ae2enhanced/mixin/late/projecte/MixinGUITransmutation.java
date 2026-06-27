package com.github.aeddddd.ae2enhanced.mixin.late.projecte;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.util.BigEmcFormatter;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEBigEmcHelper;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.math.BigInteger;

/**
 * 让 ProjectE 转换桌 GUI 显示 BigInteger 余额，超过 1e18 使用科学记数法。
 */
@Mixin(value = GUITransmutation.class, remap = false)
public class MixinGUITransmutation {

    @Shadow
    private TransmutationInventory inv;

    @Redirect(
            method = "func_146979_b",
            at = @At(
                    value = "INVOKE",
                    target = "Lmoze_intel/projecte/utils/TransmutationEMCFormatter;EMCFormat(J)Ljava/lang/String;"
            ),
            remap = false
    )
    private String ae2e$formatForegroundEmc(long emc) {
        return BigEmcFormatter.format(getAvailableBig());
    }

    @Redirect(
            method = "func_191948_b",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/text/NumberFormat;format(J)Ljava/lang/String;"
            ),
            remap = false
    )
    private String ae2e$formatTooltipEmc(java.text.NumberFormat format, long emc) {
        return BigEmcFormatter.format(getAvailableBig());
    }

    private BigInteger getAvailableBig() {
        if (this.inv == null || this.inv.provider == null) return BigInteger.ZERO;
        try {
            java.lang.reflect.Method m = this.inv.getClass().getMethod("ae2e$getAvailableEMCBig");
            Object result = m.invoke(this.inv);
            if (result instanceof BigInteger) {
                return (BigInteger) result;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to reflect TransmutationInventory BigInteger EMC, falling back to provider", e);
        }
        // 兜底：直接读取 knowledge provider 的 BigInteger EMC（兼容离线/包装提供者）
        return ProjectEBigEmcHelper.getEmcBig(this.inv.provider);
    }
}
