package com.github.aeddddd.ae2enhanced.mixin.late;

import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import appeng.items.tools.powered.ToolMatterCannon;

@Mixin(value = ToolMatterCannon.class, remap = false)
public class MixinToolMatterCannon {

    /**
     * 扩展物质炮使用共形不变荷时的射线射程。
     * AE2 默认 getPlayerRay() 的 reachDistance 只有 5.0 格，
     * 导致远距离实体无法被射线检测到。
     * 当 penetration > 1_000_000（共形不变荷）时，将射线终点延伸到 128 格。
     */
    @ModifyArgs(
        method = "func_77659_a",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/items/tools/powered/ToolMatterCannon;standardAmmo(FLnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;DDD)V"
        )
    )
    private void extendRange(Args args) {
        float penetration = args.get(0);
        if (penetration > 1_000_000.0f) {
            Vec3d vec3d2 = args.get(3);
            Vec3d vec3d1 = args.get(4);
            Vec3d dir = vec3d1.subtract(vec3d2).normalize();
            Vec3d extended = vec3d2.add(dir.x * 128.0, dir.y * 128.0, dir.z * 128.0);
            args.set(4, extended);
        }
    }
}
