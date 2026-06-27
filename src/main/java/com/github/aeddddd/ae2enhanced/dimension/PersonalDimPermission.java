package com.github.aeddddd.ae2enhanced.dimension;

/**
 * 个人维度访问权限。
 */
public enum PersonalDimPermission {
    /**
     * 允许进入维度。
     */
    ENTER,
    /**
     * 允许放置/破坏方块。
     */
    BUILD,
    /**
     * 允许与方块、实体交互（打开 GUI、使用机器、骑乘等）。
     */
    INTERACT,
    /**
     * 允许修改维度规则、邀请/踢出其他玩家。
     */
    MANAGE_RULES
}
