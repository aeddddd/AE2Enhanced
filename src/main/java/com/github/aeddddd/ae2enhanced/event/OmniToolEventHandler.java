package com.github.aeddddd.ae2enhanced.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUndo;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;

/**
 * 先进 ME 全能工具全局事件处理器。
 * <p>该类不在 {@code @Mod.EventBusSubscriber} 中自动注册，而是在
 * {@code FMLCommonSetupEvent} 中手动注册（参照 StructureEventHandler 的模式）。</p>
 */
public final class OmniToolEventHandler {

    private OmniToolEventHandler() {}

    // ==================== 基岩破坏者（左键不可破坏方块） ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.isCanceled()) return;
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)) return;

        int mode = AdvancedMEOmniToolItem.getMode(stack);
        if (mode != AdvancedMEOmniToolItem.MODE_UNIVERSAL && mode != AdvancedMEOmniToolItem.MODE_TRAVEL) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getDestroySpeed(event.getLevel(), event.getPos()) >= 0.0f) return;
        if (MiningModule.isBlacklisted(state.getBlock())) return;

        event.setCanceled(true);
        MiningModule.forceBreakBlock(player, event.getLevel(), event.getPos(), stack);
    }

    // ==================== 放置模式：Ctrl+右键撤销 ====================

    @SubscribeEvent
    public static void onPlacementToolRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || event.isCanceled()) return;

        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)
                || AdvancedMEOmniToolItem.getMode(stack) != AdvancedMEOmniToolItem.MODE_PLACEMENT) {
            return;
        }

        // 仅在客户端检测 Ctrl 键；服务端分支已在上方返回
        if (!net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            return;
        }

        event.setCanceled(true);
        ModNetwork.CHANNEL.sendToServer(new PacketPlacementUndo());
    }

    // ==================== 掉落模式改写（普通挖掘路径） ====================
    // 注：NeoForge 1.20.1 已移除 BlockDropsEvent，普通挖掘掉落改写由
    // MixinBlockOmniToolDrops 拦截 Block.dropResources 实现。

    // ==================== 禁疗（混沌核心） ====================

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (CombatModule.hasAntiHeal(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 兜底：每 tick 末强制把带禁疗标记且未被移除的实体标记为已移除。
     * 防止某些具有自定义存活逻辑的实体通过覆盖存活判定等手段
     * 阻止自身被 level 的实体管理器移除。
     * 仅遍历 CombatModule 的禁疗追踪集合（施加时登记）,不做全维度实体扫描。
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel serverLevel)) return;
        // 追踪集合跨维度共享,只在主世界 tick 处理一次
        if (serverLevel != serverLevel.getServer().overworld()) return;
        Set<LivingEntity> tracked = CombatModule.getAntiHealTracked();
        if (tracked.isEmpty()) return;
        for (LivingEntity living : new ArrayList<>(tracked)) {
            if (living.isRemoved()) {
                tracked.remove(living);
            } else {
                ForceKillHelper.forceSetRemoved(living);
            }
        }
    }

    /**
     * 存档重载/跨维度后,依据持久 NBT 重新登记禁疗实体到追踪集合。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof LivingEntity living && CombatModule.hasAntiHeal(living)) {
            CombatModule.trackAntiHeal(living);
        }
    }

    // ==================== 共形不变荷：掉落物不消失 ====================

    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (stack.getItem() instanceof AdvancedMEOmniToolItem && OmniToolUpgrades.hasConformalCharge(stack)) {
            event.setCanceled(true);
        }
    }

    // ==================== 共形不变荷：死亡保留 / 重生返还 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || event.isCanceled() || event.getDrops().isEmpty()) return;

        ListTag preserved = new ListTag();
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next().getItem();
            if (stack.getItem() instanceof AdvancedMEOmniToolItem && OmniToolUpgrades.hasConformalCharge(stack)) {
                it.remove();
                preserved.add(stack.save(new CompoundTag()));
            }
        }
        if (!preserved.isEmpty()) {
            player.getPersistentData().put(OmniToolNBT.CONFORMAL_PRESERVED, preserved);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        // 玩家重生时清除禁疗标记,防止 persistentData 继承导致的重生后持续强制移除
        CombatModule.clearAntiHeal(player);
        CompoundTag data = player.getPersistentData();
        if (!data.contains(OmniToolNBT.CONFORMAL_PRESERVED, Tag.TAG_LIST)) return;
        ListTag preserved = data.getList(OmniToolNBT.CONFORMAL_PRESERVED, Tag.TAG_COMPOUND);
        for (int i = 0; i < preserved.size(); i++) {
            ItemStack stack = ItemStack.of(preserved.getCompound(i));
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        data.remove(OmniToolNBT.CONFORMAL_PRESERVED);
    }
}
