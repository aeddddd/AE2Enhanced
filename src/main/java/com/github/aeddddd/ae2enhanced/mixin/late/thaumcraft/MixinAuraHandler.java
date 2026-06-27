package com.github.aeddddd.ae2enhanced.mixin.late.thaumcraft;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.common.world.aura.AuraHandler;

import java.util.Random;

/**
 * Thaumcraft 6 Vis 适配：个人维度新 chunk 初始 vis 固定为 3000。
 *
 * <p>注入 {@link AuraHandler#generateAura(Chunk, Random)} 的 HEAD，
 * 当 chunk 所属世界为个人维度时，直接写入 base=3000、vis=3000、flux=0 并 return，
 * 覆盖 TC6 默认基于生物群系的 aura 生成逻辑。</p>
 */
@Mixin(value = AuraHandler.class, remap = false)
public class MixinAuraHandler {

    @Inject(
            method = "generateAura",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ae2e$setPersonalDimVis(Chunk chunk, Random rand, CallbackInfo ci) {
        if (chunk == null || chunk.getWorld() == null) return;
        int dim = chunk.getWorld().provider.getDimension();
        if (PersonalDimensionManager.isPersonalDimension(dim)) {
            AuraHandler.addAuraChunk(dim, chunk, (short) 3000, 3000.0f, 0.0f);
            ci.cancel();
        }
    }
}
