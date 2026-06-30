package com.github.aeddddd.ae2enhanced.multiblock;

import appeng.api.storage.MEStorage;

/**
 * 可作为网络存储宿主的控制器标记接口。
 */
public interface IStorageHost extends IMultiblockController {

    /**
     * @return 需要挂载到 AE2 网络的存储实例。
     */
    MEStorage getStorage();

    /**
     * 当通用接口节点的存储挂载状态需要刷新时调用。
     */
    default void onStorageChanged() {
    }
}
