package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.dimension.WorldProviderPersonalDim;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 取消其他模组的世界生成装饰器在个人维度中运行。
 *
 * <p>PersonalWorlds 采用相同策略：个人维度由自定义 ChunkGenerator 生成纯粹的地板，
 * 不需要也不应该让其他模组的 oreGen/decoration/population 逻辑在其中生成实体或结构，
 * 这是 entityId 冲突和异常世界状态的来源之一。</p>
 */
@Mixin(value = GameRegistry.class, remap = false)
public class MixinGameRegistry {

    @Inject(method = "generateWorld", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ae2e$cancelModWorldGenInPersonalDim(
            int chunkX, int chunkZ, World world,
            IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
            CallbackInfo ci) {
        if (world.provider instanceof WorldProviderPersonalDim) {
            ci.cancel();
        }
    }
}
