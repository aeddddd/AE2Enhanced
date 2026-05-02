package com.github.aeddddd.ae2enhanced.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 订单调度器：管理 pending / active 订单队列。
 * P1 骨架 —— 完整调度策略将在后续迭代中实现。
 */
public class OrderScheduler {

    private final Queue<CraftingOrder> pendingOrders = new ConcurrentLinkedQueue<>();
    private final List<CraftingOrder> activeOrders = new ArrayList<>();
    private final int maxActiveOrders;

    public OrderScheduler(int maxActiveOrders) {
        this.maxActiveOrders = maxActiveOrders;
    }

    public synchronized void submit(CraftingOrder order) {
        if (order == null) return;
        pendingOrders.offer(order);
    }

    /**
     * 从 pending 队列取出一个订单并标记为 RUNNING。
     * 若 active 已满则返回 null。
     */
    public synchronized CraftingOrder pollNext() {
        if (activeOrders.size() >= maxActiveOrders) return null;
        CraftingOrder order = pendingOrders.poll();
        if (order != null) {
            order.state = CraftingOrder.State.RUNNING;
            activeOrders.add(order);
        }
        return order;
    }

    public synchronized void complete(CraftingOrder order) {
        if (activeOrders.remove(order)) {
            order.state = CraftingOrder.State.COMPLETED;
        }
    }

    public synchronized void fail(CraftingOrder order) {
        if (activeOrders.remove(order)) {
            order.state = CraftingOrder.State.FAILED;
        }
    }

    public synchronized void pause(CraftingOrder order) {
        if (activeOrders.remove(order)) {
            order.state = CraftingOrder.State.PAUSED;
            pendingOrders.offer(order); // 重新放入队列前端（ConcurrentLinkedQueue 不支持，直接 offer）
        }
    }

    public int getPendingCount() {
        return pendingOrders.size();
    }

    public int getActiveCount() {
        return activeOrders.size();
    }

    public int getMaxActiveOrders() {
        return maxActiveOrders;
    }

    public synchronized List<CraftingOrder> getActiveOrdersSnapshot() {
        return new ArrayList<>(activeOrders);
    }
}
