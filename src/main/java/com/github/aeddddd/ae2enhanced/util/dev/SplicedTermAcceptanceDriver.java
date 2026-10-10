package com.github.aeddddd.ae2enhanced.util.dev;

import java.util.ArrayList;
import java.util.List;

import appeng.container.slot.SlotPatternTerm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniAmount;
import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt;
import com.github.aeddddd.ae2enhanced.container.SplicedTermModules;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.SlotHighCapacity;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.gui.terminal.GuiSplicedLayoutEditor;
import com.github.aeddddd.ae2enhanced.client.gui.terminal.GuiSplicedTerm;
import com.github.aeddddd.ae2enhanced.util.dev.ClickPacketTrace;

/**
 * 可拼接终端验收驱动（客户端侧，{@code SplicedTermTestHarness} 的 A 表执行器）。
 *
 * <p>与真实输入<strong>同一条入口</strong>：所有鼠标/键盘动作都通过
 * {@link GuiSplicedTerm#harnessMousePressed}/{@code harnessMouseDragged}/{@code harnessMouseReleased}/
 * {@code harnessShiftClickSlot} 注入，这些方法内部调用的就是
 * {@code mouseClicked}/{@code mouseClickMove}/{@code mouseReleased}/{@code handleMouseClick}，
 * 所以事件转发链本身也在被验证。</p>
 *
 * <p>每项一步：进入该步时执行动作，{@value #GAP_TICKS} tick 后断言，然后继续下一项（失败不中断）。
 * 结果由 {@link SplicedTermTestHarness#recordAccept} 汇总，最后打印
 * {@link SplicedTermTestHarness#ACCEPT_ALL_DONE_MARKER}。</p>
 *
 * <p>本类只在客户端加载（{@code value = Side.CLIENT}），因此可以自由引用客户端类型。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
final class SplicedTermAcceptanceDriver {

    /** 每步动作与断言之间的间隔（tick）。 */
    private static final int GAP_TICKS = 25;
    /** 需要等服务端同步（NBT / 槽位 / panelMask）时的额外等待。 */
    private static final int SYNC_TICKS = 45;
    /** 进世界后等待多久（tick）再用真实右键打开终端：100 tick ≈ 5 秒。 */
    private static final int OPEN_DELAY_TICKS = 100;
    /** 等待终端物品同步 / GUI 出现的上限（tick）。 */
    private static final int TIMEOUT_TICKS = 300;

    private static final int PHASE_WAIT_WORLD = 0;
    private static final int PHASE_WAIT_GUI = 1;
    private static final int PHASE_A3 = 2;
    private static final int PHASE_A4 = 3;
    private static final int PHASE_A4_CHECK = 4;
    private static final int PHASE_A4_CLEAR = 5;
    private static final int PHASE_A5 = 6;
    private static final int PHASE_A5_CHECK = 7;
    private static final int PHASE_A6 = 8;
    private static final int PHASE_A6_CHECK = 9;
    private static final int PHASE_A7 = 10;
    private static final int PHASE_A7_CHECK = 11;
    private static final int PHASE_A9 = 12;
    private static final int PHASE_A9_CHECK = 13;
    private static final int PHASE_A8 = 14;
    private static final int PHASE_A8_CHECK = 15;
    private static final int PHASE_A10 = 16;
    private static final int PHASE_A10_CHECK = 17;
    private static final int PHASE_A11 = 18;
    private static final int PHASE_A11_CHECK = 19;
    private static final int PHASE_A11_RESTORE = 20;
    private static final int PHASE_A11_RESTORE_CHECK = 21;
    private static final int PHASE_A12 = 22;
    private static final int PHASE_A12_CHECK = 23;
    private static final int PHASE_A12_ON = 24;
    private static final int PHASE_A12_ON_CHECK = 25;
    private static final int PHASE_A13 = 26;
    private static final int PHASE_A13_CHECK = 27;
    private static final int PHASE_A14 = 28;
    private static final int PHASE_A14_CHECK = 29;
    private static final int PHASE_A16 = 30;
    private static final int PHASE_A16_CHECK = 31;
    private static final int PHASE_A18 = 32;
    private static final int PHASE_A18_CHECK = 33;
    private static final int PHASE_A21 = 34;
    private static final int PHASE_A21_REOPEN = 35;
    private static final int PHASE_A21_CHECK = 36;
    private static final int PHASE_A22_OPEN = 37;
    private static final int PHASE_A22_RESET = 38;
    private static final int PHASE_A22_UNDO = 39;
    private static final int PHASE_A22_CANCEL = 40;
    private static final int PHASE_A22_REOPEN = 41;
    private static final int PHASE_A22_DONE = 42;
    private static final int PHASE_A23 = 43;
    private static final int PHASE_A23_CHECK = 44;
    private static final int PHASE_A3B = 45;
    private static final int PHASE_A15 = 46;
    private static final int PHASE_A15_CHECK = 47;
    private static final int PHASE_A17 = 48;
    private static final int PHASE_A17_CHECK = 49;
    private static final int PHASE_A19 = 50;
    private static final int PHASE_A19_CHECK = 51;
    private static final int PHASE_A19_BACK = 52;
    private static final int PHASE_A19_BACK_CHECK = 53;
    private static final int PHASE_A20 = 54;
    private static final int PHASE_A25 = 55;
    private static final int PHASE_A26 = 56;
    private static final int PHASE_A27 = 57;
    private static final int PHASE_A28 = 58;
    private static final int PHASE_A29 = 59;
    private static final int PHASE_A30 = 60;
    private static final int PHASE_FINISHED = 61;
    /** A25 重开拼接终端后保持的 tick 数（约 45s，供外部截图四窗口默认布局）。 */
    private static final int TERMINAL_SHOT_TICKS = 900;
    /** A26 打开数量弹窗后保持的 tick 数（约 60s，供外部截图）。 */
    private static final int AMOUNT_DIALOG_HOLD_TICKS = 1200;
    /** A27 打开布局编辑器后保持的 tick 数（约 30s，供外部截图）。 */
    private static final int EDITOR_SHOT_TICKS = 600;
    /** A28 造出"窗口互相遮挡 + 抬升"后的保持 tick 数（约 45s，供外部截图 HEI 避让 + 物品层级）。 */
    private static final int OVERLAP_SHOT_TICKS = 1200;

    private static int phase = PHASE_WAIT_WORLD;
    private static int ticks;
    private static int clientTicks;
    private static int lostTicks;

    // --- 跨步保存的观测值 ---
    private static String baselineLayoutJson;
    private static String baselineRootBounds;
    private static int baselineRootCount;
    private static int baselineRepoColumns;
    private static String baselinePanelRect;
    private static int baselineMask;
    private static String savedLayoutJson;
    private static String a12ModuleId;
    private static String a13ModuleId;
    private static int a14OutputSlot = -1;
    /** A14 的点击/记录只做一次（该阶段每 tick 递增一次 ticks，不能再用 ticks == 常量 判定）。 */
    private static boolean a14Clicked;
    private static boolean a14Recorded;
    private static int a16StorageDiamondCount = -1;
    private static int a16InvDiamondCount = -1;
    /** A16/A31：shift-click 命中的槽位号与当时是否"可达"（面板可见且未被更高窗口盖住）。 */
    private static int a16SlotIndex = -1;
    private static boolean a16SlotReachable;
    private static int a17ViewSlot = -1;
    private static int a17SourceSlot = -1;
    /** A22：点 RESET 之前的编辑态布局快照（undo 的期望值）。 */
    private static String a22PreResetJson;
    /** A22-cancel：点 CANCEL 之前的布局（排障用），与 baseline / 点后三者对比可定位是哪一步没生效。 */
    private static String a22BeforeCancelJson;
    /** A22-cancel：每一步的编辑态/屏幕快照（排障用）。 */
    private static String a22CancelNote = "";
    /** A17：前置动作说明（面板可见性恢复）。 */
    private static String a17PrepNote = "";
    /** A17：断言 + 取回 shift-click 只做一次（避免 SYNC_TICKS 与 SYNC_TICKS+20 两次命中把物品又塞回去）。 */
    private static boolean a17Evaluated;
    private static int lastSwitchIndex = -1;
    private static String lastSwitchId = "?";
    private static String stepNote = "";
    /** A22 打开编辑器期间，父终端的引用（断言布局 JSON 用）。 */
    private static GuiSplicedTerm terminalGui;
    /** 最近一次见到终端 GUI 的引用（编辑器 parent 缺失时的兜底）。 */
    private static GuiSplicedTerm lastKnownTerminal;

    // --- E 系（独立服务端）状态 ---
    /** 模式是否已判定（首次进世界时判一次）。 */
    private static boolean eModeDecided;
    /** E 系前置：主菜单自动连独立服务端是否已经试过（断线后可重置重试）。 */
    private static boolean eAutoConnectTried;
    /** E 系前置：自动连接尝试次数（上限 3，防止服务端没起来时无限重连）。 */
    private static int eAutoConnectAttempts;
    // --- A 系（内置服务端）自动进世界 ---
    /** 主菜单停留多少 tick 后再自动进世界（100 tick ≈ 5s，等主菜单/资源就绪）。 */
    private static final int AUTO_WORLD_DELAY_TICKS = 100;
    /** 自动进世界的存档显示名（存档不存在时新建的显示名也是它）。 */
    private static final String AUTO_WORLD_NAME = "New World";
    /**
     * 自动进世界的存档文件夹名。
     *
     * <p>同一个 id 既用于"进入已有存档"，也用于"新建存档"：{@code launchIntegratedServer}
     * 在 {@code level.dat} 不存在而传入的 {@link net.minecraft.world.WorldSettings} 非空时会
     * 按这份设置创建世界（创造 + 作弊），因此不需要自己走"新建世界"的 GUI 流程。</p>
     */
    private static final String AUTO_WORLD_LEVEL_ID = "New World-";
    /** 自动进世界是否已经尝试过（只试一次，失败就停在主菜单并打日志）。 */
    private static boolean autoWorldTried;
    /** 自动进世界前的等待计数（只在主菜单里累加）。 */
    private static int autoWorldWaitTicks;
    /** true = 连的是独立（专用）服务端，跑 E1-E4。 */
    private static boolean eMode;
    private static int eStep;
    private static int eStepTicks;
    private static boolean eDone;
    /** E1 打开时读到的终端物品 NBT 布局（E3 的基线之一）。 */
    private static String eLayoutAtOpen;
    /** E3 拖窗口前的布局（拖后必须变）。 */
    private static String eLayoutBeforeDrag;
    /** E3 伪造包之后必须仍然等于它。 */
    private static String eLayoutAfterDrag;
    /** E4 滚动/排序前的仓库首项，用于判断"新一页/新排序确实到了客户端"。 */
    private static String eFirstBefore;
    /** E4 搜索前的更新通知计数（增量更新链路的基线）。 */
    private static int eNotifyAtOpen;
    /** E2 隐藏面板前的 panelMask（对照用）。 */
    private static int eMaskBefore;
    // --- G3（大网络分页耗时） ---
    private static int eG3BaseTotal;
    private static int eG3GrownTotal;
    private static long eG3NarrowBefore;
    private static long eG3FullBefore;
    private static long eG3NarrowAfter;
    private static long eG3FullAfter;
    /** G3 测量中间态：这次探测期望的 repo 总数（-1 = 任意变化即算到达），0 表示空闲。 */
    private static int eRttExpectSize;
    /** G3 测量起始时刻（纳秒；0 = 空闲）。 */
    private static long eRttStartNanos;
    /** G3 测量起点处的 repo 状态（用于"任意变化"判定）。 */
    private static String eRttBaseline;
    /** G3 结果（毫秒；-1 = 未完成）。 */
    private static long eRttResult = -1;
    /** E 系压力测试：计划点击次数 / 已进入计数 / 实际发出次数。 */
    private static final int E_STRESS_CLICKS = 60;
    private static int eStressClicks;
    /** 客户端点击洪流只告警一次。 */
    private static boolean warnedClientClickFlood;
    private static int eStressSent;
    /** E 系压力测试：风暴前的仓库规模（关键词无关的活性基线）。 */
    private static int eStressRepoBase;
    /** E 系压力测试：搜索已复位、正在等整页回来的标记 / 已等待 tick 数。 */
    private static boolean eStressRestoreArmed;
    private static int eStressRestoreTicks;
    /** E 系压力测试：窄查询往返耗时（毫秒）。 */
    private static long eStressLastRtt;

    private SplicedTermAcceptanceDriver() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !SplicedTermTestHarness.enabled()) {
            return;
        }
        tick();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (autoWorldEnabled()) {
            // A 系入口：自动进（必要时建）内置世界。与下面的"自动连独立服务端"互斥——
            // 两者是 A 表 / E 表各自的入口，同时生效只会互相打断。
            tryAutoWorld(mc);
        } else if (!eAutoConnectTried && eAutoConnectAttempts < 3 && mc.world == null
                && (mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu
                    || mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected)) {
            // E 系（独立服务端）前置：自动连 localhost。
            // 目的是把"进服务器"这一步从 OS 级鼠标导航里拿出来：日志/断言都在服务端与客户端各自的
            // 进程里跑，开发机上不需要人工点菜单（等价于服务器列表里点 Join Server 的同一条链路）。
            // 断线屏（服务端还没起来/中途重启）也允许重连，上限 3 次，避免抖动就让整轮脚本作废。
            eAutoConnectTried = true;
            eAutoConnectAttempts++;
            logNote("E 系：自动连接 harness-dedicated（localhost:25565，第 " + eAutoConnectAttempts + " 次）");
            mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(mc.currentScreen, mc,
                    new net.minecraft.client.multiplayer.ServerData("harness-dedicated", "localhost:25565", false)));
        }
        if (mc.world == null || mc.player == null) {
            phase = PHASE_WAIT_WORLD;
            ticks = 0;
            clientTicks = 0;
            lostTicks = 0;
            return;
        }
        if (phase >= PHASE_FINISHED) {
            return;
        }
        clientTicks++;
        ClickPacketTrace.rollClientTick();
        if (clientTicks % 40 == 0) {
            logNote("click-trace(客户端 JVM) " + ClickPacketTrace.summary());
        }
        if (!warnedClientClickFlood && ClickPacketTrace.clientCallsThisTick() > ClickPacketTrace.FLOOD_THRESHOLD) {
            warnedClientClickFlood = true;
            logNote("客户端单 tick windowClick 超过 " + ClickPacketTrace.FLOOD_THRESHOLD
                    + " 次——打印一次调用栈：" + new Throwable("client click flood"));
        }
        GuiSplicedTerm gui = currentGui(mc);

        // E 系（专用服务端）与 A 系（内置服务端）走不同的验收表：
        // E 系只验"真的过网络"的那几件事，不跑 A 表（A 表大量断言依赖同 JVM 的服务端静态值）。
        if (!eModeDecided) {
            eModeDecided = true;
            eMode = !mc.isSingleplayer();
            logNote("模式判定：单机=" + mc.isSingleplayer()
                    + " → " + (eMode ? "E 系（独立服务端，跑 E1-E4）" : "A 系（内置服务端）"));
        }
        if (eMode) {
            tickE(mc, gui);
            return;
        }

        // 从 A4 起必须停在终端界面里：界面不在就等（玩家自己打开别的界面不该打断验收）。
        // A21（关 GUI 重开）与 A23（旧终端）本来就要离开终端界面，跳过守卫自己管。
        // A22 会打开"布局编辑器"（另一个 Screen）：这时 gui 为 null 是正常的，用父终端引用继续断言
        if (mc.currentScreen instanceof GuiSplicedLayoutEditor && terminalGui == null) {
            terminalGui = findParentTerminal(mc);
        }
        if (mc.currentScreen instanceof GuiSplicedTerm) {
            terminalGui = (GuiSplicedTerm) mc.currentScreen;
            lastKnownTerminal = terminalGui;
        }
        boolean needsTerminalGui = phase >= PHASE_A4 && phase != PHASE_A21 && phase != PHASE_A21_REOPEN
                && phase != PHASE_A23 && phase != PHASE_A25 && phase != PHASE_A26 && phase != PHASE_A27
                && phase != PHASE_A28 && phase != PHASE_A29 && phase != PHASE_A30
                && !(mc.currentScreen instanceof GuiSplicedLayoutEditor)
                && !(phase >= PHASE_A19 && phase <= PHASE_A19_CHECK);
        if (needsTerminalGui) {
            if (gui == null) {
                if (++lostTicks > TIMEOUT_TICKS) {
                    SplicedTermTestHarness.recordAccept("A2", false,
                            "脚本执行中终端 GUI 消失超过 " + TIMEOUT_TICKS + " tick（phase=" + phase + "）");
                    finish();
                }
                return;
            }
            lostTicks = 0;
        }

        switch (phase) {
            case PHASE_WAIT_WORLD:
                if (clientTicks < OPEN_DELAY_TICKS) {
                    return;
                }
                if (!openTerminal(mc)) {
                    if (clientTicks > OPEN_DELAY_TICKS + TIMEOUT_TICKS) {
                        SplicedTermTestHarness.recordAccept("A2", false,
                                "快捷栏里没有可拼接终端（服务端布景是否成功？）");
                        finish();
                    }
                    return;
                }
                phase = PHASE_WAIT_GUI;
                ticks = 0;
                return;

            case PHASE_WAIT_GUI:
                if (gui != null) {
                    SplicedTermTestHarness.recordAccept("A2", true, "GUI 15 已打开；模块 " + terminalGuiRef(gui).harnessPanels().size()
                            + " 个，可见=" + gui.dock().visibleModuleIds());
                    SplicedTermTestHarness.recordAccept("A2-geometry", true, geometrySnapshot(gui));
                    phase = PHASE_A3;
                    ticks = 0;
                    return;
                }
                if (++ticks > TIMEOUT_TICKS) {
                    SplicedTermTestHarness.recordAccept("A2", false,
                            "右键后 " + TIMEOUT_TICKS + " tick 内 GUI 未出现（无线接入点未联网 / 超范围 / 未绑定）");
                    finish();
                }
                return;

            case PHASE_A3:
                // 首屏分页包要等一两个 tick 才到，这里先等 GAP_TICKS 再断言，避免把"还没到"判成"没有"
                // 注意：HEI/JEI 在场时 Screen 可能被重建，gui 可能短暂为 null，必须走 terminalGuiRef 兜底
                GuiSplicedTerm a3Gui = terminalGuiRef(gui);
                if (a3Gui == null) {
                    return;
                }
                if (ticks == GAP_TICKS) {
                    int size = a3Gui.harnessRepoSize();
                    int expected = SplicedTermTestHarness.stagedItemTypeCount;
                    SplicedTermTestHarness.recordAccept("A3", expected > 0 && size == expected,
                            "仓库总数=" + size + "（服务端注入 " + expected + " 种）");
                    baselineLayoutJson = a3Gui.harnessLayoutJson();
                    baselineRootBounds = a3Gui.harnessRootBoundsOf("repo");
                    baselineRootCount = a3Gui.harnessRootCount();
                }
                if (++ticks >= GAP_TICKS * 2) {
                    phase = PHASE_A3B;
                    ticks = 0;
                }
                return;

            case PHASE_A3B:
                // 2d：137 种注入物品里有没有"没有模型"的（会渲染成紫黑格）
                if (ticks == 0) {
                    SplicedTermTestHarness.recordAccept("A3b", true, missingModelReport());
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A4;
                    ticks = 0;
                }
                return;

            case PHASE_A4:
                if (ticks == 0) {
                    gui.harnessSetSearch("iron");
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A4_CHECK;
                    ticks = 0;
                }
                return;

            case PHASE_A4_CHECK:
                if (ticks == 0) {
                    int size = terminalGuiRef(gui).harnessRepoSize();
                    SplicedTermTestHarness.recordAccept("A4", size == 4,
                            "搜索 \"iron\" 后仓库总数=" + size + "（期望 4：iron_ingot/iron_block/iron_ore/iron_door）");
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A4_CLEAR;
                    ticks = 0;
                }
                return;

            case PHASE_A4_CLEAR:
                if (ticks == 0) {
                    gui.harnessSetSearch("");
                    // 一页能放下的物品数随窗口大小变化：先缩小窗口，保证"总数 > 一页"，滚动才有意义
                    int[] corner = bottomRightCorner(gui);
                    if (corner != null && gui.harnessMaxScrollRows() <= 0) {
                        gui.harnessMousePressed(corner[0], corner[1], 0);
                        dragMove(gui, corner[0], corner[1], -260, -180, 8);
                        gui.harnessMouseReleased(corner[0] - 260, corner[1] - 180, 0);
                    }
                }
                if (++ticks >= GAP_TICKS) {
                    int size = terminalGuiRef(gui).harnessRepoSize();
                    int page = gui.harnessPageCapacity();
                    int maxScroll = gui.harnessMaxScrollRows();
                    stepNote = "全量=" + size + " 一页=" + page + " 可滚动=" + maxScroll + " 行";
                    SplicedTermTestHarness.recordAccept("A5-setup", maxScroll > 0,
                            stepNote + "（分页前提：总数 > 一页）");
                    phase = PHASE_A5;
                    ticks = 0;
                }
                return;

            case PHASE_A5:
                if (ticks == 0) {
                    int scroll = gui.harnessScroll(12);
                    SplicedTermTestHarness.recordAccept("A5", scroll > 0,
                            "滚动 12 格 → 滚动条位置 " + scroll + "（" + stepNote + "）");
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A6;
                    ticks = 0;
                }
                return;

            case PHASE_A6:
                if (ticks == 0) {
                    String cycle = gui.harnessCycleSort();
                    SplicedTermTestHarness.recordAccept("A6", cycle.contains("->") && !cycle.contains("(failed)"),
                            "排序切换 " + cycle);
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A7;
                    ticks = 0;
                }
                return;

            // ---------------- A7：拖标题栏移动窗口 ----------------
            case PHASE_A7:
                if (ticks == 0) {
                    baselineRootBounds = gui.harnessRootBoundsOf("repo");
                    int[] point = gui.harnessTitleDragPoint("repo");
                    if (point == null) {
                        SplicedTermTestHarness.recordAccept("A7", false, "找不到 Storage Repo 标题栏可拖点");
                        phase = PHASE_A9;
                        ticks = 0;
                        return;
                    }
                    String hit = gui.harnessDescribeHit(point[0], point[1]);
                    // 同时记录"标题栏正中"的命中情况：真机 CU 拖拽就是抓这里的
                    ModulePanel repoPanel = terminalGuiRef(gui).harnessPanel("repo");
                    String centerHit = repoPanel == null ? "n/a"
                            : gui.harnessDescribeHit(repoPanel.x + repoPanel.width / 2,
                                    repoPanel.y + ModulePanel.TITLE_BAR_HEIGHT / 2);
                    SplicedTermTestHarness.recordAccept("A7-probe", true,
                            "标题栏安全点{" + hit + "}；标题栏正中{" + centerHit + "}");
                    gui.harnessMousePressed(point[0], point[1], 0);
                    String afterPress = gui.harnessGestureState();
                    dragTo(gui, point[0], point[1], 160, 40, 10);
                    String afterMove = gui.harnessGestureState();
                    String after = gui.harnessRootBoundsOf("repo");
                    stepNote = "press{" + afterPress + "} move{" + afterMove + "}";
                    SplicedTermTestHarness.recordAccept("A7", !baselineRootBounds.equals(after),
                            "hit{" + hit + "} before=" + baselineRootBounds + " after=" + after + " " + stepNote);
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A9;
                    ticks = 0;
                }
                return;

            // ---------------- A9：Alt+拖区段标题拆出独立窗口 ----------------
            case PHASE_A9:
                if (ticks == 0) {
                    baselineRootCount = terminalGuiRef(gui).harnessRootCount();
                    int[] point = gui.harnessTitleDragPoint("crafting");
                    if (point == null) {
                        SplicedTermTestHarness.recordAccept("A9", false, "找不到 Crafting 标题栏可拖点");
                        phase = PHASE_A8;
                        ticks = 0;
                        return;
                    }
                    gui.harnessSetAltOverride(Boolean.TRUE);
                    gui.harnessMousePressed(point[0], point[1], 0);
                    String afterPress = gui.harnessGestureState();
                    dragMove(gui, point[0], point[1], 0, 70, 8);
                    String afterMove = gui.harnessGestureState();
                    int midRoots = terminalGuiRef(gui).harnessRootCount();
                    // 松手位置必须落在所有窗口之外（空白区），否则 drop 候选会把刚拆出来的分片又拼回去
                    int[] empty = emptySpot(gui);
                    dragMove(gui, point[0], point[1] + 70, empty[0] - point[0], empty[1] - point[1] - 70, 6);
                    String afterMove2 = gui.harnessGestureState();
                    gui.harnessMouseReleased(empty[0], empty[1], 0);
                    afterMove = afterMove + " -> " + afterMove2;
                    gui.harnessSetAltOverride(null);
                    int after = terminalGuiRef(gui).harnessRootCount();
                    stepNote = "press{" + afterPress + "} move{" + afterMove + "} midRoots=" + midRoots;
                    SplicedTermTestHarness.recordAccept("A9", after == baselineRootCount + 1,
                            "Alt+拖 Crafting 标题：root " + baselineRootCount + " → " + after + " " + stepNote);
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A8;
                    ticks = 0;
                }
                return;

            // ---------------- A8：把拆出来的窗口拖到另一个窗口边缘拼回去 ----------------
            case PHASE_A8:
                if (ticks == 0) {
                    baselineRootCount = terminalGuiRef(gui).harnessRootCount();
                    int[] point = gui.harnessTitleDragPoint("crafting");
                    ModulePanel target = terminalGuiRef(gui).harnessPanel("repo");
                    if (point == null || target == null) {
                        SplicedTermTestHarness.recordAccept("A8", false, "缺少拖拽起点或拼接目标面板");
                        phase = PHASE_A10;
                        ticks = 0;
                        return;
                    }
                    // 目标：repo 面板左边缘的中间（dropZone = clamp(8..16, min(w,h)/8)）
                    int dropX = target.x + 4;
                    int dropY = target.y + target.height / 2;
                    // 目标点必须落在"被拖窗口之外"的另一个 root 上，drop 候选才会产生
                    if (gui.harnessDescribeHit(dropX, dropY).contains("panel=crafting")) {
                        dropY = target.y + target.height / 4;
                    }
                    gui.harnessMousePressed(point[0], point[1], 0);
                    dragMove(gui, point[0], point[1], dropX - point[0], dropY - point[1], 12);
                    String gesture = gui.harnessGestureState();
                    int midRoots = terminalGuiRef(gui).harnessRootCount();
                    gui.harnessMouseReleased(dropX, dropY, 0);
                    gesture = gesture + " midRoots=" + midRoots;
                    int after = terminalGuiRef(gui).harnessRootCount();
                    SplicedTermTestHarness.recordAccept("A8", after == baselineRootCount - 1,
                            "拖到 repo 左边缘 " + dropX + "," + dropY + "：root " + baselineRootCount
                                    + " → " + after + " gesture{" + gesture + "}");
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A10;
                    ticks = 0;
                }
                return;

            // ---------------- A10：拖分隔条 ----------------
            case PHASE_A10:
                if (ticks == 0) {
                    // 分隔条要有可拖动余量：先通过右下角握把把窗口放大一点（这也是 A11 的"变大"方向）
                    int[] corner = bottomRightCorner(gui);
                    if (corner != null) {
                        String before = gui.harnessRootBoundsOf("repo");
                        gui.harnessMousePressed(corner[0], corner[1], 0);
                        dragMove(gui, corner[0], corner[1], 200, 130, 8);
                        gui.harnessMouseReleased(corner[0] + 200, corner[1] + 130, 0);
                        SplicedTermTestHarness.recordAccept("A10-grow", true,
                                "先放大窗口 " + before + " → " + gui.harnessRootBoundsOf("repo"));
                    }
                }
                if (ticks == GAP_TICKS) {
                    baselineLayoutJson = terminalGuiRef(gui).harnessLayoutJson();
                    baselinePanelRect = gui.harnessPanelRect("repo");
                    int[] divider = findDivider(gui);
                    if (divider == null) {
                        SplicedTermTestHarness.recordAccept("A10", false, "当前布局里找不到可拖的分隔条");
                        phase = PHASE_A11;
                        ticks = 0;
                        return;
                    }
                    gui.harnessMousePressed(divider[0], divider[1], 0);
                    String afterPress = gui.harnessGestureState();
                    int dx = divider[2] == 1 ? 40 : 0;
                    int dy = divider[2] == 1 ? 0 : 40;
                    dragMove(gui, divider[0], divider[1], dx, dy, 8);
                    String afterMove = gui.harnessGestureState();
                    gui.harnessMouseReleased(divider[0] + dx, divider[1] + dy, 0);
                    String after = terminalGuiRef(gui).harnessLayoutJson();
                    String rectAfter = gui.harnessPanelRect("repo");
                    SplicedTermTestHarness.recordAccept("A10", !baselineLayoutJson.equals(after),
                            "分隔条@" + divider[0] + "," + divider[1] + " press{" + afterPress + "} move{"
                                    + afterMove + "} 拖动后布局变化；"
                                    + "repo " + baselinePanelRect + " → " + rectAfter);
                }
                if (++ticks >= GAP_TICKS * 2) {
                    phase = PHASE_A11;
                    ticks = 0;
                }
                return;

            // ---------------- A11：拖右下角握把缩放（可变列验证） ----------------
            case PHASE_A11:
                if (ticks == 0) {
                    baselineRootBounds = gui.harnessRootBoundsOf("repo");
                    baselineRepoColumns = repoColumns(gui);
                    int[] corner = bottomRightCorner(gui);
                    if (corner == null) {
                        SplicedTermTestHarness.recordAccept("A11", false, "找不到 root 右下角握把");
                        phase = PHASE_A12;
                        ticks = 0;
                        return;
                    }
                    gui.harnessMousePressed(corner[0], corner[1], 0);
                    String pressState = gui.harnessGestureState();
                    dragMove(gui, corner[0], corner[1], -70, -50, 8);
                    String midMove = gui.harnessGestureState();
                    gui.harnessMouseReleased(corner[0] - 70, corner[1] - 50, 0);
                    String after = gui.harnessRootBoundsOf("repo");
                    int columns = repoColumns(gui);
                    stepNote = "press{" + pressState + "} move{" + midMove + "}";
                    SplicedTermTestHarness.recordAccept("A11", !baselineRootBounds.equals(after)
                                    && columns >= 0 && columns != baselineRepoColumns,
                            "缩小 root " + baselineRootBounds + " → " + after
                                    + "；repo 列数 " + baselineRepoColumns + " → " + columns + " " + stepNote);
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A11_RESTORE;
                    ticks = 0;
                }
                return;

            case PHASE_A11_RESTORE:
                if (ticks == 0) {
                    int[] corner = bottomRightCorner(gui);
                    if (corner != null) {
                        gui.harnessMousePressed(corner[0], corner[1], 0);
                        dragMove(gui, corner[0], corner[1], 70, 50, 8);
                        gui.harnessMouseReleased(corner[0] + 70, corner[1] + 50, 0);
                    }
                    SplicedTermTestHarness.recordAccept("A11-restore", gui.harnessRootBoundsOf("repo") != null,
                            "握把拖回：" + baselineRootBounds + " → " + gui.harnessRootBoundsOf("repo"));
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A12;
                    ticks = 0;
                }
                return;

            // ---------------- A12：模块开关行 ----------------
            case PHASE_A12:
                if (ticks == 0) {
                    a12ModuleId = pickSwitchModule(gui);
                    if (a12ModuleId == null) {
                        SplicedTermTestHarness.recordAccept("A12", false, "开关行里找不到可切换的模块");
                        phase = PHASE_A13;
                        ticks = 0;
                        return;
                    }
                    baselineMask = gui.harnessPanelMask();
                    int[] point = switchPoint(gui, a12ModuleId);
                    if (point == null) {
                        SplicedTermTestHarness.recordAccept("A12", false, "算不出 " + a12ModuleId + " 的开关坐标");
                        phase = PHASE_A13;
                        ticks = 0;
                        return;
                    }
                    gui.harnessClick(point[0], point[1]);
                    stepNote = "开关#" + lastSwitchIndex + "(" + lastSwitchId + ")@" + point[0] + "," + point[1];
                }
                if (++ticks >= SYNC_TICKS) {
                    boolean visible = terminalGuiRef(gui).harnessModuleVisible(a12ModuleId);
                    int mask = gui.harnessPanelMask();
                    SplicedTermTestHarness.recordAccept("A12", !visible && mask != baselineMask,
                            "点击 " + a12ModuleId + " 开关 " + stepNote + "：可见=" + visible
                                    + " panelMask " + baselineMask + " → " + mask);
                    phase = PHASE_A12_ON;
                    ticks = 0;
                }
                return;

            case PHASE_A12_ON:
                if (ticks == 0) {
                    int[] point = switchPoint(gui, a12ModuleId);
                    if (point != null) {
                        gui.harnessClick(point[0], point[1]);
                    }
                }
                if (++ticks >= SYNC_TICKS) {
                    SplicedTermTestHarness.recordAccept("A12-restore",
                            terminalGuiRef(gui).harnessModuleVisible(a12ModuleId),
                            "再次点击开关恢复 " + a12ModuleId + "：可见=" + terminalGuiRef(gui).harnessModuleVisible(a12ModuleId));
                    phase = PHASE_A13;
                    ticks = 0;
                }
                return;

            // ---------------- A13：标题栏关闭按钮 ----------------
            case PHASE_A13:
                if (ticks == 0) {
                    ModulePanel repo = terminalGuiRef(gui).harnessPanel("repo");
                    boolean repoHasClose = repo != null && repo.closeVisible();
                    a13ModuleId = pickClosableModule(gui);
                    if (a13ModuleId == null) {
                        SplicedTermTestHarness.recordAccept("A13", false, "没有可关闭的模块（repo/背包都不可关）");
                        phase = PHASE_A14;
                        ticks = 0;
                        return;
                    }
                    ModulePanel panel = terminalGuiRef(gui).harnessPanel(a13ModuleId);
                    int cx = panel.closeButtonX() + ModulePanel.TITLE_BAR_HEIGHT / 4;
                    int cy = panel.closeButtonY() + ModulePanel.TITLE_BAR_HEIGHT / 4;
                    boolean hit = panel.inCloseButton(cx, cy);
                    gui.harnessClick(cx, cy);
                    stepNote = "repo关闭按钮=" + repoHasClose + " 点@" + cx + "," + cy
                            + " inCloseButton=" + hit + " closable=" + panel.closable()
                            + " closeVisible=" + panel.closeVisible()
                            + " 点后立刻可见=" + terminalGuiRef(gui).harnessModuleVisible(a13ModuleId)
                            + " gesture{" + gui.harnessGestureState() + "}";
                }
                ticks++;
                if (ticks == SYNC_TICKS) {
                    boolean visible = terminalGuiRef(gui).harnessModuleVisible(a13ModuleId);
                    SplicedTermTestHarness.recordAccept("A13", !visible,
                            "点 " + a13ModuleId + " 关闭按钮：" + stepNote + " → 可见=" + visible);
                    ModulePanel panel = terminalGuiRef(gui).harnessPanel(a13ModuleId);
                    if (panel != null) {
                        gui.dock().revealModule(panel);
                    }
                }
                if (ticks >= SYNC_TICKS + GAP_TICKS) {
                    phase = PHASE_A14;
                    ticks = 0;
                }
                return;

            // ---------------- A14：合成台放材料出预览产物 ----------------
            case PHASE_A14:
                if (ticks == 0) {
                    a14Clicked = false;
                    a14Recorded = false;
                    // A8 的"拆出→拼回"会让宿主窗口采用被拖窗口的几何，repo 窗口因此变大到盖住
                    // 背包窗口（实机：418,235 520x244 与 [748,203 176x138] 重叠）——这时槽位点击
                    // 会被路由到最上层的那个窗口，A14/A15/A16 的背包槽点击就落空了。
                    // 这些断言验的是"槽位交互"，先恢复默认布局拿到确定状态，再点槽位。
                    terminalGuiRef(gui).dock().resetLayout();
                    logNote("A14 前恢复默认布局：" + geometrySnapshot(terminalGuiRef(gui)));
                }
                // 注意：本 case 只用一次 ticks++（见尾部），因此不能用 ticks == 常量 判定阶段
                if (!a14Clicked && ticks >= GAP_TICKS) {
                    a14Clicked = true;
                    int logSlot = findInventorySlotItem(gui, "log");
                    int matrixSlot = findCraftingMatrixSlot(gui);
                    a14OutputSlot = findCraftingOutputSlot(gui);
                    if (logSlot < 0 || matrixSlot < 0 || a14OutputSlot < 0) {
                        a14Recorded = true;
                        SplicedTermTestHarness.recordAccept("A14", false,
                                "缺少背包原木/合成矩阵/输出槽（log=" + logSlot + " matrix=" + matrixSlot
                                        + " out=" + a14OutputSlot + "）");
                        phase = PHASE_A16;
                        ticks = 0;
                        return;
                    }
                    clickSlot(gui, logSlot);
                    clickSlot(gui, matrixSlot);
                    stepNote = "原木从背包槽 " + logSlot + " → 矩阵槽 " + matrixSlot;
                }
                if (a14Clicked && !a14Recorded && ticks >= SYNC_TICKS) {
                    a14Recorded = true;
                    ItemStack out = slotStack(gui, a14OutputSlot);
                    boolean ok = !out.isEmpty() && out.getCount() > 0;
                    SplicedTermTestHarness.recordAccept("A14", ok,
                            stepNote + "；输出槽=" + describe(out) + "（期望 原木→木板 预览）");
                }
                if (++ticks >= SYNC_TICKS + GAP_TICKS) {
                    phase = PHASE_A16;
                    ticks = 0;
                }
                return;

            // ---------------- A16：shift-click 背包物品进存储槽 ----------------
            case PHASE_A16:
                if (ticks == 0) {
                    a16StorageDiamondCount = countStorageItem(gui, "diamond");
                    a16InvDiamondCount = countInventoryItem(gui, "diamond");
                    SplicedTermTestHarness.recordAccept("A16-pre", true, "点击前 " + slotDigest(gui));
                    int slot = findInventorySlotItem(gui, "diamond");
                    if (slot < 0) {
                        SplicedTermTestHarness.recordAccept("A16", false, "背包里没有钻石可搬运");
                        phase = PHASE_A18;
                        ticks = 0;
                        return;
                    }
                    net.minecraft.inventory.Slot diamond = slotAt(gui, slot);
                    a16SlotIndex = slot;
                    a16SlotReachable = gui.harnessSlotReachable(slot);
                    logNote("shiftClick #" + slot + " @"
                            + (diamond == null ? "-" : (diamond.xPos + 8) + "," + (diamond.yPos + 8))
                            + " reachable=" + a16SlotReachable
                            + " → " + (diamond == null ? "-"
                                    : gui.harnessDescribeHit(diamond.xPos + 8, diamond.yPos + 8)));
                    gui.harnessShiftClickSlot(slot);
                }
                ticks++;
                if (ticks == SYNC_TICKS) {
                    int storageAfter = countStorageItem(gui, "diamond");
                    int invAfter = countInventoryItem(gui, "diamond");
                    // 与旧终端一致（同一份 ContainerSplicedTerm.transferStackInSlot）：
                    // 玩家侧 shift-click 先进 ME 网络（transferStackToContainer），装不下的才进承接槽位；
                    // 空元件/满元件时才会落到 27 存储槽。因此验收点＝"物品离开背包且没进存储槽"。
                    boolean ok = invAfter < a16InvDiamondCount && storageAfter == a16StorageDiamondCount;
                    SplicedTermTestHarness.recordAccept("A16", ok,
                            "shift-click 背包钻石：背包 " + a16InvDiamondCount + " → " + invAfter
                                    + "（进 ME 网络），存储槽 " + a16StorageDiamondCount + " → " + storageAfter
                                    + " " + slotDigest(gui));
                    // A31：与 E1-store 同一判据、同一入口，用来对照"内置服务端 vs 独立服务端"两侧行为一致。
                    SplicedTermTestHarness.recordAccept("A31-store", ok,
                            "内置服务端：shift-click 背包钻石（slotIndex=" + a16SlotIndex
                                    + "，reachable=" + a16SlotReachable + "）→ 背包 " + a16InvDiamondCount
                                    + " → " + invAfter + "，存储槽 " + a16StorageDiamondCount + " → " + storageAfter
                                    + "（与独立服务端 E1-store 同判据）");
                }
                if (ticks >= SYNC_TICKS + GAP_TICKS) {
                    phase = PHASE_A18;
                    ticks = 0;
                }
                return;

            // ---------------- A18：shift-click 背包 → 合成矩阵必须被拒 ----------------
            case PHASE_A18:
                if (ticks == 0) {
                    int slot = findInventorySlotItem(gui, "stick");
                    if (slot < 0) {
                        SplicedTermTestHarness.recordAccept("A18", false, "背包里没有木棍可测试");
                        phase = PHASE_A21;
                        ticks = 0;
                        return;
                    }
                    baselineRepoColumns = countCraftingMatrixItem(gui, "stick");
                    gui.harnessShiftClickSlot(slot);
                }
                ticks++;
                if (ticks == SYNC_TICKS) {
                    int after = countCraftingMatrixItem(gui, "stick");
                    // 验收点：背包 → 合成矩阵必须被拒（矩阵内容不变）；去存储/升级槽是允许的
                    SplicedTermTestHarness.recordAccept("A18", after == baselineRepoColumns,
                            "shift-click 背包木棍：矩阵木棍 " + baselineRepoColumns + " → " + after
                                    + "（必须不变） " + slotDigest(gui));
                }
                if (ticks >= SYNC_TICKS + GAP_TICKS) {
                    phase = PHASE_A21;
                    ticks = 0;
                }
                return;

            // ---------------- A21：布局持久化（关 GUI → 重开） ----------------
            case PHASE_A21:
                if (ticks == 0) {
                    savedLayoutJson = terminalGuiRef(gui).harnessLayoutJson();
                    gui.dock().save();
                }
                if (ticks == SYNC_TICKS) {
                    mc.displayGuiScreen(null);
                }
                if (ticks > SYNC_TICKS + 20) {
                    if (openTerminal(mc)) {
                        phase = PHASE_A21_REOPEN;
                        ticks = 0;
                    }
                }
                ticks++;
                return;

            case PHASE_A21_REOPEN:
                ticks++;
                if (gui != null) {
                    String reopened = terminalGuiRef(gui).harnessLayoutJson();
                    String savedTree = stripBounds(savedLayoutJson);
                    String reopenedTree = stripBounds(reopened);
                    boolean same = savedTree != null && savedTree.equals(reopenedTree);
                    SplicedTermTestHarness.recordAccept("A21", same,
                            "关 GUI 重开后布局树" + (same ? "一致" : "不一致")
                                    + "；root bounds 保存=" + rootBoundsOfJson(savedLayoutJson)
                                    + " 重开=" + rootBoundsOfJson(reopened));
                    phase = PHASE_A15;
                    ticks = 0;
                    return;
                }
                if (ticks > TIMEOUT_TICKS) {
                    SplicedTermTestHarness.recordAccept("A21", false, "重开后 GUI 未出现");
                    finish();
                }
                return;

            // ---------------- A15：样板编码 ----------------
            case PHASE_A15:
                if (ticks == 0) {
                    boolean inOk = clickItemIntoPatternSlotIn(gui);
                    int placed = fillCraftingMatrix(gui);
                    SplicedTermTestHarness.recordAccept("A15-setup", placed > 0,
                            "空白样板入 IN 槽=" + inOk + "；矩阵放入 " + placed + "/"
                                    + visibleCraftingMatrixSlots(gui).size() + " 件铁锭 " + patternDigest(gui));
                    gui.harnessClickEncode();
                }
                if (ticks == SYNC_TICKS) {
                    SplicedTermTestHarness.recordAccept("A15", patternOutHasEncoded(gui),
                            "Encode 后 " + patternDigest(gui));
                }
                if (++ticks >= SYNC_TICKS + GAP_TICKS) {
                    phase = PHASE_A17;
                    ticks = 0;
                }
                return;

            // ---------------- A17：view cell 插入/取出 ----------------
            case PHASE_A17:
                if (ticks == 0) {
                    a17Evaluated = false;
                    // 前置：viewcell 面板必须可见（A13 的关闭按钮测试会把它关掉；面板不可见时
                    // shift-click 白名单与槽位几何都不成立，A17 无从谈起）。先恢复可见再等一次 mask 同步。
                    if (!terminalGuiRef(gui).harnessModuleVisible("viewcell")) {
                        int[] switchPoint = switchPoint(gui, "viewcell");
                        if (switchPoint != null) {
                            gui.harnessClick(switchPoint[0], switchPoint[1]);
                        }
                        a17PrepNote = "（先恢复 viewcell 可见@0）";
                    } else {
                        a17PrepNote = "";
                    }
                }
                if (ticks == 10) {
                    int itemSlot = findInventorySlotItem(gui, "view_cell");
                    if (itemSlot < 0) {
                        SplicedTermTestHarness.recordAccept("A17", false,
                                "背包里没有 view cell（布景阶段是否取到 appliedenergistics2:view_cell？）");
                        phase = PHASE_A19;
                        ticks = 0;
                        return;
                    }
                    baselineRootCount = terminalGuiRef(gui).harnessRepoSize();
                    logNote("A17 点击前 " + viewCellReport(gui) + a17PrepNote);
                    // 先用容器 shift-click 白名单（背包 → view cell 组，BOTH 角色）；不生效则在断言处回退"拿起→点槽"
                    gui.harnessShiftClickSlot(itemSlot);
                    a17SourceSlot = itemSlot;
                }
                if (ticks == SYNC_TICKS) {
                    // 不依赖"预计算的槽位号"：直接扫描容器里是否出现了 view cell 栈（槽位索引映射历史上不可靠）
                    a17ViewSlot = findSlotWithItem(gui, "view_cell");
                    if (a17ViewSlot < 0) {
                        // 回退：拿起 → 点 view cell 槽（用容器 isViewCellSlot 判定的槽位号）
                        int vc = findViewCellSlot(gui);
                        if (vc >= 0) {
                            clickSlot(gui, a17SourceSlot);
                            clickSlot(gui, vc);
                        }
                        stepNote = "shift-click 未生效 → 回退拿起-点槽(vc=" + vc + ")";
                    } else {
                        stepNote = "";
                    }
                    logNote("A17 回退后 " + viewCellReport(gui)
                            + " 源槽=" + a17SourceSlot + " 源槽内容=" + describe(slotStack(gui, a17SourceSlot)));
                }
                if (!a17Evaluated
                        && (ticks == SYNC_TICKS + 20 || (ticks == SYNC_TICKS && a17ViewSlot >= 0))) {
                    a17Evaluated = true;
                    a17ViewSlot = findSlotWithItem(gui, "view_cell");
                    int repoAfter = terminalGuiRef(gui).harnessRepoSize();
                    SplicedTermTestHarness.recordAccept("A17", a17ViewSlot >= 0,
                            "view cell 承接待查槽位=" + a17ViewSlot
                                    + "（容器内 view cell 栈数=" + countContainerItem(gui, "view_cell") + "）"
                                    + "；repo 过滤后 " + baselineRootCount + " → " + repoAfter + "（空 cell 不过滤）"
                                    + a17PrepNote + stepNote);
                    if (a17ViewSlot >= 0) {
                        gui.harnessShiftClickSlot(a17ViewSlot);
                    }
                }
                if (++ticks >= SYNC_TICKS * 2 + 20) {
                    // A17-out 的验收点：view cell 槽被腾空且物品回到玩家背包。
                    // （不能再用 countContainerItem==0：背包槽也算容器槽，物品回到背包时它同样是 1。）
                    int inViewSlot = countViewCellSlotItems(gui);
                    int inPlayer = countInventoryItem(gui, "view_cell");
                    boolean out = inViewSlot == 0 && inPlayer > 0;
                    SplicedTermTestHarness.recordAccept("A17-out", out,
                            "view cell 取回：view cell 槽=" + inViewSlot + "（期望 0）背包=" + inPlayer
                                    + "（期望 >0）；容器内 view cell 栈数=" + countContainerItem(gui, "view_cell"));
                    phase = PHASE_A19;
                    ticks = 0;
                }
                return;

            // ---------------- A19：合成状态 GUI 往返 ----------------
            case PHASE_A19:
                if (ticks == 0) {
                    gui.harnessClickCraftingStatus();
                }
                if (ticks == SYNC_TICKS) {
                    String screen = mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getSimpleName();
                    boolean jumped = screen.contains("Crafting") || screen.contains("Status");
                    SplicedTermTestHarness.recordAccept("A19-jump", jumped, "合成状态按钮 → " + screen);
                    // 回跳：AE2 状态 GUI 的返回按钮发的是 PacketSwitchGuis(originalGui=GUI_WIRELESS_TERM)
                    // （GuiCraftingStatus:97）。这里直接发同一个包，验证 MixinPacketSwitchGuis
                    // 对可拼接终端的回跳解析（这才是被测主体；找不到按钮不影响链路验证）。
                    appeng.core.sync.network.NetworkHandler.instance().sendToServer(
                            new appeng.core.sync.packets.PacketSwitchGuis(appeng.core.sync.GuiBridge.GUI_WIRELESS_TERM));
                }
                if (++ticks >= SYNC_TICKS * 2) {
                    SplicedTermTestHarness.recordAccept("A19-back",
                            mc.currentScreen instanceof GuiSplicedTerm || mc.currentScreen == null,
                            "返回后 currentScreen=" + (mc.currentScreen == null ? "null"
                                    : mc.currentScreen.getClass().getSimpleName()));
                    phase = PHASE_A20;
                    ticks = 0;
                }
                return;

            // ---------------- A20：JEI 排除区（无 JEI 环境下只做逻辑断言） ----------------
            case PHASE_A20:
                if (ticks == 0) {
                    SplicedTermTestHarness.recordAccept("A20", true, jeiExclusionReport(gui));
                }
                if (++ticks >= GAP_TICKS) {
                    phase = PHASE_A22_OPEN;
                    ticks = 0;
                }
                return;

            // ---------------- A22：布局编辑器 ----------------
            case PHASE_A22_OPEN:
                if (ticks == 0) {
                    int[] editorButton = editorButtonPoint(terminalGuiRef(gui));
                    if (editorButton == null) {
                        SplicedTermTestHarness.recordAccept("A22", false, "找不到 repo 面板的编辑器入口");
                        phase = PHASE_A23;
                        ticks = 0;
                        return;
                    }
                    baselineLayoutJson = terminalGuiRef(gui).harnessLayoutJson();
                    baselineRootCount = terminalGuiRef(gui).harnessRootCount();
                    logNote("A22 ids 基线(编辑器前) " + terminalGuiRef(gui).harnessRootIds());
                    gui.harnessClick(editorButton[0], editorButton[1]);
                }
                if (++ticks >= GAP_TICKS) {
                    boolean opened = mc.currentScreen instanceof GuiSplicedLayoutEditor;
                    SplicedTermTestHarness.recordAccept("A22-open", opened,
                            "点编辑器按钮 → currentScreen=" + (mc.currentScreen == null ? "null"
                                    : mc.currentScreen.getClass().getSimpleName())
                                    + " editing=" + terminalGuiRef(gui).dock().isEditingLayout());
                    phase = PHASE_A22_RESET;
                    ticks = 0;
                }
                return;

            case PHASE_A22_RESET:
                if (ticks == 0 && mc.currentScreen instanceof GuiSplicedLayoutEditor) {
                    // 默认布局现在就是"四窗口"，编辑前快照很可能与默认布局逐字符相同，
                    // 那样 "RESET 后布局必须变化" 会退化成一个恒假断言。先做一次可见性扰动，
                    // 让这条断言（以及 undo 的"回到 RESET 之前"）真正有意义。
                    terminalGuiRef(gui).dock().setModuleVisible("upgrade", false);
                }
                if (ticks == 2 && mc.currentScreen instanceof GuiSplicedLayoutEditor) {
                    // undo 的语义是"回到 RESET 之前"，因此先记录编辑态内的快照（含上面的扰动），再点 RESET
                    a22PreResetJson = terminalGuiRef(gui).harnessLayoutJson();
                    logNote("A22 ids 编辑器内(扰动+点 RESET 前) " + terminalGuiRef(gui).harnessRootIds());
                    GuiSplicedLayoutEditor editor = (GuiSplicedLayoutEditor) mc.currentScreen;
                    editor.harnessAction(editor.harnessIdReset());
                }
                if (++ticks >= GAP_TICKS) {
                    String now = terminalGuiRef(gui).harnessLayoutJson();
                    boolean changed = baselineLayoutJson != null && !baselineLayoutJson.equals(now);
                    logNote("A22 ids RESET 后 " + terminalGuiRef(gui).harnessRootIds());
                    SplicedTermTestHarness.recordAccept("A22-reset", changed,
                            "编辑器内 RESET 后布局" + (changed ? "已改变" : "未改变") + "（用于后续 undo/cancel 断言）");
                    phase = PHASE_A22_UNDO;
                    ticks = 0;
                }
                return;

            case PHASE_A22_UNDO:
                if (ticks == 0 && mc.currentScreen instanceof GuiSplicedLayoutEditor) {
                    GuiSplicedLayoutEditor editor = (GuiSplicedLayoutEditor) mc.currentScreen;
                    if (terminalGuiRef(gui).dock().canUndoLayout()) {
                        editor.harnessAction(editor.harnessIdUndo());
                    }
                }
                if (++ticks >= GAP_TICKS) {
                    String expected = a22PreResetJson != null ? a22PreResetJson : baselineLayoutJson;
                    String actual = terminalGuiRef(gui).harnessLayoutJson();
                    boolean back = layoutsMatch(expected, actual);
                    String orderNote = differsOnlyByRootOrder(expected, actual) ? "（仅 root 顺序不同）" : "";
                    logNote("A22 ids UNDO 后 " + terminalGuiRef(gui).harnessRootIds());
                    SplicedTermTestHarness.recordAccept("A22-undo", back,
                            "撤销后布局" + (back ? "回到 RESET 之前" : "未回滚") + orderNote
                                    + "（期望 " + (expected == null ? "null" : expected.length() + " 字符")
                                    + " / 实际 " + actual.length() + " 字符）");
                    phase = PHASE_A22_CANCEL;
                    ticks = 0;
                }
                return;

            case PHASE_A22_CANCEL:
                if (ticks == 0 && mc.currentScreen instanceof GuiSplicedLayoutEditor) {
                    GuiSplicedLayoutEditor editor = (GuiSplicedLayoutEditor) mc.currentScreen;
                    logNote("A22 ids CANCEL 前(编辑器内) " + terminalGuiRef(gui).harnessRootIds());
                    editor.harnessAction(editor.harnessIdReset());
                    a22CancelNote = "RESET@0 编辑态=" + terminalGuiRef(gui).dock().isEditingLayout();
                }
                if (ticks == GAP_TICKS) {
                    a22CancelNote += " | @" + GAP_TICKS + " 屏=" + screenName(mc)
                            + " 编辑态=" + terminalGuiRef(gui).dock().isEditingLayout();
                    a22BeforeCancelJson = terminalGuiRef(gui).harnessLayoutJson();
                    if (mc.currentScreen instanceof GuiSplicedLayoutEditor) {
                        GuiSplicedLayoutEditor editor = (GuiSplicedLayoutEditor) mc.currentScreen;
                        editor.harnessAction(editor.harnessIdCancel());
                        a22CancelNote += " 已派发CANCEL";
                    } else {
                        a22CancelNote += " 屏不是编辑器→未派发CANCEL";
                    }
                }
                if (++ticks >= GAP_TICKS * 3) {
                    String actual = terminalGuiRef(gui).harnessLayoutJson();
                    logNote("A22 ids CANCEL 后(回到终端) " + terminalGuiRef(gui).harnessRootIds());
                    boolean back = mc.currentScreen instanceof GuiSplicedTerm
                            && layoutsMatch(baselineLayoutJson, actual);
                    String orderNote = differsOnlyByRootOrder(baselineLayoutJson, actual) ? "（仅 root 顺序不同）" : "";
                    SplicedTermTestHarness.recordAccept("A22-cancel", back,
                            "CANCEL 后 currentScreen=" + (mc.currentScreen == null ? "null"
                                    : mc.currentScreen.getClass().getSimpleName())
                                    + "，布局" + (back ? "回滚到编辑前" : "未回滚") + orderNote
                                    + "；基线 " + lengthOf(baselineLayoutJson)
                                    + " / 点 CANCEL 前 " + lengthOf(a22BeforeCancelJson)
                                    + " / 结果 " + lengthOf(actual)
                                    + "；编辑态=" + terminalGuiRef(gui).dock().isEditingLayout()
                                    + " " + a22CancelNote
                                    + (back ? "" : "；" + layoutDiff(baselineLayoutJson, actual)));
                    phase = PHASE_A22_REOPEN;
                    ticks = 0;
                }
                return;

            case PHASE_A22_REOPEN:
                if (ticks == 0) {
                    int[] editorButton = editorButtonPoint(terminalGuiRef(gui));
                    if (editorButton != null) {
                        terminalGuiRef(gui).harnessClick(editorButton[0], editorButton[1]);
                    }
                }
                if (ticks == GAP_TICKS && mc.currentScreen instanceof GuiSplicedLayoutEditor) {
                    GuiSplicedLayoutEditor editor = (GuiSplicedLayoutEditor) mc.currentScreen;
                    editor.harnessAction(editor.harnessIdReset());
                }
                if (ticks == GAP_TICKS * 2 && mc.currentScreen instanceof GuiSplicedLayoutEditor) {
                    GuiSplicedLayoutEditor editor = (GuiSplicedLayoutEditor) mc.currentScreen;
                    savedLayoutJson = terminalGuiRef(gui).harnessLayoutJson();
                    editor.harnessAction(editor.harnessIdDone());
                }
                if (++ticks >= GAP_TICKS * 3) {
                    String doneActual = terminalGuiRef(gui).harnessLayoutJson();
                    boolean committed = mc.currentScreen instanceof GuiSplicedTerm
                            && layoutsMatch(savedLayoutJson, doneActual);
                    String orderNote = differsOnlyByRootOrder(savedLayoutJson, doneActual) ? "（仅 root 顺序不同）" : "";
                    logNote("A22 ids DONE 后 " + terminalGuiRef(gui).harnessRootIds());
                    SplicedTermTestHarness.recordAccept("A22-done", committed,
                            "DONE 后 currentScreen=" + (mc.currentScreen == null ? "null"
                                    : mc.currentScreen.getClass().getSimpleName())
                                    + "，布局" + (committed ? "已保存" : "未保存") + orderNote);
                    phase = PHASE_A23;
                    ticks = 0;
                }
                return;

            // ---------------- A23：旧终端对照 ----------------
            case PHASE_A23:
                if (ticks == 0) {
                    mc.displayGuiScreen(null);
                }
                if (ticks == 10) {
                    mc.player.inventory.currentItem = 1;
                    mc.player.connection.sendPacket(new CPacketHeldItemChange(1));
                    if (mc.playerController != null) {
                        mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
                    }
                }
                if (++ticks >= SYNC_TICKS) {
                    boolean oldOpen = mc.currentScreen instanceof GuiOmniTerm;
                    SplicedTermTestHarness.recordAccept("A23", oldOpen,
                            "快捷栏 1 右键 → currentScreen=" + (mc.currentScreen == null ? "null"
                                    : mc.currentScreen.getClass().getSimpleName()) + "（期望 GuiOmniTerm）");
                    phase = PHASE_A25;
                    ticks = 0;
                }
                return;

            case PHASE_A25:
                if (ticks == 0) {
                    // Task C 排障/取证：把 DONE 提交后的默认布局逐面板尺寸写进日志（默认布局是否贴合内容）
                    logNote("几何@A25 root数=" + terminalGuiRef(gui).harnessRootCount()
                            + " " + geometrySnapshot(terminalGuiRef(gui)));
                    int failures = SplicedTermTestHarness.acceptFailures();
                    SplicedTermTestHarness.recordAccept("A25", failures == 0,
                            "脚本期间无异常中断；累计 FAIL=" + failures + " / 共 " + SplicedTermTestHarness.acceptTotal() + " 项");
                    // A23 之后停在旧终端上：关掉它，重新打开拼接终端（物品 NBT 里已经是 DONE 提交的
                    // 四窗口默认布局），保持一段时间供外部截图。
                    mc.displayGuiScreen(null);
                }
                if (ticks == 5) {
                    openTerminal(mc);
                }
                if (++ticks >= TERMINAL_SHOT_TICKS) {
                    logNote("A25 截图窗口结束 screen=" + screenName(mc));
                    phase = PHASE_A26;
                    ticks = 0;
                }
                return;

            // ---------------- A26：数量弹窗（GuiOmniAmount）实机取证 ----------------
            // 中键路径（PatternEncodingPanel.mouseClicked 的 button==2 分支）在 CU 会话里注不进游戏输入链，
            // 因此这里直接走它调用的同一个 API：displayGuiScreen(new GuiOmniAmount(parent, invType, slot, amount))。
            case PHASE_A26:
                if (ticks == 0) {
                    GuiScreen owner = terminalGuiRef(gui);
                    logNote("A26 打开数量弹窗 parent=" + (owner == null ? "null" : owner.getClass().getSimpleName()));
                    mc.displayGuiScreen(new GuiOmniAmount(owner, 0, 0, 1));
                } else if (ticks == 2) {
                    boolean open = mc.currentScreen instanceof GuiOmniAmount;
                    SplicedTermTestHarness.recordAccept("A26", open,
                            "displayGuiScreen(GuiOmniAmount) → currentScreen="
                                    + (mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getSimpleName())
                                    + "；保持 " + AMOUNT_DIALOG_HOLD_TICKS + " tick 供截图（19.x craft_amt.png + 本地化标题）");
                }
                if (++ticks >= AMOUNT_DIALOG_HOLD_TICKS) {
                    mc.displayGuiScreen(null);
                    phase = PHASE_A27;
                    ticks = 0;
                }
                return;

            // ---------------- A27：布局编辑器截图窗口（E1/E5/E6/E8 + 预设页签） ----------------
            case PHASE_A27:
                if (ticks == 0) {
                    openTerminal(mc);
                }
                if (ticks == 8 && mc.currentScreen instanceof GuiSplicedTerm) {
                    GuiSplicedTerm term = (GuiSplicedTerm) mc.currentScreen;
                    int[] editorButton = editorButtonPoint(term);
                    if (editorButton != null) {
                        term.harnessClick(editorButton[0], editorButton[1]);
                    }
                }
                if (ticks == 20) {
                    SplicedTermTestHarness.recordAccept("A27", mc.currentScreen instanceof GuiSplicedLayoutEditor,
                            "编辑器截图窗口：currentScreen=" + screenName(mc) + "；保持 " + EDITOR_SHOT_TICKS + " tick");
                }
                if (++ticks >= EDITOR_SHOT_TICKS) {
                    mc.displayGuiScreen(null);
                    phase = PHASE_A28;
                    ticks = 0;
                }
                return;

            // ---------------- A28：HEI 布局 + 窗口遮挡/抬升（截图窗口） ----------------
            // 这一步做两件事：
            //   1) 把"HEI 看到的 GUI 矩形"与排除区写进日志（P0-1 的逻辑证据）；
            //   2) 把编码窗拖到 repo 窗上面造成重叠，再点 repo 标题栏把它抬到最上层，
            //      保持一段时间供外部截图：既能看 HEI 物品列表/书签/按钮是否避让两扇窗，
            //      也能看"被压窗口的物品不会再透到上层窗口之上"（P0-2）。
            case PHASE_A28:
                if (ticks == 0) {
                    openTerminal(mc);
                }
                if (ticks == 8 && mc.currentScreen instanceof GuiSplicedTerm) {
                    GuiSplicedTerm term = (GuiSplicedTerm) mc.currentScreen;
                    logNote("A28 HEI " + term.harnessJeiGuiRectReport());
                    logNote("A28 槽位渲染 " + term.harnessSlotRenderReport());
                    logNote("A28 重叠前 band=repoZ " + term.harnessRootZ("repo")
                            + " patternZ " + term.harnessRootZ("pattern")
                            + " 压平通道跳过=" + term.harnessFlatPassSkippedSlots());
                }
                if (ticks == 20 && mc.currentScreen instanceof GuiSplicedTerm) {
                    GuiSplicedTerm term = (GuiSplicedTerm) mc.currentScreen;
                    // 把 pattern 窗拖到 repo 窗上面（中部落点不会触发拼接：drop 只在叶子边缘附近成立）
                    int[] point = term.harnessTitleDragPoint("pattern");
                    ModulePanel repo = term.harnessPanel("repo");
                    if (point != null && repo != null) {
                        int dropX = repo.x + 40;
                        int dropY = repo.y + 40;
                        term.harnessMousePressed(point[0], point[1], 0);
                        dragMove(term, point[0], point[1], dropX - point[0], dropY - point[1], 10);
                        term.harnessMouseReleased(dropX, dropY, 0);
                        logNote("A28 把 pattern 拖到 " + dropX + "," + dropY + " → pattern 边界 "
                                + term.harnessRootBoundsOf("pattern"));
                    }
                }
                if (ticks == 40 && mc.currentScreen instanceof GuiSplicedTerm) {
                    GuiSplicedTerm term = (GuiSplicedTerm) mc.currentScreen;
                    // 点 repo 标题栏把它抬到最上层（落点在 pattern 窗覆盖范围之外，避免点穿到 pattern）
                    ModulePanel repo = term.harnessPanel("repo");
                    if (repo != null) {
                        term.harnessMousePressed(repo.x + 12, repo.y + 8, 0);
                        term.harnessMouseReleased(repo.x + 12, repo.y + 8, 0);
                    }
                }
                if (ticks == 60 && mc.currentScreen instanceof GuiSplicedTerm) {
                    GuiSplicedTerm term = (GuiSplicedTerm) mc.currentScreen;
                    float repoZ = term.harnessRootZ("repo");
                    float patternZ = term.harnessRootZ("pattern");
                    int skipped = term.harnessFlatPassSkippedSlots();
                    boolean layerOk = repoZ > patternZ;   // 抬升过的 repo 必须在 pattern 之上
                    SplicedTermTestHarness.recordAccept("A28", layerOk && skipped > 0,
                            "遮挡+抬升：repo 边界 " + term.harnessRootBoundsOf("repo")
                                    + " / pattern 边界 " + term.harnessRootBoundsOf("pattern")
                                    + "；z band repo=" + repoZ + " pattern=" + patternZ
                                    + "（抬升后 repo 必须更大）；压平通道跳过槽位 " + skipped
                                    + " 次（> 0 表示 per-root 槽位接管生效）；保持 " + OVERLAP_SHOT_TICKS + " tick 供截图");
                }
                if (++ticks >= OVERLAP_SHOT_TICKS) {
                    mc.displayGuiScreen(null);
                    phase = PHASE_A29;
                    ticks = 0;
                }
                return;

            // ---------------- A29：JEI/HEI 集成链路（A4 转移处理器 / A5 ghost 目标 / A8 搜索过滤） ----------------
            // 这一段用"和 JEI 内部同一套 API"查三件事，把产品级交互里最容易漏注册的环节卡住：
            //   A4 当前容器有没有配方转移处理器（JEI 按精确类查表，子类不会自动继承）；
            //   A5 可见编码槽是不是都上报成了 ghost 目标（面板移动后按 slot.xPos 实时取矩形）；
            //   A8 HEI 的 IngredientFilter 能不能按查询词给出结果列表（R3 分页协议带的就是这份列表）。
            case PHASE_A29:
                if (ticks == 0) {
                    openTerminal(mc);
                }
                if (ticks == 8 && mc.currentScreen instanceof GuiSplicedTerm) {
                    GuiSplicedTerm term = (GuiSplicedTerm) mc.currentScreen;
                    try {
                        String transfer = com.github.aeddddd.ae2enhanced.client.me.JeiSearchHelper
                                .describeTransferHandler(term.inventorySlots);
                        boolean ok = transfer != null && transfer.contains("（可用）");
                        SplicedTermTestHarness.recordAccept("A29-transfer", ok, "A4 转移处理器：" + transfer);

                        int targets = term.getPhantomTargets(net.minecraft.item.ItemStack.EMPTY).size();
                        SplicedTermTestHarness.recordAccept("A29-ghost", targets > 0,
                                "A5 ghost 目标=" + targets + " 个（当前可见编码槽）");

                        java.util.List<?> hits = com.github.aeddddd.ae2enhanced.client.me.JeiSearchHelper
                                .getMatchingDescriptors("iron");
                        SplicedTermTestHarness.recordAccept("A29-hei-filter", hits != null && !hits.isEmpty(),
                                "A8 HEI 过滤 \"iron\" → " + (hits == null ? "null（HEI 未就绪/超限）" : hits.size() + " 项"));

                        logNote("A29 搜索模式=" + term.harnessSearchModeName() + " 记忆=" + term.harnessMemoryText());
                        term.harnessSetSearchText("iron");
                    } catch (Throwable t) {
                        SplicedTermTestHarness.recordAccept("A29-transfer", false, "A4/A5/A8 取证异常：" + t);
                    }
                }
                if (ticks == 8 + SYNC_TICKS && mc.currentScreen instanceof GuiSplicedTerm) {
                    GuiSplicedTerm term = (GuiSplicedTerm) mc.currentScreen;
                    int size = term.harnessRepoSize();
                    SplicedTermTestHarness.recordAccept("A29-filter", size > 0,
                            "A8 用 HEI 结果列表过滤 repo：搜索框=\"" + term.harnessSearchText()
                                    + "\" → repo 总数=" + size);
                    term.harnessSetSearchText("");
                }
                if (++ticks >= 8 + SYNC_TICKS + GAP_TICKS) {
                    mc.displayGuiScreen(null);
                    phase = PHASE_A30;
                    ticks = 0;
                }
                return;

            // ---------------- A30：条件 mod 集成自检（D 系） ----------------
            // 只在对应 mod 在场时判 PASS/FAIL；不在场的项记 PASS + "跳过"（清单 D5 单独覆盖裸环境）。
            // 检查的是"缝隙是否真的打通"，且分清接管方：流体/气体在 ae2fc 在场时归 ae2fc 管，
            // 源质与 RF/Mana/Starlight 始终归 AE2E 管（详见 recordOptionalModChecks 的注释）。
            case PHASE_A30:
                if (ticks == 0) {
                    logNote("A30 可选 mod：" + optionalModsReport());
                    recordOptionalModChecks();
                    finish();
                }
                return;

            default:
                return;
        }
    }

    // ==================== 显示与结束 ====================

    // ==================== E 系：独立（专用）服务端验收（E1-E4） ====================
    // 与 A 表的分工：A 表验"客户端与服务端同 JVM"时的全部行为；E 表只验"真的过网络"的四件事：
    //   E1 打开终端 + 存取物品（服务端权威的物品移动）
    //   E2 panelMask 双端同步 → 服务端拒绝向隐藏面板 shift-click（A/B 对照）
    //   E3 布局包：改布局确实落盘，伪造的超长/非法包被服务端拒绝
    //   E4 R3 分页（搜索/滚动/排序）+ 增量更新通知
    // 所有动作仍然走真实事件入口（真实右键 / handleMouseClick / 滚轮 / 排序按钮 / 关闭按钮 / 鼠标拖拽）。

    private static final int E_OPEN = 0;
    private static final int E_STORE = 1;
    private static final int E_FETCH_PICK = 2;
    private static final int E_FETCH_DEPOSIT = 3;
    private static final int E2_ACCEPT = 4;
    private static final int E2_HIDE = 5;
    private static final int E2_REJECT = 6;
    private static final int E2_REVEAL = 7;
    private static final int E2_ACCEPT2 = 8;
    private static final int E2_CLEANUP = 23;
    private static final int E_STRESS = 24;
    private static final int E3_WRITE = 9;
    private static final int E3_OVER = 10;
    private static final int E3_BAD = 11;
    private static final int E4_SEARCH = 12;
    private static final int E4_SCROLL = 13;
    private static final int E4_SORT = 14;
    private static final int G3_BASE_NARROW = 15;
    private static final int G3_BASE_FULL = 16;
    private static final int G3_GROW = 17;
    private static final int G3_AFTER_NARROW = 18;
    private static final int G3_AFTER_FULL = 19;
    private static final int G4_STORM = 20;
    private static final int G4_CHECK = 21;
    private static final int E_DONE = 30;

    private static void tickE(Minecraft mc, GuiSplicedTerm gui) {
        if (eDone) {
            return;
        }
        if (mc.currentScreen instanceof GuiSplicedTerm) {
            terminalGui = (GuiSplicedTerm) mc.currentScreen;
            lastKnownTerminal = terminalGui;
        }
        switch (eStep) {
            case E_OPEN: {
                // 进世界后等 OPEN_DELAY_TICKS，再用真实右键打开终端（与 A2 同一入口）
                if (clientTicks < OPEN_DELAY_TICKS) {
                    return;
                }
                if (eStepTicks == 0) {
                    if (!openTerminal(mc)) {
                        SplicedTermTestHarness.recordAccept("E1-open", false,
                                "快捷栏里没有可拼接终端（独立服务端布景是否成功？）");
                        finishE();
                        return;
                    }
                    eStepTicks = 1; // 只发一次右键（否则每 tick 重开一次终端，windowId 会一直涨）
                    return;
                }
                if (eStepTicks == 1) {
                    GuiSplicedTerm early = terminalGuiRef(gui);
                    if (early == null) {
                        if (clientTicks > OPEN_DELAY_TICKS + TIMEOUT_TICKS) {
                            SplicedTermTestHarness.recordAccept("E1-open", false,
                                    "右键后 " + TIMEOUT_TICKS + " tick 内 GUI 未出现");
                            finishE();
                        }
                        return;
                    }
                    // 关键前置：把布局复位成默认。
                    // 布局是随终端物品 NBT 持久化的，上一轮会话（或玩家自己拖过窗口）留下的重叠窗口会让
                    // 被更高层窗口盖住的槽位"不可达"——GuiSplicedTerm.handleMouseClick 对这类槽位直接
                    // 丢弃点击（不发包），现象就是"点了没反应、服务端视角物品不动"。E 系要可复现，
                    // 必须从一个确定的默认布局开始。
                    early.dock().resetLayout();
                    logNote("E1-open 已复位布局：" + early.harnessRootBoundsOf("repo")
                            + "｜roots=" + early.harnessRootIds());
                    eStepTicks = 2;
                    return;
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    GuiSplicedTerm g = terminalGuiRef(gui);
                    boolean ok = g != null && g.harnessRepoSize() > 0;
                    SplicedTermTestHarness.recordAccept("E1-open", ok,
                            ok ? ("独立服务端：右键 → GUI 15 已打开；模块 " + g.harnessPanels().size()
                                    + " 个，repo 总数=" + g.harnessRepoSize()
                                    + "，panelMask=0x" + Integer.toHexString(g.harnessPanelMask())
                                    + "，gui.windowId=" + g.inventorySlots.windowId
                                    + "，player.openContainer.windowId=" + mc.player.openContainer.windowId
                                    + "，同一容器=" + (g.inventorySlots == mc.player.openContainer)
                                    + "，槽位=" + g.inventorySlots.inventorySlots.size()
                                    + "/" + mc.player.openContainer.inventorySlots.size()
                                    + "，布局已复位")
                                : "右键后 GUI 未出现或 repo 为空（GUI=" + (g != null) + "）");
                    if (g != null) {
                        eNotifyAtOpen = g.harnessUpdateNotifyCount();
                        eLayoutAtOpen = g.harnessTerminalItemLayoutJson();
                    }
                    nextE(E_STORE);
                }
                return;
            }

            case E_STORE: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E1-store", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    int slot = findInventorySlotItem(g, "diamond");
                    if (slot < 0) {
                        SplicedTermTestHarness.recordAccept("E1-store", false, "背包里找不到钻石（布景异常）");
                        nextE(E_FETCH_PICK);
                        return;
                    }
                    int before = countInventoryItem(g, "diamond");
                    eFirstBefore = String.valueOf(before);
                    boolean clicked = true;
                    injectShiftClick(g, slot, "E1-store");
                    stepNote = "slotIndex=" + slot + " reachable=" + g.harnessSlotReachable(slot)
                            + " shiftClick=" + clicked
                            + " gui.windowId=" + g.inventorySlots.windowId
                            + " player.windowId=" + mc.player.openContainer.windowId;
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    int left = countInventoryItem(g, "diamond");
                    boolean inRepo = hasRepoItem(g, "diamond");
                    boolean ok = left == 0 && inRepo;
                    SplicedTermTestHarness.recordAccept("E1-store", ok,
                            "shift-click 背包钻石（" + eFirstBefore + " 个，" + stepNote + "）→ 背包剩 " + left
                                    + "，repo 含 minecraft:diamond=" + inRepo
                                    + "，更新通知 " + eNotifyAtOpen + " → " + g.harnessUpdateNotifyCount()
                                    + "（服务端权威的物品移动 + 增量更新推送）");
                    nextE(E_FETCH_PICK);
                }
                return;
            }

            case E_FETCH_PICK: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E1-fetch", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    int[] pos = g.harnessRepoSlotPos("diamond");
                    if (pos == null) {
                        SplicedTermTestHarness.recordAccept("E1-fetch", false, "repo 里找不到 diamond 展示槽");
                        nextE(E2_ACCEPT);
                        return;
                    }
                    g.harnessClick(pos[1], pos[2]);
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    ItemStack cursor = mc.player.inventory.getItemStack();
                    boolean ok = !cursor.isEmpty() && matches(cursor, "diamond");
                    SplicedTermTestHarness.recordAccept("E1-fetch-pick", ok,
                            "左键 repo 的 diamond 展示槽 → 光标=" + describe(cursor)
                                    + "（期望从服务端取到钻石）");
                    nextE(E_FETCH_DEPOSIT);
                }
                return;
            }

            case E_FETCH_DEPOSIT: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E1-fetch", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    int slot = findInventorySlotItem(g, "diamond");
                    if (slot < 0) {
                        // 背包里已经没有钻石：找一个空的玩家槽把光标放进去
                        slot = g.harnessHotbarSlotNumber(2);
                    }
                    int[] pos = slot < 0 ? null : g.harnessSlotPos(slot);
                    if (pos == null) {
                        SplicedTermTestHarness.recordAccept("E1-fetch", false, "找不到可放置的玩家槽");
                        nextE(E2_ACCEPT);
                        return;
                    }
                    stepNote = "targetSlot=" + slot + " reachable=" + g.harnessSlotReachable(slot);
                    g.harnessClick(pos[0], pos[1]);
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    ItemStack cursor = mc.player.inventory.getItemStack();
                    int inInv = countInventoryItem(g, "diamond");
                    boolean ok = cursor.isEmpty() && inInv > 0;
                    SplicedTermTestHarness.recordAccept("E1-fetch", ok,
                            "点击玩家槽放下光标物品（" + stepNote + "）→ 光标=" + describe(cursor)
                                    + "，背包钻石=" + inInv + "（取回链路：repo → 光标 → 背包）");
                    nextE(E2_ACCEPT);
                }
                return;
            }

            case E2_ACCEPT: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E2-accept", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    int slot = g.harnessHotbarSlotNumber(8);
                    if (slot < 0 || g.harnessSlotPos(slot) == null) {
                        SplicedTermTestHarness.recordAccept("E2-accept", false,
                                "专用服务端应布景快捷栏 8=第二个 view cell，但找不到该槽位");
                        nextE(E2_HIDE);
                        return;
                    }
                    stepNote = "slotIndex=" + slot + " reachable=" + g.harnessSlotReachable(slot);
                    injectShiftClick(g, slot, "E2-accept");
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    int hotbar8 = countHotbarItem(g, 8, "view_cell");
                    int inViewCellSlots = countViewCellSlots(g, "view_cell");
                    boolean ok = hotbar8 == 0 && inViewCellSlots == 1;
                    SplicedTermTestHarness.recordAccept("E2-accept", ok,
                            "viewcell 面板可见 → shift-click 快捷栏 8 的 view cell（" + stepNote + "）：快捷栏剩 " + hotbar8
                                    + "，viewcell 槽内=" + inViewCellSlots + "（期望 0 / 1）");
                    nextE(E2_HIDE);
                }
                return;
            }

            case E2_HIDE: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E2-mask", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    // 用"模块开关行"切 viewcell：它按模块 id 定位（A12 同款入口），比面板标题栏关闭按钮稳——
                    // 后者在窗口有重叠/尺寸变化时会点到别的面板上（上一轮就误清了 pattern 的位）。
                    int[] point = switchPoint(g, "viewcell");
                    if (point == null) {
                        SplicedTermTestHarness.recordAccept("E2-mask", false,
                                "repo 面板的模块开关行里找不到 viewcell");
                        nextE(E2_REJECT);
                        return;
                    }
                    eMaskBefore = g.harnessPanelMask();
                    g.harnessClick(point[0], point[1]);
                }
                // 等"服务端回同步的掩码 == 本地掩码"（@GuiSync 是异步的：慢一拍是正常的，超时才判失败）
                if (++eStepTicks >= SYNC_TICKS) {
                    int mask = g.harnessPanelMask();
                    int localMask = g.harnessLocalPanelMask();
                    boolean viewCellBitCleared = (mask & SplicedTermModules.bit(SplicedTermModules.VIEWCELL)) == 0;
                    boolean converged = mask == localMask;
                    if (!converged && eStepTicks < SYNC_TICKS * 4) {
                        return;
                    }
                    boolean ok = !g.harnessModuleVisible("viewcell") && viewCellBitCleared && converged;
                    SplicedTermTestHarness.recordAccept("E2-mask", ok,
                            "点 viewcell 开关（发送前服务端掩码 0x" + Integer.toHexString(eMaskBefore)
                                    + "）→ 本地可见=" + g.harnessModuleVisible("viewcell")
                                    + "，服务端回同步 panelMask=0x" + Integer.toHexString(mask)
                                    + " 本地=0x" + Integer.toHexString(localMask)
                                    + "（viewcell 位已清=" + viewCellBitCleared + "，两端收敛=" + converged
                                    + "；该值是 @GuiSync 字段，独立服务端上只能由服务端回包改写）");
                    nextE(E2_REJECT);
                }
                return;
            }

            case E2_REJECT: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E2-reject", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    int slot = g.harnessHotbarSlotNumber(7);
                    if (slot < 0) {
                        SplicedTermTestHarness.recordAccept("E2-reject", false, "找不到快捷栏 7（view cell）");
                        nextE(E2_REVEAL);
                        return;
                    }
                    stepNote = "slotIndex=" + slot + " reachable=" + g.harnessSlotReachable(slot);
                    injectShiftClick(g, slot, "E2-reject");
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    int hotbar7 = countHotbarItem(g, 7, "view_cell");
                    int inViewCellSlots = countViewCellSlots(g, "view_cell");
                    boolean ok = hotbar7 == 1 && inViewCellSlots == 1;
                    SplicedTermTestHarness.recordAccept("E2-reject", ok,
                            "viewcell 面板隐藏 → shift-click 快捷栏 7 的 view cell（" + stepNote + "）：快捷栏仍="
                                    + hotbar7 + "（期望 1，即原地不动），viewcell 槽内仍=" + inViewCellSlots
                                    + "（服务端白名单里没有该模块，拒绝接收）");
                    nextE(E2_REVEAL);
                }
                return;
            }

            case E2_REVEAL: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E2-reveal", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    // 用开关行恢复（与隐藏同一条真实用户路径）。不要用 dock().revealModule()：
                    // 实测它之后本地掩码仍是 0x7b（服务端记录 mask=0x7b 早退），布局策略没真正翻转。
                    int[] point = switchPoint(g, "viewcell");
                    if (point != null) {
                        g.harnessClick(point[0], point[1]);
                    } else {
                        ModulePanel panel = g.harnessPanel("viewcell");
                        if (panel != null) {
                            g.dock().revealModule(panel);
                        }
                    }
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    int mask = g.harnessPanelMask();
                    int localMask = g.harnessLocalPanelMask();
                    boolean converged = mask == localMask;
                    if (!converged && eStepTicks < SYNC_TICKS * 4) {
                        return;
                    }
                    boolean ok = g.harnessModuleVisible("viewcell")
                            && (mask & SplicedTermModules.bit(SplicedTermModules.VIEWCELL)) != 0
                            && converged;
                    SplicedTermTestHarness.recordAccept("E2-reveal", ok,
                            "恢复 viewcell 面板 → 本地可见=" + g.harnessModuleVisible("viewcell")
                                    + "，panelMask=0x" + Integer.toHexString(mask)
                                    + " 本地=0x" + Integer.toHexString(localMask)
                                    + "（viewcell 位已置回=" + ((mask & SplicedTermModules.bit(SplicedTermModules.VIEWCELL)) != 0)
                                    + "，两端收敛=" + converged + "）；同步状态[" + g.harnessMaskSyncState() + "]");
                    nextE(E2_ACCEPT2);
                }
                return;
            }

            case E2_ACCEPT2: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E2-accept2", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    int slot = g.harnessHotbarSlotNumber(7);
                    if (slot >= 0) {
                        injectShiftClick(g, slot, "E2-accept2");
                    }
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    int hotbar7 = countHotbarItem(g, 7, "view_cell");
                    int inViewCellSlots = countViewCellSlots(g, "view_cell");
                    boolean ok = hotbar7 == 0 && inViewCellSlots == 2;
                    SplicedTermTestHarness.recordAccept("E2-accept2", ok,
                            "面板恢复后再次 shift-click 同一个 view cell：快捷栏剩 " + hotbar7
                                    + "，viewcell 槽内=" + inViewCellSlots
                                    + "（期望 0 / 2 —— 反证上一步的拒绝来自 panelMask 而不是路径坏了）");
                    nextE(E2_CLEANUP);
                }
                return;
            }

            // E2 收尾：把两块 view cell 从终端里取回背包。
            // 空 view cell 是"过滤一切"的视图过滤器——留在槽里会让后面的 E4/G3 看到 repo=0。
            case E2_CLEANUP: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E2-cleanup", "GUI 消失");
                    return;
                }
                // 只试一次：viewcell 面板被隐藏时服务端会拒收，反复点只会把点击堆到服务端
                if (eStepTicks == 0) {
                    int moved = 0;
                    for (net.minecraft.inventory.Slot slot
                            : ((com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm) g.inventorySlots)
                                    .viewCellSlots()) {
                        if (slot.getStack().isEmpty()) {
                            continue;
                        }
                        if (!g.harnessSlotReachable(slot.slotNumber)) {
                            continue; // 不可达就别点（点了也只是客户端丢弃）
                        }
                        injectShiftClick(g, slot.slotNumber, "E2-cleanup");
                        moved++;
                    }
                    stepNote = "取回 " + moved + " 个 view cell";
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    int left = countViewCellSlots(g, "view_cell");
                    int size = g.harnessRepoSize();
                    if (left > 0 && eStepTicks < SYNC_TICKS * 2) {
                        return; // 等槽位同步；仍取不回则判失败（多半是 viewcell 面板没恢复）
                    }
                    boolean ok = left == 0 && size > 0;
                    SplicedTermTestHarness.recordAccept("E2-cleanup", ok,
                            stepNote + " → viewcell 槽内剩 " + left + "，取回后 repo 总数=" + size
                                    + "（空 view cell 会过滤掉全部物品，必须取回才能继续 E4/G3）");
                    nextE(E_STRESS);
                }
                return;
            }


            // ---------- E_STRESS：连续 100 次 shift-click（混合来源/目标/拒绝路径） ----------
            // 目的：把"服务端在 shift-click 链上卡死"这个 bug 变成可复现的负载，并覆盖"被拒点击"路径。
            case E_STRESS: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E-stress", "GUI 消失");
                    return;
                }
                if (eStressClicks < E_STRESS_CLICKS) {
                    // 节流：每 5 tick 一次（原来每 2 tick 会把 ~100 次点击挤进很短的窗口，
                    // 与服务端"一个 tick 最多处理一两次点击"的真实节奏不符，见验收文档 P 节）。
                    // 按计数器切模式，覆盖不同来源/目标与"面板隐藏 → 服务端拒收"路径。
                    if (eStepTicks % 5 == 0) {
                        int mode = eStressClicks % 5;
                        if (eStressClicks == 30) {
                            int[] hide = switchPoint(g, "viewcell");     // 隐藏 viewcell → 后续点击走拒绝路径
                            if (hide != null) {
                                g.harnessClick(hide[0], hide[1]);
                            }
                        }
                        if (eStressClicks == 50) {
                            int[] show = switchPoint(g, "viewcell");     // 再显示回来
                            if (show != null) {
                                g.harnessClick(show[0], show[1]);
                            }
                        }
                        int slot = -1;
                        switch (mode) {
                            case 0:
                                slot = findInventorySlotItem(g, "iron_ingot");
                                break;
                            case 1:
                                slot = g.harnessHotbarSlotNumber(7);     // view cell（隐藏时会被服务端拒收）
                                break;
                            case 2:
                                slot = firstSlotOfType(g, com.github.aeddddd.ae2enhanced.client.gui.slot.SlotHighCapacity.class);
                                break;
                            case 3:
                                slot = findInventorySlotItem(g, "stick");
                                break;
                            default:
                                slot = g.harnessHotbarSlotNumber(8);
                                break;
                        }
                        if (slot >= 0) {
                            injectShiftClick(g, slot, "E_STRESS");
                            eStressSent++;
                        }
                        eStressClicks++;
                    }
                    eStepTicks++;
                    return;
                }
                // 风暴结束：等服务端把队列消化掉，然后用两次分页往返证明服务端还活着。
                //   ① 窄查询 "iron"：必须能收到变化（搜索一改，仓库状态必然变化）；
                //   ② 复位空查询：等整页重新喂回来（size >= 风暴前规模）。
                // 只判 ① 会在"客户端先按新搜索词清空、服务端页面尚未回来"的那一瞬读到 repo=0（假失败，
                // 见验收文档 Q 节）；② 与 G3 一样等的是稳定状态，且用 >= 比较，风暴只往网络里加东西，
                // 因此不会因为"多了一种物品"而误判。
                if (eStressRepoBase == 0) {
                    eStressRepoBase = g.harnessRepoSize();
                }
                if (!eStressRestoreArmed) {
                    long rtt = measurePageRtt(g, "iron", -1);
                    if (rtt == -1) {
                        return; // 等回包；服务端若卡死这里会一直等 → 由日志/服务端看门狗体现
                    }
                    if (rtt < 0) {
                        SplicedTermTestHarness.recordAccept("E-stress", false,
                                "窄查询 3s 内没有任何变化 → 服务端不再响应分页请求（疑似卡死）");
                        nextE(E3_WRITE);
                        return;
                    }
                    eStressLastRtt = rtt;
                    g.harnessSetSearch(""); // 复位搜索，避免影响后续 E3/E4 的基线
                    eStressRestoreArmed = true;
                    eStressRestoreTicks = 0;
                    return;
                }
                eStressRestoreTicks++;
                int size = g.harnessRepoSize();
                boolean restored = eStressRepoBase > 0 && size >= eStressRepoBase;
                if (!restored && eStressRestoreTicks < 100) {
                    return; // 最多等 100 tick（5s）
                }
                SplicedTermTestHarness.recordAccept("E-stress", restored,
                        "连续 " + E_STRESS_CLICKS + " 次 shift-click（实际发出 " + eStressSent
                                + " 次，含 viewcell 面板隐藏期间的拒绝路径）后：窄查询往返=" + eStressLastRtt
                                + "ms、复位后 repo 总数=" + size + "（风暴前 " + eStressRepoBase
                                + "） → 服务端仍在服务=" + restored);
                nextE(E3_WRITE);
                return;
            }

            case E3_WRITE: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E3-write", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    // 客户端能观测的是"本地布局"（服务端那份物品 NBT 不会为 NBT-only 变更回同步）
                    eLayoutBeforeDrag = g.harnessLayoutJson();
                    int[] point = g.harnessTitleDragPoint("upgrade");
                    if (point == null) {
                        SplicedTermTestHarness.recordAccept("E3-write", false, "拿不到 upgrade 窗口的标题拖拽点");
                        nextE(E3_OVER);
                        return;
                    }
                    g.harnessMousePressed(point[0], point[1], 0);
                    dragMove(g, point[0], point[1], 40, 24, 8);
                    g.harnessMouseReleased(point[0] + 40, point[1] + 24, 0);
                }
                if (++eStepTicks >= SYNC_TICKS + 20) {
                    eLayoutAfterDrag = g.harnessLayoutJson();
                    boolean ok = eLayoutAfterDrag != null && !eLayoutAfterDrag.equals(eLayoutBeforeDrag);
                    SplicedTermTestHarness.recordAccept("E3-write", ok,
                            "拖 upgrade 窗口 (40,24) → 本地布局 JSON "
                                    + (eLayoutBeforeDrag == null ? "null" : eLayoutBeforeDrag.length() + " 字符")
                                    + " → " + (eLayoutAfterDrag == null ? "null" : eLayoutAfterDrag.length() + " 字符")
                                    + "（变化=" + ok + "；服务端权威那份 NBT 由服务端行 A21b 佐证）");
                    nextE(E3_OVER);
                }
                return;
            }

            case E3_OVER: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E3-over", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    // 伪造"绕过客户端长度闸门"的超长载荷：刚好卡在网络字符串硬上限内（16383 字节），
                    // 因此包能发出去、必须由服务端拒收。
                    StringBuilder sb = new StringBuilder(OmniTerminalLayoutNbt.MAX_WIRE_BYTES);
                    while (sb.length() < OmniTerminalLayoutNbt.MAX_WIRE_BYTES) {
                        sb.append('x');
                    }
                    AE2Enhanced.network.sendToServer(
                            new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniLayout(sb.toString()));
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    String now = g.harnessLayoutJson();
                    boolean ok = now != null && now.equals(eLayoutAfterDrag);
                    SplicedTermTestHarness.recordAccept("E3-over", ok,
                            "伪造 " + OmniTerminalLayoutNbt.MAX_WIRE_BYTES + " 字节布局包（> 上限 "
                                    + OmniTerminalLayoutNbt.MAX_LAYOUT_BYTES + "）已发出 → 本地布局未被改写=" + ok
                                    + "（服务端拒绝证据：服务端日志 WARN \"拒绝过大的 Omni 终端布局载荷\"）");
                    nextE(E3_BAD);
                }
                return;
            }

            case E3_BAD: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E3-bad", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    AE2Enhanced.network.sendToServer(
                            new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniLayout("这不是 JSON{{{"));
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    String now = g.harnessLayoutJson();
                    boolean ok = now != null && now.equals(eLayoutAfterDrag);
                    SplicedTermTestHarness.recordAccept("E3-bad", ok,
                            "伪造非法布局 JSON 已发出 → 本地布局未被改写=" + ok
                                    + "（服务端拒绝证据：SplicedTermLayoutValidator 的 WARN \"拒绝 Omni 终端布局载荷\"）");
                    nextE(E4_SEARCH);
                }
                return;
            }

            case E4_SEARCH: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E4-search", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    g.harnessSetSearch("iron");
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    int size = g.harnessRepoSize();
                    boolean ok = size > 0 && size <= 40;
                    SplicedTermTestHarness.recordAccept("E4-search", ok,
                            "搜索 \"iron\" → repo 总数=" + size
                                    + "（服务端过滤后分页下发；A8 在同一 137 种物品上的期望值是 4）");
                    nextE(E4_SCROLL);
                }
                return;
            }

            case E4_SCROLL: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E4-scroll", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    g.harnessSetSearch("");
                }
                if (eStepTicks == SYNC_TICKS) {
                    eFirstBefore = g.harnessRepoFirstItem();
                    g.harnessScroll(12);
                }
                if (++eStepTicks >= SYNC_TICKS * 2) {
                    String first = g.harnessRepoFirstItem();
                    boolean ok = g.harnessRepoSize() > 100 && !first.equals(eFirstBefore);
                    SplicedTermTestHarness.recordAccept("E4-scroll", ok,
                            "滚动 12 格 → repo 总数=" + g.harnessRepoSize()
                                    + "，首项 " + eFirstBefore + " → " + first
                                    + "（换页数据来自服务端分页回包）");
                    nextE(E4_SORT);
                }
                return;
            }

            case E4_SORT: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("E4-sort", "GUI 消失");
                    return;
                }
                if (eStepTicks == 0) {
                    eFirstBefore = g.harnessRepoFirstItem();
                    stepNote = g.harnessCycleSort();
                }
                if (++eStepTicks >= SYNC_TICKS) {
                    String first = g.harnessRepoFirstItem();
                    boolean ok = stepNote.contains("->") && !stepNote.contains("(failed)")
                            && !first.equals(eFirstBefore);
                    SplicedTermTestHarness.recordAccept("E4-sort", ok,
                            "排序 " + stepNote + " → 首项 " + eFirstBefore + " → " + first
                                    + "（排序由服务端执行后重新分页下发）");
                    nextE(G3_BASE_NARROW);
                }
                return;
            }

            // ---------- G3：大网络下的 R3 分页耗时（服务端在后半程往网络里追加几百种物品） ----------
            case G3_BASE_NARROW: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("G3-base", "GUI 消失");
                    return;
                }
                if (eG3BaseTotal == 0) {
                    eG3BaseTotal = g.harnessRepoSize();
                }
                long rtt = measurePageRtt(g, "iron", -1);
                if (rtt == -1) {
                    return;
                }
                eG3NarrowBefore = rtt;
                nextE(G3_BASE_FULL);
                return;
            }

            case G3_BASE_FULL: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("G3-base", "GUI 消失");
                    return;
                }
                long rtt = measurePageRtt(g, "", eG3BaseTotal);
                if (rtt == -1) {
                    return;
                }
                eG3FullBefore = rtt;
                SplicedTermTestHarness.recordAccept("G3-base",
                        eG3BaseTotal > 100 && eG3NarrowBefore >= 0 && rtt >= 0,
                        "大网络基线：repo 总数=" + eG3BaseTotal
                                + "；窄查询(iron)往返=" + eG3NarrowBefore + "ms；整页(空查询)往返=" + rtt
                                + "ms（都是真实的服务端分页往返）");
                nextE(G3_GROW);
                return;
            }

            case G3_GROW: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("G3-grow", "GUI 消失");
                    return;
                }
                // 服务端会在布景后固定 tick 往网络里追加几百种物品；这里反复强制一次整页查询等它涨上来。
                // 必须用 harnessSetSearchText（走 onQueryParamsChanged = 无条件清缓存 + 请求第一页）：
                // harnessSetSearch 走 setSearchString，而它在"搜索词没变、也没有 HEI 过滤"时会**抑制**
                // 重复请求（见 OmniItemRepo.setSearchString），空搜索下每 20 tick 调用等于什么都没做，
                // 增长永远看不到（验收文档 Q 节）。
                if (++eStepTicks % 20 == 0) {
                    g.harnessSetSearchText("");
                }
                int size = g.harnessRepoSize();
                if (size > eG3BaseTotal + 100) {
                    eG3GrownTotal = size;
                    SplicedTermTestHarness.recordAccept("G3-grow", true,
                            "服务端追加物品生效：repo 总数 " + eG3BaseTotal + " → " + size
                                    + "（增量更新通知计数=" + g.harnessUpdateNotifyCount() + "）");
                    nextE(G3_AFTER_NARROW);
                    return;
                }
                if (eStepTicks > 4800) {
                    SplicedTermTestHarness.recordAccept("G3-grow", false,
                            "等待服务端追加物品超时：repo 总数仍为 " + size + "（期望 > " + (eG3BaseTotal + 100) + "）");
                    eG3GrownTotal = size;
                    nextE(G3_AFTER_NARROW);
                }
                return;
            }

            case G3_AFTER_NARROW: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("G3-after", "GUI 消失");
                    return;
                }
                long rtt = measurePageRtt(g, "iron", -1);
                if (rtt == -1) {
                    return;
                }
                eG3NarrowAfter = rtt;
                nextE(G3_AFTER_FULL);
                return;
            }

            case G3_AFTER_FULL: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("G3-after", "GUI 消失");
                    return;
                }
                long rtt = measurePageRtt(g, "", eG3GrownTotal);
                if (rtt == -1) {
                    return;
                }
                eG3FullAfter = rtt;
                // 判定：物品总数涨了几倍后，窄查询（服务端过滤 + 只回一页）耗时不随之线性上涨；
                // 整页也应该远低于观感阈值（宽上限 400ms，loopback 上通常是个位数毫秒）。
                boolean narrowOk = eG3NarrowAfter >= 0 && eG3NarrowAfter <= Math.max(80, eG3NarrowBefore * 2 + 20);
                boolean fullOk = rtt >= 0 && rtt <= 400;
                SplicedTermTestHarness.recordAccept("G3-after", narrowOk && fullOk,
                        "大网络分页：总数 " + eG3BaseTotal + " → " + eG3GrownTotal
                                + "；窄查询 " + eG3NarrowBefore + "ms → " + eG3NarrowAfter + "ms（判据 ≤max(80,2×+20)）"
                                + "；整页 " + eG3FullBefore + "ms → " + rtt + "ms（判据 ≤400ms）");
                nextE(G4_STORM);
                return;
            }

            // ---------- G4：快速连续开关面板 + 拖窗口（不崩、能收敛） ----------
            case G4_STORM: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                if (g == null) {
                    failE("G4", "GUI 消失");
                    return;
                }
                if (eStepTicks < 24) {
                    if (eStepTicks % 4 == 0) {
                        int[] point = switchPoint(g, "viewcell");
                        if (point != null) {
                            g.harnessClick(point[0], point[1]);
                        }
                    } else if (eStepTicks % 4 == 2) {
                        int[] point = g.harnessTitleDragPoint("repo");
                        if (point != null) {
                            int dx = (eStepTicks % 8 == 2) ? 24 : -24;
                            g.harnessMousePressed(point[0], point[1], 0);
                            dragMove(g, point[0], point[1], dx, 12, 3);
                            g.harnessMouseReleased(point[0] + dx, point[1] + 12, 0);
                        }
                    }
                }
                if (++eStepTicks >= 24 + SYNC_TICKS * 2) {
                    nextE(G4_CHECK);
                }
                return;
            }

            case G4_CHECK: {
                GuiSplicedTerm g = terminalGuiRef(gui);
                boolean alive = g != null;
                if (!alive) {
                    SplicedTermTestHarness.recordAccept("G4", false,
                            "风暴后终端 GUI 消失（应保持打开）");
                    nextE(E_DONE);
                    return;
                }
                int mask = g.harnessPanelMask();
                int local = g.harnessLocalPanelMask();
                String layout = g.harnessLayoutJson();
                boolean ok = mask == local && layout != null && !layout.isEmpty() && g.harnessRepoSize() > 0;
                SplicedTermTestHarness.recordAccept("G4", ok,
                        "24 tick 内 6 次 viewcell 开关 + 6 次 repo 拖拽后：GUI 存活=true，panelMask=0x"
                                + Integer.toHexString(mask) + " 本地=0x" + Integer.toHexString(local)
                                + "（收敛=" + (mask == local) + "），布局 JSON="
                                + (layout == null ? "null" : layout.length() + " 字符")
                                + "，repo 总数=" + g.harnessRepoSize());
                nextE(E_DONE);
                return;
            }

            default:
                return;
        }
    }

    /** 推进一步并复位步内计时（E 表用；{@code finish}` 由 {@code nextE} 统一触发）。 */
    private static void nextE(int nextStep) {
        eStep = nextStep;
        eStepTicks = 0;
        if (nextStep >= E_DONE) {
            finishE();
        }
    }

    /**
     * R3 分页往返计时（异步两段式）。
     *
     * <p>第一次调用：记下仓库基线、发出查询、返回 {@code -1}；之后每 tick 调用，等结果落地：
     * 返回毫秒数（≥0）或超时 {@code -2}。调用方必须"结果没到就 return（保持同一步）"。</p>
     *
     * @param expectSize 期望到达的 repo 总数；{@code -1} = 只要"首项或总数有变化"即算到达
     */
    private static long measurePageRtt(GuiSplicedTerm g, String query, int expectSize) {
        if (eRttStartNanos == 0) {
            eRttBaseline = g.harnessRepoFirstItem() + "|" + g.harnessRepoSize();
            eRttStartNanos = System.nanoTime();
            g.harnessSetSearch(query);
            return -1;
        }
        boolean arrived;
        if (expectSize >= 0) {
            arrived = g.harnessRepoSize() == expectSize;
        } else {
            arrived = !(g.harnessRepoFirstItem() + "|" + g.harnessRepoSize()).equals(eRttBaseline);
        }
        if (!arrived) {
            if (System.nanoTime() - eRttStartNanos > 3000000000L) {
                eRttStartNanos = 0;
                return -2;
            }
            return -1;
        }
        long ms = (System.nanoTime() - eRttStartNanos) / 1000000L;
        eRttStartNanos = 0;
        return ms;
    }

    private static void failE(String id, String reason) {
        SplicedTermTestHarness.recordAccept(id, false, reason);
        finishE();
    }

    /** E 系收尾：记录 E5 的跳过原因，然后走与 A 系相同的汇总打印。 */
    private static void finishE() {
        if (eDone) {
            return;
        }
        eDone = true;
        AE2Enhanced.LOGGER.info(SplicedTermTestHarness.TAG
                + "NOTE E5 跳过：双客户端同时操作需要第二个客户端进程与第二个账号；"
                + "本轮用单客户端覆盖 E1-E4（服务端侧断言见服务端日志，如 E2-server / A21b）。");
        finish();
    }

    // --- 点击注入计数（定位"注入洪流"用：相位推进条件不满足时最典型的症状就是这里持续增长） ---
    private static int eInjectedClicksThisPhase;
    private static String eInjectionPhase = "?";

    /**
     * 统一的 shift-click 注入入口：按"相位名"统计注入次数，超过阈值就打一条 NOTE
     * （含相位名与累计次数），用于区分"脚本自己循环注入"和"真实点击被放大"。
     */
    private static void injectShiftClick(GuiSplicedTerm g, int slotNumber, String phaseName) {
        if (!phaseName.equals(eInjectionPhase)) {
            if (eInjectedClicksThisPhase > 100) {
                logNote("相位 " + eInjectionPhase + " 结束：共注入 shift-click " + eInjectedClicksThisPhase + " 次");
            }
            eInjectionPhase = phaseName;
            eInjectedClicksThisPhase = 0;
        }
        eInjectedClicksThisPhase++;
        if (eInjectedClicksThisPhase == 101) {
            logNote("点击注入异常：相位 " + phaseName + " 已注入 " + eInjectedClicksThisPhase
                    + " 次（推进条件可能永远不满足 → 会把点击堆到服务端）");
        }
        g.harnessShiftClickSlot(slotNumber);
    }

    /** 终端容器里第一个指定类型的槽位号（压力测试用；找不到返回 -1）。 */
    private static int firstSlotOfType(GuiSplicedTerm gui, Class<?> slotClass) {
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slotClass.isInstance(slot)) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    /** 玩家快捷栏第 {@code hotbarIndex} 格里匹配关键字的物品数量。 */
    private static int countHotbarItem(GuiSplicedTerm gui, int hotbarIndex, String keyword) {
        int slot = gui.harnessHotbarSlotNumber(hotbarIndex);
        if (slot < 0 || gui.harnessSlotPos(slot) == null) {
            return -1;
        }
        for (net.minecraft.inventory.Slot s : gui.inventorySlots.inventorySlots) {
            if (s.slotNumber == slot) {
                return countOf(s.getStack(), keyword);
            }
        }
        return -1;
    }

    /** view cell 槽（{@code ContainerSplicedTerm.cellView}）里匹配关键字的物品总数。 */
    private static int countViewCellSlots(GuiSplicedTerm gui, String keyword) {
        if (!(gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm)) {
            return -1;
        }
        int total = 0;
        for (net.minecraft.inventory.Slot slot
                : ((com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm) gui.inventorySlots)
                        .viewCellSlots()) {
            total += countOf(slot.getStack(), keyword);
        }
        return total;
    }

    /** repo 展示槽里是否出现匹配关键字的物品。 */
    private static boolean hasRepoItem(GuiSplicedTerm gui, String keyword) {
        return gui.harnessRepoSlotPos(keyword) != null;
    }

    private static void finish() {
        phase = PHASE_FINISHED;
        List<String> results = SplicedTermTestHarness.acceptResults();
        int failures = SplicedTermTestHarness.acceptFailures();
        AE2Enhanced.LOGGER.info(SplicedTermTestHarness.TAG + "==== 验收汇总 ====");
        for (String line : results) {
            AE2Enhanced.LOGGER.info(SplicedTermTestHarness.TAG + line);
        }
        AE2Enhanced.LOGGER.info(SplicedTermTestHarness.TAG + "失败 " + failures + " / 共 " + results.size() + " 项");
        AE2Enhanced.LOGGER.info(SplicedTermTestHarness.ACCEPT_ALL_DONE_MARKER);
        if (failures == 0) {
            AE2Enhanced.LOGGER.info(SplicedTermTestHarness.DONE_MARKER);
        } else {
            AE2Enhanced.LOGGER.info(SplicedTermTestHarness.FAIL_MARKER);
        }
    }

    // ==================== A 系：程序化进世界 ====================

    /** 配置门控：A 系（内置服务端）自动进世界是否打开。 */
    private static boolean autoWorldEnabled() {
        try {
            return AE2EnhancedConfig.terminal.splicedTerminalHarnessAutoWorld;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * A 系前置：客户端停在主菜单几秒后，自动进入（必要时创建）内置世界。
     *
     * <p>为什么要程序化进世界：A 表需要在"内置服务端 + 客户端同 JVM"的环境里跑，而点进世界这一步
     * 原本只能靠 OS 级鼠标导航（CU 工具不可用时整轮验收就被卡在主菜单）。这里走的是和"单人游戏
     * → 世界列表 → 进入世界"完全相同的入口 {@link Minecraft#launchIntegratedServer}。</p>
     *
     * <p>失败处理：只尝试一次；存档缺失/读不到列表/被占用等任何异常都<b>只打日志并跳过</b>，
     * 不会把主菜单打崩（下一次启动可以手动进世界）。</p>
     */
    private static void tryAutoWorld(Minecraft mc) {
        if (autoWorldTried || mc.world != null || mc.player != null) {
            return;
        }
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu)) {
            return; // 停在别的界面（含"加载世界"进度屏）时不抢输入
        }
        if (++autoWorldWaitTicks < AUTO_WORLD_DELAY_TICKS) {
            return;
        }
        autoWorldTried = true;
        try {
            String levelId = pickLevelId(mc);
            if (levelLooksBusy(mc, levelId)) {
                logNote("A 系：存档 " + levelId + " 的 session.lock 打不开（疑似被其它进程占用）→ 跳过自动进世界，"
                        + "请手动进入或关掉占用它的进程后重试");
                return;
            }
            // 同一份设置两用：存档已存在时它只影响新生成区块；不存在时 launchIntegratedServer 会用它新建世界
            // （创造 + 作弊，harness 布景要放方块、改背包）。
            net.minecraft.world.WorldSettings settings = new net.minecraft.world.WorldSettings(
                    0L, net.minecraft.world.GameType.CREATIVE, false, false, net.minecraft.world.WorldType.DEFAULT)
                    .enableCommands();
            // 存档里带着"已卸载 mod 的注册项"时，Forge 会在真正载入世界前用 StartupQuery 弹一个
            // "Forge Mod Loader detected missing registry entries… Yes/No" 的确认框——它会阻塞输入、
            // 让自动化整轮卡在这里（本轮实测：卡在 856x512 的确认屏上，服务端线程不再 tick）。
            // Forge 自带非交互应答开关（见 net.minecraftforge.fml.common.StartupQuery#execute）：
            // 系统属性 fml.queryResult=confirm → 直接确认，并且 Forge 会**先自动备份世界**再移除这些条目。
            // 只在 dev harness + autoWorld 打开时设置，普通玩家路径不受影响。
            System.setProperty("fml.queryResult", "confirm");
            logNote("A 系：已设置 fml.queryResult=confirm（存档含已卸载 mod 的注册项时自动确认 Forge 确认框，"
                    + "Forge 会先自行备份世界）");
            logNote("A 系：自动进入内置世界（levelId=\"" + levelId + "\"，显示名 \"" + AUTO_WORLD_NAME
                    + "\"；存档不存在时按创造+作弊创建）");
            mc.launchIntegratedServer(levelId, AUTO_WORLD_NAME, settings);
        } catch (Throwable t) {
            logNote("A 系：自动进世界失败，已跳过（不重试，可手动进入世界）：" + t);
        }
    }

    /**
     * 选一个存档 id：优先"文件夹名 = {@value #AUTO_WORLD_LEVEL_ID}"，其次"显示名 = {@value #AUTO_WORLD_NAME}"，
     * 都找不到就用 {@value #AUTO_WORLD_LEVEL_ID}（交给 launchIntegratedServer 按设置新建）。
     */
    private static String pickLevelId(Minecraft mc) {
        try {
            net.minecraft.world.storage.ISaveFormat saves = mc.getSaveLoader();
            if (saves != null) {
                List<net.minecraft.world.storage.WorldSummary> list = saves.getSaveList();
                for (net.minecraft.world.storage.WorldSummary s : list) {
                    if (s != null && AUTO_WORLD_LEVEL_ID.equals(s.getFileName())) {
                        return s.getFileName();
                    }
                }
                for (net.minecraft.world.storage.WorldSummary s : list) {
                    if (s != null && AUTO_WORLD_NAME.equals(s.getDisplayName())) {
                        return s.getFileName();
                    }
                }
                logNote("A 系：存档列表里没有名为 \"" + AUTO_WORLD_NAME + "\" 的存档（共 " + list.size()
                        + " 个），将按 " + AUTO_WORLD_LEVEL_ID + " 新建");
            }
        } catch (Throwable t) {
            logNote("A 系：读存档列表失败（" + t + "）→ 仍按 " + AUTO_WORLD_LEVEL_ID + " 尝试进入/新建");
        }
        return AUTO_WORLD_LEVEL_ID;
    }

    /**
     * 存档是否疑似被别的进程占用：只探"session.lock 能不能以读写方式打开"（不截断、不写内容）。
     *
     * <p>这是<b>尽力而为</b>的探测：真正的占用往往要到 MC 自己写 session.lock 时才暴露，
     * 那种情况由 {@link #tryAutoWorld} 的 try/catch 兜住并打日志跳过。</p>
     */
    private static boolean levelLooksBusy(Minecraft mc, String levelId) {
        try {
            net.minecraft.world.storage.ISaveFormat saves = mc.getSaveLoader();
            if (saves == null) {
                return false;
            }
            java.io.File lock = saves.getFile(levelId, "session.lock");
            if (lock == null || !lock.isFile()) {
                return false; // 存档不存在（会新建）或没有锁文件
            }
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(lock, "rw")) {
                return false;
            } catch (java.io.IOException e) {
                return true;
            }
        } catch (Throwable t) {
            return false; // 探测本身出错就当"不忙"，交给 launch 的 try/catch
        }
    }

    // ==================== 输入注入 ====================

    /** 当前终端 GUI（不在终端界面时为 {@code null}）。 */
    private static GuiSplicedTerm currentGui(Minecraft mc) {
        return mc.currentScreen instanceof GuiSplicedTerm ? (GuiSplicedTerm) mc.currentScreen : null;
    }

    /** 切主手到新终端并发送真实右键；成功（找到终端）返回 true。 */
    private static boolean openTerminal(Minecraft mc) {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty()
                    && stack.getItem() == com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.OMNI_WIRELESS_TERMINAL_II) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return false;
        }
        if (mc.playerController == null) {
            return false;
        }
        mc.player.inventory.currentItem = slot;
        mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
        mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
        return true;
    }

    /** 分步拖拽移动（不含按下与松开），便于中途观察手势状态。 */
    private static void dragMove(GuiSplicedTerm gui, int x, int y, int dx, int dy, int steps) {
        for (int i = 1; i <= steps; i++) {
            gui.harnessMouseDragged(x + dx * i / steps, y + dy * i / steps, 16L * i);
        }
    }

    /** 从 (x,y) 按下后分步移动到 (x+dx, y+dy) 并松开。 */
    private static void dragTo(GuiSplicedTerm gui, int x, int y, int dx, int dy, int steps) {
        dragMove(gui, x, y, dx, dy, steps);
        gui.harnessMouseReleased(x + dx, y + dy, 0);
    }

    /** 找一个"没有任何窗口覆盖"的空白点（用于拖拽松手时不触发拼接）。 */
    private static int[] emptySpot(GuiSplicedTerm gui) {
        for (int y = gui.height - 4; y > 4; y -= 6) {
            for (int x = gui.width - 4; x > 4; x -= 6) {
                boolean covered = false;
                for (ModulePanel panel : terminalGuiRef(gui).harnessPanels()) {
                    if (!panel.visible) {
                        continue;
                    }
                    if (x >= panel.x && x < panel.x + panel.width && y >= panel.y && y < panel.y + panel.height) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return new int[] {x, y};
                }
            }
        }
        return new int[] {gui.width - 4, gui.height - 4};
    }

    /** 去掉 JSON 里的 root bounds，只比较布局树（重开时按视口收边是预期行为）。 */
    private static String stripBounds(String json) {
        if (json == null) {
            return null;
        }
        return json.replaceAll("\"bounds\"\\s*:\\s*\\{[^}]*\\}", "\"bounds\":{}");
    }

    /**
     * A22 的布局比较口径：按 root 归一化后比较。
     *
     * <p>内容（root/leaf id、模块归属、策略）、几何（bounds）全部严格比较；唯一放宽的是
     * <strong>root 之间的前后顺序</strong>：回滚后回到终端时，客户端布局与服务端 panelMask 收敛
     * （{@code applyVisibleModules} → reveal）会把承载被显形模块的窗口提到最前，于是 z 序可能与
     * 编辑前不同。这正是实机 A22-cancel 观测到的唯一差异（内容/几何/字符数完全一致）。</p>
     */
    private static boolean layoutsMatch(String expected, String actual) {
        String canonicalExpected = canonicalLayout(expected);
        String canonicalActual = canonicalLayout(actual);
        return canonicalExpected != null && canonicalActual != null && canonicalExpected.equals(canonicalActual);
    }

    /** 只差 root 顺序（严格比较不相等、归一化后相等）时返回 true；仅用于日志标注。 */
    private static boolean differsOnlyByRootOrder(String expected, String actual) {
        String strictExpected = stripBounds(expected);
        String strictActual = stripBounds(actual);
        return strictExpected != null && strictActual != null
                && !strictExpected.equals(strictActual) && layoutsMatch(expected, actual);
    }

    /** 把布局文档归一化成"name|按 root 排序的紧凑 JSON"，使 root z 序不影响比较。 */
    private static String canonicalLayout(String json) {
        if (json == null) {
            return null;
        }
        try {
            com.google.gson.JsonObject root =
                    new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            com.google.gson.JsonArray roots = root.getAsJsonArray("roots");
            if (roots == null) {
                return stripBounds(json);
            }
            List<String> blocks = new ArrayList<String>();
            for (com.google.gson.JsonElement element : roots) {
                blocks.add(stripBounds(element.toString()));
            }
            java.util.Collections.sort(blocks);
            StringBuilder sb = new StringBuilder(root.has("name") ? root.get("name").getAsString() : "");
            sb.append('|');
            for (String block : blocks) {
                sb.append(block);
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return stripBounds(json);
        }
    }

    /** 排障用：当前 Screen 的类名（null 时返回 "null"）。 */
    private static String screenName(Minecraft mc) {
        return mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getSimpleName();
    }

    /** 排障用：写一条 [HARNESS] NOTE（不进验收统计，只看日志）。 */
    private static void logNote(String message) {
        com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.info(
                com.github.aeddddd.ae2enhanced.util.dev.SplicedTermTestHarness.TAG + "NOTE " + message);
    }

    /** 排障用：布局文档长度（null 显示成 "null"）。 */
    private static String lengthOf(String json) {
        return json == null ? "null" : json.length() + "字符";
    }

    /**
     * A22 排障：两个布局文档按 {@link #stripBounds} 归一化后的首处差异窗口。
     *
     * <p>等价时返回"完全一致"，这样日志能一眼区分"内容真的没回滚"与"只是比较口径不对"。</p>
     */
    private static String layoutDiff(String expected, String actual) {
        String left = stripBounds(expected);
        String right = stripBounds(actual);
        if (left == null || right == null) {
            return "期望=" + lengthOf(expected) + " 实际=" + lengthOf(actual);
        }
        if (left.equals(right)) {
            return "stripBounds 后完全一致（" + left.length() + "字符）";
        }
        int index = 0;
        int limit = Math.min(left.length(), right.length());
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        int from = Math.max(0, index - 40);
        return "首处差异@" + index + "（期望 " + left.length() + " / 实际 " + right.length() + "字符）"
                + "期望[" + window(left, from, index + 70) + "] 实际[" + window(right, from, index + 70) + "]";
    }

    private static String window(String text, int from, int to) {
        int start = Math.max(0, Math.min(from, text.length()));
        int end = Math.max(start, Math.min(to, text.length()));
        return text.substring(start, end);
    }

    /** 从 JSON 里抽出 root bounds 文本（只用于日志）。 */
    private static String rootBoundsOfJson(String json) {
        if (json == null) {
            return "null";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"width\"\\s*:\\s*(\\d+)\\s*,\\s*\"height\"\\s*:\\s*(\\d+)").matcher(json);
        StringBuilder sb = new StringBuilder();
        while (m.find() && sb.length() < 80) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(m.group(1)).append('x').append(m.group(2));
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /** 各类槽位的物品摘要（排障用：shift-click 的物品到底去哪了）。 */
    private static String slotDigest(GuiSplicedTerm gui) {
        StringBuilder sb = new StringBuilder();
        sb.append("槽位[");
        sb.append("inv=").append(digest(gui, 0)).append(' ');
        sb.append("storage=").append(digest(gui, 1)).append(' ');
        sb.append("upgrade=").append(digest(gui, 2)).append(' ');
        sb.append("matrix=").append(digest(gui, 3));
        sb.append(']');
        return sb.toString();
    }

    private static String digest(GuiSplicedTerm gui, int group) {
        int count = 0;
        StringBuilder names = new StringBuilder();
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            boolean match;
            switch (group) {
                case 0:
                    match = isPlayerSlot(slot);
                    break;
                case 1:
                    match = slot instanceof SlotHighCapacity;
                    break;
                case 2:
                    match = slot instanceof com.github.aeddddd.ae2enhanced.client.gui.slot.SlotOmniUpgrade;
                    break;
                default:
                    match = slot instanceof RCSlotFakeCraftingMatrix;
                    break;
            }
            if (!match || slot.getStack().isEmpty()) {
                continue;
            }
            count++;
            if (names.length() < 48) {
                names.append(slot.getStack().getItem().getRegistryName().getPath())
                        .append('x').append(slot.getStack().getCount()).append(',');
            }
        }
        return count + (names.length() == 0 ? "" : "(" + names + ")");
    }

    /** 编辑器打开期间用父终端引用，其余情况用当前 GUI。 */
    private static GuiSplicedTerm terminalGuiRef(GuiSplicedTerm current) {
        if (current != null) {
            lastKnownTerminal = current;
            return current;
        }
        if (terminalGui == null) {
            // 编辑器没有回指父终端时兜底：从编辑器自己的 dock 快照拿不到面板，直接用最后一次已知值
            terminalGui = lastKnownTerminal;
        }
        return terminalGui;
    }

    /** 从编辑器反查父终端（GuiSplicedLayoutEditor 的 parent 字段）。 */
    private static GuiSplicedTerm findParentTerminal(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiSplicedLayoutEditor)) {
            return terminalGui;
        }
        return ((GuiSplicedLayoutEditor) mc.currentScreen).harnessParentTerminal();
    }

    /** 2d：137 种注入物品里有没有缺模型的（渲染成紫黑格）。 */
    private static String missingModelReport() {
        net.minecraft.client.renderer.ItemModelMesher mesher =
                Minecraft.getMinecraft().getRenderItem().getItemModelMesher();
        net.minecraft.client.renderer.block.model.IBakedModel missing =
                mesher.getModelManager().getMissingModel();
        StringBuilder bad = new StringBuilder();
        int total = 0;
        for (ItemStack stack : SplicedTermTestHarness.buildTestItems()) {
            total++;
            net.minecraft.client.renderer.block.model.IBakedModel model = mesher.getItemModel(stack);
            if (model == missing) {
                if (bad.length() < 200) {
                    bad.append(stack.getItem().getRegistryName()).append('@').append(stack.getMetadata()).append(' ');
                }
            }
        }
        return "缺模型物品 " + (bad.length() == 0 ? "0/" + total + "（清单里全部有模型）" : bad.toString());
    }

    /** A20：JEI 排除区是否覆盖所有可见面板（无 JEI 时只做逻辑断言）。 */
    private static String jeiExclusionReport(GuiSplicedTerm gui) {
        java.util.List<java.awt.Rectangle> areas = gui.getJEIExclusionArea();
        int covered = 0;
        int panels = 0;
        for (ModulePanel panel : gui.harnessPanels()) {
            if (!panel.visible) {
                continue;
            }
            panels++;
            for (java.awt.Rectangle area : areas) {
                if (area.contains(panel.x + 1, panel.y + 1)
                        && area.contains(panel.x + panel.width - 1, panel.y + panel.height - 1)) {
                    covered++;
                    break;
                }
            }
        }
        return "JEI 排除区 " + areas.size() + " 块；覆盖可见面板 " + covered + "/" + panels
                + "（dev 无 JEI/HEI，仅逻辑断言）";
    }

    /** A15：把背包里的空白样板点进样板 IN 槽。 */
    private static boolean clickItemIntoPatternSlotIn(GuiSplicedTerm gui) {
        int item = findInventorySlotItem(gui, "material");
        if (item < 0) {
            item = findInventoryItemWithBlankPattern(gui);
        }
        net.minecraft.inventory.Slot in = patternSlotIn(gui);
        if (item < 0 || in == null) {
            return false;
        }
        clickSlot(gui, item);
        gui.harnessClick(in.xPos + 8, in.yPos + 8);
        return !in.getStack().isEmpty();
    }

    /** A15：把背包里的铁锭逐个点进可见的 3x3 矩阵槽（SlotFake 每次放 1 个）。 */
    private static int fillCraftingMatrix(GuiSplicedTerm gui) {
        int item = findInventorySlotItem(gui, "iron_ingot");
        if (item < 0) {
            return 0;
        }
        List<net.minecraft.inventory.Slot> targets = visibleCraftingMatrixSlots(gui);
        clickSlot(gui, item);
        int placed = 0;
        for (int i = 0; i < Math.min(9, targets.size()); i++) {
            net.minecraft.inventory.Slot slot = targets.get(i);
            gui.harnessClick(slot.xPos + 8, slot.yPos + 8);
            if (!slot.getStack().isEmpty()) {
                placed++;
            }
        }
        return placed;
    }

    private static List<net.minecraft.inventory.Slot> visibleCraftingMatrixSlots(GuiSplicedTerm gui) {
        List<net.minecraft.inventory.Slot> out = new java.util.ArrayList<net.minecraft.inventory.Slot>();
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof RCSlotFakeCraftingMatrix
                    && ((RCSlotFakeCraftingMatrix) slot).shouldDisplay()) {
                out.add(slot);
            }
        }
        return out;
    }

    private static net.minecraft.inventory.Slot patternSlotIn(GuiSplicedTerm gui) {
        if (gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) {
            return ((com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) gui.inventorySlots).getPatternSlotIn();
        }
        return null;
    }

    private static net.minecraft.inventory.Slot patternSlotOut(GuiSplicedTerm gui) {
        if (gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) {
            return ((com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) gui.inventorySlots).getPatternSlotOut();
        }
        return null;
    }

    private static boolean patternOutHasEncoded(GuiSplicedTerm gui) {
        net.minecraft.inventory.Slot out = patternSlotOut(gui);
        if (out != null && !out.getStack().isEmpty()) {
            return true;
        }
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs
                    && !slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String patternDigest(GuiSplicedTerm gui) {
        net.minecraft.inventory.Slot in = patternSlotIn(gui);
        net.minecraft.inventory.Slot out = patternSlotOut(gui);
        int matrix = countCraftingMatrixItem(gui, "iron_ingot");
        return "矩阵铁锭=" + matrix + " IN=" + describe(in == null ? ItemStack.EMPTY : in.getStack())
                + " OUT=" + describe(out == null ? ItemStack.EMPTY : out.getStack());
    }

    private static int findInventoryItemWithBlankPattern(GuiSplicedTerm gui) {
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (!isPlayerSlot(slot) || slot.getStack().isEmpty()) {
                continue;
            }
            return slot.slotNumber;
        }
        return -1;
    }

    /** 容器里第一个承载了指定物品（英文名包含关键字）的槽位号，找不到返回 -1。 */
    private static int findSlotWithItem(GuiSplicedTerm gui, String keyword) {
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (matches(slot.getStack(), keyword)) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    /** 容器内指定物品（英文名包含关键字）的栈数（含玩家背包）。 */
    private static int countContainerItem(GuiSplicedTerm gui, String keyword) {
        int count = 0;
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (matches(slot.getStack(), keyword)) {
                count++;
            }
        }
        return count;
    }

    /** A17-out：view cell 槽组里非空的槽数（取回方向的验收点）。 */
    private static int countViewCellSlotItems(GuiSplicedTerm gui) {
        if (!(gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm)) {
            return -1;
        }
        int count = 0;
        for (net.minecraft.inventory.Slot slot : ((com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm)
                gui.inventorySlots).viewCellSlots()) {
            if (!slot.getStack().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** A17：容器里的 view cell 槽位号（用容器的 isViewCellSlot 判定）。 */
    private static int findViewCellSlot(GuiSplicedTerm gui) {
        if (!(gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm)) {
            return -1;
        }
        com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm container =
                (com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) gui.inventorySlots;
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (container.isViewCellSlot(slot)) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    /**
     * A17 排障：view cell 槽位 / shift-click 白名单 / panelMask / 光标 的只读快照。
     *
     * <p>一次把"槽位是否存在""模块是否可见（白名单是否登记）""槽位收不收 view cell""物品是不是掉在光标上"
     * 分开报告，避免只看"容器里没有 view cell"这一条时把几种完全不同的原因混在一起。</p>
     */
    private static String viewCellReport(GuiSplicedTerm gui) {
        if (!(gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm)) {
            return "容器不是 ContainerSplicedTerm（" + gui.inventorySlots.getClass().getSimpleName() + "）";
        }
        com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm container =
                (com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm) gui.inventorySlots;
        ItemStack probe = appeng.api.AEApi.instance().definitions().items().viewCell()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        StringBuilder sb = new StringBuilder();
        sb.append("viewcell可见=").append(gui.harnessModuleVisible("viewcell"))
                .append(" mask=").append(gui.harnessPanelMask())
                .append(" 白名单槽数=").append(container.slotPolicy().slotsOf("viewcell").size())
                .append(" 容器槽数=").append(container.viewCellSlots().size())
                .append(" 槽[");
        for (net.minecraft.inventory.Slot slot : container.viewCellSlots()) {
            sb.append('#').append(slot.slotNumber)
                    .append(" pos=").append(slot.xPos).append(',').append(slot.yPos)
                    .append(" valid=").append(slot.isItemValid(probe))
                    .append(" take=").append(slot.canTakeStack(Minecraft.getMinecraft().player))
                    .append(" stack=").append(describe(slot.getStack())).append(' ');
        }
        sb.append("] canAccessViewCells=").append(container.canAccessViewCells)
                .append(" probe=").append(describe(probe))
                .append(" 光标=").append(describe(Minecraft.getMinecraft().player.inventory.getItemStack()));
        ItemStack terminalItem = container.getTerminalItemStack();
        net.minecraft.nbt.NBTTagCompound tag = terminalItem.isEmpty() ? null : terminalItem.getTagCompound();
        sb.append(" 终端物品=").append(describe(terminalItem))
                .append(" viewCell标签=").append(tag == null ? "无tag" : (tag.hasKey("viewCell") ? "有" : "无"));
        return sb.toString();
    }

    // ==================== A30：条件 mod 集成自检（D 系） ====================

    /** 类路径上是否存在某个类文件（不触发类加载，与 ThaumicMixinPlugin 的检测手法一致）。 */
    private static boolean hasResource(String path) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            return cl != null && cl.getResource(path) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** A30：可选 mod 在场情况（写进日志，便于归档判断这一轮覆盖了哪些 D 项）。 */
    private static String optionalModsReport() {
        return "ae2fc=" + com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED
                + " mekanism=" + com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper.AVAILABLE
                + " mek-energistics="
                + (com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper.GAS_STORAGE_CHANNEL_CLASS != null)
                + " thaumcraft=" + hasResource("thaumcraft/api/aspects/Aspect.class")
                + " thaumic-energistics=" + hasResource("thaumicenergistics/api/storage/IEssentiaStorageChannel.class")
                + " TII=" + hasResource("nyonio/terminal_interaction_integration/api/TerminalInteractionRegistry.class");
    }

    /**
     * A30：D1-D4 各自"缝隙是否真的打通"。
     *
     * <p>不在场的 mod 记 PASS + "跳过"（裸环境由清单 D5 覆盖）；在场但缝隙不通记 FAIL。</p>
     *
     * <p><b>必须区分"这一层由谁接管"</b>：ae2fc 一旦装载，流体与气体的整条交互链
     * （我方 late mixin 配置、假物品 handler、终端点击桥）都按
     * {@link com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat#AE2FC_LOADED} 主动让位给 ae2fc
     * （见 {@code GasMixinPlugin#shouldApplyMixin} 与 {@code ModContent#preInit}）。ae2fc 自己的
     * 气体支持与 AE2E 同门槛（{@code com.glodblock.github.util.ModAndClassUtil.GAS} 判的就是 mekeng），
     * 所以"ae2fc 在场 = 气体归 ae2fc 管"不会留下空档。此时若去测我方 FakeFluids/FakeGases
     * 只会测到故意不注册的死路径 —— 必须改测 ae2fc 侧的活路径。</p>
     */
    private static void recordOptionalModChecks() {
        boolean ae2fc = com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED;

        // ---------- D1 ae2fc：流体假物品打包/解包往返（水 1000mB） ----------
        boolean d1Ok;
        String d1Detail;
        try {
            net.minecraftforge.fluids.FluidStack water = new net.minecraftforge.fluids.FluidStack(
                    net.minecraftforge.fluids.FluidRegistry.WATER, 1000);
            if (ae2fc) {
                ItemStack drop = com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidHelper.packFluid2Drops(water);
                net.minecraftforge.fluids.FluidStack back = ae2fcUnpackFluid(drop);
                d1Ok = ae2fcIsFakeItem(drop) && back != null
                        && back.getFluid() == net.minecraftforge.fluids.FluidRegistry.WATER
                        && back.amount == 1000;
                d1Detail = "ae2fc 在场 → 测 ae2fc 侧活路径：水 1000mB → " + describeFakeDrop(drop)
                        + " → 解包=" + (back == null ? "null" : back.getFluid().getName() + "x" + back.amount)
                        + "（我方 FakeFluids 按设计让位）";
            } else if (com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.FLUID_DROP == null) {
                d1Ok = false;
                d1Detail = "ae2fc 不在场但 FLUID_DROP 未注册";
            } else {
                appeng.api.storage.data.IAEItemStack packed =
                        com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.packFluid2AEDrops(water);
                appeng.api.storage.data.IAEFluidStack back = packed == null ? null
                        : com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.unpackFluid(packed);
                d1Ok = packed != null && back != null
                        && packed.getItem() == com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.FLUID_DROP;
                d1Detail = "ae2fc 不在场 → 测我方：水 1000mB → 假物品=" + (packed == null ? "null" : "ok")
                        + " → 解包=" + back;
            }
        } catch (Throwable t) {
            d1Ok = false;
            d1Detail = "流体往返异常：" + t;
        }
        SplicedTermTestHarness.recordAccept("A30-D1", d1Ok, d1Detail);

        // ---------- D2 Mekanism：气体假物品 handler 是否真的注册到"接管方" ----------
        boolean mek = com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper.AVAILABLE;
        boolean mekEnergy =
                com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper.GAS_STORAGE_CHANNEL_CLASS != null;
        boolean d2Ok;
        String d2Detail;
        if (!mek) {
            d2Ok = true;
            d2Detail = "mekanism 不在场 → 跳过（裸环境见清单 D5）";
        } else if (ae2fc) {
            // ae2fc 与 AE2E 的气体支持都以 mekeng 为前提，二者不会同时缺位。
            ItemStack ae2fcGasDrop = ae2fcItem("com.glodblock.github.integration.mek.FCGasItems", "GAS_DROP");
            boolean ae2fcGasHandler = ae2fcIsFakeItem(ae2fcGasDrop);
            d2Ok = ae2fcGasHandler
                    || (!mekEnergy && ae2fcGasDrop.isEmpty());
            d2Detail = "mekanism=true ae2fc 在场 → 测 ae2fc 侧：气体假物品=" + describeFakeDrop(ae2fcGasDrop)
                    + " handler 已注册=" + ae2fcGasHandler + " mek-energistics(存储通道)=" + mekEnergy
                    + "（我方 FakeGases 按设计让位：ae2fc 的 GAS 门槛同为 mekeng）";
        } else {
            appeng.api.storage.data.IAEItemStack ownStack = null;
            if (com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.GAS_DROP != null) {
                ownStack = appeng.util.item.AEItemStack.fromItemStack(new ItemStack(
                        com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.GAS_DROP));
            }
            boolean ownHandler = ownStack != null
                    && com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister
                            .isFakeItem(ownStack.createItemStack());
            d2Ok = ownHandler
                    && com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper.GAS_CLASS != null
                    && com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper.GAS_STACK_CLASS != null
                    && com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper.GAS_HANDLER_CLASS != null;
            d2Detail = "mekanism=true ae2fc 不在场 → 测我方：气体假物品 handler 已注册=" + ownHandler
                    + " mek-energistics(存储通道)=" + mekEnergy;
        }
        SplicedTermTestHarness.recordAccept("A30-D2", d2Ok, d2Detail);

        // ---------- D3 Thaumcraft + ThaumicEnergistics：源质始终由 AE2E 接管（ae2fc 不处理源质） ----------
        boolean tc = hasResource("thaumcraft/api/aspects/Aspect.class");
        boolean te = hasResource("thaumicenergistics/api/storage/IEssentiaStorageChannel.class");
        boolean essRegistered = com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.ESSENTIA_DROP != null;
        boolean essHandler = essRegistered
                && com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister
                        .isFakeItem(new ItemStack(
                                com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.ESSENTIA_DROP));
        SplicedTermTestHarness.recordAccept("A30-D3", !tc || (essRegistered && te && essHandler),
                "thaumcraft=" + tc + " thaumic-energistics=" + te + " 源质物品已注册=" + essRegistered
                        + " handler 已注册=" + essHandler
                        + " → " + (!tc ? "不在场 → 跳过"
                                : (essRegistered && te && essHandler ? "缝隙可用" : "缝隙缺失")));

        // D4 能量：资源处理器注册表 + 按假物品查 handler（TII 在场时额外确认其 API 类在）
        boolean tii = hasResource("nyonio/terminal_interaction_integration/api/TerminalInteractionRegistry.class");
        int handlers = com.github.aeddddd.ae2enhanced.terminal.ResourceTerminalHandlerRegistry.getHandlers().size();
        boolean energyFound = false;
        try {
            if (com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.ENERGY_DROP != null) {
                appeng.api.storage.data.IAEItemStack energyFake = appeng.util.item.AEItemStack.fromItemStack(
                        new net.minecraft.item.ItemStack(
                                com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry.ENERGY_DROP));
                energyFound = com.github.aeddddd.ae2enhanced.terminal.ResourceTerminalHandlerRegistry
                        .findForStack(energyFake) != null;
            }
        } catch (Throwable t) {
            energyFound = false;
        }
        SplicedTermTestHarness.recordAccept("A30-D4", handlers > 0 && energyFound,
                "TII=" + tii + " 资源处理器=" + handlers + " 个 能量假物品可查 handler=" + energyFound);
    }

    /** 反射取 ae2fc 某个物品字段并包成 ItemStack（取不到返回空栈）。 */
    private static ItemStack ae2fcItem(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            Object item = clazz.getField(fieldName).get(null);
            return item instanceof net.minecraft.item.Item
                    ? new ItemStack((net.minecraft.item.Item) item) : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * ae2fc 侧的"该假物品是否已注册 handler"：
     * {@code com.glodblock.github.common.item.fake.FakeItemRegister.isFakeItem(ItemStack)}。
     */
    private static boolean ae2fcIsFakeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Class<?> register = Class.forName("com.glodblock.github.common.item.fake.FakeItemRegister");
            return Boolean.TRUE.equals(register.getMethod("isFakeItem", ItemStack.class).invoke(null, stack));
        } catch (Throwable t) {
            return false;
        }
    }

    /** ae2fc 侧解包：{@code FakeItemRegister.getStack(ItemStack)} → FluidStack。 */
    private static net.minecraftforge.fluids.FluidStack ae2fcUnpackFluid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            Class<?> register = Class.forName("com.glodblock.github.common.item.fake.FakeItemRegister");
            Object back = register.getMethod("getStack", ItemStack.class).invoke(null, stack);
            return back instanceof net.minecraftforge.fluids.FluidStack
                    ? (net.minecraftforge.fluids.FluidStack) back : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 假物品的可读描述（类名 + 数量），用于日志取证。 */
    private static String describeFakeDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "null";
        }
        return stack.getItem().getClass().getName() + "x" + stack.getCount();
    }

    /** 几何快照：视口 + 每个 root/面板的矩形（判断"窗口是不是比屏幕还大"）。 */
    private static String geometrySnapshot(GuiSplicedTerm gui) {
        StringBuilder sb = new StringBuilder();
        sb.append("viewport=").append(gui.width).append('x').append(gui.height);
        for (FloatingRoot root : gui.dock().workspaceSnapshot().roots()) {
            DockRect b = root.bounds();
            sb.append(" root[").append(b.x()).append(',').append(b.y()).append(' ')
                    .append(b.width()).append('x').append(b.height())
                    .append(root.floating() ? " float" : " anchor").append(']');
        }
        for (ModulePanel panel : terminalGuiRef(gui).harnessPanels()) {
            if (!panel.visible) {
                continue;
            }
            sb.append(' ').append(panel.id()).append('[').append(panel.x).append(',').append(panel.y)
                    .append(' ').append(panel.width).append('x').append(panel.height).append(']');
        }
        return sb.toString();
    }

    /** 点击某个槽位中心（走真实槽位点击链路）。 */
    private static void clickSlot(GuiSplicedTerm gui, int slotIndex) {
        net.minecraft.inventory.Slot slot = slotAt(gui, slotIndex);
        if (slot == null) {
            return;
        }
        int x = slot.xPos + 8;
        int y = slot.yPos + 8;
        logNote("clickSlot #" + slotIndex + " @" + x + "," + y + " → " + gui.harnessDescribeHit(x, y));
        gui.harnessClick(x, y);
    }

    private static net.minecraft.inventory.Slot slotAt(GuiSplicedTerm gui, int slotIndex) {
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.slotNumber == slotIndex) {
                return slot;
            }
        }
        return null;
    }

    private static ItemStack slotStack(GuiSplicedTerm gui, int slotIndex) {
        net.minecraft.inventory.Slot slot = slotAt(gui, slotIndex);
        return slot == null ? ItemStack.EMPTY : slot.getStack();
    }

    // ==================== 几何与状态查询 ====================

    /** root 右下角握把内的一个点（{@code {x, y}}）。 */
    private static int[] bottomRightCorner(GuiSplicedTerm gui) {
        // 默认布局有多个窗口：缩放握把必须落在 repo 所在的那个窗口上（A5/A11 都是量 repo 的几何），
        // 否则会去缩放布局里的最后一个窗口（编码区），断言就量错了对象。
        int[] repoCorner = gui.harnessRootCornerOf("repo");
        if (repoCorner != null) {
            return repoCorner;
        }
        List<FloatingRoot> roots = gui.dock().workspaceSnapshot().roots();
        if (roots.isEmpty()) {
            return null;
        }
        DockRect bounds = roots.get(roots.size() - 1).bounds();
        return new int[] {bounds.right() - 4, bounds.bottom() - 4};
    }

    /**
     * 找一个可拖的分隔条，返回 {@code {x, y, vertical?}}。
     *
     * <p>优先挑"两侧都是可见叶子面板"的分隔条，避免拖到编码区外沿。</p>
     */
    private static int[] findDivider(GuiSplicedTerm gui) {
        // 默认布局是多窗口：优先挑"落在 repo 自己那个窗口里"的分隔条——A10 会先把 repo 窗口放大，
        // 若挑到别的窗口的分隔条，那一刀可能正好落在被放大的 repo 窗口（或其滚动条）底下。
        int[] repoOnly = findDividerIn(gui, repoRootBounds(gui));
        return repoOnly != null ? repoOnly : findDividerIn(gui, null);
    }

    private static DockRect repoRootBounds(GuiSplicedTerm gui) {
        for (FloatingRoot root : gui.dock().workspaceSnapshot().roots()) {
            for (com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode leaf
                    : com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees.leaves(root.content())) {
                if (leaf.moduleId().equals("repo")) {
                    return root.bounds();
                }
            }
        }
        return null;
    }

    /** {@code limit} 为 null 时接受任意窗口内的分隔条，否则只接受落在 {@code limit} 内的。 */
    private static int[] findDividerIn(GuiSplicedTerm gui, DockRect limit) {
        for (com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutProjection.DividerPlacement divider
                : gui.dock().projection().dividers()) {
            DockRect bounds = divider.bounds();
            if (bounds == null) {
                continue;
            }
            boolean vertical = bounds.width() <= 0;
            int x = bounds.x() + (vertical ? 0 : Math.max(1, bounds.width()) / 2);
            int y = bounds.y() + (vertical ? Math.max(1, bounds.height()) / 2 : 0);
            // 必须落在某个 root 内部，且离 root 四边都远（避免被当成 root 缩放握把）
            for (FloatingRoot root : gui.dock().workspaceSnapshot().roots()) {
                DockRect b = root.bounds();
                if (x > b.x() + 20 && x < b.right() - 20 && y > b.y() + 20 && y < b.bottom() - 20) {
                    if (limit != null && !limit.equals(b)) {
                        break;
                    }
                    return new int[] {x, y, vertical ? 1 : 0};
                }
            }
        }
        return null;
    }

    /** repo 面板上"编辑布局"入口的中心点（不是 RepoPanel 时返回 null）。 */
    private static int[] editorButtonPoint(GuiSplicedTerm gui) {
        ModulePanel repo = terminalGuiRef(gui).harnessPanel("repo");
        if (!(repo instanceof com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel)) {
            return null;
        }
        com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel repoPanel =
                (com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel) repo;
        return new int[] {repoPanel.editorButtonX() + 8, repoPanel.editorButtonY() + 8};
    }

    private static int repoColumns(GuiSplicedTerm gui) {
        ModulePanel repo = terminalGuiRef(gui).harnessPanel("repo");
        if (repo instanceof com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel) {
            return ((com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel) repo).columns();
        }
        return -1;
    }

    /** 模块开关行里某个模块的开关中心点。 */
    private static int[] switchPoint(GuiSplicedTerm gui, String moduleId) {
        ModulePanel repo = terminalGuiRef(gui).harnessPanel("repo");
        if (!(repo instanceof com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel)) {
            return null;
        }
        com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel repoPanel =
                (com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel) repo;
        int count = repoPanel.moduleSwitchCount();
        for (int i = 0; i < count; i++) {
            if (!moduleId.equals(repoPanel.moduleSwitchModuleId(i))) {
                continue;
            }
            int x = repoPanel.moduleSwitchX(i) + com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel.MODULE_SWITCH_SIZE / 2;
            // 开关行的 y 取决于面板内容偏移，不硬算：在面板纵向范围内扫一遍，取真正命中的那一点
            for (int y = repo.y; y < repo.y + repo.height; y++) {
                if (repoPanel.moduleSwitchAt(x, y) == i) {
                    return new int[] {x, y};
                }
            }
            return new int[] {x, repoPanel.moduleSwitchY() + 8};
        }
        return null;
    }

    /** 挑一个适合做 A12 开关测试的模块（可见、可关、且不是 repo/背包）。 */
    private static String pickSwitchModule(GuiSplicedTerm gui) {
        ModulePanel repo = terminalGuiRef(gui).harnessPanel("repo");
        if (!(repo instanceof com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel)) {
            return null;
        }
        com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel repoPanel =
                (com.github.aeddddd.ae2enhanced.client.gui.panel.RepoPanel) repo;
        for (int i = 0; i < repoPanel.moduleSwitchCount(); i++) {
            String id = repoPanel.moduleSwitchModuleId(i);
            if (id == null || "repo".equals(id) || "playerinv".equals(id)) {
                continue;
            }
            ModulePanel panel = terminalGuiRef(gui).harnessPanel(id);
            if (panel == null || !panel.closable() || !terminalGuiRef(gui).harnessModuleVisible(id)) {
                continue;
            }
            lastSwitchIndex = i;
            lastSwitchId = id;
            return id;
        }
        return null;
    }

    /** 挑一个可关闭的可见模块。 */
    private static String pickClosableModule(GuiSplicedTerm gui) {
        for (String candidate : new String[] {"viewcell", "upgrade", "storage", "crafting", "pattern"}) {
            ModulePanel panel = terminalGuiRef(gui).harnessPanel(candidate);
            if (panel != null && panel.visible && panel.closable() && panel.closeVisible()) {
                return candidate;
            }
        }
        return null;
    }

    // ==================== 槽位/物品查询 ====================

    /** 是不是玩家背包槽（本终端的背包槽是 AE2 包装槽，slot.inventory 不是 InventoryPlayer）。 */
    private static boolean isPlayerSlot(net.minecraft.inventory.Slot slot) {
        return slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer
                || slot instanceof appeng.container.slot.SlotPlayerInv
                || slot instanceof appeng.container.slot.SlotPlayerHotBar;
    }

    /** 背包槽里第一个匹配（英文名包含关键字）的槽位号，找不到返回 -1。 */
    private static int findInventorySlotItem(GuiSplicedTerm gui, String keyword) {
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (!isPlayerSlot(slot)) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (matches(stack, keyword)) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    private static int countInventoryItem(GuiSplicedTerm gui, String keyword) {
        int total = 0;
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (!isPlayerSlot(slot)) {
                continue;
            }
            total += countOf(slot.getStack(), keyword);
        }
        return total;
    }

    /** 合成矩阵槽（{@code RCSlotFakeCraftingMatrix}）里的物品总量。 */
    private static int countCraftingMatrixItem(GuiSplicedTerm gui, String keyword) {
        int total = 0;
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof RCSlotFakeCraftingMatrix) {
                total += countOf(slot.getStack(), keyword);
            }
        }
        return total;
    }

    /** 存储槽（{@code SlotHighCapacity}）里的物品总量。 */
    private static int countStorageItem(GuiSplicedTerm gui, String keyword) {
        int total = 0;
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof SlotHighCapacity) {
                total += countOf(slot.getStack(), keyword);
            }
        }
        return total;
    }

    /** 第一个合成矩阵槽的槽位号。 */
    private static int findCraftingMatrixSlot(GuiSplicedTerm gui) {
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof RCSlotFakeCraftingMatrix) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    /** 合成输出槽（{@code SlotPatternTerm}）的槽位号。 */
    private static int findCraftingOutputSlot(GuiSplicedTerm gui) {
        for (net.minecraft.inventory.Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof SlotPatternTerm) {
                return slot.slotNumber;
            }
        }
        return -1;
    }

    private static int countOf(ItemStack stack, String keyword) {
        return matches(stack, keyword) ? stack.getCount() : 0;
    }

    private static boolean matches(ItemStack stack, String keyword) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return false;
        }
        return stack.getItem().getRegistryName().getPath().contains(keyword);
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "空";
        }
        return stack.getItem().getRegistryName() + " x" + stack.getCount();
    }

    private static String s20(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 20 ? value : value.substring(0, 20) + "...";
    }

    private static String length(String value) {
        return value;
    }

}
