package com.github.aeddddd.ae2enhanced.display;

import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 三档降采样环形缓冲.
 *
 * <p>tier0: 1s 粒度 ×300 (5 分钟)</p>
 * <p>tier1: 10s 粒度 ×330 (55 分钟)</p>
 * <p>tier2: 60s 粒度 ×1440 (24 小时)</p>
 *
 * <p>无效采样(断网/卸载期间)以 valid=false 记录,渲染为断线.</p>
 * 服务端与客户端共用本类,通过 NBT 全量同步 + 增量包逐点追加保持一致.
 */
public class TrendBuffer {

    public static final int[] TIER_CAPACITY = {300, 330, 1440};
    public static final int[] TIER_INTERVAL = {1, 10, 60};

    private static final int TIERS = 3;

    private final long[][] values = new long[TIERS][];
    private final byte[][] valid = new byte[TIERS][];
    private final int[] head = new int[TIERS];
    private final int[] size = new int[TIERS];
    private final long[] total = new long[TIERS];

    public TrendBuffer() {
        for (int t = 0; t < TIERS; t++) {
            values[t] = new long[TIER_CAPACITY[t]];
            valid[t] = new byte[TIER_CAPACITY[t]];
        }
    }

    /** 追加一个采样点(1s 粒度). 每 10 点降采样到 tier1,每 60 点到 tier2. */
    public void push(long value, boolean isValid) {
        pushTier(0, value, isValid);
        if (total[0] % 10 == 0) {
            pushTier(1, value, isValid);
        }
        if (total[0] % 60 == 0) {
            pushTier(2, value, isValid);
        }
    }

    private void pushTier(int tier, long value, boolean isValid) {
        int cap = TIER_CAPACITY[tier];
        values[tier][head[tier]] = value;
        valid[tier][head[tier]] = (byte) (isValid ? 1 : 0);
        head[tier] = (head[tier] + 1) % cap;
        if (size[tier] < cap) {
            size[tier]++;
        }
        total[tier]++;
    }

    public void clear() {
        for (int t = 0; t < TIERS; t++) {
            head[t] = 0;
            size[t] = 0;
            total[t] = 0;
        }
    }

    /** 为给定时间范围选择最合适的 tier:数据覆盖范围 >= 请求范围的最低粒度档. */
    public static int selectTier(int rangeSeconds) {
        for (int t = 0; t < TIERS; t++) {
            if (TIER_CAPACITY[t] * TIER_INTERVAL[t] >= rangeSeconds) {
                return t;
            }
        }
        return TIERS - 1;
    }

    public int getSize(int tier) {
        return size[tier];
    }

    /** 取某 tier 从最新往回数第 n 个采样(0 = 最新). 调用方保证 n < size. */
    public long getValue(int tier, int n) {
        int cap = TIER_CAPACITY[tier];
        return values[tier][Math.floorMod(head[tier] - 1 - n, cap)];
    }

    public boolean isValid(int tier, int n) {
        int cap = TIER_CAPACITY[tier];
        return valid[tier][Math.floorMod(head[tier] - 1 - n, cap)] != 0;
    }

    // ---- NBT ----

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        for (int t = 0; t < TIERS; t++) {
            // 按时间顺序(旧→新)写出,读取端顺序回填
            int s = size[t];
            // long[] 打包为 byte[](stable_39 下 NBTTagLongArray.data 为 private)
            byte[] vals = new byte[s * 8];
            byte[] oks = new byte[s];
            int cap = TIER_CAPACITY[t];
            for (int i = 0; i < s; i++) {
                // i=0 为最旧
                int idx = Math.floorMod(head[t] - s + i, cap);
                long v = values[t][idx];
                for (int b = 0; b < 8; b++) {
                    vals[i * 8 + b] = (byte) (v >>> (56 - b * 8));
                }
                oks[i] = valid[t][idx];
            }
            tag.setTag("v" + t, new NBTTagByteArray(vals));
            tag.setTag("k" + t, new NBTTagByteArray(oks));
            tag.setLong("total" + t, total[t]);
        }
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        clear();
        for (int t = 0; t < TIERS; t++) {
            byte[] vals = tag.getByteArray("v" + t);
            byte[] oks = tag.getByteArray("k" + t);
            int s = Math.min(vals.length / 8, TIER_CAPACITY[t]);
            for (int i = 0; i < s; i++) {
                long v = 0;
                for (int b = 0; b < 8; b++) {
                    v = (v << 8) | (vals[i * 8 + b] & 0xFFL);
                }
                boolean ok = i < oks.length && oks[i] != 0;
                pushTier(t, v, ok);
            }
            // pushTier 会累加 total,这里以 NBT 中的 total 为准
            total[t] = Math.max(tag.getLong("total" + t), total[t]);
        }
    }
}
