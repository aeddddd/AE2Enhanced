package com.github.aeddddd.ae2enhanced.dimension.rules;

import javax.annotation.Nullable;

/** 玩家能力的单一 base 与按来源组合的修饰项。 */
public final class PlayerAbilityState {
    private PlayerAbilityState() {}

    public static Resolved resolve(Base base, @Nullable Modifier dimension, @Nullable Modifier ring,
                                   boolean creativeOrSpectator, boolean currentlyFlying) {
        boolean allow = base.allowFlying || creativeOrSpectator
                || dimension != null && dimension.allowFlying
                || ring != null && ring.allowFlying;
        float fly = base.flySpeed;
        float walk = base.walkSpeed;
        if (!creativeOrSpectator) {
            if (dimension != null) {
                if (dimension.flySpeed != null) fly = dimension.flySpeed;
                if (dimension.walkSpeed != null) walk = dimension.walkSpeed;
            }
            if (ring != null) {
                if (ring.flySpeed != null) fly = ring.flySpeed;
                if (ring.walkSpeed != null) walk = ring.walkSpeed;
            }
        }
        return new Resolved(allow, allow && currentlyFlying, fly, walk);
    }

    public static final class Base {
        public final boolean allowFlying;
        public final float flySpeed;
        public final float walkSpeed;

        public Base(boolean allowFlying, float flySpeed, float walkSpeed) {
            this.allowFlying = allowFlying;
            this.flySpeed = flySpeed;
            this.walkSpeed = walkSpeed;
        }
    }

    public static final class Modifier {
        public final boolean allowFlying;
        @Nullable public final Float flySpeed;
        @Nullable public final Float walkSpeed;

        public Modifier(boolean allowFlying, @Nullable Float flySpeed, @Nullable Float walkSpeed) {
            this.allowFlying = allowFlying;
            this.flySpeed = flySpeed;
            this.walkSpeed = walkSpeed;
        }
    }

    public static final class Resolved {
        public final boolean allowFlying;
        public final boolean isFlying;
        public final float flySpeed;
        public final float walkSpeed;

        private Resolved(boolean allowFlying, boolean isFlying, float flySpeed, float walkSpeed) {
            this.allowFlying = allowFlying;
            this.isFlying = isFlying;
            this.flySpeed = flySpeed;
            this.walkSpeed = walkSpeed;
        }
    }
}
