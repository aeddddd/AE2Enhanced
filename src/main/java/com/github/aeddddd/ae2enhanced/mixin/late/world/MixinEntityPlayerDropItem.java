package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截玩家丢物品 (EntityPlayer.dropItem).
 *
 * 在 EntityItem 加入世界之前,直接把物品交给先进 ME 收集器处理.
 */
@Mixin(value = EntityPlayer.class, remap = true)
public class MixinEntityPlayerDropItem {

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void ae2e$onDropItem(ItemStack droppedItem, boolean dropAround, CallbackInfoReturnable<EntityItem> cir) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (player.world.isRemote || droppedItem.isEmpty()) return;
        if (!CollectorRegistry.hasCollectors(player.world)) return;

        TileAdvancedMECollector collector = CollectorRegistry.findBestCollector(player.world, player.posX, player.posY, player.posZ);
        if (collector == null) return;

        ItemStack remaining = collector.tryCollectStackForced(droppedItem);
        if (remaining.isEmpty()) {
            cir.setReturnValue(null);
        }
    }
}
