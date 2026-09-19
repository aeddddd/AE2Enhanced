package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单元自举重解轨迹的文本转储. 种子自举重解超轮截断时, 把单元键、库存、需求、样板表与
 * 逐轮轨迹写入 {@code <workdir>/lp-dumps/unit-*.txt}, 用于离线定位重解不收敛.
 * 与 {@link LpModelDump} 同目录, IO 失败时静默返回 null.
 */
public final class UnitTraceDump {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private UnitTraceDump() {
    }

    /** 把轨迹文本写入 {@code <workdir>/lp-dumps/}, 返回文件; IO 失败时静默返回 null. */
    public static File dump(String tag, CharSequence content) {
        try {
            File dir = new File("lp-dumps");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, "unit-" + tag + "-" + TS.format(new Date()) + "-"
                    + SEQ.incrementAndGet() + ".txt");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file),
                    StandardCharsets.UTF_8)) {
                w.write(content.toString());
            }
            return file;
        } catch (Throwable t) {
            return null;
        }
    }
}
