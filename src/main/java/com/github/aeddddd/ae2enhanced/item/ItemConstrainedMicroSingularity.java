package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.render.ConstrainedSingularityItemRenderer;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * 被约束的微型奇点（物品形态）.
 * 由奇点约束器右键方块形态的微型奇点转化而来,NBT 携带剩余存在时间与永久标记.
 * 物品形态下倒计时以 1/30 速度继续流逝；扔出落地静置后恢复为方块形态,
 * 并在原地返还一个空的奇点约束器.
 */
public class ItemConstrainedMicroSingularity extends Item {

    private static final String NBT_LIFE_TICKS = "LifeTicks";
    private static final String NBT_PERMANENT = "Permanent";
    private static final String NBT_SLOW_COUNTER = "SlowCounter";
    /** 实体落地计时键（存于 EntityItem persistentData） */
    private static final String NBT_GROUNDED = "ae2enhanced:grounded";
    /** 物品形态倒计时减速倍率 */
    private static final int SLOW_FACTOR = 30;
    /** 落地静置多少 tick 后恢复为方块 */
    private static final int RESTORE_DELAY_TICKS = 20;

    public ItemConstrainedMicroSingularity() {
        setRegistryName(AE2Enhanced.MOD_ID, "constrained_micro_singularity");
        setTranslationKey(AE2Enhanced.MOD_ID + ".constrained_micro_singularity");
        setMaxStackSize(1);
    }

    /** 创建携带指定状态的被约束奇点物品. */
    public static ItemStack createStack(int lifeTicks, boolean permanent) {
        ItemStack stack = new ItemStack(ItemRegistry.CONSTRAINED_MICRO_SINGULARITY);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBT_LIFE_TICKS, lifeTicks > 0 ? lifeTicks : TileMicroSingularity.DEFAULT_LIFE_TICKS);
        tag.setBoolean(NBT_PERMANENT, permanent);
        stack.setTagCompound(tag);
        return stack;
    }

    public static int getLifeTicks(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(NBT_LIFE_TICKS)
                ? tag.getInteger(NBT_LIFE_TICKS)
                : TileMicroSingularity.DEFAULT_LIFE_TICKS;
    }

    public static boolean isPermanent(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(NBT_PERMANENT);
    }

    /** 物品形态倒计时：每 SLOW_FACTOR tick 才流逝 1 tick 寿命. */
    private static void tickSlowCountdown(ItemStack stack) {
        if (isPermanent(stack)) {
            return;
        }
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        int slow = tag.getInteger(NBT_SLOW_COUNTER) + 1;
        if (slow >= SLOW_FACTOR) {
            slow = 0;
            tag.setInteger(NBT_LIFE_TICKS, getLifeTicks(stack) - 1);
        }
        tag.setInteger(NBT_SLOW_COUNTER, slow);
        stack.setTagCompound(tag);
    }

    /** 倒计时归零：奇点在物品形态下坍缩湮灭. */
    private static void collapseStack(ItemStack stack, World world, double x, double y, double z) {
        stack.shrink(stack.getCount());
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.0f, 0.5f);
    }

    @Override
    public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        if (worldIn.isRemote) {
            return;
        }
        tickSlowCountdown(stack);
        if (!isPermanent(stack) && getLifeTicks(stack) <= 0) {
            collapseStack(stack, worldIn, entityIn.posX, entityIn.posY, entityIn.posZ);
        }
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        World world = entityItem.world;
        if (world.isRemote) {
            return false;
        }
        ItemStack stack = entityItem.getItem();

        tickSlowCountdown(stack);
        if (!isPermanent(stack) && getLifeTicks(stack) <= 0) {
            collapseStack(stack, world, entityItem.posX, entityItem.posY, entityItem.posZ);
            entityItem.setDead();
            return false;
        }

        // 落地静置计时,到达延迟后恢复为方块形态
        if (entityItem.onGround) {
            int grounded = entityItem.getEntityData().getInteger(NBT_GROUNDED) + 1;
            entityItem.getEntityData().setInteger(NBT_GROUNDED, grounded);
            if (grounded >= RESTORE_DELAY_TICKS) {
                tryRestoreBlock(world, entityItem, stack);
            }
        } else {
            entityItem.getEntityData().setInteger(NBT_GROUNDED, 0);
        }
        return false;
    }

    /**
     * 在物品实体所在位置恢复微型奇点方块,并返还空的奇点约束器.
     */
    private static void tryRestoreBlock(World world, EntityItem entityItem, ItemStack stack) {
        BlockPos pos = entityItem.getPosition();
        if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
            pos = pos.up();
        }
        if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
            // 位置被占用,等待下一周期重试
            entityItem.getEntityData().setInteger(NBT_GROUNDED, 0);
            return;
        }

        world.setBlockState(pos, BlockRegistry.MICRO_SINGULARITY.getDefaultState());
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileMicroSingularity) {
            ((TileMicroSingularity) te).setLifetimeTicks(getLifeTicks(stack));
            ((TileMicroSingularity) te).setPermanent(isPermanent(stack));
        }
        entityItem.setDead();

        // 返还空的奇点约束器
        EntityItem constrictor = new EntityItem(world,
                entityItem.posX, entityItem.posY + 0.5, entityItem.posZ,
                new ItemStack(ItemRegistry.SINGULARITY_CONSTRICTOR));
        constrictor.setDefaultPickupDelay();
        constrictor.motionY = 0.15;
        world.spawnEntity(constrictor);

        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 0.6f, 1.2f);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean hasEffect(ItemStack stack) {
        // 永久奇点带附魔光效,便于区分
        return isPermanent(stack);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        if (isPermanent(stack)) {
            tooltip.add(I18n.format("item.ae2enhanced.constrained_micro_singularity.tooltip.permanent"));
        } else {
            tooltip.add(I18n.format("item.ae2enhanced.constrained_micro_singularity.tooltip.remaining",
                    String.format("%.1f", getLifeTicks(stack) / 20.0)));
        }
        tooltip.addAll(Arrays.asList(I18n.format("item.ae2enhanced.constrained_micro_singularity.tooltip.hint")
                .replace("\\n", "\n").split("\n")));
    }

    /**
     * 客户端初始化：注册内置物品渲染器（TEISR）.
     * 模型 JSON 使用 builtin/entity,RenderItem 走 Item 自己的 renderer.
     */
    @SideOnly(Side.CLIENT)
    public void initModel() {
        this.setTileEntityItemStackRenderer(ConstrainedSingularityItemRenderer.INSTANCE);
    }
}
