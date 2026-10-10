package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一个机器族的声明式表项：匹配哪些方块 + 用哪个行为描述驱动.
 *
 * <p>加一台机器 = 加一条记录（同族），或加一个 {@link MachineStorageAccess} 实现 + 一条记录（新族），
 * 而不是加一段重复的推料/收集/判定骨架。</p>
 *
 * <p><b>谁来读这份表项</b>：{@code AbstractMachineHandler#canHandle} 用 {@link #matches} 裁定匹配，
 * {@code HandlerRegistry} 用 {@link #exactBlockIds()} / {@link #blockIdPrefixes()} 建两级索引。
 * 因此 blockId 规则只在声明表项的这一处出现。</p>
 *
 * <p><b>关于前缀匹配</b>：EnderIO / Thermal Expansion / NuclearCraft 三个 handler 按
 * 命名空间前缀匹配<b>整个 mod 的所有方块</b>（含导管、电容库等无输入槽方块）。粒度无法收窄，
 * 因为 {@code IRemoteHandler#canHandle(String)} 只拿到 blockId、拿不到 TileEntity，
 * 匹配阶段无法用类型判定。实际使用由 {@code isValidTarget}（= 目录构造成功）把关，
 * 无槽位的方块会被判为 UNAVAILABLE。</p>
 */
public final class MachineSpec {

    private final Set<String> exactBlockIds;
    private final List<String> blockIdPrefixes;
    private final MachineStorageAccess access;

    private MachineSpec(Set<String> exactBlockIds, List<String> blockIdPrefixes, MachineStorageAccess access) {
        this.exactBlockIds = Collections.unmodifiableSet(exactBlockIds);
        this.blockIdPrefixes = Collections.unmodifiableList(blockIdPrefixes);
        this.access = access;
    }

    /** 该方块 id 是否属于本机器族。 */
    public boolean matches(String blockId) {
        if (blockId == null) {
            return false;
        }
        if (this.exactBlockIds.contains(blockId)) {
            return true;
        }
        for (String prefix : this.blockIdPrefixes) {
            if (blockId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public MachineStorageAccess access() {
        return this.access;
    }

    public Set<String> exactBlockIds() {
        return this.exactBlockIds;
    }

    public List<String> blockIdPrefixes() {
        return this.blockIdPrefixes;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 构造器。 */
    public static final class Builder {

        private final Set<String> exactBlockIds = new LinkedHashSet<>();
        private final List<String> prefixes = new ArrayList<>();
        private MachineStorageAccess access;
        private boolean fallback;

        public Builder exactIds(String... ids) {
            if (ids != null) {
                this.exactBlockIds.addAll(Arrays.asList(ids));
            }
            return this;
        }

        public Builder prefixes(String... prefixes) {
            if (prefixes != null) {
                this.prefixes.addAll(Arrays.asList(prefixes));
            }
            return this;
        }

        /**
         * 声明本表项为<b>兜底行</b>：不主动匹配任何 blockId.
         *
         * <p>只有 {@code DefaultSingleBatchHandler} 用它：{@code canHandle} 恒 false，
         * 仅由 {@code HandlerRegistry} 作为默认返回。除此之外的表项都必须声明至少一个 id 或前缀，
         * 否则会静默抢走其它 handler 的匹配（注册表的索引也永远命中不到它）。</p>
         */
        public Builder fallback() {
            this.fallback = true;
            return this;
        }

        public Builder access(MachineStorageAccess access) {
            this.access = access;
            return this;
        }

        public MachineSpec build() {
            if (this.access == null) {
                throw new IllegalStateException("MachineSpec 必须提供 MachineStorageAccess");
            }
            if (this.exactBlockIds.isEmpty() && this.prefixes.isEmpty() && !this.fallback) {
                throw new IllegalStateException("MachineSpec 必须声明至少一个 blockId 或前缀（兜底行请显式调用 fallback()）");
            }
            return new MachineSpec(this.exactBlockIds, this.prefixes, this.access);
        }
    }
}
