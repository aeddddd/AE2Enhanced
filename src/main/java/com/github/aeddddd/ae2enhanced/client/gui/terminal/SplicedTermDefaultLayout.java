package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockEdge;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspaceEditor;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockWorkspaceDefaults;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LegacyMigrationContext;
import com.github.aeddddd.ae2enhanced.client.gui.panel.CraftingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.PatternEncodingPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.PlayerInvPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.StoragePanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.UpgradePanel;
import com.github.aeddddd.ae2enhanced.client.gui.panel.ViewCellPanel;

/**
 * 可拼接终端的默认布局：四个窗口，每个窗口的尺寸都等于自己内容的尺寸。
 *
 * <pre>
 * ┌───────────────────────────┬──────┐   ┌─────────────┐
 * │ repo (18 列 × 5 行)        │ view │   │  pattern    │
 * ├──────────────┬────────────┤ cell │   ├─────────────┤
 * │ crafting     │ storage     │      │   │  playerinv  │
 * │              │             │      │   ├─────────────┤
 * │              │             │      │   │  upgrade    │
 * └──────────────┴────────────┘      ┘   └─────────────┘
 * </pre>
 *
 * <p>窗口与拼法：</p>
 * <ol>
 *   <li>{@code w1 = H[repo | viewcell]}：AE2 终端的经典布局（view cell 列贴在物品格右侧）；</li>
 *   <li>{@code w2 = H[crafting | storage]}：两块面板内容高度相同，正好等高；</li>
 *   <li>{@code w3 = V[playerinv / upgrade]}：两块面板内容宽度相同，正好等宽；</li>
 *   <li>{@code w4 = pattern}：编码区不与任何面板同行同列。</li>
 * </ol>
 *
 * <p><b>为什么默认布局不用"一个大窗口装七个模块"</b>：布局引擎（与 MEST 的
 * {@code allocateShellFirst} 一致）只在 <b>垂直</b>轴的富余分配上尊重 {@code expandVertically}；
 * 而壳模型要求每个子节点占满父矩形的<b>交叉轴</b>，所以把一个矮面板（如编码区，内容高 108+chrome）
 * 和一个高面板放进同一个横向 split，矮面板就会被拉成整列高度，右下留下大片空白
 * （改版前实测 156px）。把内容尺寸互不相同的面板拆成各自匹配的窗口后，默认布局里不再有任何面板
 * 被拉伸——这也与 MEST 的默认布局一致（它的默认布局就是一叠按内容尺寸摆放的窗口 + 一个拼接窗口）。</p>
 *
 * <p>唯一保留的"同行对齐"是 {@code w1} 里的 view cell 列：它与 repo 同行，列高由 repo 决定
 * （内容 5 格 = 90px，列高 132px，差 18px 留在列底），AE2 19.x 的 view cell 列就是这个观感。</p>
 *
 * <p>本类刻意与 Screen/面板实现解耦（只依赖 dock 模型 + 模块 id 常量，id 是编译期常量不会拉起面板类），
 * 因此可以在无头环境直接单测"默认布局能不能构造出来"——默认布局一旦构造失败，
 * {@code DockManager} 会静默退回 catalog 级联摆放，正是最需要回归拦住的情况。</p>
 */
public final class SplicedTermDefaultLayout {

    private static final Logger LOGGER = LogManager.getLogger(SplicedTermDefaultLayout.class);

    /** 视口内保留的边距。 */
    private static final int VIEWPORT_MARGIN = 8;
    private static final int MIN_ROOT_WIDTH = 240;
    private static final int MIN_ROOT_HEIGHT = 160;
    /** 窗口之间的间隔。 */
    private static final int WINDOW_GAP = 8;

    private SplicedTermDefaultLayout() {
    }

    /**
     * 构造默认 workspace。
     *
     * <p>任何 {@link RuntimeException} 都退回 catalog 级联摆放：默认布局失败绝不能挡住终端打开。</p>
     */
    public static DockWorkspace create(ModuleCatalog catalog, LegacyMigrationContext geometry,
            UnaryOperator<DockWorkspace> shellFit) {
        DockWorkspace base = DockWorkspaceDefaults.create(catalog, geometry, shellFit);
        String repoRootId = DockWorkspaceDefaults.rootId(RepoPanel.MODULE_ID);
        if (rootById(base, repoRootId) == null) {
            return base;
        }
        try {
            DockWorkspaceEditor editor = new DockWorkspaceEditor(catalog);
            int repoWidth = widthOf(catalog, geometry, RepoPanel.MODULE_ID);
            int repoHeight = heightOf(catalog, geometry, RepoPanel.MODULE_ID);
            int viewWidth = widthOf(catalog, geometry, ViewCellPanel.MODULE_ID);
            int craftWidth = widthOf(catalog, geometry, CraftingPanel.MODULE_ID);
            int craftHeight = heightOf(catalog, geometry, CraftingPanel.MODULE_ID);
            int storageWidth = widthOf(catalog, geometry, StoragePanel.MODULE_ID);
            int storageHeight = heightOf(catalog, geometry, StoragePanel.MODULE_ID);
            int playerWidth = widthOf(catalog, geometry, PlayerInvPanel.MODULE_ID);
            int playerHeight = heightOf(catalog, geometry, PlayerInvPanel.MODULE_ID);
            int upgradeHeight = heightOf(catalog, geometry, UpgradePanel.MODULE_ID);
            int patternWidth = widthOf(catalog, geometry, PatternEncodingPanel.MODULE_ID);
            int patternHeight = heightOf(catalog, geometry, PatternEncodingPanel.MODULE_ID);

            // w1 = repo | viewcell
            DockWorkspace w1 = editor.insertSplit(base,
                    DockWorkspaceDefaults.leafNodeId(ViewCellPanel.MODULE_ID),
                    DockWorkspaceDefaults.leafNodeId(RepoPanel.MODULE_ID),
                    DockEdge.RIGHT, "w1.repo.viewcell", repoWidth / (double) (repoWidth + viewWidth));

            // w2 = crafting | storage（两块内容等高，正好等高拼接）
            DockWorkspace w2 = editor.insertSplit(w1,
                    DockWorkspaceDefaults.leafNodeId(StoragePanel.MODULE_ID),
                    DockWorkspaceDefaults.leafNodeId(CraftingPanel.MODULE_ID),
                    DockEdge.RIGHT, "w2.crafting.storage",
                    craftWidth / (double) (craftWidth + storageWidth));

            // w3 = playerinv / upgrade（两块内容等宽，正好等宽拼接）
            DockWorkspace joined = editor.insertSplit(w2,
                    DockWorkspaceDefaults.leafNodeId(UpgradePanel.MODULE_ID),
                    DockWorkspaceDefaults.leafNodeId(PlayerInvPanel.MODULE_ID),
                    DockEdge.BOTTOM, "w3.playerinv.upgrade",
                    playerHeight / (double) (playerHeight + upgradeHeight));

            int w1Width = repoWidth + viewWidth;
            int w1Height = repoHeight;
            int w2Width = craftWidth + storageWidth;
            int w2Height = Math.max(craftHeight, storageHeight);
            int w3Width = playerWidth;
            int w3Height = playerHeight + upgradeHeight;
            int w4Width = patternWidth;
            int w4Height = patternHeight;

            // 左列 = w1 / w2，右列 = w3 / w4；整体居中，装不下时等比收缩（绝不放大）
            int columnOneWidth = Math.max(w1Width, w2Width);
            int columnOneHeight = w1Height + WINDOW_GAP + w2Height;
            int columnTwoWidth = Math.max(w3Width, w4Width);
            int columnTwoHeight = w3Height + WINDOW_GAP + w4Height;
            int totalWidth = columnOneWidth + WINDOW_GAP + columnTwoWidth;
            int totalHeight = Math.max(columnOneHeight, columnTwoHeight);
            double scale = Math.min(1.0D, Math.min(
                    Math.max(MIN_ROOT_WIDTH, geometry.viewportWidth() - VIEWPORT_MARGIN) / (double) totalWidth,
                    Math.max(MIN_ROOT_HEIGHT, geometry.viewportHeight() - VIEWPORT_MARGIN) / (double) totalHeight));

            int originX = Math.max(0, (geometry.viewportWidth() - scaledExtent(totalWidth, scale)) / 2);
            int originY = Math.max(0, (geometry.viewportHeight() - scaledExtent(totalHeight, scale)) / 2);
            int secondColumnX = originX + scaledExtent(columnOneWidth, scale) + WINDOW_GAP;

            DockWorkspace placed = editor.setRootBounds(joined, repoRootId,
                    rect(originX, originY, w1Width, w1Height, scale));
            placed = editor.setRootBounds(placed, DockWorkspaceDefaults.rootId(CraftingPanel.MODULE_ID),
                    rect(originX, originY + scaledExtent(w1Height, scale) + WINDOW_GAP, w2Width, w2Height, scale));
            placed = editor.setRootBounds(placed, DockWorkspaceDefaults.rootId(PlayerInvPanel.MODULE_ID),
                    rect(secondColumnX, originY, w3Width, w3Height, scale));
            return editor.setRootBounds(placed,
                    DockWorkspaceDefaults.rootId(PatternEncodingPanel.MODULE_ID),
                    rect(secondColumnX, originY + scaledExtent(w3Height, scale) + WINDOW_GAP,
                            w4Width, w4Height, scale));
        } catch (RuntimeException e) {
            // 静默退回级联摆放会让问题很难查，这里至少留一行日志
            LOGGER.warn("Failed to build the spliced terminal default layout, falling back to cascade", e);
            return base;
        }
    }

    private static int scaledExtent(int extent, double scale) {
        return Math.max(1, (int) Math.floor(extent * scale));
    }

    private static DockRect rect(int x, int y, int width, int height, double scale) {
        return new DockRect(x, y, scaledExtent(width, scale), scaledExtent(height, scale));
    }

    /**
     * 面板真实宽度。
     *
     * <p>传进来的 catalog 是 DockManager 的 {@code persistenceCatalog}：为了兼容 v1 文档，它的每个模块
     * 尺寸都减掉了 legacy 内边距（{@code 7,25,7,7} → 水平 14 / 垂直 32）。而"默认布局"要和面板在屏幕上的
     * 真实尺寸对齐（投影用的是未减内边距的 catalog），所以这里必须把内边距加回去——否则窗口会比面板内容
     * 小 14/32px，叶子溢出 root（实机踩过：356x100 的窗口里装 384x132 的内容，槽位点击全部错位）。</p>
     */
    private static int widthOf(ModuleCatalog catalog, LegacyMigrationContext geometry, String moduleId) {
        return catalog.metrics(moduleId).defaultSize().width() + geometry.rootInsets().horizontal();
    }

    private static int heightOf(ModuleCatalog catalog, LegacyMigrationContext geometry, String moduleId) {
        return catalog.metrics(moduleId).defaultSize().height() + geometry.rootInsets().vertical();
    }

    private static FloatingRoot rootById(DockWorkspace workspace, String rootId) {
        for (FloatingRoot root : workspace.roots()) {
            if (root.rootId().equals(rootId)) {
                return root;
            }
        }
        return null;
    }
}
