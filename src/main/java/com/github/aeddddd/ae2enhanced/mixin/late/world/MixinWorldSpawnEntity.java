package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截世界中所有 EntityItem 的生成,在实体加入世界前尝试由先进 ME 收集器强制收取.
 *
 * <p>作为兜底拦截:Block.spawnAsEntity 和 EntityPlayer.dropItem 已在更早位置处理,
 * 此处覆盖其他所有调用 World.spawnEntity 产生物品实体的路径(如生物死亡、漏斗投掷器、mod 机器等).</p>
 */
@Mixin(value = World.class, remap = true)
public class MixinWorldSpawnEntity {

    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void ae2e$onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof EntityItem) || entity.world.isRemote) {
            return;
        }

        EntityItem item = (EntityItem) entity;
        TileAdvancedMECollector collector = CollectorRegistry.findBestCollector(item);
        if (collector != null) {
            ItemStack remaining = collector.tryCollectStackForced(item.getItem());
            if (remaining.isEmpty()) {
                cir.setReturnValue(true);
            }
        }
    }
}
