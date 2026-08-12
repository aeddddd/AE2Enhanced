package com.github.aeddddd.ae2enhanced.ring;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 指环玩家 tick 管理器.
 *
 * <p>服务端职责：飞行/速度能力快照与恢复、穿墙、触及距离、全身供能、
 * 药水移除、永久饱食、自动回血、内部缓存回充、异常位移回滚.
 * 客户端职责：穿墙 noClip 镜像(夜视由 Mixin 直接读 NBT,无需客户端状态).</p>
 */
public final class RingManager {

    private RingManager() {}

    private static final UUID REACH_MODIFIER_UUID = UUID.fromString("a3e9c1d2-7b4f-4e6a-9c8d-1f2a3b4c5d6e");
    private static final String REACH_MODIFIER_NAME = "ae2enhanced:network_link_ring_reach";

    /** 能力快照(参照 PlayerAbilityApplier 的快照/恢复范式) */
    private static final class CapSnapshot {
        final boolean allowFlying;
        final boolean isFlying;
        final float flySpeed;
        final float walkSpeed;

        CapSnapshot(PlayerCapabilities cap) {
            this.allowFlying = cap.allowFlying;
            this.isFlying = cap.isFlying;
            this.flySpeed = cap.getFlySpeed();
            this.walkSpeed = cap.getWalkSpeed();
        }
    }

    private static final Map<UUID, CapSnapshot> SNAPSHOTS = new HashMap<>();
    /** 由指环施加的 noClip 标记(避免误清观察者模式自身的 noClip) */
    private static final Map<UUID, Boolean> NOCLIP_APPLIED = new HashMap<>();
    /** 上一 tick 位置(飞升异常位移回滚用) */
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    /** 飞升指环的饱食功能能量状态(供 exhaustion mixin 快速判定) */
    private static final Map<UUID, Boolean> SATURATION_ACTIVE = new HashMap<>();
    /** III 阶段免死冷却到期时间(世界时间) */
    private static final Map<UUID, Long> DEATH_BLOCK_CD = new HashMap<>();

    public static void discard(UUID playerId) {
        SNAPSHOTS.remove(playerId);
        NOCLIP_APPLIED.remove(playerId);
        LAST_POS.remove(playerId);
        SATURATION_ACTIVE.remove(playerId);
        DEATH_BLOCK_CD.remove(playerId);
        RingEnergyHandler.discard(playerId);
        RingProtection.discard(playerId);
    }

    /** 重生后清空快照,下一 tick 重新快照并应用. */
    public static void onRespawn(EntityPlayer player) {
        SNAPSHOTS.remove(player.getUniqueID());
        NOCLIP_APPLIED.remove(player.getUniqueID());
        LAST_POS.remove(player.getUniqueID());
    }

    public static boolean isSaturationActive(EntityPlayer player) {
        return Boolean.TRUE.equals(SATURATION_ACTIVE.get(player.getUniqueID()));
    }

    // ==================== III 阶段免死冷却 ====================

    public static boolean isDeathBlockOnCooldown(EntityPlayer player) {
        Long expiry = DEATH_BLOCK_CD.get(player.getUniqueID());
        return expiry != null && expiry > player.world.getTotalWorldTime();
    }

    public static void markDeathBlock(EntityPlayer player) {
        int cd = AE2EnhancedConfig.ring.deathBlockCooldownTicks;
        if (cd > 0) {
            DEATH_BLOCK_CD.put(player.getUniqueID(), player.world.getTotalWorldTime() + cd);
        }
    }

    // ==================== 服务端 tick ====================

    public static void tickServer(EntityPlayerMP player) {
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty()) {
            restoreAll(player);
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            // 创造/观察者拥有自身能力体系,指环仅保留防护/回血/供能等非能力功能
            tickUtilities(player, ring);
            return;
        }

        SNAPSHOTS.computeIfAbsent(player.getUniqueID(), id -> new CapSnapshot(player.capabilities));
        PlayerCapabilities cap = player.capabilities;
        boolean changed = false;

        // ---- 飞行(阶段 II 起) ----
        boolean canFly = RingNBT.tierAtLeast(ring, 1);
        boolean forceFlight = canFly && RingNBT.isForceFlightEnabled(ring)
                && RingEnergyHandler.consumeFully(player, ring,
                RingEnergyHandler.price(ring, AE2EnhancedConfig.ring.forceFlightCostPerTick));
        boolean normalFlight = canFly && !forceFlight && RingNBT.isFlightEnabled(ring)
                && RingEnergyHandler.consumeFully(player, ring,
                RingEnergyHandler.price(ring, AE2EnhancedConfig.ring.flightCostPerTick));
        boolean wantFly = forceFlight || normalFlight;
        CapSnapshot snapshot = SNAPSHOTS.get(player.getUniqueID());
        if (cap.allowFlying != wantFly && !( !wantFly && snapshot.allowFlying)) {
            // 关闭飞行时恢复快照值(可能由其他模组授予)
            boolean target = wantFly || snapshot.allowFlying;
            if (cap.allowFlying != target) {
                cap.allowFlying = target;
                if (!target) {
                    cap.isFlying = false;
                    player.fallDistance = 0.0f;
                }
                changed = true;
            }
        }

        // ---- 飞行/行走速度 ----
        int maxPct = AE2EnhancedConfig.ring.maxSpeedPercent;
        float flyBase = 0.05f;
        float walkBase = 0.1f;
        float maxFly = flyBase * maxPct / 100f;
        float maxWalk = walkBase * maxPct / 100f;
        float targetFly = wantFly ? clamp(RingNBT.getFlySpeed(ring), flyBase, maxFly) : snapshot.flySpeed;
        // 行走速度仅在玩家显式开启调整开关后接管,否则恢复快照(保留其他模组的速度来源)
        float targetWalk = RingNBT.isWalkTweakEnabled(ring)
                ? clamp(RingNBT.getWalkSpeed(ring), 0.05f, maxWalk)
                : snapshot.walkSpeed;
        if (Math.abs(cap.getFlySpeed() - targetFly) > 1e-4f) {
            cap.setFlySpeed(targetFly);
            changed = true;
        }
        if (Math.abs(cap.getWalkSpeed() - targetWalk) > 1e-4f) {
            cap.setPlayerWalkSpeed(targetWalk);
            changed = true;
        }

        if (changed) {
            player.sendPlayerAbilities();
        }

        // ---- 穿墙 ----
        tickWallPhaseServer(player, ring);

        // ---- 触及距离 ----
        tickReach(player, ring);

        // ---- 飞升专属 ----
        if (RingNBT.isAscended(ring)) {
            // 永久饱食
            boolean saturation = RingEnergyHandler.consumeFully(player, ring,
                    RingEnergyHandler.price(ring, AE2EnhancedConfig.ring.saturationCostPerTick));
            SATURATION_ACTIVE.put(player.getUniqueID(), saturation);
            if (saturation) {
                player.getFoodStats().setFoodLevel(20);
                player.getFoodStats().setFoodSaturationLevel(5.0f);
            }
            // 异常位移回滚
            tickDisplacementGuard(player);
        } else {
            SATURATION_ACTIVE.put(player.getUniqueID(), false);
        }

        tickUtilities(player, ring);
    }

    /** 与能力无关的通用功能(创造模式同样生效). */
    private static void tickUtilities(EntityPlayerMP player, ItemStack ring) {
        // ---- 全身供能(阶段 I 起) ----
        if (RingNBT.isFeedEnabled(ring)) {
            tickFeedItems(player, ring);
        }

        // ---- 自动回血(阶段 II 起) ----
        // 药水移除已由 addPotionEffect mixin 在注入前拦截(RingProtection.isPotionSuppressed)
        if (RingNBT.isAutoHealEnabled(ring) && RingNBT.tierAtLeast(ring, 1) && player.getHealth() > 0.0f) {
            float threshold = player.getMaxHealth() * RingNBT.getHealThreshold(ring) / 100.0f;
            float missing = player.getHealth() < threshold ? threshold - player.getHealth() : 0.0f;
            if (missing > 0.0f) {
                float healAmount = Math.min(missing, AE2EnhancedConfig.ring.healMaxPerTick);
                healWithEnergy(player, ring, healAmount);
            }
        }

        // ---- 内部缓存回充 ----
        RingEnergyHandler.rechargeInternal(player, ring);
    }

    /** 按可支付量回血(HEAL 类别节流,按阶段倍率计价). */
    public static void healWithEnergy(EntityPlayer player, ItemStack ring, float amount) {
        if (!RingNBT.tierAtLeast(ring, 1)) return;
        long costPerPoint = RingEnergyHandler.price(ring, AE2EnhancedConfig.ring.healCostPerPoint);
        long affordable = RingEnergyHandler.available(player, ring, Long.MAX_VALUE) / costPerPoint;
        float actual = (float) Math.min(amount, Math.min(affordable, player.getMaxHealth() - player.getHealth()));
        if (actual <= 0.0f) return;
        long cost = (long) Math.ceil(actual * (double) costPerPoint);
        if (RingEnergyHandler.consumeThrottled(player, ring, cost, RingEnergyHandler.Category.HEAL)) {
            player.heal(actual);
        }
    }

    /** 瞬间完全恢复(手动按键,阶段 II 起). */
    public static void healToFull(EntityPlayer player) {
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty() || !RingNBT.tierAtLeast(ring, 1)) return;
        float missing = player.getMaxHealth() - player.getHealth();
        if (missing > 0.0f) {
            healWithEnergy(player, ring, missing);
        }
    }

    // ==================== 穿墙 ====================

    private static void tickWallPhaseServer(EntityPlayerMP player, ItemStack ring) {
        UUID id = player.getUniqueID();
        boolean active = RingNBT.isWallPhaseEnabled(ring) && RingNBT.tierAtLeast(ring, 2)
                && RingEnergyHandler.consumeFully(player, ring,
                RingEnergyHandler.price(ring, AE2EnhancedConfig.ring.wallPhaseCostPerTick));
        if (active) {
            player.noClip = true;
            NOCLIP_APPLIED.put(id, true);
        } else if (Boolean.TRUE.equals(NOCLIP_APPLIED.get(id))) {
            player.noClip = false;
            NOCLIP_APPLIED.put(id, false);
            rescueIfInsideBlock(player);
        }
    }

    /** 关闭穿墙时若卡在方块内,向上寻找最近空气格自救. */
    private static void rescueIfInsideBlock(EntityPlayerMP player) {
        World world = player.world;
        BlockPos pos = player.getPosition();
        if (!world.getBlockState(pos).getMaterial().isSolid()) return;
        for (int y = pos.getY(); y < world.getHeight(); y++) {
            BlockPos test = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.getBlockState(test).getMaterial().isSolid()
                    && !world.getBlockState(test.up()).getMaterial().isSolid()) {
                RingProtection.allowTeleport(player.getUniqueID(), world.getTotalWorldTime() + 5);
                player.setPositionAndUpdate(pos.getX() + 0.5, y, pos.getZ() + 0.5);
                player.fallDistance = 0.0f;
                return;
            }
        }
    }

    // ==================== 触及距离 ====================

    private static void tickReach(EntityPlayerMP player, ItemStack ring) {
        IAttributeInstance attr = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE);
        if (attr == null) return;
        AttributeModifier existing = attr.getModifier(REACH_MODIFIER_UUID);
        float maxReach = (float) AE2EnhancedConfig.ring.maxReachDistance;
        float target = clamp(RingNBT.getReach(ring), 5.0f, maxReach);

        // 与先进 ME 工具(手持时)的触及距离取最大,不叠加
        float omniBonus = 0.0f;
        ItemStack held = player.getHeldItemMainhand();
        if (held.getItem() instanceof ItemAdvancedMEOmniTool
                && ItemAdvancedMEOmniTool.getMode(held) == ItemAdvancedMEOmniTool.MODE_PLACEMENT) {
            float omniReach = new PlacementConfig(held).getReachDistance();
            omniBonus = Math.max(0.0f, omniReach - 5.0f);
        }
        double amount = Math.max(0.0f, target - 5.0f - omniBonus);

        if (amount <= 0.0) {
            if (existing != null) {
                attr.removeModifier(REACH_MODIFIER_UUID);
            }
            return;
        }
        if (existing == null || existing.getAmount() != amount) {
            if (existing != null) {
                attr.removeModifier(REACH_MODIFIER_UUID);
            }
            attr.applyModifier(new AttributeModifier(REACH_MODIFIER_UUID, REACH_MODIFIER_NAME, amount, 0));
        }
    }

    private static void removeReachModifier(EntityPlayer player) {
        IAttributeInstance attr = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE);
        if (attr != null && attr.getModifier(REACH_MODIFIER_UUID) != null) {
            attr.removeModifier(REACH_MODIFIER_UUID);
        }
    }

    // ==================== 全身供能 ====================

    private static void tickFeedItems(EntityPlayerMP player, ItemStack ring) {
        int mode = RingNBT.getFeedMode(ring);
        List<ItemStack> targets = new ArrayList<>();
        targets.add(player.getHeldItemMainhand());
        targets.add(player.getHeldItemOffhand());
        for (ItemStack armor : player.inventory.armorInventory) {
            targets.add(armor);
        }
        if (mode == 0) {
            for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
                targets.add(player.inventory.mainInventory.get(i));
            }
        }
        collectBaubleStacks(player, targets);

        for (ItemStack stack : targets) {
            if (stack.isEmpty() || stack.getItem() instanceof ItemNetworkLinkCredential) continue;
            IEnergyStorage cap = stack.getCapability(CapabilityEnergy.ENERGY, null);
            if (cap == null || !cap.canReceive()) continue;
            int demand = cap.receiveEnergy(Integer.MAX_VALUE, true);
            if (demand <= 0) continue;
            // 供能按阶段倍率计价：低阶段充能效率低下(充 1 RF 扣 mult RF)
            double mult = (double) RingEnergyHandler.price(ring, 1000) / 1000.0;
            long budget = RingEnergyHandler.available(player, ring, demand);
            if (budget <= 0) return; // 能量耗尽,停止后续供能
            int actual = cap.receiveEnergy((int) Math.min(demand, budget), false);
            if (actual > 0) {
                RingEnergyHandler.consume(player, ring, (long) Math.ceil(actual * mult));
            }
        }
    }

    private static void collectBaubleStacks(EntityPlayer player, List<ItemStack> out) {
        if (!Loader.isModLoaded("baubles")) return;
        try {
            Object handler = Class.forName("baubles.api.BaublesApi")
                    .getMethod("getBaublesHandler", EntityPlayer.class)
                    .invoke(null, player);
            int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
            for (int i = 0; i < slots; i++) {
                ItemStack stack = (ItemStack) handler.getClass()
                        .getMethod("getStackInSlot", int.class).invoke(handler, i);
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to scan Baubles for ring feed", e);
        }
    }

    // ==================== 飞升异常位移回滚 ====================

    private static void tickDisplacementGuard(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        double[] last = LAST_POS.get(id);
        double[] now = {player.posX, player.posY, player.posZ};
        LAST_POS.put(id, now);
        if (last == null) return;
        if (RingProtection.isTeleportAllowed(player)) return;
        if (player.capabilities.isFlying) return; // 高速飞行产生的位移不视为异常
        double dx = now[0] - last[0];
        double dy = now[1] - last[1];
        double dz = now[2] - last[2];
        if (dx * dx + dy * dy + dz * dz > 9.0) { // 单 tick 位移超过 3 格(非自愿)
            RingProtection.allowTeleport(id, player.world.getTotalWorldTime() + 2);
            player.setPositionAndUpdate(last[0], last[1], last[2]);
            player.motionX = 0.0;
            player.motionY = 0.0;
            player.motionZ = 0.0;
            LAST_POS.put(id, new double[]{last[0], last[1], last[2]});
        }
    }

    // ==================== 恢复 ====================

    /** 指环离身：恢复能力快照、移除触及修正、清理 noClip. */
    private static void restoreAll(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        SATURATION_ACTIVE.remove(id);
        LAST_POS.remove(id);

        if (Boolean.TRUE.equals(NOCLIP_APPLIED.remove(id))) {
            player.noClip = false;
            rescueIfInsideBlock(player);
        }
        removeReachModifier(player);

        CapSnapshot snapshot = SNAPSHOTS.remove(id);
        if (snapshot == null || player.isCreative() || player.isSpectator()) return;
        PlayerCapabilities cap = player.capabilities;
        boolean changed = false;
        if (cap.allowFlying != snapshot.allowFlying) {
            cap.allowFlying = snapshot.allowFlying;
            if (!snapshot.allowFlying) {
                cap.isFlying = false;
                player.fallDistance = 0.0f;
            }
            changed = true;
        }
        if (Math.abs(cap.getFlySpeed() - snapshot.flySpeed) > 1e-4f) {
            cap.setFlySpeed(snapshot.flySpeed);
            changed = true;
        }
        if (Math.abs(cap.getWalkSpeed() - snapshot.walkSpeed) > 1e-4f) {
            cap.setPlayerWalkSpeed(snapshot.walkSpeed);
            changed = true;
        }
        if (changed) {
            player.sendPlayerAbilities();
        }
    }

    // ==================== 客户端 tick ====================

    /** 客户端 noClip 镜像标记(独立 JVM 侧状态,避免与服务器侧同 UUID 冲突) */
    private static final Map<UUID, Boolean> CLIENT_NOCLIP = new HashMap<>();

    public static void tickClient(EntityPlayer player) {
        ItemStack ring = RingLocator.findRing(player);
        UUID id = player.getUniqueID();
        if (ring.isEmpty()) {
            if (Boolean.TRUE.equals(CLIENT_NOCLIP.remove(id))) {
                player.noClip = false;
            }
            return;
        }
        // 穿墙 noClip 客户端镜像(能量判定以服务端为准,客户端仅按开关镜像,阶段 III 起)
        if (RingNBT.isWallPhaseEnabled(ring) && RingNBT.tierAtLeast(ring, 2)) {
            player.noClip = true;
            CLIENT_NOCLIP.put(id, true);
        } else if (Boolean.TRUE.equals(CLIENT_NOCLIP.get(id))) {
            player.noClip = false;
            CLIENT_NOCLIP.put(id, false);
        }
        // 飞行惯性取消(阶段 II 起,移动由客户端权威计算,必须在客户端清零)
        if (RingNBT.isNoInertiaEnabled(ring) && RingNBT.tierAtLeast(ring, 1)
                && player.capabilities.isFlying
                && player.moveForward == 0.0f && player.moveStrafing == 0.0f) {
            player.motionX = 0.0;
            player.motionZ = 0.0;
        }
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
