package com.github.aeddddd.ae2enhanced.centralinterface;

import com.github.aeddddd.ae2enhanced.diag.DiagLog;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 中枢 ME 接口流体生命周期的<b>逐节点</b>诊断日志.
 *
 * <p>用途：流体"凭空消失"这类故障无法在离线环境复现，必须在实机上一次跑出因果链。
 * 本类把流体的每个处理节点都编号命名，每行日志都带"数量 + 流体名 + 位置 + 动作"，
 * 于是只要看日志就能判定流体死在哪个节点（详见各节点 javadoc 与交付说明）。</p>
 *
 * <p><b>节点名（日志前缀 {@code [AE2E-Fluid][节点]}）</b>：</p>
 * <ul>
 *   <li>{@link #COLLECT_EXPECTED} —— 收集阶段 1：按样板声明的产物精确抽取</li>
 *   <li>{@link #COLLECT_SCAN} —— 收集阶段 2：按机器自身声明的 {@code canDrain} 扫描抽取</li>
 *   <li>{@link #PRE_CLEAN} —— 发配前回收输出罐残留（{@code clearOutputFluids}）</li>
 *   <li>{@link #INJECT} / {@link #INJECT_OK} / {@link #INJECT_REJECT} / {@link #INJECT_THROW}
 *       —— 注入 AE 流体通道（尝试 / 全部接受 / 部分或全部被拒 / 抛异常）</li>
 *   <li>{@link #PUSHBACK_OK} / {@link #PUSHBACK_FAIL} —— 兜底第 1 级：推回目标机器</li>
 *   <li>{@link #BUFFER} / {@link #BUFFER_FLUSH} / {@link #BUFFER_EXPIRE} —— 兜底第 2 级：接口流体缓冲</li>
 *   <li>{@link #PACK} / {@link #PACK_FAIL} —— 兜底第 3 级：转流体假物品</li>
 *   <li>{@link #DROP_INJECT} / {@link #DROP_STASH} —— 假物品走物品通道 / 进接口 storage 槽</li>
 *   <li>{@link #DESTROYED} —— 真正被丢弃（唯一会丢流体的节点，必须重点看）</li>
 * </ul>
 *
 * <p><b>节流</b>：按 (节点, 流体名) 计数，前 {@link #BURST} 条原样输出，之后每
 * {@link #PERIOD} 条输出一条并带 {@code [xN]} 后缀。因此开启后既能看到首次故障现场，
 * 又不会在长期运行的机器上刷屏。开关关闭时本类零格式化开销。</p>
 */
public final class FluidFlowDiag {

    /** 收集阶段 1：按样板声明产物精确抽取。 */
    public static final String COLLECT_EXPECTED = "COLLECT-1";
    /** 收集阶段 2：按机器 canDrain 声明扫描抽取。 */
    public static final String COLLECT_SCAN = "COLLECT-2";
    /** 发配前回收输出罐残留。 */
    public static final String PRE_CLEAN = "PRE-CLEAN";
    /** 本批流体输入被推入目标机器（此后流体归机器所有，可能被机器消耗）。 */
    public static final String PUSH_INPUT = "PUSH-INPUT";
    /** 回退（超时/失败）时流体未被抽取，保留在目标内。 */
    public static final String REVERT_KEEP = "REVERT-KEEP";
    /** 注入流体通道（尝试）。 */
    public static final String INJECT = "INJECT";
    /** 注入流体通道（全部被接受）。 */
    public static final String INJECT_OK = "INJECT-OK";
    /** 注入流体通道（部分或全部被拒）。 */
    public static final String INJECT_REJECT = "INJECT-REJECT";
    /** 注入流体通道（抛异常，按被拒处理）。 */
    public static final String INJECT_THROW = "INJECT-THROW";
    /** 推回目标成功。 */
    public static final String PUSHBACK_OK = "PUSHBACK-OK";
    /** 推回目标失败。 */
    public static final String PUSHBACK_FAIL = "PUSHBACK-FAIL";
    /** 进入接口流体缓冲。 */
    public static final String BUFFER = "BUFFER";
    /** 缓冲重试成功注入流体通道。 */
    public static final String BUFFER_FLUSH = "BUFFER-FLUSH";
    /** 缓冲重试耗尽，转入假物品兜底。 */
    public static final String BUFFER_EXPIRE = "BUFFER-EXPIRE";
    /** 兜底第 3 级：转流体假物品。 */
    public static final String PACK = "PACK";
    /** 无法转成流体假物品。 */
    public static final String PACK_FAIL = "PACK-FAIL";
    /** 假物品注入物品通道。 */
    public static final String DROP_INJECT = "DROP-INJECT";
    /** 假物品被物品通道拒绝，落到接口 storage 槽/世界。 */
    public static final String DROP_STASH = "DROP-STASH";
    /** 流体被真正丢弃（走投无路）。 */
    public static final String DESTROYED = "DESTROYED";

    /** 每个 (节点, 流体) 无节流输出的条数上限。 */
    private static final int BURST = 5;
    /** 超出 BURST 后的采样周期。 */
    private static final int PERIOD = 20;
    /** 计数器上限（超过则整体清空，避免长会话下无界增长）。 */
    private static final int COUNTER_LIMIT = 512;

    private static final Map<String, int[]> COUNTERS = new HashMap<>();

    private FluidFlowDiag() {
    }

    /** 开关是否打开；调用方可据此跳过昂贵的日志参数构造。 */
    public static boolean enabled() {
        return DiagSwitch.isEnabled(DiagSwitch.CENTRAL_FLUID);
    }

    /**
     * 记录一个流体生命周期节点.
     *
     * @param node      节点名（本类的常量之一）
     * @param fluidName 流体名（{@code null} 时用 {@code -} 参与节流键）
     * @param message   带占位符的消息
     * @param args      消息参数
     */
    public static void log(String node, String fluidName, String message, Object... args) {
        if (!DiagSwitch.isEnabled(DiagSwitch.CENTRAL_FLUID)) {
            return;
        }
        int seen = bump(node, fluidName == null ? "-" : fluidName);
        if (seen > BURST && seen % PERIOD != 0) {
            return;
        }
        String suffix = seen > BURST ? " [x" + seen + "]" : "";
        DiagLog.info(DiagSwitch.CENTRAL_FLUID, "[AE2E-Fluid][" + node + "] " + message + suffix, args);
    }

    /**
     * 记录一个与具体流体堆叠相关的节点，自动拼出"数量 + 流体名".
     */
    public static void logFluid(String node, FluidStack fluid, String message, Object... args) {
        log(node, fluidName(fluid), message, args);
    }

    /** 记录"某位置的机器"上的节点，自动拼出"数量 + 流体名 + 位置". */
    public static void logAt(String node, FluidStack fluid, String blockId, BlockPos pos,
                             String action, Object... extra) {
        if (!DiagSwitch.isEnabled(DiagSwitch.CENTRAL_FLUID)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(action).append(' ').append(amountOf(fluid)).append("mb ").append(fluidName(fluid));
        if (blockId != null || pos != null) {
            sb.append(" at ").append(blockId == null ? "?" : blockId);
            if (pos != null) {
                sb.append('@').append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
            }
        }
        for (Object o : extra) {
            sb.append(' ').append(o);
        }
        log(node, fluidName(fluid), sb.toString());
    }

    /** 流体注册名；流体为空时返回 {@code -}。 */
    public static String fluidName(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            return "-";
        }
        return fluid.getFluid().getName();
    }

    /** 流体数量（mB）；流体为空时返回 0。 */
    public static long amountOf(FluidStack fluid) {
        return fluid == null ? 0L : fluid.amount;
    }

    private static int bump(String node, String fluidName) {
        String key = node + '|' + fluidName;
        int[] counter = COUNTERS.get(key);
        if (counter == null) {
            if (COUNTERS.size() >= COUNTER_LIMIT) {
                COUNTERS.clear();
            }
            counter = new int[1];
            COUNTERS.put(key, counter);
        }
        counter[0]++;
        return counter[0];
    }
}
