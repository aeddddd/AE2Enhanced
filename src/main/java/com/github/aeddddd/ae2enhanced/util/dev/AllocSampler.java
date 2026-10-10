package com.github.aeddddd.ae2enhanced.util.dev;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ItemStack 分配采样器（事故排查用，harness 门控）。
 *
 * <p>Forge 在**每次** {@code new ItemStack(...)} 时会发一封 {@link AttachCapabilitiesEvent}（payload 为
 * ItemStack），因此订阅它就能以很低的成本统计"谁在造 ItemStack"：</p>
 * <ul>
 *   <li>每次都只做计数（原子自增 + 按"顶层业务帧"聚合）；</li>
 *   <li>每 {@link #SAMPLE_INTERVAL} 次才取一次栈、写一行到 {@code build/tmp/alloc_stacks.txt}
 *       （最多 {@link #MAX_LINES} 行，避免拖慢被测进程）；</li>
 *   <li>每 {@link #WARN_INTERVAL} 次打一条 WARN，带 Top 5 来源，便于在看门狗杀进程前留下证据。</li>
 * </ul>
 *
 * <p>只有 {@code splicedTerminalTestHarness} 打开时才工作，关闭时订阅方法第一行返回。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class AllocSampler {

    /** 采样间隔（每 N 次分配取一次栈）。 */
    private static final long SAMPLE_INTERVAL = 5000L;
    /** 最多写多少行采样到文件。 */
    private static final int MAX_LINES = 300;
    /** 每 N 次分配打一条 WARN（含 Top 来源）。 */
    private static final long WARN_INTERVAL = 200000L;

    private static final AtomicLong COUNT = new AtomicLong();
    private static final Map<String, AtomicLong> BY_SOURCE = new ConcurrentHashMap<String, AtomicLong>();
    private static final AtomicLong LINES = new AtomicLong();
    private static BufferedWriter writer;
    private static final Object WRITE_LOCK = new Object();

    private AllocSampler() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        if (!SplicedTermTestHarness.enabled()) {
            return;
        }
        long n = COUNT.incrementAndGet();
        if (n % SAMPLE_INTERVAL != 0) {
            if (n % WARN_INTERVAL == 0) {
                AE2Enhanced.LOGGER.warn("[AE2E] ItemStack 分配已达 {} 次；Top 来源：{}", n, topSources());
            }
            return;
        }
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        String source = topBusinessFrame(frames);
        AtomicLong counter = BY_SOURCE.get(source);
        if (counter == null) {
            counter = new AtomicLong();
            BY_SOURCE.put(source, counter);
        }
        counter.incrementAndGet();
        writeSample(n, source, frames);
    }

    /** 跳过 java./sun./net.minecraft./net.minecraftforge. 之后的第一帧（= 真正在造 ItemStack 的业务代码）。 */
    private static String topBusinessFrame(StackTraceElement[] frames) {
        for (StackTraceElement frame : frames) {
            String cls = frame.getClassName();
            if (cls.startsWith("java.") || cls.startsWith("sun.") || cls.startsWith("javax.")) {
                continue;
            }
            if (cls.startsWith("net.minecraft.") || cls.startsWith("net.minecraftforge.")) {
                continue;
            }
            return cls + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "（只有 MC/Forge 帧）";
    }

    private static void writeSample(long n, String source, StackTraceElement[] frames) {
        synchronized (WRITE_LOCK) {
            long line = LINES.get();
            if (line >= MAX_LINES) {
                return;
            }
            try {
                if (writer == null) {
                    File dir = new File("build/tmp");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    writer = new BufferedWriter(new FileWriter(new File(dir, "alloc_stacks.txt"), true));
                }
                writer.write("#" + n + " source=" + source);
                writer.newLine();
                int limit = Math.min(frames.length, 14);
                for (int i = 0; i < limit; i++) {
                    writer.write("    at " + frames[i]);
                    writer.newLine();
                }
                writer.flush();
                LINES.incrementAndGet();
            } catch (Throwable ignored) {
                LINES.set(MAX_LINES); // 写失败就不再尝试
            }
        }
    }

    private static String topSources() {
        List<Map.Entry<String, AtomicLong>> entries =
                new ArrayList<Map.Entry<String, AtomicLong>>(BY_SOURCE.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size() && i < 5; i++) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(entries.get(i).getValue().get()).append('×').append(entries.get(i).getKey());
        }
        return sb.length() == 0 ? "（尚无采样）" : sb.toString();
    }

    /** 汇总（供 harness NOTE 使用）。 */
    public static String summary() {
        return "ItemStack 分配=" + COUNT.get() + "；Top 来源：" + topSources();
    }
}
