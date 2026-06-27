package com.github.aeddddd.ae2enhanced.mixin.late.thaumcraft;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.common.entities.EntityFluxRift;

/**
 * Thaumcraft 6 裂隙适配：禁止在个人维度中生成裂隙。
 *
 * <p>注入 {@link EntityFluxRift#createRift(World, BlockPos)} 的 HEAD，
 * 当世界为个人维度时直接 cancel，不消耗 flux、不生成裂隙实体。</p>
 */
@Mixin(value = EntityFluxRift.class, remap = false)
public class MixinEntityFluxRift {

    @Inject(
            method = "createRift",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ae2e$cancelRiftInPersonalDim(World world, BlockPos pos, CallbackInfo ci) {
        if (world != null && PersonalDimensionManager.isPersonalDimension(world.provider.getDimension())) {
            ci.cancel();
        }
    }
}
