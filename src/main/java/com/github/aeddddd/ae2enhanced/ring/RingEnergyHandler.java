package com.github.aeddddd.ae2enhanced.ring;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.omnitool.placement.SecurityTerminalBindingHelper;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import com.github.aeddddd.ae2enhanced.util.ServerTickCounter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 指环能耗管理.
 *
 * <p>消耗顺序：绑定网络的 RF 存储(能量通道)优先 → 内部 2.1G RF 缓存兜底.
 * 生命恢复与伤害阻挡两类消耗受独立的"每秒 RF 上限"节流：
 * 超过上限后效果<b>照常执行但不再扣费</b>(上限是玩家能量存储的保护,不是功能熔断).</p>
 */
public final class RingEnergyHandler {

    private RingEnergyHandler() {}

    public enum Category { HEAL, BLOCK }

    /**
     * 按阶段能耗倍率计价：基础阶段效率低下,随阶段推进递减,飞升恢复标准价.
     */
    public static long price(ItemStack ring, long baseCost) {
        if (baseCost <= 0) return 0;
        double mult;
        if (RingNBT.isAscended(ring)) {
            mult = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.ascendedCostMultiplier;
        } else {
            switch (RingNBT.getTier(ring)) {
                case 0: mult = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.tier1CostMultiplier; break;
                case 1: mult = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.tier2CostMultiplier; break;
                default: mult = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.tier3CostMultiplier; break;
            }
        }
        return Math.max(1L, (long) Math.ceil(baseCost * mult));
    }

    @Nullable
    @SuppressWarnings("rawtypes")
    private static IMEMonitor getMonitor(EntityPlayer player, ItemStack ring) {
        long now = ServerTickCounter.now(player);
        String encryptionKey = SecurityTerminalBindingHelper.getEncryptionKey(ring);
        GridCache cache = GRID_CACHE.get(player.getUniqueID());
        if (cache != null && cache.expiry > now && cache.identity.matches(ring, encryptionKey)) {
            return cache.monitor;
        }
        cache = new GridCache();
        cache.expiry = now + 20;
        cache.identity = new RingGridCacheIdentity(ring, encryptionKey);
        cache.grid = SecurityTerminalBindingHelper.getLinkedGrid(ring, player.world, null);
        if (cache.grid != null) {
            try {
                // 经 EnergyChannelResolver 解析当前生效的能量通道（兼容 Flux_Applied 外部通道）
                IStorageChannel channel = EnergyChannelResolver.getChannel();
                if (channel != null) {
                    IStorageGrid storageGrid = cache.grid.getCache(IStorageGrid.class);
                    if (storageGrid != null) {
                        cache.monitor = storageGrid.getInventory(channel);
                    }
                }
            } catch (Exception ignored) {
                // 通道未注册或网格访问异常,视为无网络能量
            }
        }
        GRID_CACHE.put(player.getUniqueID(), cache);
        return cache.monitor;
    }

    private static final Map<UUID, GridCache> GRID_CACHE = new HashMap<>();
    private static final Map<UUID, long[]> THROTTLE = new HashMap<>(); // [healUsed, blockUsed]
    private static final Map<UUID, Long> SECOND_MARK = new HashMap<>();

    public static void discard(UUID playerId) {
        GRID_CACHE.remove(playerId);
        THROTTLE.remove(playerId);
        SECOND_MARK.remove(playerId);
    }

    // ==================== 网格访问 ====================

    /**
     * 节流扣费(仅 HEAL / BLOCK 类别).
     * 费用不超过每秒上限的部分正常扣除；超过上限的部分免单,效果照常.
     * 能量不足全额时返回 false(效果不执行),此时分文不取.
     */
    public static boolean consumeThrottled(EntityPlayer player, ItemStack ring, long amount, Category category) {
        if (amount <= 0) return true;
        if (available(player, ring, amount) < amount) return false;
        long cap = category == Category.HEAL
                ? com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.healMaxRfPerSecond
                : com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.blockMaxRfPerSecond;
        long charge = Math.min(amount, Math.max(0L, cap - usedThisSecond(player, category)));
        if (charge > 0) {
            addUsed(player, category, charge);
            consume(player, ring, charge);
        }
        return true;
    }

    private static IActionSource source(EntityPlayer player) {
        return SecurityTerminalBindingHelper.createPlayerSource(player);
    }

    // ==================== 可用量与消耗 ====================

    /** 网络中可提取的 RF(模拟). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static long networkAvailable(EntityPlayer player, ItemStack ring, long amount) {
        IMEMonitor monitor = getMonitor(player, ring);
        if (monitor == null) return 0;
        try {
            IAEStack request = EnergyChannelResolver.createStack(amount);
            if (request == null) return 0;
            IAEStack sim = (IAEStack) monitor.extractItems(request, Actionable.SIMULATE, source(player));
            return sim != null ? sim.getStackSize() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static long networkExtract(EntityPlayer player, ItemStack ring, long amount) {
        IMEMonitor monitor = getMonitor(player, ring);
        if (monitor == null) return 0;
        try {
            IAEStack request = EnergyChannelResolver.createStack(amount);
            if (request == null) return 0;
            IAEStack got = (IAEStack) monitor.extractItems(request, Actionable.MODULATE, source(player));
            return got != null ? got.getStackSize() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 当前总可支付量(网络 + 内部缓存,封顶到 amount). */
    public static long available(EntityPlayer player, ItemStack ring, long amount) {
        int internal = RingNBT.getEnergy(ring);
        if (internal >= amount) return amount;
        return internal + networkAvailable(player, ring, amount - internal);
    }

    /**
     * 实际消耗 RF(网络优先,内部缓存兜底).
     *
     * @return 实际消耗量
     */
    public static long consume(EntityPlayer player, ItemStack ring, long amount) {
        if (amount <= 0) return 0;
        long remaining = amount;
        long fromNetwork = networkExtract(player, ring, remaining);
        remaining -= fromNetwork;
        if (remaining > 0) {
            int internal = RingNBT.getEnergy(ring);
            int fromInternal = (int) Math.min(internal, remaining);
            if (fromInternal > 0) {
                RingNBT.setEnergy(ring, internal - fromInternal);
            }
            remaining -= fromInternal;
        }
        return amount - remaining;
    }

    /** 全额支付检查：可支付则消耗并返回 true,否则分文不取返回 false. */
    public static boolean consumeFully(EntityPlayer player, ItemStack ring, long amount) {
        if (amount <= 0) return true;
        if (available(player, ring, amount) < amount) return false;
        return consume(player, ring, amount) >= amount;
    }

    /** 按所属服务器的单调 tick 对齐每秒窗口。 */
    private static void rollSecond(EntityPlayer player) {
        long second = ServerTickCounter.now(player) / 20;
        Long mark = SECOND_MARK.get(player.getUniqueID());
        if (mark == null || mark != second) {
            SECOND_MARK.put(player.getUniqueID(), second);
            THROTTLE.put(player.getUniqueID(), new long[2]);
        }
    }

    private static long usedThisSecond(EntityPlayer player, Category category) {
        rollSecond(player);
        long[] used = THROTTLE.get(player.getUniqueID());
        if (used == null) return 0;
        return category == Category.HEAL ? used[0] : used[1];
    }

    private static void addUsed(EntityPlayer player, Category category, long amount) {
        long[] used = THROTTLE.computeIfAbsent(player.getUniqueID(), k -> new long[2]);
        if (category == Category.HEAL) {
            used[0] += amount;
        } else {
            used[1] += amount;
        }
    }

    /** 从网络向内部缓存回充(每 tick 调用一次). */
    public static void rechargeInternal(EntityPlayer player, ItemStack ring) {
        int rate = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.networkRechargePerTick;
        if (rate <= 0) return;
        int max = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().ring.internalBufferSize;
        int current = RingNBT.getEnergy(ring);
        int space = max - current;
        if (space <= 0) return;
        long pulled = networkExtract(player, ring, Math.min(space, (long) rate));
        if (pulled > 0) {
            RingNBT.setEnergy(ring, (int) Math.min(max, current + pulled));
        }
    }

    /** 网格/监视器缓存,每 20 tick 重建,避免每 tick 全链路查找 */
    @SuppressWarnings("rawtypes")
    private static final class GridCache {
        IGrid grid;
        IMEMonitor monitor;
        RingGridCacheIdentity identity;
        long expiry;
    }
}
