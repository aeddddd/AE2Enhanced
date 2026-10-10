package com.github.aeddddd.ae2enhanced.client.gui.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.InternalSlotME;
import appeng.client.me.SlotME;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.me.OmniItemRepo;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest;

/**
 * 阶段 2b：repo 面板与宿主 {@code inventorySlots} 的协作契约。
 *
 * <p>关键不变量：</p>
 * <ul>
 *   <li>ME 展示槽永远<strong>追加在列表尾部</strong>（AE2 用 {@code inventorySlots.size()} 当 ME 操作
 *       的哨兵 slotId）；</li>
 *   <li>形状（列×行）变化 → 整批重建；只有位置变化 → 复用原 {@code SlotME} 对象只改坐标
 *       （{@code InternalSlotME} 的坐标是构造期固化的）；</li>
 *   <li>{@code AEBaseGui.initGui} 重建过这批槽位后，宿主能重新接管新对象；</li>
 *   <li>面板不可见时 ME 槽被挪到屏幕外且不再算"有面板承接"。</li>
 * </ul>
 */
class RepoPanelSlotTest {

    private static final ISortSource NO_SORT = new ISortSource() {
        @Override
        public Enum getSortBy() {
            return null;
        }

        @Override
        public Enum getSortDir() {
            return null;
        }

        @Override
        public Enum getSortDisplay() {
            return null;
        }
    };

    private static final class RecordingRepo extends OmniItemRepo {
        RecordingRepo() {
            super(new GuiScrollbar(), NO_SORT);
        }

        @Override
        protected void sendToServer(PacketOmniPageRequest request) {
            // 无头测试没有网络：只吞掉请求
        }
    }

    private static RepoPanel panel() {
        RepoPanel panel = new RepoPanel(null, new RecordingRepo(), new GuiScrollbar(), null);
        // 默认几何：18 列 × 5 行（见 RepoPanelTest 的往返校验）
        panel.x = 10;
        panel.y = 20;
        panel.width = RepoPanel.DEFAULT_COLUMNS * RepoPanel.SLOT_SIZE + RepoPanel.SCROLLBAR_GAP
                + RepoPanel.SCROLLBAR_WIDTH + 2 * CONTENT_PADDING;
        panel.height = TITLE_BAR_HEIGHT + RepoPanel.SEARCH_ROW_HEIGHT
                + RepoPanel.DEFAULT_ROWS * RepoPanel.SLOT_SIZE + CONTENT_PADDING;
        return panel;
    }

    private static final int CONTENT_PADDING =
            com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel.CONTENT_PADDING;
    private static final int TITLE_BAR_HEIGHT =
            com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel.TITLE_BAR_HEIGHT;

    private static void layoutAndApply(RepoPanel panel, List<Slot> slots) {
        panel.layoutSlots();
        panel.applySlots(slots);
    }

    @Test
    void firstLayoutAppendsTheWholeGridAtTheTail() {
        RepoPanel panel = panel();
        List<Slot> slots = new ArrayList<Slot>();
        Slot ordinary = new Slot(new InventoryBasic("test", false, 1), 0, 5, 6);
        slots.add(ordinary);

        layoutAndApply(panel, slots);

        assertEquals(RepoPanel.DEFAULT_COLUMNS, panel.columns());
        assertEquals(RepoPanel.DEFAULT_ROWS, panel.rows());
        int expected = RepoPanel.DEFAULT_COLUMNS * RepoPanel.DEFAULT_ROWS;
        assertEquals(1 + expected, slots.size());
        // 真实槽位在前，ME 槽永远尾追加
        assertSame(ordinary, slots.get(0), "真实槽位必须保持在前");
        for (int i = 1; i < slots.size(); i++) {
            assertTrue(slots.get(i) instanceof SlotME);
        }
        assertEquals(expected, panel.slotObjects().size());
        assertEquals(expected, panel.meSlots().size());

        // 0 号格：col=0,row=0 → 内容区左上角 +1（16×16 图标居中于 18×18 格）
        SlotME first = (SlotME) slots.get(1);
        assertEquals(panel.gridLeft() + 1, first.xPos);
        assertEquals(panel.gridTop() + 1, first.yPos);
        // 最后一格：col=17,row=4
        SlotME last = (SlotME) slots.get(slots.size() - 1);
        assertEquals(panel.gridLeft() + (RepoPanel.DEFAULT_COLUMNS - 1) * RepoPanel.SLOT_SIZE + 1, last.xPos);
        assertEquals(panel.gridTop() + (RepoPanel.DEFAULT_ROWS - 1) * RepoPanel.SLOT_SIZE + 1, last.yPos);
        // 搜索框占内容区首行
        assertEquals(panel.contentTop() + RepoPanel.SEARCH_ROW_HEIGHT, panel.gridTop());
    }

    @Test
    void pureMoveReusesTheSameSlotObjects() {
        RepoPanel panel = panel();
        List<Slot> slots = new ArrayList<Slot>();
        layoutAndApply(panel, slots);
        SlotME before = (SlotME) slots.get(0);

        panel.x += 40;
        panel.y += 12;
        layoutAndApply(panel, slots);

        assertEquals(slots.size(), panel.slotObjects().size());
        // 仅位置变化：不重建对象，只改坐标
        assertSame(before, slots.get(0), "位置变化不应重建 SlotME");
        assertEquals(panel.gridLeft() + 1, before.xPos);
        assertEquals(panel.gridTop() + 1, before.yPos);
    }

    @Test
    void resizingRebuildsTheGridWithTheNewColumnCount() {
        RepoPanel panel = panel();
        List<Slot> slots = new ArrayList<Slot>();
        layoutAndApply(panel, slots);
        SlotME before = (SlotME) slots.get(0);

        // 收窄到 9 列 3 行
        panel.width = 9 * RepoPanel.SLOT_SIZE + RepoPanel.SCROLLBAR_GAP + RepoPanel.SCROLLBAR_WIDTH
                + 2 * CONTENT_PADDING;
        panel.height = TITLE_BAR_HEIGHT + RepoPanel.SEARCH_ROW_HEIGHT + 3 * RepoPanel.SLOT_SIZE
                + CONTENT_PADDING;
        layoutAndApply(panel, slots);

        assertEquals(9, panel.columns());
        assertEquals(3, panel.rows());
        assertEquals(27, slots.size());
        assertNotSame(before, slots.get(0), "形状变化必须重建 SlotME（InternalSlotME 坐标是构造期固化的）");
        assertEquals(27, panel.slotObjects().size());
    }

    @Test
    void adoptsSlotsRebuiltByInitGui() {
        RepoPanel panel = panel();
        List<Slot> slots = new ArrayList<Slot>();
        layoutAndApply(panel, slots);
        SlotME stale = (SlotME) slots.get(0);

        // 模拟 AEBaseGui.initGui：移除所有 SlotME 后按 getMeSlots() 重建
        slots.removeIf(slot -> slot instanceof SlotME);
        for (InternalSlotME me : panel.meSlots()) {
            slots.add(new SlotME(me));
        }
        SlotME rebuilt = (SlotME) slots.get(0);

        layoutAndApply(panel, slots);

        assertEquals(panel.meSlots().size(), slots.size(), "接管后不应再重复追加");
        assertSame(rebuilt, slots.get(0));
        assertNotSame(stale, panel.slotObjects().get(0), "必须接管新对象而不是停留在失效引用上");
        assertEquals(panel.gridLeft() + 1, rebuilt.xPos);
    }

    @Test
    void hiddenPanelParksItsSlotsAndStopsHostingThem() {
        RepoPanel panel = panel();
        List<Slot> slots = new ArrayList<Slot>();
        layoutAndApply(panel, slots);
        assertTrue(panel.hostedSlots().size() > 0);

        panel.visible = false;
        layoutAndApply(panel, slots);

        for (Slot slot : slots) {
            assertEquals(com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel.HIDDEN_SLOT_POSITION,
                    slot.xPos);
        }
        assertTrue(panel.hostedSlots().isEmpty(), "不可见时不应再声称承接槽位");
    }

    @Test
    void slotOwnershipIsIdentityBased() {
        RepoPanel panel = panel();
        List<Slot> slots = new ArrayList<Slot>();
        layoutAndApply(panel, slots);

        SlotME owned = (SlotME) slots.get(0);
        assertTrue(panel.ownsSlot(owned));
        // 同坐标的另一个槽位对象不属于本面板
        assertTrue(!panel.ownsSlot(new Slot(new InventoryBasic("test", false, 1), 0, owned.xPos, owned.yPos)));
        assertTrue(!panel.ownsSlot(null));
    }

    @Test
    void scrollbarGeometryFollowsTheGrid() {
        RepoPanel panel = panel();
        GuiScrollbar bar = new GuiScrollbar();
        RepoPanel withBar = new RepoPanel(null, new RecordingRepo(), bar, null);
        withBar.x = panel.x;
        withBar.y = panel.y;
        withBar.width = panel.width;
        withBar.height = panel.height;

        List<Slot> slots = new ArrayList<Slot>();
        layoutAndApply(withBar, slots);

        assertEquals(withBar.gridLeft() + RepoPanel.DEFAULT_COLUMNS * RepoPanel.SLOT_SIZE
                + RepoPanel.SCROLLBAR_GAP, bar.getLeft());
        assertEquals(withBar.gridTop(), bar.getTop());
        assertEquals(RepoPanel.DEFAULT_ROWS * RepoPanel.SLOT_SIZE, bar.getHeight());
        assertEquals(RepoPanel.SCROLLBAR_WIDTH, bar.getWidth());
    }

    @Test
    void moduleSwitchesAreRightAlignedAndHitTestInsideTheirBoxes() {
        RepoPanel panel = panel();
        final int[] toggled = { -1 };
        panel.setModuleSwitchHost(new RepoPanel.ModuleSwitchHost() {
            @Override
            public int switchCount() {
                return 3;
            }

            @Override
            public String switchLabel(int index) {
                return "L" + index;
            }

            @Override
            public String switchTooltip(int index) {
                return "T" + index;
            }

            @Override
            public boolean isSwitchOn(int index) {
                return index != 1;
            }

            @Override
            public void toggleSwitch(int index) {
                toggled[0] = index;
            }
        });

        int rowRight = panel.gridLeft() + 18 * RepoPanel.SLOT_SIZE + RepoPanel.SCROLLBAR_GAP
                + RepoPanel.SCROLLBAR_WIDTH;
        int last = 2;
        assertEquals(rowRight - RepoPanel.MODULE_SWITCH_SIZE, panel.moduleSwitchX(last));
        assertEquals(rowRight - 3 * RepoPanel.MODULE_SWITCH_SIZE - 2 * RepoPanel.MODULE_SWITCH_GAP,
                panel.moduleSwitchX(0));
        // 开关行落在搜索框那一行里
        assertEquals(panel.contentTop() + 1, panel.moduleSwitchY());

        int x0 = panel.moduleSwitchX(0) + 5;
        int y0 = panel.moduleSwitchY() + 5;
        assertEquals(0, panel.moduleSwitchAt(x0, y0));
        assertEquals(2, panel.moduleSwitchAt(panel.moduleSwitchX(2) + 1, y0));
        assertEquals(-1, panel.moduleSwitchAt(panel.moduleSwitchX(0) - 4, y0), "开关左侧不是开关");
        assertEquals(-1, panel.moduleSwitchAt(x0, panel.moduleSwitchY() - 4), "开关行上沿之外");

        assertTrue(panel.mouseClicked(x0, y0, 0), "左键点开关由面板消费");
        assertEquals(0, toggled[0]);
        assertFalse(panel.mouseClicked(x0, y0, 1), "右键不切开关");
    }

    @Test
    void moduleSwitchRowShrinksTheSearchField() {
        RepoPanel panel = panel();
        int[] before = panel.searchFieldBounds();
        panel.setModuleSwitchHost(new RepoPanel.ModuleSwitchHost() {
            @Override
            public int switchCount() {
                return 7;
            }

            @Override
            public String switchLabel(int index) {
                return "X";
            }

            @Override
            public String switchTooltip(int index) {
                return "X";
            }

            @Override
            public boolean isSwitchOn(int index) {
                return true;
            }

            @Override
            public void toggleSwitch(int index) {
            }
        });
        int[] after = panel.searchFieldBounds();
        assertEquals(before[0], after[0]);
        assertTrue(after[2] < before[2], "搜索框要给开关行让位");
        assertTrue(after[2] >= 40, "让位后仍不小于下限");
        // 开关行右端不越出内容区
        assertTrue(panel.moduleSwitchX(6) + RepoPanel.MODULE_SWITCH_SIZE
                <= panel.contentLeft() + panel.contentWidth());
    }

    @Test
    void emptyInventoryListIsTolerated() {
        RepoPanel panel = panel();
        List<Slot> slots = new ArrayList<Slot>();
        panel.layoutSlots();
        assertTrue(panel.slotObjects().isEmpty(), "applySlots 之前不该有槽位对象");

        panel.applySlots(slots);

        // 没有其它槽位时 ME 槽就是整个列表，但仍然按"尾部追加"处理
        assertEquals(panel.meSlots().size(), slots.size());
        assertEquals(panel.meSlots().size(), panel.slotObjects().size());
    }
}
