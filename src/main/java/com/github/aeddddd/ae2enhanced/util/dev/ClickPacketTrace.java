package com.github.aeddddd.ae2enhanced.util.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 点击包取证：把"驱动注入 → 客户端 {@code PlayerControllerMP.windowClick} → 服务端收到的
 * {@code CPacketClickWindow}"三侧的计数与字段记下来，用于定位"少量点击被放大成百万次调用"。
 *
 * <p>只由 dev harness 门控调用（{@link SplicedTermTestHarness#enabled()} 为 false 时本类不产生任何输出），
 * 且自身有界：每 tick 只保留计数与"Top 重复键"，不会因为洪水而自己制造洪水。</p>
 */
public final class ClickPacketTrace {

    /** 单 tick 内超过这个数量就认为是异常洪流，摘要里会带上 Top 重复键。 */
    public static final int FLOOD_THRESHOLD = 200;

    private static final Object LOCK = new Object();

    // --- 服务端收到包 ---
    private static int serverPackets;
    private static int serverThisTick;
    private static int serverMaxPerTick;
    private static String serverWorstTickTop = "";
    private static final Map<String, Integer> serverKeysThisTick = new HashMap<String, Integer>();

    // --- 客户端 windowClick 调用 ---
    private static int clientCalls;
    private static int clientThisTick;
    private static int clientMaxPerTick;
    private static String clientWorstTickTop = "";
    private static final Map<String, Integer> clientKeysThisTick = new HashMap<String, Integer>();

    private ClickPacketTrace() {
    }

    /** 服务端：{@code NetHandlerPlayServer.processClickWindow} 入口。 */
    public static void serverPacket(int windowId, int slotId, int button, String clickType, short actionNumber) {
        synchronized (LOCK) {
            serverPackets++;
            serverThisTick++;
            count(serverKeysThisTick, key(slotId, button, clickType, windowId, actionNumber));
            // 关键：洪流在"挂死的那一个 tick"里发生，periodic NOTE 永远打不出来 →
            // 到阈值时立刻 WARN（log4j 立即落盘），并把洪流前若干行直接写文件（不经日志系统）。
            if (serverThisTick == FLOOD_THRESHOLD) {
                AE2Enhanced.LOGGER.warn("[AE2E] 服务端单 tick 收到点击包已达 {}：{}", serverThisTick, summary());
            }
            if (serverThisTick >= FLOOD_THRESHOLD) {
                dump("S", serverThisTick, "win=" + windowId + " slot=" + slotId + " btn=" + button
                        + " mode=" + clickType + " action=" + actionNumber);
            }
        }
    }

    /** 客户端：{@code PlayerControllerMP.windowClick} 入口。 */
    public static void clientCall(int windowId, int slotId, int button, String clickType) {
        synchronized (LOCK) {
            clientCalls++;
            clientThisTick++;
            count(clientKeysThisTick, key(slotId, button, clickType, windowId, (short) -1));
            if (clientThisTick == FLOOD_THRESHOLD) {
                StringBuilder stack = new StringBuilder();
                StackTraceElement[] frames = new Throwable().getStackTrace();
                for (int i = 1; i < frames.length && i <= 4; i++) {
                    stack.append(" <- ").append(frames[i]);
                }
                AE2Enhanced.LOGGER.warn("[AE2E] 客户端单 tick windowClick 已达 {}：{}；调用方{}",
                        clientThisTick, summary(), stack);
            }
            if (clientThisTick >= FLOOD_THRESHOLD) {
                dump("C", clientThisTick, "win=" + windowId + " slot=" + slotId + " btn=" + button
                        + " mode=" + clickType);
            }
        }
    }

    // --- 洪流专用文件落盘（只在检测到洪流后启用；最多 FLOOD_DUMP_LIMIT 行/侧） ---
    private static final int FLOOD_DUMP_LIMIT = 5000;
    private static final Map<String, Integer> dumpLines = new HashMap<String, Integer>();
    private static final Map<String, java.io.BufferedWriter> dumpWriters = new HashMap<String, java.io.BufferedWriter>();

    private static void dump(String side, int tickCount, String detail) {
        Integer written = dumpLines.get(side);
        int count = written == null ? 0 : written;
        if (count >= FLOOD_DUMP_LIMIT) {
            return;
        }
        try {
            java.io.BufferedWriter writer = dumpWriters.get(side);
            if (writer == null) {
                java.io.File dir = new java.io.File("build/tmp");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                java.io.File file = new java.io.File(dir,
                        "click_flood_" + ("S".equals(side) ? "server" : "client") + ".txt");
                writer = new java.io.BufferedWriter(new java.io.FileWriter(file, true));
                dumpWriters.put(side, writer);
            }
            writer.write(side + " tickCount=" + tickCount + " " + detail);
            writer.newLine();
            if (count % 100 == 99) {
                writer.flush(); // 周期性落盘：看门狗杀进程前的证据也能留住
            }
            dumpLines.put(side, count + 1);
        } catch (Throwable ignored) {
            dumpLines.put(side, FLOOD_DUMP_LIMIT); // 写失败就不再尝试
        }
    }

    private static void count(Map<String, Integer> map, String key) {
        Integer prev = map.get(key);
        map.put(key, prev == null ? 1 : prev + 1);
    }

    private static String key(int slotId, int button, String clickType, int windowId, short actionNumber) {
        return "slot=" + slotId + ",btn=" + button + ",mode=" + clickType + ",win=" + windowId
                + (actionNumber < 0 ? "" : ",action=" + actionNumber);
    }

    /**
     * 每个 server tick 调一次（由 harness 调）：滚动本 tick 的统计。
     *
     * @return 本 tick 服务端包数（调用方据此决定是否打点）
     */
    public static int rollServerTick() {
        synchronized (LOCK) {
            int count = serverThisTick;
            if (count > serverMaxPerTick) {
                serverMaxPerTick = count;
                serverWorstTickTop = topKeys(serverKeysThisTick);
            }
            serverThisTick = 0;
            serverKeysThisTick.clear();
            return count;
        }
    }

    /** 每个 client tick 调一次（由 harness 调）。 */
    public static void rollClientTick() {
        synchronized (LOCK) {
            int count = clientThisTick;
            if (count > clientMaxPerTick) {
                clientMaxPerTick = count;
                clientWorstTickTop = topKeys(clientKeysThisTick);
            }
            clientThisTick = 0;
            clientKeysThisTick.clear();
        }
    }

    private static String topKeys(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return "（无）";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        entries.sort(new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size() && i < 5; i++) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(entries.get(i).getValue()).append('×').append(entries.get(i).getKey());
        }
        return sb.toString();
    }

    /** 本 tick 客户端 windowClick 调用数（呼叫方在滚动前读取）。 */
    public static int clientCallsThisTick() {
        synchronized (LOCK) {
            return clientThisTick;
        }
    }

    /** 供 harness NOTE 用的三侧汇总。 */
    public static String summary() {
        synchronized (LOCK) {
            return "客户端 windowClick=" + clientCalls + "（单 tick 峰值 " + clientMaxPerTick + "）"
                    + "，服务端收包=" + serverPackets + "（单 tick 峰值 " + serverMaxPerTick + "）"
                    + "；服务端最忙 tick 的重复 Top: " + (serverMaxPerTick > FLOOD_THRESHOLD ? serverWorstTickTop : "未超阈值")
                    + "；客户端最忙 tick: " + (clientMaxPerTick > FLOOD_THRESHOLD ? clientWorstTickTop : "未超阈值");
        }
    }

    /** 诊断用：服务端累计收包数。 */
    public static int serverPacketCount() {
        synchronized (LOCK) {
            return serverPackets;
        }
    }

    /** 诊断用：客户端累计 windowClick 数。 */
    public static int clientCallCount() {
        synchronized (LOCK) {
            return clientCalls;
        }
    }
}
