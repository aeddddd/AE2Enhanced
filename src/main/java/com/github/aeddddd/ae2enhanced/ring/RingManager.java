package com.github.aeddddd.ae2enhanced.ring;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
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

    /** 能力快照(参照 PlayerAbilityApplier 的快照/恢复范式,速度读写走反射安全路径) */
    private static final class CapSnapshot {
        final boolean allowFlying;
        final boolean isFlying;
        final float flySpeed;
        final float walkSpeed;

        CapSnapshot(PlayerCapabilities cap) {
            this.allowFlying = cap.allowFlying;
            this.isFlying = cap.isFlying;
            this.flySpeed = PlayerAbilityApplier.getFlySpeedSafe(cap);
            this.walkSpeed = PlayerAbilityApplier.getWalkSpeedSafe(cap);
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
    /** 飞升强制飞行: tick 末观察到的飞行状态(检测外部清除并恢复) */
    private static final Map<UUID, Boolean> PREV_FLYING = new HashMap<>();
    /** 飞升凭证最后持有时间(反 disarm 找回依据) */
    private static final Map<UUID, Long> ASCENDED_LAST_SEEN = new HashMap<>();
    /** 玩家上一 tick 所在维度(Vethea 切换检测) */
    private static final Map<UUID, Integer> LAST_DIMENSION = new HashMap<>();

    public static void discard(UUID playerId) {
        SNAPSHOTS.remove(playerId);
        NOCLIP_APPLIED.remove(playerId);
        LAST_POS.remove(playerId);
        SATURATION_ACTIVE.remove(playerId);
        DEATH_BLOCK_CD.remove(playerId);
        PREV_FLYING.remove(playerId);
        ASCENDED_LAST_SEEN.remove(playerId);
        LAST_DIMENSION.remove(playerId);
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
        tickVetheaConsolidation(player);
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty()) {
            tryRecoverDisarmedCredential(player);
            restoreAll(player);
            return;
        }
        if (RingNBT.isAscended(ring)) {
            ASCENDED_LAST_SEEN.put(player.getUniqueID(), player.world.getTotalWorldTime());
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
        if (Math.abs(PlayerAbilityApplier.getFlySpeedSafe(cap) - targetFly) > 1e-4f) {
            PlayerAbilityApplier.setFlySpeedSafe(cap, targetFly);
            changed = true;
        }
        if (Math.abs(PlayerAbilityApplier.getWalkSpeedSafe(cap) - targetWalk) > 1e-4f) {
            PlayerAbilityApplier.setWalkSpeedSafe(cap, targetWalk);
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
                setFoodLevelSafe(player.getFoodStats(), 20);
                setFoodSaturationSafe(player.getFoodStats(), 5.0f);
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
        if (Math.abs(PlayerAbilityApplier.getFlySpeedSafe(cap) - snapshot.flySpeed) > 1e-4f) {
            PlayerAbilityApplier.setFlySpeedSafe(cap, snapshot.flySpeed);
            changed = true;
        }
        if (Math.abs(PlayerAbilityApplier.getWalkSpeedSafe(cap) - snapshot.walkSpeed) > 1e-4f) {
            PlayerAbilityApplier.setWalkSpeedSafe(cap, snapshot.walkSpeed);
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

    // ==================== 反 disarm 找回(飞升) ====================

    /**
     * 飞升凭证被外部强制移除(如盖亚 III 的 disarm 直清槽位,不走 InventoryPlayer.clear)时,
     * 从玩家附近的掉落物中找回同一个物品实体并吸回背包.
     * 只找回不复制：必须找到真实的掉落物实体才恢复,杜绝物品复制.
     */
    private static void tryRecoverDisarmedCredential(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        Long lastSeen = ASCENDED_LAST_SEEN.get(id);
        if (lastSeen == null) return;
        long now = player.world.getTotalWorldTime();
        if (now - lastSeen > 600) {
            // 脱离持有状态太久(正常丢弃/存入容器),不再追踪
            ASCENDED_LAST_SEEN.remove(id);
            return;
        }
        for (net.minecraft.entity.item.EntityItem item : player.world.getEntitiesWithinAABB(
                net.minecraft.entity.item.EntityItem.class,
                player.getEntityBoundingBox().grow(16.0))) {
            ItemStack stack = item.getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof ItemNetworkLinkCredential
                    && RingNBT.isAscended(stack)) {
                ItemStack taken = item.getItem().copy();
                item.setDead();
                if (!player.inventory.addItemStackToInventory(taken)) {
                    player.dropItem(taken, false);
                }
                ASCENDED_LAST_SEEN.put(id, now);
                AE2Enhanced.LOGGER.info("[AE2E] Recovered ascended NetworkLinkCredential for player {}",
                        player.getName());
                return;
            }
        }
    }

    // ==================== 梦魇世界(Vethea)物品整合(飞升) ====================
    // DivineRPG 进出 Vethea 时将背包/饰品整体序列化到玩家持久 NBT
    // (PlayerPersisted → divinerpg → OverworldInv/VetheaInv/Baubles_Overworld/Baubles_Vethea)
    // 并换装另一侧存档.饰品槽交换不走 InventoryPlayer.clear,clear mixin 管不到,
    // 凭证会被存走导致 Vethea 内失效;而背包侧 clear 保护又会在存档里留下冗余副本.
    // 此处做精确的维度切换整合,不变量: 切换后玩家身上恰好持有原有数量的凭证,
    // 刚保存一侧的存档列表中零冗余副本(杜绝复制).

    /** 维度切换后检查并整合飞升凭证(仅 DivineRPG 存在且涉及 Vethea 时). */
    private static void tickVetheaConsolidation(EntityPlayerMP player) {
        if (!Loader.isModLoaded("divinerpg")) return;
        UUID id = player.getUniqueID();
        int dim = player.dimension;
        Integer prev = LAST_DIMENSION.put(id, dim);
        if (prev == null || prev == dim) return;
        boolean toVethea = isVetheaDimension(dim);
        if (!toVethea && !isVetheaDimension(prev)) return;

        net.minecraft.nbt.NBTTagCompound persisted =
                player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey("divinerpg")) return;
        net.minecraft.nbt.NBTTagCompound divine = persisted.getCompoundTag("divinerpg");

        int held = RingLocator.countAscended(player);
        if (held == 0) {
            // 凭证被换装存进了存档(饰品交换/槽位覆盖),取回一枚还给玩家
            for (String key : new String[]{"OverworldInv", "VetheaInv", "Baubles_Overworld", "Baubles_Vethea"}) {
                ItemStack cred = extractOneCredential(divine, key);
                if (!cred.isEmpty()) {
                    if (!player.inventory.addItemStackToInventory(cred)) {
                        player.dropItem(cred, false);
                    }
                    AE2Enhanced.LOGGER.info(
                            "[AE2E] Recovered ascended credential from Vethea storage ({}) for player {}",
                            key, player.getName());
                    return;
                }
            }
        } else {
            // 玩家通过 clear 保护携带了凭证,刚保存一侧存档中的同物条目是冗余快照,移除防复制
            String[] justSaved = toVethea
                    ? new String[]{"OverworldInv", "Baubles_Overworld"}
                    : new String[]{"VetheaInv", "Baubles_Vethea"};
            int toRemove = held;
            for (String key : justSaved) {
                toRemove -= removeCredentialEntries(divine, key, toRemove);
                if (toRemove <= 0) break;
            }
        }
    }

    private static boolean isVetheaDimension(int dim) {
        try {
            net.minecraft.world.DimensionType type = net.minecraftforge.common.DimensionManager.getProviderType(dim);
            return type != null && type.getName() != null
                    && type.getName().toLowerCase(java.util.Locale.ROOT).contains("vethea");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAscendedCredentialNBT(net.minecraft.nbt.NBTTagCompound entry) {
        return entry != null && !entry.getKeySet().isEmpty()
                && "ae2enhanced:network_link_credential".equals(entry.getString("id"))
                && entry.getCompoundTag("tag").getBoolean(RingNBT.ASCENDED);
    }

    /** 从存档列表取出一枚飞升凭证并移除其条目(饰品列表索引即槽位,置空而非移除). */
    private static ItemStack extractOneCredential(net.minecraft.nbt.NBTTagCompound divine, String key) {
        if (!divine.hasKey(key)) return ItemStack.EMPTY;
        net.minecraft.nbt.NBTTagList list = divine.getTagList(key, 10);
        boolean baubleList = key.startsWith("Baubles_");
        for (int i = 0; i < list.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound entry = list.getCompoundTagAt(i);
            if (isAscendedCredentialNBT(entry)) {
                ItemStack stack = new ItemStack(entry);
                if (baubleList) {
                    list.set(i, new net.minecraft.nbt.NBTTagCompound());
                } else {
                    list.removeTag(i);
                }
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 从存档列表移除至多 max 个飞升凭证条目,返回移除数. */
    private static int removeCredentialEntries(net.minecraft.nbt.NBTTagCompound divine, String key, int max) {
        if (!divine.hasKey(key) || max <= 0) return 0;
        net.minecraft.nbt.NBTTagList list = divine.getTagList(key, 10);
        boolean baubleList = key.startsWith("Baubles_");
        int removed = 0;
        for (int i = list.tagCount() - 1; i >= 0 && removed < max; i--) {
            if (isAscendedCredentialNBT(list.getCompoundTagAt(i))) {
                if (baubleList) {
                    list.set(i, new net.minecraft.nbt.NBTTagCompound());
                } else {
                    list.removeTag(i);
                }
                removed++;
            }
        }
        return removed;
    }

    // ==================== 强制飞行 tick 末恢复(飞升) ====================
    // 其他模组的禁飞(BrokenWings/额外植物学盖亚 III 等)在实体 tick 或 PlayerTickEvent 中
    // 清除 isFlying/allowFlying;ServerTickEvent.END/ClientTickEvent.END 在所有这些之后执行,
    // 最后写入者获胜,实现稳定的强制飞行.

    /** ServerTickEvent.END 调用：恢复被外部清除的服务端飞行状态. */
    public static void tickServerEndFlightRestore(EntityPlayerMP player) {
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty() || !RingNBT.isForceFlightEnabled(ring) || player.isCreative() || player.isSpectator()) {
            PREV_FLYING.remove(player.getUniqueID());
            return;
        }
        PlayerCapabilities cap = player.capabilities;
        boolean wasFlying = Boolean.TRUE.equals(PREV_FLYING.get(player.getUniqueID()));
        boolean changed = false;
        if (!cap.allowFlying) {
            cap.allowFlying = true;
            changed = true;
        }
        // 玩家上一 tick 末处于飞行、本 tick 被外部清除且仍在空中 → 恢复
        if (!cap.isFlying && wasFlying && !player.onGround) {
            cap.isFlying = true;
            player.fallDistance = 0.0f;
            changed = true;
        }
        PREV_FLYING.put(player.getUniqueID(), cap.isFlying);
        if (changed) {
            player.sendPlayerAbilities();
        }
    }

    /** ClientTickEvent.END 调用：客户端镜像恢复(移动由客户端权威计算). */
    public static void tickClientEndFlightRestore(EntityPlayer player) {
        ItemStack ring = RingLocator.findRing(player);
        if (ring.isEmpty() || !RingNBT.isForceFlightEnabled(ring) || player.isCreative() || player.isSpectator()) {
            return;
        }
        PlayerCapabilities cap = player.capabilities;
        boolean wasFlying = Boolean.TRUE.equals(PREV_FLYING.get(player.getUniqueID()));
        if (!cap.allowFlying) {
            cap.allowFlying = true;
        }
        if (!cap.isFlying && wasFlying && !player.onGround) {
            cap.isFlying = true;
            player.fallDistance = 0.0f;
        }
        PREV_FLYING.put(player.getUniqueID(), cap.isFlying);
    }

    // ==================== FoodStats 反射安全写入 ====================
    // 与 PlayerAbilityApplier 相同的兜底策略：MCP 名反射 → SRG 名反射 → 直接写字段,
    // 兼容 Cleanroom/Mohist 等 PlayerCapabilities/FoodStats 方法被剥离或改名的环境.

    private static final java.lang.reflect.Method SET_FOOD_LEVEL;
    private static final java.lang.reflect.Method SET_FOOD_SATURATION;
    private static final java.lang.reflect.Field FOOD_LEVEL_FIELD;
    private static final java.lang.reflect.Field FOOD_SATURATION_FIELD;

    static {
        SET_FOOD_LEVEL = findFoodMethod("setFoodLevel", "func_75114_a", int.class);
        SET_FOOD_SATURATION = findFoodMethod("setFoodSaturationLevel", "func_75119_b", float.class);
        FOOD_LEVEL_FIELD = findFoodField("foodLevel", "field_75127_a");
        FOOD_SATURATION_FIELD = findFoodField("foodSaturationLevel", "field_75125_b");
    }

    private static java.lang.reflect.Method findFoodMethod(String mcp, String srg, Class<?>... params) {
        for (String name : new String[]{mcp, srg}) {
            try {
                java.lang.reflect.Method m = net.minecraft.util.FoodStats.class.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Exception ignored) {
            }
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Could not find FoodStats method {} or {}", mcp, srg);
        return null;
    }

    private static java.lang.reflect.Field findFoodField(String mcp, String srg) {
        for (String name : new String[]{mcp, srg}) {
            try {
                java.lang.reflect.Field f = net.minecraft.util.FoodStats.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Exception ignored) {
            }
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Could not find FoodStats field {} or {}", mcp, srg);
        return null;
    }

    private static void setFoodLevelSafe(net.minecraft.util.FoodStats stats, int level) {
        if (SET_FOOD_LEVEL != null) {
            try {
                SET_FOOD_LEVEL.invoke(stats, level);
                return;
            } catch (Exception ignored) {
            }
        }
        if (FOOD_LEVEL_FIELD != null) {
            try {
                FOOD_LEVEL_FIELD.setInt(stats, level);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to set FoodStats.foodLevel", e);
            }
        }
    }

    private static void setFoodSaturationSafe(net.minecraft.util.FoodStats stats, float saturation) {
        if (SET_FOOD_SATURATION != null) {
            try {
                SET_FOOD_SATURATION.invoke(stats, saturation);
                return;
            } catch (Exception ignored) {
            }
        }
        if (FOOD_SATURATION_FIELD != null) {
            try {
                FOOD_SATURATION_FIELD.setFloat(stats, saturation);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to set FoodStats.foodSaturationLevel", e);
            }
        }
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
