package com.github.aeddddd.ae2enhanced.item;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import com.github.aeddddd.ae2enhanced.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.client.render.MicroSingularityItemRenderer;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.registry.ModItems;

/**
 * 被约束的微型奇点（物品形态）.
 * 由奇点约束器右键方块形态的微型奇点转化而来,NBT 携带剩余存在时间与永久标记.
 * 物品形态下倒计时以 1/30 速度继续流逝；扔出落地静置后恢复为方块形态,
 * 并在原地返还一个空的奇点约束器.
 */
public class MicroSingularityItem extends Item {

    private static final String NBT_LIFE_TICKS = "LifeTicks";
    private static final String NBT_PERMANENT = "Permanent";
    private static final String NBT_SLOW_COUNTER = "SlowCounter";
    /** 实体落地计时键（存于 ItemEntity persistentData） */
    private static final String NBT_GROUNDED = "ae2enhanced:grounded";
    /** 物品形态倒计时减速倍率 */
    private static final int SLOW_FACTOR = 30;
    /** 落地静置多少 tick 后恢复为方块 */
    private static final int RESTORE_DELAY_TICKS = 20;

    public MicroSingularityItem(Properties properties) {
        super(properties);
    }

    /** 创建携带指定状态的被约束奇点物品. */
    public static ItemStack createStack(int lifeTicks, boolean permanent) {
        ItemStack stack = new ItemStack(ModItems.MICRO_SINGULARITY.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(NBT_LIFE_TICKS, lifeTicks > 0 ? lifeTicks : MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS);
        tag.putBoolean(NBT_PERMANENT, permanent);
        return stack;
    }

    public static int getLifeTicks(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_LIFE_TICKS)
                ? tag.getInt(NBT_LIFE_TICKS)
                : MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS;
    }

    public static boolean isPermanent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(NBT_PERMANENT);
    }

    /** 物品形态倒计时：每 SLOW_FACTOR tick 才流逝 1 tick 寿命. */
    private static void tickSlowCountdown(ItemStack stack) {
        if (isPermanent(stack)) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        int slow = tag.getInt(NBT_SLOW_COUNTER) + 1;
        if (slow >= SLOW_FACTOR) {
            slow = 0;
            tag.putInt(NBT_LIFE_TICKS, getLifeTicks(stack) - 1);
        }
        tag.putInt(NBT_SLOW_COUNTER, slow);
    }

    /** 倒计时归零：奇点在物品形态下坍缩湮灭. */
    private static void collapseStack(ItemStack stack, Level level, double x, double y, double z) {
        stack.shrink(stack.getCount());
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0f, 0.5f);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) {
            return;
        }
        tickSlowCountdown(stack);
        if (!isPermanent(stack) && getLifeTicks(stack) <= 0) {
            collapseStack(stack, level, entity.getX(), entity.getY(), entity.getZ());
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (level.isClientSide()) {
            return false;
        }

        tickSlowCountdown(stack);
        if (!isPermanent(stack) && getLifeTicks(stack) <= 0) {
            collapseStack(stack, level, entity.getX(), entity.getY(), entity.getZ());
            entity.discard();
            return false;
        }

        // 落地静置计时,到达延迟后恢复为方块形态
        if (entity.onGround()) {
            int grounded = entity.getPersistentData().getInt(NBT_GROUNDED) + 1;
            entity.getPersistentData().putInt(NBT_GROUNDED, grounded);
            if (grounded >= RESTORE_DELAY_TICKS) {
                tryRestoreBlock(level, entity, stack);
            }
        } else {
            entity.getPersistentData().putInt(NBT_GROUNDED, 0);
        }
        return false;
    }

    /**
     * 在物品实体所在位置恢复微型奇点方块,并返还空的奇点约束器.
     */
    private static void tryRestoreBlock(Level level, ItemEntity entity, ItemStack stack) {
        net.minecraft.core.BlockPos pos = entity.blockPosition();
        if (!level.getBlockState(pos).canBeReplaced()) {
            pos = pos.above();
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            // 位置被占用,等待下一周期重试
            entity.getPersistentData().putInt(NBT_GROUNDED, 0);
            return;
        }

        level.setBlockAndUpdate(pos, ModBlocks.MICRO_SINGULARITY.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof MicroSingularityBlockEntity microSingularity) {
            microSingularity.setLifetimeTicks(getLifeTicks(stack));
            microSingularity.setPermanent(isPermanent(stack));
        }
        entity.discard();

        // 返还空的奇点约束器
        ItemEntity constrictor = new ItemEntity(level,
                entity.getX(), entity.getY() + 0.5, entity.getZ(),
                new ItemStack(ModItems.SINGULARITY_CONSTRICTOR.get()));
        constrictor.setPickUpDelay(10);
        constrictor.setDeltaMovement(0, 0.15, 0);
        level.addFreshEntity(constrictor);

        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.6f, 1.2f);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // 永久奇点带附魔光效,便于区分
        return isPermanent(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (isPermanent(stack)) {
            tooltip.add(Component.translatable("item.ae2enhanced.micro_singularity.tooltip.permanent"));
        } else {
            tooltip.add(Component.translatable("item.ae2enhanced.micro_singularity.tooltip.remaining",
                    String.format("%.1f", getLifeTicks(stack) / 20.0)));
        }
        String hint = Component.translatable("item.ae2enhanced.micro_singularity.tooltip.hint").getString();
        for (String line : hint.replace("\\n", "\n").split("\n")) {
            tooltip.add(Component.literal(line));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft mc = Minecraft.getInstance();
                    renderer = new MicroSingularityItemRenderer(mc.getBlockEntityRenderDispatcher(),
                            mc.getEntityModels());
                }
                return renderer;
            }
        });
    }
}
