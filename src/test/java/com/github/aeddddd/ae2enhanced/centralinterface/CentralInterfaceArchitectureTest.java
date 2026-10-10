package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 中枢接口源码级架构<b>棘轮</b>测试（重构计划 P0 · §3.2）。
 *
 * <p>棘轮的含义：不要求今天清零，只要求<b>不许恶化</b>。断言是"实际集合是白名单的子集"，
 * 因此悄悄删掉依赖（改进）不会失败，新增依赖（劣化）立刻失败。</p>
 *
 * <p>为什么不是绝对禁止：计划初稿写的是"核心包不得 import {@code appeng.me.*}"，
 * 实测<b>今天就不成立</b>——{@code DualityCentralInterface} / {@code VirtualBatchEngine} /
 * {@code IGridConnection} 都 import {@code appeng.me.GridAccessException}（因为
 * {@code IGridConnection#storage()/energy()} 声明了 {@code throws GridAccessException}）。
 * 绝对断言会让测试一上来就红，违反"不留红测试"。</p>
 */
public class CentralInterfaceArchitectureTest {

    private static final String CORE_PKG_RELATIVE =
            "src/main/java/com/github/aeddddd/ae2enhanced/centralinterface";

    private static final Pattern APPENG_ME_IMPORT = Pattern.compile(
            "^\\s*import\\s+(appeng\\.me\\.[A-Za-z0-9_.]+)\\s*;", Pattern.MULTILINE);

    /**
     * 现存 {@code appeng.me.*} 依赖白名单（已逐处核实）。只许缩小，不许新增。
     *
     * <p>{@code DualityCentralInterface} 与 {@code VirtualBatchEngine} 两条是<b>待消灭的泄漏</b>：
     * 消灭途径见计划 §3.2（把 {@code IGridConnection#storage()/energy()} 的
     * {@code throws GridAccessException} 改为返回 {@code Optional}，则调用点不再需要该 import）。</p>
     */
    private static final Map<String, Set<String>> APPENG_ME_ALLOWLIST = buildAllowlist();

    /**
     * 5 个 {@code @WrapOperation} 包装同一 {@code CraftingCPUCluster.pushPattern} 调用点的声明顺序。
     * 该顺序决定包装链的嵌套语义（后应用者在外层），当前无任何其他守护。
     */
    private static final List<String> REQUIRED_PUSH_PATTERN_WRAPPERS = Arrays.asList(
            "MixinCraftingCPUClusterAggregate",
            "MixinCraftingCPUClusterBatch",
            "MixinCraftingCPUClusterVirtualBatch",
            "MixinCraftingCPUClusterRemaining",
            "MixinCraftingCPUClusterPushCooldown");

    @Test
    public void corePackageDoesNotGrowAppengMeDependencies() throws IOException {
        Path dir = resolve(CORE_PKG_RELATIVE);
        Map<String, Set<String>> actual = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                Set<String> hits = new HashSet<>();
                Matcher matcher = APPENG_ME_IMPORT.matcher(read(file));
                while (matcher.find()) {
                    hits.add(matcher.group(1));
                }
                if (!hits.isEmpty()) {
                    actual.put(file.getFileName().toString(), hits);
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : actual.entrySet()) {
            Set<String> allowed = APPENG_ME_ALLOWLIST.getOrDefault(entry.getKey(), Collections.emptySet());
            assertThat(entry.getValue())
                    .as("核心包新增了 appeng.me 依赖：%s（计划 §3.2 棘轮白名单，若确为有意新增请同时更新白名单与计划）",
                            entry.getKey())
                    .isSubsetOf(allowed);
        }

        // 白名单中的条目若已完全消失，说明该条可删除；此处只提示不失败（棘轮允许收紧）。
        for (Map.Entry<String, Set<String>> allowedEntry : APPENG_ME_ALLOWLIST.entrySet()) {
            Set<String> hits = actual.get(allowedEntry.getKey());
            if (hits == null) {
                continue;
            }
            assertThat(hits)
                    .as("%s 的实际 appeng.me 依赖应仍在白名单内", allowedEntry.getKey())
                    .isSubsetOf(allowedEntry.getValue());
        }
    }

    @Test
    public void pushPatternWrapperOrderIsFrozen() throws IOException {
        String json = read(resolve("src/main/resources/mixins.ae2enhanced.late.json"));

        List<String> declared = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"ae2\\.(MixinCraftingCPUCluster[A-Za-z]*)\"").matcher(json);
        while (matcher.find()) {
            String simpleName = matcher.group(1);
            if (REQUIRED_PUSH_PATTERN_WRAPPERS.contains(simpleName)) {
                declared.add(simpleName);
            }
        }

        assertThat(declared)
                .as("5 个 @WrapOperation 包同一 pushPattern 调用点的嵌套语义由 mixins json 的声明顺序决定，"
                        + "重排等价于改写 CPU 记账语义（见 docs/planning/central-interface-handler-refactor-plan.md §3.2）")
                .isEqualTo(REQUIRED_PUSH_PATTERN_WRAPPERS);
    }

    /**
     * handler 包不得再各自实现 {@code HandlerUtils} 的输入/产物匹配逻辑.
     *
     * <p>重构前 `EnderIO` / `NuclearCraft` / `ThermalExpansion` 三个 handler 各有一份逐字相同的
     * {@code isInputMaterial} / {@code matchesLoosely} 私有副本，绕过了公共实现；P4 已全部替换为
     * 调用 {@code HandlerUtils}，本断言防止复发（该断言在 P0 时会让构建变红，故推迟到 P4 落地后启用）。</p>
     */
    @Test
    public void handlerPackageDoesNotRedeclareSharedMatchingHelpers() throws IOException {
        Pattern duplicatedHelper = Pattern.compile(
                "private\\s+static\\s+boolean\\s+(isInputMaterial|matchesLoosely)\\s*\\(");
        Path dir = resolve("src/main/java/com/github/aeddddd/ae2enhanced/centralinterface/handler");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                Matcher matcher = duplicatedHelper.matcher(read(file));
                while (matcher.find()) {
                    offenders.add(dir.relativize(file) + " -> " + matcher.group(1));
                }
            }
        }

        assertThat(offenders)
                .as("这些文件重新声明了应由 HandlerUtils 提供的公共匹配逻辑")
                .isEmpty();
    }

    // ---- helpers ----

    private static Map<String, Set<String>> buildAllowlist() {
        Map<String, Set<String>> allowlist = new HashMap<>();
        // seam 本体，不受限
        allowlist.put("NetworkAccess.java", setOf(
                "appeng.me.GridAccessException",
                "appeng.me.helpers.AENetworkProxy",
                "appeng.me.helpers.MachineSource"));
        // seam 签名（storage()/energy() throws GridAccessException）
        allowlist.put("IGridConnection.java", setOf("appeng.me.GridAccessException"));
        // AE2 host 契约
        allowlist.put("ICentralInterfaceHost.java", setOf("appeng.me.helpers.IGridProxyable"));
        // 以下两条为待消灭的泄漏
        allowlist.put("DualityCentralInterface.java", setOf("appeng.me.GridAccessException"));
        allowlist.put("VirtualBatchEngine.java", setOf("appeng.me.GridAccessException"));
        return allowlist;
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 从测试工作目录向上查找相对路径。Gradle Test 的默认工作目录是工程根，
     * 但 IDE 直跑 JUnit 时可能是模块目录，因此向上兜底若干层。
     */
    private static Path resolve(String relative) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到相对路径 " + relative
                + "（自 " + Paths.get("").toAbsolutePath() + " 向上查找 4 层）");
    }
}
