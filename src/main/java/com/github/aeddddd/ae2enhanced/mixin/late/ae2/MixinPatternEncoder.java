package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotRestrictedInput;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.compat.AssemblyAutoUploadHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerPatternEncoder.class, remap = false, priority = 1000)
public class MixinPatternEncoder {

    @Shadow
    protected SlotRestrictedInput patternSlotOUT;

    // shift 点击时 AE2 走 encodeAndMoveToInventory(), 内部会先调 encode().
    // 潜行状态按 tick 同步, 与 encode 包存在到达顺序竞态, 不能用 isSneaking 判定 shift;
    // 改为标记 encodeAndMoveToInventory 调用窗口, 在窗口内跳过自动上传.
    @Unique
    private boolean ae2e$inMoveToInventory;

    @Inject(method = "encodeAndMoveToInventory", at = @At("HEAD"))
    private void ae2e$onMoveToInventoryHead(CallbackInfo ci) {
        this.ae2e$inMoveToInventory = true;
    }

    @Inject(method = "encodeAndMoveToInventory", at = @At("RETURN"))
    private void ae2e$onMoveToInventoryReturn(CallbackInfo ci) {
        this.ae2e$inMoveToInventory = false;
    }

    @Inject(method = "encode", at = @At("RETURN"))
    private void onEncodeReturn(CallbackInfo ci) {
        try {
            if (this.ae2e$inMoveToInventory) return;

            ContainerPatternEncoder container = (ContainerPatternEncoder) (Object) this;

            if (this.patternSlotOUT == null) return;

            ItemStack pattern = this.patternSlotOUT.getStack();
            if (pattern.isEmpty()) return;

            InventoryPlayer invPlayer = ((AEBaseContainer) container).getPlayerInv();
            if (invPlayer == null) return;
            EntityPlayer player = invPlayer.player;
            if (player == null) return;

            if (player.world.isRemote) return;

            appeng.api.networking.IGridNode node = ((appeng.container.implementations.ContainerMEMonitorable) container).getNetworkNode();
            appeng.api.networking.IGrid grid = (node != null) ? node.getGrid() : null;
            if (AssemblyAutoUploadHelper.tryUploadPattern(player.world, player, pattern, grid)) {
                this.patternSlotOUT.putStack(ItemStack.EMPTY);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] AutoUpload unexpected error: {}", e.toString());
        }
    }
}
