package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModel;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpModelDump;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpResult;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.RevisedSimplex;

/**
 * 失败模型离线回放:生产环境求解失败时模型被转储到 lp-dumps/,
 * 本测试逐字节回放同一份模型复现数值缺陷(无需游戏环境).
 * <p>用法:{code ./gradlew test --tests LpModelReplayTest -Dae2e.lpdump.dir=<目录>};
 * 未指定目录时跳过.</p>
 */
public class LpModelReplayTest {

    @Test
    public void replayDumpedModels() throws Exception {
        String dirPath = System.getProperty("ae2e.lpdump.dir");
        org.junit.jupiter.api.Assumptions.assumeTrue(dirPath != null, "未指定 -Dae2e.lpdump.dir");
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".lpm"));
        org.junit.jupiter.api.Assumptions.assumeTrue(files != null && files.length > 0,
                "目录无 .lpm 转储: " + dir);
        java.util.Arrays.sort(files);
        for (File file : files) {
            LpModel model = LpModelDump.read(file);
            LpResult result = RevisedSimplex.solve(model);
            System.out.println("[REPLAY] " + file.getName() + " → " + result.status
                    + (result.reason != null ? " (" + result.reason + ")" : "")
                    + " 迭代=" + result.iterations);
            assertThat(result.status).as("回放 %s", file.getName())
                    .isEqualTo(LpResult.Status.OPTIMAL);
        }
    }
}
