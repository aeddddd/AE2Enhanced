package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;

import java.math.BigInteger;
import java.util.UUID;

/**
 * 超因果计算核心的单个合成订单。
 * 支持 BigInteger 量级请求（突破 long 限制）。
 */
public class CraftingOrder {

    public final UUID orderId;
    public final ICraftingPatternDetails pattern;
    public final BigInteger requestedAmount;
    public BigInteger completedAmount = BigInteger.ZERO;
    public State state = State.PENDING;

    public enum State {
        PENDING,      // 等待调度
        RUNNING,      // 正在执行
        PAUSED,       // 暂停（资源不足等）
        COMPLETED,    // 已完成
        FAILED        // 失败
    }

    public CraftingOrder(ICraftingPatternDetails pattern, BigInteger requestedAmount) {
        this.orderId = UUID.randomUUID();
        this.pattern = pattern;
        this.requestedAmount = requestedAmount;
    }

    public boolean isComplete() {
        return completedAmount.compareTo(requestedAmount) >= 0;
    }

    public BigInteger getRemaining() {
        return requestedAmount.subtract(completedAmount).max(BigInteger.ZERO);
    }
}
