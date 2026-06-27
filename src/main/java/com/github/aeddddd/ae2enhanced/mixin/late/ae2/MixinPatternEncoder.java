package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternEncoder;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.compat.AssemblyAutoUploadHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = ContainerPatternEncoder.class, remap = false, priority = 1000)
public class MixinPatternEncoder {

    private static Field patternSlotOUTField;
    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;

    private static void tryInitReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            patternSlotOUTField = ContainerPatternEncoder.class.getDeclaredField("patternSlotOUT");
            patternSlotOUTField.setAccessible(true);
            reflectionReady = true;
        } catch (Exception e) {
            reflectionFailed = true;
            AE2Enhanced.LOGGER.error("[AE2E] MixinPatternEncoder reflection init failed: {}", e.toString());
        }
    }

    @Inject(method = "encode", at = @At("RETURN"))
    private void onEncodeReturn(CallbackInfo ci) {
        if (reflectionFailed) return;
        try {
            tryInitReflection();
            if (!reflectionReady) return;

            ContainerPatternEncoder container = (ContainerPatternEncoder) (Object) this;

            Object slotObj = patternSlotOUTField.get(container);
            if (!(slotObj instanceof Slot)) return;
            Slot patternSlotOUT = (Slot) slotObj;

            ItemStack pattern = patternSlotOUT.getStack();
            if (pattern.isEmpty()) return;

            InventoryPlayer invPlayer = ((AEBaseContainer) container).getPlayerInv();
            if (invPlayer == null) return;
            EntityPlayer player = invPlayer.player;
            if (player == null) return;

            if (player.world.isRemote) return;
            if (player.isSneaking()) return;

            appeng.api.networking.IGridNode node = ((appeng.container.implementations.ContainerMEMonitorable) container).getNetworkNode();
            appeng.api.networking.IGrid grid = (node != null) ? node.getGrid() : null;
            if (AssemblyAutoUploadHelper.tryUploadPattern(player.world, player, pattern, grid)) {
                patternSlotOUT.putStack(ItemStack.EMPTY);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] AutoUpload unexpected error: {}", e.toString());
        }
    }
}
