package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * AABB 实体扫描的 tick 级节流缓存.
 *
 * <p>中枢接口的 {@code isIdle} 判定每 tick 都要扫一遍目标方块的 AABB 实体列表
 * （Botania 魔力池 / 大地台 / 花药台，TC6 坩埚），而实体列表的实际变化速度远低于 tick 速度。
 * 本工具按 {@code (维度, 坐标, 实体类型, 扫描范围)} 缓存上一次真实扫描的结果，
 * 在 {@link #DEFAULT_INTERVAL_TICKS} 个 tick 内直接复用，把扫描频次降到 1/N。</p>
 *
 * <p><b>语义影响（可观测）</b>：被节流的调用点会引入最多 {@code intervalTicks - 1}
 * 个 tick 的<b>判定延迟</b>（默认 3 tick = 150ms）：新落入扫描范围的实体会晚最多 3 tick 被看见，
 * 因而"材料已被机器处理完"的判断最多晚 3 tick，产物收集相应延后。
 * 已死实体与已移出范围的实体不受影响 —— 前者由调用方自身的 {@code isDead} 过滤，
 * 后者由调用方在判定时读取实体的<b>实时坐标</b>（而非缓存时的坐标）过滤。</p>
 *
 * <p><b>使用约束</b>：</p>
 * <ul>
 *   <li>只用于"容错读"路径（{@code isIdle} 判定）；决定物品增减的路径
 *       （推料 / 收集产物 / 回退材料）必须继续使用实时扫描。</li>
 *   <li>返回的列表是缓存本体，调用方<b>不得修改列表结构</b>（允许修改列表内的实体，如 {@code setDead}）。</li>
 * </ul>
 */
public final class EntityScanThrottle {

    /**
     * 默认真实扫描间隔（tick）.
     *
     * <p>调大 = 更省 CPU、判定延迟更大；调小 = 更及时、扫描更频繁。
     * 与 Botania 推料后的 grace 期（4 tick 起）同数量级，因此同一发配批次的首次 {@code isIdle}
     * 必然拿到实时扫描结果。</p>
     */
    public static final int DEFAULT_INTERVAL_TICKS = 4;

    /** 缓存条目数上限；达到上限时清理长期未访问的条目，避免卸载区块后的坐标永久驻留。 */
    private static final int MAX_ENTRIES = 512;

    /** 清理阈值：超过该 tick 数未被访问的条目会被丢弃。 */
    private static final long STALE_TICKS = 400;

    private static final Map<ScanKey, CacheEntry<?>> CACHE = new HashMap<>();

    private EntityScanThrottle() {
    }

    /**
     * 按 {@link #DEFAULT_INTERVAL_TICKS} 节流地扫描指定范围内的实体.
     *
     * @param world 目标世界
     * @param pos   目标方块坐标（缓存键的一部分）
     * @param type  实体类型
     * @param box   扫描范围
     * @return 缓存或新扫描得到的实体列表；参数非法时返回空列表
     */
    public static <T extends Entity> List<T> scan(World world, BlockPos pos, Class<T> type, AxisAlignedBB box) {
        return scan(world, pos, type, box, DEFAULT_INTERVAL_TICKS);
    }

    /**
     * 按指定间隔节流地扫描指定范围内的实体.
     *
     * @param intervalTicks 真实扫描间隔（tick），{@code <= 0} 表示不节流（等价于实时扫描）
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> List<T> scan(World world, BlockPos pos, Class<T> type, AxisAlignedBB box, int intervalTicks) {
        if (world == null || pos == null || type == null || box == null) {
            return Collections.emptyList();
        }
        long now = world.getTotalWorldTime();
        ScanKey key = new ScanKey(world.provider.getDimension(), pos.toLong(), type, box);
        CacheEntry<T> entry = (CacheEntry<T>) CACHE.get(key);

        // now < entry.tick 说明世界时间被回退（同一 JVM 内重载存档），此时旧结果一律作废
        if (intervalTicks > 0 && entry != null && entry.tick <= now && now - entry.tick < intervalTicks) {
            entry.lastUse = now;
            return entry.result;
        }

        List<T> result = new ArrayList<>(world.getEntitiesWithinAABB(type, box));
        if (entry == null) {
            if (CACHE.size() >= MAX_ENTRIES) {
                cleanup(now);
            }
            entry = new CacheEntry<>();
            CACHE.put(key, entry);
        }
        entry.tick = now;
        entry.lastUse = now;
        entry.result = result;
        return result;
    }

    /** 丢弃长期未访问的条目（仅在缓存达到上限时触发，保证内存上界）。 */
    private static void cleanup(long now) {
        Iterator<CacheEntry<?>> it = CACHE.values().iterator();
        while (it.hasNext()) {
            CacheEntry<?> entry = it.next();
            if (now - entry.lastUse > STALE_TICKS) {
                it.remove();
            }
        }
    }

    private static final class CacheEntry<T extends Entity> {
        long tick = Long.MIN_VALUE;
        long lastUse = Long.MIN_VALUE;
        List<T> result = Collections.emptyList();
    }

    /**
     * 扫描缓存键：同一坐标的缓存仅在实体类型与扫描范围都一致时才可复用.
     */
    private static final class ScanKey {

        private final int dimension;
        private final long pos;
        private final Class<?> type;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        ScanKey(int dimension, long pos, Class<?> type, AxisAlignedBB box) {
            this.dimension = dimension;
            this.pos = pos;
            this.type = type;
            this.minX = box.minX;
            this.minY = box.minY;
            this.minZ = box.minZ;
            this.maxX = box.maxX;
            this.maxY = box.maxY;
            this.maxZ = box.maxZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ScanKey)) {
                return false;
            }
            ScanKey that = (ScanKey) o;
            return dimension == that.dimension
                    && pos == that.pos
                    && type == that.type
                    && Double.compare(minX, that.minX) == 0
                    && Double.compare(minY, that.minY) == 0
                    && Double.compare(minZ, that.minZ) == 0
                    && Double.compare(maxX, that.maxX) == 0
                    && Double.compare(maxY, that.maxY) == 0
                    && Double.compare(maxZ, that.maxZ) == 0;
        }

        @Override
        public int hashCode() {
            int result = 31 * dimension + (int) (pos ^ (pos >>> 32));
            result = 31 * result + type.hashCode();
            result = 31 * result + Double.hashCode(minX);
            result = 31 * result + Double.hashCode(minY);
            result = 31 * result + Double.hashCode(minZ);
            result = 31 * result + Double.hashCode(maxX);
            result = 31 * result + Double.hashCode(maxY);
            result = 31 * result + Double.hashCode(maxZ);
            return result;
        }
    }
}
