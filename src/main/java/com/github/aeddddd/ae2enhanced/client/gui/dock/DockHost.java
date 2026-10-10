package com.github.aeddddd.ae2enhanced.client.gui.dock;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutPersistence;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockWorkspaceDefaults;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LegacyMigrationContext;

/**
 * {@link DockManager} 与宿主 GUI 之间的唯一接口。
 *
 * <p>DockManager 的手势/几何/状态机路径<strong>不得</strong>触碰 Minecraft 单例：屏幕尺寸、修饰键、
 * 持久化都由宿主提供，因此整条逻辑链路可以在无头 JUnit 里驱动。MC 依赖只出现在 DockManager 的渲染
 * 方法里（{@code Gui}/{@code GlStateManager}/{@code Minecraft#fontRenderer}），测试不调用它们。</p>
 */
public interface DockHost {
    /** GUI 画布宽度（1.12.2 的 {@code GuiScreen.width}）。 */
    int screenWidth();

    /** GUI 画布高度（1.12.2 的 {@code GuiScreen.height}）。 */
    int screenHeight();

    /**
     * 由 DockManager 在构造好编解码器之后调用，返回布局的持久化实现。
     *
     * <p>返回 {@code null} 表示本次会话不落盘（单元测试、临时演示）：此时 {@link DockManager#save()}
     * 与加载路径都退化为"无持久化"，其余行为不变。</p>
     */
    DockLayoutPersistence createPersistence(DockLayoutCodec codec);

    /** Alt 键（1.12.2 的 {@code Screen.hasAltDown()}）；用于"把拼接窗口中的分片拖出来"。 */
    default boolean altDown() {
        return false;
    }

    /** Ctrl 键（1.12.2 的 {@code Screen.hasControlDown()}）；按下时拖标题条移动整个窗口。 */
    default boolean controlDown() {
        return false;
    }

    /**
     * 模块可见性被<b>本地布局操作</b>改变后回调（标题栏关闭按钮、{@link DockManager#revealModule}、
     * {@link DockManager#toggleVisible}）。
     *
     * <p>宿主要在这里把新的可见集合同步出去（终端会推送 {@code panelMask}）。漏掉这个回调的话，
     * 服务端的旧 mask 会在下一次同步时把刚关掉的模块"还原"回来。</p>
     */
    default void onModuleVisibilityChanged() {
    }

    /**
     * 没有可用持久化布局时的默认工作区。默认实现＝按 catalog 顺序级联摆放
     * （{@link DockWorkspaceDefaults#create}）；真实终端会覆写它给出"预拼装默认布局"。
     */
    default DockWorkspace createDefaultWorkspace(ModuleCatalog catalog, LegacyMigrationContext geometry,
            java.util.function.UnaryOperator<DockWorkspace> shellFit) {
        return DockWorkspaceDefaults.create(catalog, geometry, shellFit);
    }

    /**
     * 客户端配置目录（{@code config/<modid>/spliced_terminal}）。
     *
     * <p>布局预设银行（{@code presets.json}）与分享目录都落在这里。返回 {@code null} 表示
     * 本次会话不提供文件级功能：预设/导入导出整体降级，其余行为不变。</p>
     */
    default java.nio.file.Path configDir() {
        return null;
    }

    /** 布局发生变化时请求宿主重绘；宿主可忽略（每帧都会重绘）。 */
    default void requestRedraw() {
    }
}
