package com.github.aeddddd.ae2enhanced.mixin;

import java.util.Collection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在样板编码终端完成编码后，自动将已编码样板上传到最近的装配枢纽。
 * <p>仅当存在已成型且安装了“样板自动上传升级”的装配枢纽时生效；
 * 上传目标为距离玩家最近的装配枢纽，并插入其第一个空闲样板槽位。</p>
 */
@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public class MixinPatternEncodingTermMenu {

    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;

    @Inject(method = "encode", at = @At("TAIL"), remap = false)
    private void ae2e$onEncode(CallbackInfo ci) {
        PatternEncodingTermMenu self = (PatternEncodingTermMenu) (Object) this;
        if (self.isClientSide()) {
            return;
        }

        ItemStack encoded = encodedPatternSlot.getItem();
        if (encoded.isEmpty() || !PatternDetailsHelper.isEncodedPattern(encoded)) {
            return;
        }

        Player player = self.getPlayerInventory().player;
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        ControllerIndex index = ControllerIndex.get(serverLevel);
        Collection<BlockPos> controllers = index.getAll();

        AssemblyControllerBlockEntity target = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (BlockPos pos : controllers) {
            if (!(serverLevel.getBlockEntity(pos) instanceof AssemblyControllerBlockEntity hub)) {
                continue;
            }
            if (!hub.isFormed() || !hub.hasAutoUploadUpgrade()) {
                continue;
            }
            double distSq = pos.distSqr(playerPos);
            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                target = hub;
            }
        }

        if (target == null) {
            return;
        }

        ItemStackHandler handler = target.getItemHandler();
        int limit = AssemblyControllerBlockEntity.UPGRADE_SLOTS + target.getPatternSlotCount();
        for (int slot = AssemblyControllerBlockEntity.UPGRADE_SLOTS; slot < limit; slot++) {
            if (slot >= handler.getSlots()) {
                break;
            }
            if (handler.getStackInSlot(slot).isEmpty()) {
                handler.setStackInSlot(slot, encoded.copy());
                return;
            }
        }
    }
}
