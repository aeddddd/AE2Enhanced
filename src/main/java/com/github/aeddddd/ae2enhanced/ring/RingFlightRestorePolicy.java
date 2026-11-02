package com.github.aeddddd.ae2enhanced.ring;

/** 强制飞行恢复只接受本 tick 已支付的能耗结果。 */
public final class RingFlightRestorePolicy {
    private RingFlightRestorePolicy() {}

    public static boolean mayRestore(boolean paidThisTick) {
        return paidThisTick;
    }

    public static boolean shouldRestoreFlying(boolean paidThisTick, boolean wasFlying, boolean onGround) {
        return paidThisTick && wasFlying && !onGround;
    }
}
