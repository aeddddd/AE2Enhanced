package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.tile.storage.TileIOPort;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.EssentiaFakeItemChecks;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * E2a：防止流体/气体/源质假物品在 IO Port 传输时被计数为真实物品.
 */
@Mixin(value = TileIOPort.class, remap = false)
public class MixinTileIOPort {

    @Redirect(method = "transferContents", at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IAEStack;getStackSize()J", ordinal = 0))
    private long removeDropSize(IAEStack instance) {
        if (com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED) return instance.getStackSize();
        if (instance instanceof IAEItemStack) {
            ItemStack mcStack = ((IAEItemStack) instance).createItemStack();
            String className = mcStack.getItem().getClass().getName();
            if (ItemFluidDrop.isFluidDrop(mcStack)
                    || "com.github.aeddddd.ae2enhanced.item.ItemGasDrop".equals(className)
                    || EssentiaFakeItemChecks.isEssentiaFakeItem(mcStack)
                    || "com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop".equals(className)
                    || "com.github.aeddddd.ae2enhanced.item.ItemManaDrop".equals(className)
                    || "com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop".equals(className)) {
                return 0L;
            }
        }
        return instance.getStackSize();
    }
}
