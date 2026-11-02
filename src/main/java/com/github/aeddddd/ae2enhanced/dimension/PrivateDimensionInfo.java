package com.github.aeddddd.ae2enhanced.dimension;

/** 客户端管理界面使用的只读私域状态。 */
public final class PrivateDimensionInfo {
    public final DimensionOwner.Scope scope;
    public final String ownerId;
    public final String displayName;
    public final DimensionPreference preference;
    public final String floorStyleId;
    public final long floorRevision;
    public final boolean canManage;

    public PrivateDimensionInfo(DimensionOwner.Scope scope, String ownerId, String displayName,
            DimensionPreference preference, String floorStyleId, long floorRevision, boolean canManage) {
        this.scope = scope;
        this.ownerId = ownerId;
        this.displayName = displayName;
        this.preference = preference;
        this.floorStyleId = floorStyleId;
        this.floorRevision = floorRevision;
        this.canManage = canManage;
    }

    public static PrivateDimensionInfo empty() {
        return new PrivateDimensionInfo(DimensionOwner.Scope.PERSONAL, "", "", DimensionPreference.AUTO,
                FloorDefinition.DEFAULT_STYLE, 0L, true);
    }
}
