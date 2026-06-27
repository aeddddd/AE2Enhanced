package com.github.aeddddd.ae2enhanced.mixin.late.techreborn;

import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import reborncore.common.recipes.RecipeCrafter;

/**
 * TechReborn 机器产物直注 Mixin。
 *
 * <p>在 {@link RecipeCrafter#fitStack(ItemStack, int)} 写入产物到机器物品栏之前，
 * 尝试把产物重定向到已绑定的 ME 网络回收节点。若全部注入成功，
 * fitStack 看到空 stack 后直接返回，产物不会进入输出槽。</p>
 */
@Mixin(value = RecipeCrafter.class, remap = false)
public class MixinRecipeCrafter {

    @Shadow
    public TileEntity parentTile;

    @ModifyVariable(method = "fitStack", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private ItemStack ae2enhanced$redirectOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        if (this.parentTile == null) {
            return stack;
        }
        World world = this.parentTile.getWorld();
        if (world == null || world.isRemote) {
            return stack;
        }
        return MachineOutputRedirector.tryRedirect(stack, world, this.parentTile.getPos());
    }
}
