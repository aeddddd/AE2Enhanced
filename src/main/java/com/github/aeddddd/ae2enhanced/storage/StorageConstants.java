package com.github.aeddddd.ae2enhanced.storage;

/**
 * 存储模块常用常量，避免热路径重复创建对象。
 */
public final class StorageConstants {

    private StorageConstants() {
    }

    /**
     * 单条目 NBT 读取上限（64MB）。
     * v1 曾用 2MB（与网络包上限相同），但磁盘读取超限会导致整个分区进入只读安全模式——
     * 一个超大 NBT 物品（背包/嵌套容器）即可锁死分区。磁盘侧放宽到 64MB 容错，
     * 网络同步仍受原版 2MB 限制（超限物品在终端显示降级，不影响存储正确性）。
     */
    public static final long MAX_NBT_PAYLOAD_BYTES = 64L * 1024 * 1024;

    /**
     * 单条目描述符总长度上界（用于读取时校验，防止损坏文件触发巨额分配 OOM）。
     */
    public static final int MAX_ENTRY_BYTES = (int) (MAX_NBT_PAYLOAD_BYTES + 4 * 1024 * 1024);
}
