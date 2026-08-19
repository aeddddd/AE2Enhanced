package com.github.aeddddd.ae2enhanced.ring;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 指环事件防护体系.
 *
 * <p>基础指环：致命伤害溢出阻挡(事件层)、挖掘惩罚取消、跳跃增幅、自动回血等.
 * 飞升指环：事件层全伤害免疫、击退/传送免疫、死亡保留、异常位移回滚(tick 管理器).</p>
 */
public class RingEventHandler {

    private static final String NBT_PRESERVED = "AE2E_RingPreserved";

    // ==================== Tick ====================

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.side == Side.SERVER) {
            RingManager.tickServer((EntityPlayerMP) event.player);
        } else {
            RingManager.tickClient(event.player);
        }
    }

    /**
     * ServerTickEvent.END：在全部实体 tick 与 PlayerTickEvent 之后执行,
     * 恢复被外部禁飞模组(BrokenWings/盖亚 III 等)清除的飞行状态(最后写入者获胜).
     */
    @SubscribeEvent
    public void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            RingManager.tickServerEndFlightRestore(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerLoggedOutEvent event) {
        RingManager.discard(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onRespawn(PlayerRespawnEvent event) {
        RingManager.onRespawn(event.player);
        // 死亡保留的飞升指环恢复
        NBTTagCompound entityData = event.player.getEntityData();
        if (entityData.hasKey(NBT_PRESERVED, Constants.NBT.TAG_LIST)) {
            NBTTagList preserved = entityData.getTagList(NBT_PRESERVED, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < preserved.tagCount(); i++) {
                ItemStack stack = new ItemStack(preserved.getCompoundTagAt(i));
                if (!stack.isEmpty()) {
                    event.player.inventory.addItemStackToInventory(stack);
                }
            }
            entityData.removeTag(NBT_PRESERVED);
        }
    }

    // ==================== 伤害阻挡 ====================

    /**
     * 飞升指环：事件层全伤害免疫(含虚空、/kill 等绝对伤害).
     * 费用按伤害量节流计收；能量不足时伤害正常穿过.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving().world.isRemote || event.isCanceled()) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty() || !RingNBT.isAscended(ring) || !RingNBT.isDamageBlockEnabled(ring)) return;
        if (player.isCreative()) {
            event.setCanceled(true);
            return;
        }
        long cost = RingEnergyHandler.price(ring,
                (long) Math.ceil(event.getAmount() * AE2EnhancedConfig.ring.ascendedBlockCostPerPoint));
        if (RingEnergyHandler.consumeThrottled(player, ring, cost, RingEnergyHandler.Category.BLOCK)) {
            event.setCanceled(true);
        }
    }

    /**
     * III 阶段指环：致命伤害时按溢出量消耗 RF 阻挡(带冷却,飞升无冷却).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntityLiving().world.isRemote || event.isCanceled()) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.isCreative()) return;
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty() || !RingNBT.isDamageBlockEnabled(ring)) return;

        float health = player.getHealth();
        float amount = event.getAmount();
        if (RingNBT.isAscended(ring)) {
            // 飞升兜底：LivingAttack 未覆盖的路径(如其他模组直接改 amount)在此归零,无冷却
            long cost = RingEnergyHandler.price(ring,
                    (long) Math.ceil(amount * AE2EnhancedConfig.ring.ascendedBlockCostPerPoint));
            if (RingEnergyHandler.consumeThrottled(player, ring, cost, RingEnergyHandler.Category.BLOCK)) {
                event.setCanceled(true);
            }
            return;
        }

        // III 阶段：仅阻挡致命一击,触发后进入冷却；费用 = 溢出伤害 × 单价(按阶段倍率计价)
        if (!RingNBT.tierAtLeast(ring, 2)) return;
        if (amount < health) return;
        if (RingManager.isDeathBlockOnCooldown(player)) return;
        float overflow = amount - health;
        long cost = RingEnergyHandler.price(ring,
                (long) Math.ceil(overflow * AE2EnhancedConfig.ring.blockCostPerOverflowPoint));
        if (RingEnergyHandler.consumeThrottled(player, ring, cost, RingEnergyHandler.Category.BLOCK)) {
            event.setCanceled(true);
            RingManager.markDeathBlock(player);
        }
    }

    /** 飞升指环：事件层死亡拦截(最后一道保险). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote || event.isCanceled()) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.isCreative()) return;
        if (RingProtection.isAbsoluteProtectionActive(player)) {
            event.setCanceled(true);
            if (player.getHealth() <= 0.0f) {
                RingProtection.setHealthInternal(player, 1.0f);
            }
        }
    }

    // ==================== 机动 ====================

    /** 跳跃高度增幅(阶段 II 起,客户端/服务端双侧应用,避免抖动). */
    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty() || !RingNBT.tierAtLeast(ring, 1)) return;
        int pct = RingNBT.getJumpPercent(ring);
        if (pct > 100) {
            event.getEntityLiving().motionY *= pct / 100.0;
        }
    }

    /** 挖掘惩罚取消：水下 5x / 悬空 5x / 挖掘疲劳 / 错误工具惩罚全部还原. */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty() || !RingNBT.isMiningFixEnabled(ring)) return;

        float speed = event.getOriginalSpeed();
        if (player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER)
                && !EnchantmentHelper.getAquaAffinityModifier(player)) {
            speed *= 5.0f;
        }
        if (!player.onGround) {
            speed *= 5.0f;
        }
        PotionEffect fatigue = player.getActivePotionEffect(MobEffects.MINING_FATIGUE);
        if (fatigue != null) {
            float factor;
            switch (fatigue.getAmplifier()) {
                case 0: factor = 0.3f; break;
                case 1: factor = 0.09f; break;
                case 2: factor = 0.0027f; break;
                default: factor = 8.1E-4f; break;
            }
            speed /= factor;
        }
        // 错误工具惩罚：Forge 在 getDigSpeed 中对不可采集方块除以 100
        BlockPos pos = event.getPos();
        if (pos != null && event.getState() != null
                && !net.minecraftforge.common.ForgeHooks.canHarvestBlock(
                event.getState().getBlock(), player, player.world, pos)) {
            speed *= 100.0f;
        }
        event.setNewSpeed(speed);
    }

    // ==================== 飞升位移免疫 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKnockBack(LivingKnockBackEvent event) {
        if (event.isCanceled()) return;
        if (RingProtection.isPullProtectionEnabled(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    /** 飞升免疫末影珍珠/紫颂果等传送位移(1.12.2 的传送事件为 EnderTeleportEvent). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTeleport(EnderTeleportEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntity();
        if (RingProtection.isPullProtectionEnabled(player) && !RingProtection.isTeleportAllowed(player)) {
            event.setCanceled(true);
        }
    }

    // ==================== 飞升死亡保留 ====================

    /**
     * 飞升指环死亡保留.
     * 必须为 HIGHEST 且先于 ModEventHandler 注册(见 ModEventHandler.register),
     * 否则掉落物会先被先进 ME 收集器重定向进网络.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerDrops(PlayerDropsEvent event) {
        java.util.Iterator<net.minecraft.entity.item.EntityItem> it = event.getDrops().iterator();
        NBTTagList preserved = new NBTTagList();
        while (it.hasNext()) {
            ItemStack stack = it.next().getItem();
            if (stack.getItem() instanceof ItemNetworkLinkCredential && RingNBT.isAscended(stack)) {
                it.remove();
                preserved.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        if (preserved.tagCount() > 0) {
            event.getEntityPlayer().getEntityData().setTag(NBT_PRESERVED, preserved);
        }
    }
}
