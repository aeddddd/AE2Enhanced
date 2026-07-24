package com.github.aeddddd.ae2enhanced.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;

/**
 * 拦截普通挖掘掉落（1.12 HarvestDropsEvent 的对应物）。
 * <p>NeoForge 1.20.1（47.1.x）已移除 BlockDropsEvent，因此通过 mixin 拦截
 * {@link Block#dropResources}：当玩家手持全能工具且掉落模式为背包/AE 时，
 * 取消原版掉落生成并按掉落模式重新分发。</p>
 */
@Mixin(Block.class)
public abstract class MixinBlockOmniToolDrops {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void ae2e$omniToolDrops(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity,
            Entity entity, ItemStack tool, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (!(tool.getItem() instanceof AdvancedMEOmniToolItem)) return;

        int dropMode = OmniToolUpgrades.getDropMode(tool);
        if (dropMode == AdvancedMEOmniToolItem.DROP_NORMAL) return;

        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, entity, tool);
        if (drops.isEmpty()) return;

        ci.cancel();
        MiningModule.handleDrops(level, player, pos, drops, tool);
    }
}
