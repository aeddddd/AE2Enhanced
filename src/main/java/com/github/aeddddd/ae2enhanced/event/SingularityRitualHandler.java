package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 微型奇点仪式触发器.
 * 玩家手持指定物品右键指定目标方块时,扫描周围 5×5×5 区域内的物品实体,
 * 如果匹配仪式配方,则消耗材料并生成微型奇点.
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class SingularityRitualHandler {

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote) return;

        BlockPos pos = event.getPos();
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = event.getItemStack();

        SingularityRecipe recipe = SingularityRecipeRegistry.findMatching(world, pos, held);
        if (recipe == null) {
            return;
        }

        // 取消默认交互,防止打开其他 GUI
        event.setCanceled(true);

        // 检查手持物品是否需要消耗
        if (!recipe.getHeldItem().isEmpty() && !player.capabilities.isCreativeMode) {
            held.shrink(1);
        }

        // 执行仪式
        recipe.craft(world, pos, held);

        // 特效
        world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN,
                SoundCategory.BLOCKS, 1.0f, 0.5f);
    }
}
