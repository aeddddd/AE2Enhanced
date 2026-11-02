package com.github.aeddddd.ae2enhanced.diag.plansnapshot;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.observation.PlanSnapshotObservation;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 通用计划快照的 JSON 诊断持久化。 */
public final class PlanSnapshot extends PlanSnapshotObservation {

    private static final Gson GSON = new Gson();

    public static File dump(PlanSnapshotObservation snapshot) {
        try {
            File dir = new File("logs/ae2enhanced");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, "plansnapshot-" + snapshot.timestamp + ".json");
            try (Writer writer = new java.io.OutputStreamWriter(Files.newOutputStream(file.toPath()),
                    StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
            AE2Enhanced.LOGGER.info("[LP计划] 计划快照已写出: {} (样板 {}, 库存键 {})",
                    file.getAbsolutePath(), snapshot.patterns.size(), snapshot.stock.size());
            return file;
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[LP计划] 计划快照写出失败(不影响求解)", t);
            return null;
        }
    }

    public static void save(PlanSnapshotObservation snapshot, File file) throws IOException {
        if (snapshot.root != null && snapshot.root.count == 0) {
            snapshot.root.count = 1;
        }
        try (Writer writer = new java.io.OutputStreamWriter(Files.newOutputStream(file.toPath()),
                StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot, writer);
        }
    }

    public static PlanSnapshot load(File file) throws IOException {
        try (Reader reader = new java.io.InputStreamReader(Files.newInputStream(file.toPath()),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, PlanSnapshot.class);
        }
    }

    public void save(File file) throws IOException {
        save(this, file);
    }
}
