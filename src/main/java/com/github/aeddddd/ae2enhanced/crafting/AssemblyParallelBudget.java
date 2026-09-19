package com.github.aeddddd.ae2enhanced.crafting;

import java.util.*;

/**
 * 装配枢纽跨配方并行预算分配器.
 *
 * <h3>语义</h3>
 * <p>并行升级卡给出的「并行上限」= 枢纽<b>可同时持有的合成份数</b>（在途份数），
 * 一份在途占用 {@code cycleTicks}（速度升级卡决定的结算周期）后释放。
 * 平均吞吐 = 上限 / 周期，与原「批量上限 + 冷却」语义一致；区别在于预算可以被
 * 同一周期内的<b>多个样板共享</b>，而不是被某个样板吃满后整枢纽空等冷却。</p>
 *
 * <h3>并发配方数（跨配方并行模块）</h3>
 * <p>{@code maxSlots} 限制同一周期内<b>同时持有在途占用的样板数量</b>：
 * 未持有槽位的样板只有在 {@code maxSlots} 未占满时才能获得首轮份额，
 * 槽位占满时它们本 tick 直接让位（不会挡住已持有槽位的样板消化剩余额度）。
 * {@code maxSlots = 1} 即退化为原始的「一次只处理一种配方」；
 * {@link Integer#MAX_VALUE} 即不限并发配方数。</p>
 *
 * <h3>分配规则（每 tick 一趟，趟内可多轮）</h3>
 * <ol>
 *   <li>预算 = 并行上限 − 在途份数（无在途即全额，满级并行卡视为不受限）。</li>
 *   <li>本 tick 尚未拿到份额、且能拿到槽位的样板：份额 = max(1, 预算 / 待分配数)，
 *       保证多个配方同时推进（跨配方并行），份额之和不超过预算。</li>
 *   <li>已拿到份额的样板：仅在「没有样板还在等首轮份额」时消化剩余额度 ——
 *       小样板用不完的额度回流给仍有需求的大样板（完全利用并行）。</li>
 *   <li>额度实际消耗以结算份数为准（材料/能量不足时自动少占），预算随实际占用递减，
 *       因此剩余额度会在同一趟内继续回流。</li>
 * </ol>
 *
 * <p>公平性：份额按竞争者数量均分，且「本 tick 已获得份额」的样板让位于未获得者，
 * 因此当并行上限/并发槽位小于待服务样板数时，超出的样板不会永久饥饿（调用方按 tick
 * 轮转迭代顺序，逐轮覆盖全部样板）。</p>
 *
 * <p>纯逻辑、无 MC 依赖，可单测。</p>
 */
public final class AssemblyParallelBudget {

    /** 「无限并行」判定阈值：上限达到该值即视为不受在途约束（对齐并行卡满级 Long.MAX_VALUE）. */
    private static final long INFINITE_THRESHOLD = Long.MAX_VALUE / 2;
    private final IdentityHashMap<Object, Claim> claims = new IdentityHashMap<>();
    /** 本 tick 已获得过份额的样板（set 语义）. */
    private final Set<Object> offeredThisTick = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    /** 本趟该样板的额度（趟内缓存，避免同一趟重复计算/重复扣减待分配数）. */
    private final IdentityHashMap<Object, Long> passAllowance = new IdentityHashMap<>();
    /** 在途总份数（所有样板之和）. */
    private long inFlight;
    private long offeredTick = Long.MIN_VALUE;
    private long passNow;
    private long passCycleTicks = 1L;
    private long passBudget;
    private int passFresh;
    private int passMaxSlots = Integer.MAX_VALUE;

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }

    /**
     * 开始一趟分配.
     *
     * @param now        当前世界时间（tick）
     * @param cap        并行上限
     * @param cycleTicks 单次结算的占用周期（速度升级卡）
     * @param maxSlots   同时持有在途占用的样板数上限（跨配方并行模块；{@code <= 0} 视为不限）
     * @param pending    本趟待服务的样板集合（用于统计待分配竞争者数量）
     */
    public void beginPass(long now, long cap, long cycleTicks, int maxSlots, Collection<?> pending) {
        sweep(now);
        if (now != offeredTick) {
            offeredTick = now;
            offeredThisTick.clear();
        }
        this.passNow = now;
        this.passCycleTicks = Math.max(1L, cycleTicks);
        this.passMaxSlots = maxSlots <= 0 ? Integer.MAX_VALUE : maxSlots;
        this.passBudget = budgetFor(cap);

        // 待分配数 = 尚未获得份额的样板数，但受可激活槽位约束：
        // 空槽位 + 已持槽位且尚未获得份额的样板（后者不需要新槽位）
        int notOffered = 0;
        int activeAmongPending = 0;
        if (pending != null) {
            for (Object pattern : pending) {
                if (offeredThisTick.contains(pattern)) {
                    continue;
                }
                notOffered++;
                if (claims.containsKey(pattern)) {
                    activeAmongPending++;
                }
            }
        }
        int freeSlots = Math.max(0, passMaxSlots - claims.size());
        this.passFresh = Math.min(notOffered, freeSlots + activeAmongPending);
        this.passAllowance.clear();
    }

    /**
     * 本趟该样板可结算的最大份数.
     *
     * @return 0 表示本趟不应结算（预算耗尽 / 无并发槽位 / 仍需让位于未获得首轮份额的样板）
     */
    public long allowance(Object pattern) {
        Long cached = passAllowance.get(pattern);
        if (cached != null) {
            return cached;
        }
        long allow = 0L;
        if (passBudget > 0L) {
            if (offeredThisTick.contains(pattern)) {
                // 本 tick 已有份额：无人再等首轮份额时才吃剩余额度（回流给仍有需求的样板）
                if (passFresh <= 0) {
                    allow = Math.max(1L, passBudget);
                }
            } else if (claims.containsKey(pattern) || claims.size() < passMaxSlots) {
                long fresh = Math.max(1, passFresh);
                allow = Math.max(1L, passBudget / fresh);
                offeredThisTick.add(pattern);
                passFresh = Math.max(0, passFresh - 1);
            } else {
                // 并发槽位已满且该样板未持有槽位：本 tick 让位（标记已轮询,避免挡住他人回流）
                offeredThisTick.add(pattern);
            }
        }
        passAllowance.put(pattern, allow);
        return allow;
    }

    /**
     * 记录实际结算份数（结算成功后调用；{@code ops <= 0} 视为无占用）.
     *
     * <p>在途份数按实际结算量累加，并在 {@code cycleTicks} 后整体释放，因此
     * 长期吞吐恒定为 上限 / 周期，与「批量 + 冷却」的节奏等价。</p>
     */
    public void claim(Object pattern, long ops) {
        if (pattern == null || ops <= 0L) {
            return;
        }
        sweep(passNow);
        Claim existing = claims.get(pattern);
        long base = existing == null ? 0L : existing.ops;
        Claim claim = new Claim();
        claim.ops = saturatedAdd(base, ops);
        claim.expiresAt = passNow + passCycleTicks;
        claims.put(pattern, claim);
        inFlight = saturatedAdd(inFlight, ops);
        offeredThisTick.add(pattern);
        passBudget = Math.max(0L, passBudget - ops);
    }

    /** 回收已到期的在途占用. */
    public void sweep(long now) {
        if (claims.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Object, Claim>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            Claim claim = it.next().getValue();
            if (claim == null || claim.expiresAt <= now) {
                if (claim != null) {
                    inFlight = Math.max(0L, inFlight - claim.ops);
                }
                it.remove();
            }
        }
    }

    /** 当前在途份数. */
    public long inFlightOps() {
        return inFlight;
    }

    /** 当前持有并发槽位的样板数. */
    public int activePatterns() {
        return claims.size();
    }

    /** 清空全部状态（结构拆除 / 控制器卸载）. */
    public void clear() {
        claims.clear();
        inFlight = 0L;
        offeredThisTick.clear();
        offeredTick = Long.MIN_VALUE;
        passAllowance.clear();
        passBudget = 0L;
        passFresh = 0;
        passMaxSlots = Integer.MAX_VALUE;
    }

    private long budgetFor(long cap) {
        if (cap <= 0L) {
            return 0L;
        }
        if (cap >= INFINITE_THRESHOLD) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, cap - inFlight);
    }

    /** 单样板的在途占用. */
    private static final class Claim {
        long ops;
        long expiresAt;
    }
}
