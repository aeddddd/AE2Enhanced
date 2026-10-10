package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult;

/**
 * Omni 终端 GUI 的公共契约：让只认识 `instanceof GuiOmniTerm` 的既有桥接点
 * （R3 分页包、AE2 终端 mixin）能同时覆盖旧 GUI 与阶段 2a 的
 * {@link GuiSplicedTerm}。
 *
 * <p>这里只声明"终端 GUI 必须响应"的最小集合；搜索框、repo、滚动条等
 * 属于具体实现（旧 GUI 走 GuiMEMonitorable 的宿主能力，新 GUI 自托管）。</p>
 */
public interface IOmniTerminalGui {
    /** R3 分页结果回填（服务端 {@code PacketOmniPageResult} 的落点）。 */
    void handlePageResult(PacketOmniPageResult packet);

    /** R3 变化通知（节流刷新）。 */
    void handleUpdateNotify();
}
