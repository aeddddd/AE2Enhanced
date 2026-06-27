package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端注入：允许带有基岩破坏者升级的先进ME全能工具对硬度为 -1 的方块发送破坏包。
 * 原版 clickBlock 在 hardness < 0 时不会发送 START_DESTROY_BLOCK，导致服务端收不到点击事件。
 */
@Mixin(value = PlayerControllerMP.class, remap = true)
public class MixinPlayerControllerMP {

    @Inject(method = "clickBlock", at = @At("HEAD"), cancellable = true)
    private void ae2e$clickBlock(BlockPos loc, EnumFacing face, CallbackInfoReturnable<Boolean> cir) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return;

        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemAdvancedMEOmniTool)) return;
        if (!OmniToolUpgrades.hasBedrockBreaker(stack)) return;

        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) return;
        IBlockState state = world.getBlockState(loc);
        if (state.getBlockHardness(world, loc) >= 0.0F) return;

        if (Minecraft.getMinecraft().getConnection() != null) {
            Minecraft.getMinecraft().getConnection().sendPacket(
                    new CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, loc, face));
        }
        cir.setReturnValue(true);
        cir.cancel();
    }
}
