package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端注入：允许带有基岩破坏者升级的先进ME全能工具破坏硬度为 -1 的方块。
 * 原版 onBlockClicked 会在 hardness < 0 时直接返回，不会调用 onBlockStartBreak。
 * 此处提前检测并调用工具的破坏逻辑，然后取消原方法。
 */
@Mixin(value = PlayerInteractionManager.class, remap = true)
public class MixinPlayerInteractionManager {

    @Inject(method = "onBlockClicked", at = @At("HEAD"), cancellable = true)
    private void ae2e$onBlockClicked(BlockPos pos, EnumFacing side, CallbackInfo ci) {
        PlayerInteractionManager self = (PlayerInteractionManager) (Object) this;
        EntityPlayerMP player = self.player;
        if (player == null) return;

        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemAdvancedMEOmniTool)) return;
        if (!OmniToolUpgrades.hasBedrockBreaker(stack)) return;

        World world = self.world;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlockHardness(world, pos) >= 0.0F) return;

        stack.getItem().onBlockStartBreak(stack, pos, player);
        ci.cancel();
    }
}
