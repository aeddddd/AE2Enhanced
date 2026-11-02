package com.github.aeddddd.ae2enhanced.dimension.team;

/** 中性 FTB 玩家团队快照，不暴露 FTB 类型或可变化 uid。 */
public final class TeamInfo {
    private final String teamId;
    private final String displayName;

    public TeamInfo(String teamId, String displayName) {
        this.teamId = teamId;
        this.displayName = displayName;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
