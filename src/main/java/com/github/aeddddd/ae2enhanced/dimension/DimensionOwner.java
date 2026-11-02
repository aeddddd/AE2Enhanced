package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.util.math.BlockPos;

/** 统一的私域维度所有者描述，不携带 World、FTB Team 或 Tile 引用。 */
public final class DimensionOwner {
    public final Scope scope;
    public final String ownerId;
    public final String bindingId;
    public final int dimensionId;
    public final PersonalDimensionRules rules;
    public final BlockPos entryPoint;
    public final FloorDefinition floor;
    private DimensionOwner(Scope scope, String ownerId, String bindingId, int dimensionId,
            PersonalDimensionRules rules, BlockPos entryPoint, FloorDefinition floor) {
        this.scope = scope;
        this.ownerId = ownerId;
        this.bindingId = bindingId;
        this.dimensionId = dimensionId;
        this.rules = rules;
        this.entryPoint = entryPoint;
        this.floor = floor;
    }

    public static DimensionOwner personal(PlayerDimEntry entry) {
        return new DimensionOwner(Scope.PERSONAL, entry.playerId.toString(), null, entry.dimensionId,
                entry.rules, entry.entryPoint, entry.floor);
    }

    public static DimensionOwner team(TeamDimEntry entry) {
        return new DimensionOwner(Scope.TEAM, entry.teamId, entry.bindingId, entry.dimensionId,
                entry.rules, entry.entryPoint, entry.floor);
    }

    public boolean isTeam() {
        return scope == Scope.TEAM;
    }

    public enum Scope {
        PERSONAL,
        TEAM
    }
}
