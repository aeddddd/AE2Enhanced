package com.github.aeddddd.ae2enhanced.menu;

/**
 * 由 MixinCraftAmountMenu 添加到 CraftAmountMenu 的 long 型下单确认入口.
 */
public interface CraftAmountMenuLongExt {

    /**
     * 以 long 数量执行与 {@code CraftAmountMenu.confirm(int, boolean, boolean)} 服务端分支
     * 相同的流程（库存抵扣 -> 打开确认菜单 -> 提交计算）.
     */
    void ae2e$confirmLong(long amount, boolean craftMissingAmount, boolean autoStart);
}
