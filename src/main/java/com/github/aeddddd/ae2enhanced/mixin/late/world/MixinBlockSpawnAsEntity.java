package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截方块破坏掉落的总入口 Block.spawnAsEntity.
 *
 * 在原版即将创建 EntityItem 之前,直接尝试把物品交给先进 ME 收集器处理.
 */
@Mixin(value = net.minecraft.block.Block.class, remap = true)
public class MixinBlockSpawnAsEntity {

    @Inject(method = "spawnAsEntity", at = @At("HEAD"), cancellable = true)
    private static void ae2e$onSpawnAsEntity(World worldIn, BlockPos pos, ItemStack stack, CallbackInfo ci) {
        if (worldIn == null || worldIn.isRemote || stack.isEmpty()) return;
        if (!CollectorRegistry.hasCollectors(worldIn)) return;

        TileAdvancedMECollector collector = CollectorRegistry.findBestCollector(worldIn, pos);
        if (collector == null) return;

        ItemStack remaining = collector.tryCollectStackForced(stack);
        if (remaining.isEmpty()) {
            ci.cancel();
        }
    }
}
