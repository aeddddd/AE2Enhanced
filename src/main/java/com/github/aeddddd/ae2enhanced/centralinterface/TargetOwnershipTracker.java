package com.github.aeddddd.ae2enhanced.centralinterface;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 全局坐标所有权跟踪器.
 *
 * <p>Central ME Interface 的核心设计是“一个接口 → 多台机器”,但同一块物理机器可能被多个接口同时绑定。
 * 本跟踪器保证同一 {@code (维度, 坐标, 方块ID)} 在同一时刻只被一个 {@link DualityCentralInterface} 占用，
 * 从而避免以下问题：</p>
 * <ul>
 *   <li>Botania 设备被多个接口同时推料/收集，互相清理对方输入实体</li>
 *   <li>Thaumcraft 注魔矩阵被多个接口同时清 pedestals 并强制启动，互相破坏合成状态</li>
 * </ul>
 *
 * <p>所有权在推送成功时获取，在目标回到 IDLE、绑定移除、接口销毁或处理超时时释放。</p>
 */
public final class TargetOwnershipTracker {

    private static final TargetOwnershipTracker INSTANCE = new TargetOwnershipTracker();

    private final Map<TargetBinding, DualityCentralInterface> owners = new HashMap<>();

    private TargetOwnershipTracker() {
    }

    public static TargetOwnershipTracker instance() {
        return INSTANCE;
    }

    /**
     * 尝试获取指定目标的所有权。
     *
     * <p>若现有所有者的宿主 Tile 已失效（isInvalid / world 为空，例如单机跨存档重载后
     * 残留的旧实例），则回收其所有权并允许新所有者获取，避免机器被永久锁定。</p>
     *
     * @return 当前无所有者、所有者已失效被回收、或所有者就是自身时返回 true；否则返回 false
     */
    public synchronized boolean tryAcquire(TargetBinding binding, DualityCentralInterface owner) {
        DualityCentralInterface current = owners.get(binding);
        if (current != null && current != owner && !current.isAlive()) {
            // 旧所有者已失效（如服务器重启/跨存档后残留的实例），回收其所有权
            owners.remove(binding);
            current = null;
        }
        if (current == null || current == owner) {
            owners.put(binding, owner);
            return true;
        }
        return false;
    }

    /**
     * 判断当前对象是否是指定目标的所有者。
     */
    public synchronized boolean isOwner(TargetBinding binding, DualityCentralInterface owner) {
        return owners.get(binding) == owner;
    }

    /**
     * 释放指定目标的所有权（仅当自身是所有者时才生效）。
     */
    public synchronized void release(TargetBinding binding, DualityCentralInterface owner) {
        if (owners.get(binding) == owner) {
            owners.remove(binding);
        }
    }

    /**
     * 释放指定接口拥有的全部目标所有权。
     */
    public synchronized void releaseAll(DualityCentralInterface owner) {
        Iterator<Map.Entry<TargetBinding, DualityCentralInterface>> it = owners.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TargetBinding, DualityCentralInterface> entry = it.next();
            if (entry.getValue() == owner) {
                it.remove();
            }
        }
    }

    /**
     * 清空全部所有权记录。在服务器停止（FMLServerStoppingEvent）时调用，
     * 避免静态表残留旧存档的 DualityCentralInterface 实例导致新存档中机器被永久锁定。
     */
    public synchronized void clearAll() {
        owners.clear();
    }

    /**
     * 清空全部所有权记录。<b>仅供单元测试使用</b>：全局单例状态需要在测试间隔离，
     * 生产代码不得调用此方法。
     */
    public static synchronized void resetForTesting() {
        INSTANCE.owners.clear();
    }
}
