package com.github.aeddddd.ae2enhanced.util.memorycard.api;

/**
 * UMC 粘贴操作的结果枚举.
 */
public enum PasteResult {
    SUCCESS,
    /** 粘贴成功,但 handler 已自行发送反馈消息(服务层不再重复提示). */
    SUCCESS_CUSTOM,
    INVALID_MACHINE,
    MISSING_UPGRADES,
    FAILED
}
