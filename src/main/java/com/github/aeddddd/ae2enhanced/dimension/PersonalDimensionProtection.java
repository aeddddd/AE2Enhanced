package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmorStand;
import net.minecraft.item.ItemBoat;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemMinecart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fluids.UniversalBucket;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 个人维度建造/交互权限的强制执行器。
 *
 * <p>维度所有者与服务端 OP（权限等级 &ge; 2）始终绕过检查；
 * 其余玩家按 {@link PlayerDimEntry} 中的权限表判定：</p>
 * <ul>
 *   <li>{@link PersonalDimPermission#BUILD}：破坏方块、放置方块/实体、桶装取流体</li>
 *   <li>{@link PersonalDimPermission#INTERACT}：右键方块（打开 GUI、按钮、拉杆等）、
 *   右键实体、攻击实体</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class PersonalDimensionProtection {

    private PersonalDimensionProtection() {}

    /**
     * 拒绝提示的发送冷却，防止连续点击时聊天栏刷屏。
     */
    private static final Map<UUID, Long> DENY_MESSAGE_COOLDOWN = new HashMap<>();
    private static final long DENY_MESSAGE_INTERVAL_TICKS = 20L;

    /**
     * 检查玩家在所处个人维度内是否拥有某项权限。
     * 非个人维度、客户端侧、维度所有者与 OP 一律放行。
     *
     * <p>FakePlayer（机器假玩家）策略：不做特殊处理，按普通访客判定。
     * 假玩家通常权限等级为 0，不会命中 OP 放行；所有者可在白名单中为
     * 假玩家的 UUID 显式授予权限，未授权的机器一律按无权限访客拦截。</p>
     */
    public static boolean canAct(EntityPlayer player, PersonalDimPermission permission) {
        if (player.world.isRemote) return true;
        int dimId = player.dimension;
        if (!PersonalDimensionManager.isPersonalDimension(dimId)) return true;
        PlayerDimEntry entry = PersonalDimensionManager.getEntryByDimension(dimId);
        if (entry == null) return true;
        if (entry.playerId.equals(player.getUniqueID())) return true;
        if (player.canUseCommand(2, "")) return true;
        return entry.hasPermission(player.getUniqueID(), permission);
    }

    private static void sendDenyMessage(EntityPlayer player, String langKey) {
        long now = player.world.getTotalWorldTime();
        Long last = DENY_MESSAGE_COOLDOWN.get(player.getUniqueID());
        if (last != null && now - last < DENY_MESSAGE_INTERVAL_TICKS) return;
        DENY_MESSAGE_COOLDOWN.put(player.getUniqueID(), now);
        player.sendMessage(new TextComponentTranslation(langKey));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DENY_MESSAGE_COOLDOWN.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!canAct(event.getPlayer(), PersonalDimPermission.BUILD)) {
            event.setCanceled(true);
            sendDenyMessage(event.getPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_build");
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!canAct(event.getPlayer(), PersonalDimPermission.BUILD)) {
            event.setCanceled(true);
            sendDenyMessage(event.getPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_build");
        }
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.MultiPlaceEvent event) {
        if (!canAct(event.getPlayer(), PersonalDimPermission.BUILD)) {
            event.setCanceled(true);
            sendDenyMessage(event.getPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_build");
        }
    }

    /**
     * 桶的装取都会改变世界的流体状态，归入 BUILD。
     */
    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        if (!canAct(event.getEntityPlayer(), PersonalDimPermission.BUILD)) {
            event.setCanceled(true);
            sendDenyMessage(event.getEntityPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_build");
        }
    }

    /**
     * 判断物品是否属于"改变世界状态"的使用类物品，归入 BUILD 语义。
     *
     * <p>采用特判而非一律 deny useItem：桶类（含 {@link UniversalBucket}，
     * {@code ItemLavaBucket} 继承自 {@link ItemBucket}）倾倒流体、打火石点火
     * 走 useItem 路径且不触发 PlaceEvent；船/矿车/盔甲架通过物品放置实体，
     * 同样改变世界。末影珍珠、食物等纯功能性物品不在此列，避免误伤。</p>
     */
    private static boolean isWorldChangingItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ItemBucket
                || item instanceof UniversalBucket
                || item instanceof ItemFlintAndSteel
                || item instanceof ItemBoat
                || item instanceof ItemMinecart
                || item instanceof ItemArmorStand;
    }

    /**
     * 无 INTERACT 权限时拒绝方块激活（打开 GUI、按钮、拉杆等）；
     * 无 BUILD 权限时额外拒绝改变世界类物品（桶/打火石/船等）的使用，
     * 防止访客通过倒岩浆、点火、放置实体绕过方块放置拦截。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!canAct(event.getEntityPlayer(), PersonalDimPermission.INTERACT)) {
            event.setUseBlock(Event.Result.DENY);
            sendDenyMessage(event.getEntityPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_interact");
        }
        if (isWorldChangingItem(event.getItemStack())
                && !canAct(event.getEntityPlayer(), PersonalDimPermission.BUILD)) {
            event.setUseItem(Event.Result.DENY);
            sendDenyMessage(event.getEntityPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_build");
        }
    }

    /**
     * 右键空气（raytrace 未命中方块）路径：改变世界类物品同样需要 BUILD 权限。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isWorldChangingItem(event.getItemStack())
                && !canAct(event.getEntityPlayer(), PersonalDimPermission.BUILD)) {
            event.setCanceled(true);
            sendDenyMessage(event.getEntityPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_build");
        }
    }

    /**
     * 个人维度是玩家的私人建造空间，规则中没有爆炸相关选项，
     * 按 BUILD 语义保守处理：维度内爆炸不破坏任何方块（实体伤害不受影响）。
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) return;
        if (!PersonalDimensionManager.isPersonalDimension(event.getWorld().provider.getDimension())) return;
        event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!canAct(event.getEntityPlayer(), PersonalDimPermission.INTERACT)) {
            event.setCanceled(true);
            sendDenyMessage(event.getEntityPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_interact");
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!canAct(event.getEntityPlayer(), PersonalDimPermission.INTERACT)) {
            event.setCanceled(true);
            sendDenyMessage(event.getEntityPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_interact");
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!canAct(event.getEntityPlayer(), PersonalDimPermission.INTERACT)) {
            event.setCanceled(true);
            sendDenyMessage(event.getEntityPlayer(), "chat.ae2enhanced.personal_dimension.no_permission_interact");
        }
    }
}
