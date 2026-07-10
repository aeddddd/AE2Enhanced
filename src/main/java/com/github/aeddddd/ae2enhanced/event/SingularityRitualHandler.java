package com.github.aeddddd.ae2enhanced.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRecipeRegistry;

/**
 * 微型奇点仪式触发器。
 * 玩家手持指定物品右键指定目标方块时，扫描周围 5×5×5 区域内的物品实体，
 * 如果匹配仪式配方，则消耗材料并生成微型奇点。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SingularityRitualHandler {

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();

        SingularityRecipe recipe = SingularityRecipeRegistry.findMatching(level, pos, held);
        if (recipe == null) {
            return;
        }

        // 取消默认交互，防止打开其他 GUI
        event.setCanceled(true);

        // 检查手持物品是否需要消耗
        if (!recipe.getHeldItem().isEmpty() && !player.isCreative()) {
            held.shrink(1);
        }

        // 执行仪式
        recipe.craft(level, pos, held);

        // 特效
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    1, 0, 0, 0, 0);
        }
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 1.0f, 0.5f);
    }
}
