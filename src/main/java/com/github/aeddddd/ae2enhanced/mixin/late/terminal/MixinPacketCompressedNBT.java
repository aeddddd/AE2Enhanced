package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.packets.PacketCompressedNBT;
import com.github.aeddddd.ae2enhanced.client.gui.terminal.TerminalPacketBridge;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketCompressedNBT.class, remap = false)
public abstract class MixinPacketCompressedNBT {
    @Shadow
    private NBTTagCompound in;

    @Inject(method = "clientPacketData", at = @At("HEAD"), cancellable = true)
    private void ae2e$routeToSplicedTerminal(INetworkInfo network, AppEngPacket packet,
            EntityPlayer player, CallbackInfo ci) {
        if (TerminalPacketBridge.postUpdate(in)) {
            ci.cancel();
        }
    }
}
