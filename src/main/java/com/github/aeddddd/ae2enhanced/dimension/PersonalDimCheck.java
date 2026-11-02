package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.observation.CheckObservation;
import com.github.aeddddd.ae2enhanced.observation.ObservationCheck;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.util.Collection;
import java.util.List;

/**
 * 个人维度数据一致性检查.
 *
 * <ul>
 *   <li>条目维度已分配但未注册 → ERROR（孤儿条目）</li>
 *   <li>条目维度已注册但不是个人维度类型 → ERROR（ID 冲突）</li>
 *   <li>未分配维度的空条目 → 不告警（规则预编辑属正常）</li>
 * </ul>
 */
public final class PersonalDimCheck implements ObservationCheck {

    private static final String KEY_PREFIX = "chat.ae2enhanced.check.personaldim.";

    @Override
    public String name() {
        return "personaldim";
    }

    @Override
    public String displayName() {
        return KEY_PREFIX + "name";
    }

    @Override
    public void run(MinecraftServer server, List<CheckObservation> out) {
        try {
            WorldServer overworld = server.getWorld(0);
            if (overworld == null) {
                out.add(CheckObservation.warn(KEY_PREFIX + "overworld_unavailable"));
                return;
            }
            PersonalDimensionData data = PersonalDimensionData.get(overworld);
            Collection<PlayerDimEntry> entries = data.getAllEntries();

            int assigned = 0;
            int unassigned = 0;
            int orphan = 0;
            int typeConflict = 0;
            for (PlayerDimEntry entry : entries) {
                int dimId = entry.dimensionId;
                if (dimId == Integer.MIN_VALUE) {
                    unassigned++;
                    continue;
                }
                assigned++;
                if (!DimensionManager.isDimensionRegistered(dimId)) {
                    orphan++;
                    out.add(CheckObservation.error(KEY_PREFIX + "orphan", String.valueOf(entry.playerId), dimId));
                } else if (!PersonalDimensionManager.isPersonalDimension(dimId)) {
                    typeConflict++;
                    out.add(CheckObservation.error(KEY_PREFIX + "conflict", String.valueOf(entry.playerId), dimId));
                }
            }
            out.add(CheckObservation.ok(KEY_PREFIX + "entries_summary", entries.size(), assigned, unassigned));
            if (orphan == 0 && typeConflict == 0 && assigned > 0) {
                out.add(CheckObservation.ok(KEY_PREFIX + "all_consistent"));
            }
        } catch (Exception e) {
            out.add(CheckObservation.error(KEY_PREFIX + "exception", String.valueOf(e)));
        }
    }
}
