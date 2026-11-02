package com.github.aeddddd.ae2enhanced.api;

/** 被约束微型奇点物品的快照；其寿命每 30 个实体 tick 才减少 1 tick。 */
public final class ConstrainedSingularitySnapshot {
    private final int remainingTicks;
    private final boolean permanent;
    private final int countdownDivisor;

    public ConstrainedSingularitySnapshot(int remainingTicks, boolean permanent, int countdownDivisor) {
        this.remainingTicks = Math.max(0, remainingTicks);
        this.permanent = permanent;
        this.countdownDivisor = Math.max(1, countdownDivisor);
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public double getRemainingSeconds() {
        return remainingTicks / 20.0D;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public int getCountdownDivisor() {
        return countdownDivisor;
    }
}
