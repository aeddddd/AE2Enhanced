package com.github.aeddddd.ae2enhanced;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
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

/** 全仓库包依赖棘轮：历史边只许减少，新增边必须显式更新迁移基线。 */
public class ArchitectureDependencyTest {

    private static final String PACKAGE_PREFIX = "com.github.aeddddd.ae2enhanced.";
    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?" + Pattern.quote(PACKAGE_PREFIX)
                    + "([A-Za-z0-9_.]+)\\s*;", Pattern.MULTILINE);

    private static List<Set<String>> cyclicComponents(Set<String> nodes, Map<String, Set<String>> edges) {
        List<Set<String>> result = new ArrayList<>();
        Map<String, Integer> indices = new HashMap<>();
        Map<String, Integer> lowlinks = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        int[] nextIndex = {0};
        for (String node : nodes) {
            if (!indices.containsKey(node)) {
                visit(node, edges, indices, lowlinks, stack, onStack, nextIndex, result);
            }
        }
        return result;
    }

    private static void visit(String node, Map<String, Set<String>> edges,
            Map<String, Integer> indices, Map<String, Integer> lowlinks,
            Deque<String> stack, Set<String> onStack, int[] nextIndex, List<Set<String>> result) {
        int index = nextIndex[0]++;
        indices.put(node, index);
        lowlinks.put(node, index);
        stack.push(node);
        onStack.add(node);
        for (String target : edges.getOrDefault(node, Collections.emptySet())) {
            if (!indices.containsKey(target)) {
                visit(target, edges, indices, lowlinks, stack, onStack, nextIndex, result);
                lowlinks.put(node, Math.min(lowlinks.get(node), lowlinks.get(target)));
            } else if (onStack.contains(target)) {
                lowlinks.put(node, Math.min(lowlinks.get(node), indices.get(target)));
            }
        }
        if (lowlinks.get(node).equals(indices.get(node))) {
            Set<String> component = new HashSet<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));
            if (component.size() > 1 || edges.getOrDefault(node, Collections.emptySet()).contains(node)) {
                result.add(component);
            }
        }
    }

    private static Set<String> loadBaseline() throws IOException {
        InputStream input = ArchitectureDependencyTest.class.getResourceAsStream(
                "/architecture/package-dependencies.tsv");
        assertThat(input).as("缺少包依赖基线").isNotNull();
        Set<String> result = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\t");
                assertThat(parts).as("非法依赖基线行: " + line).hasSize(2);
                result.add(parts[0] + ">" + parts[1]);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> scanDependencies() throws IOException {
        Path sourceRoot = projectRoot().resolve("src/main/java/com/github/aeddddd/ae2enhanced");
        Set<String> edges = new HashSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                Path relative = sourceRoot.relativize(file);
                String source = relative.getNameCount() > 1
                        ? relative.getName(0).toString() : "(root)";
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Matcher matcher = IMPORT.matcher(text);
                while (matcher.find()) {
                    String target = matcher.group(1).split("\\.")[0];
                    if (!source.equals(target)) {
                        edges.add(source + ">" + target);
                    }
                }
            }
        }
        return edges;
    }

    private static Path projectRoot() {
        return Paths.get(System.getProperty("ae2e.projectDir", ""))
                .toAbsolutePath().normalize();
    }

    @Test
    public void baselineHasExpectedShape() throws IOException {
        Set<String> baseline = loadBaseline();
        assertThat(baseline).hasSize(264);
        assertThat(baseline).contains(
                "specialcrafting>observation",
                "crafting>observation",
                "diag>observation",
                "storage>observation",
                "integration>storage",
                "centralinterface>crafting",
                "container>dock")
                .doesNotContain(
                        "specialcrafting>diag", "specialcrafting>mixin",
                        "crafting>diag", "crafting>mixin", "crafting>centralinterface",
                        "diag>specialcrafting", "diag>crafting", "diag>storage", "diag>tile",
                        "storage>integration", "dimension>ring", "pathing>mixin",
                        "gui>client", "container>client",
                        "crafting>specialcrafting", "specialcrafting>crafting",
                        "crafting>integration", "crafting>tile", "tile>mixin", "tile>integration",
                        "api>integration", "api>storage", "client>mixin", "util>client", "network>client");
    }

    @Test
    public void packageDependenciesOnlyShrink() throws IOException {
        Set<String> baseline = loadBaseline();
        Set<String> actual = scanDependencies();
        Set<String> added = new HashSet<>(actual);
        added.removeAll(baseline);

        assertThat(added)
                .as("新增跨包依赖必须先在迁移文档中说明，并更新 architecture/package-dependencies.tsv；"
                        + "已有依赖只允许减少。当前边数=" + actual.size())
                .isEmpty();
    }

    @Test
    public void packageDependenciesAreAcyclic() throws IOException {
        Set<String> nodes = new HashSet<>();
        Map<String, Set<String>> edges = new HashMap<>();
        for (String edge : scanDependencies()) {
            String[] parts = edge.split(">", 2);
            nodes.add(parts[0]);
            nodes.add(parts[1]);
            edges.computeIfAbsent(parts[0], key -> new HashSet<>()).add(parts[1]);
        }
        assertThat(cyclicComponents(nodes, edges)).isEmpty();
    }
}
