package com.github.aeddddd.ae2enhanced.registry;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 声明清单与 116 项注册兼容基线的棘轮；不替代真实 Forge 注册验证。 */
class RegistryCompatibilityTest {

    private static final Pattern CONTENT = Pattern.compile(
            "(?:block|item|simple|part)\\(\"([a-z0-9_]+)\"[\\s\\S]*?->\\s*"
                    + "(BlockRegistry|ItemRegistry|PartRegistry)\\.([A-Z0-9_]+)");
    private static final Pattern TILE = Pattern.compile(
            "tile\\(\"([a-z0-9_]+)\",\\s*(Tile\\w+)\\.class");
    private static final Pattern ALIAS = Pattern.compile("\"([a-z0-9_]+)\"\\.equals\\(path\\)");

    private static Map<String, String> contentDefinitions() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = CONTENT.matcher(definitionSource());
        while (matcher.find()) {
            String holder = matcher.group(2) + "." + matcher.group(3);
            assertThat(result.put(holder, "ae2enhanced:" + matcher.group(1)))
                    .as("重复声明: " + holder).isNull();
        }
        return result;
    }

    private static Set<String> aliasRows(String method, String kind) {
        assertThat(method).contains("mapping.remap(target)");
        Set<String> aliases = new HashSet<>();
        Matcher matcher = ALIAS.matcher(method);
        while (matcher.find()) {
            aliases.add(kind + "->ae2enhanced:network_access_node|ae2enhanced:" + matcher.group(1));
        }
        assertThat(aliases).hasSize(3);
        return aliases;
    }

    private static List<Entry> loadBaseline() throws IOException {
        InputStream stream = RegistryCompatibilityTest.class.getResourceAsStream("/protocol/registry-v1.tsv");
        assertThat(stream).as("缺少 registry-v1.tsv").isNotNull();
        List<Entry> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("kind\t")) continue;
                String[] columns = line.split("\\t", -1);
                assertThat(columns).hasSize(4);
                Entry entry = new Entry(columns[0], columns[1], columns[2], columns[3]);
                assertThat(unique.add(entry.kind + "|" + entry.holder + "|" + entry.name)).isTrue();
                result.add(entry);
            }
        }
        return result;
    }

    private static Map<String, String> namesByHolder(List<Entry> baseline, String kind) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Entry entry : baseline) {
            if (entry.kind.equals(kind)) result.put(entry.holder, entry.name);
        }
        return result;
    }

    private static Set<String> shortHolders(List<Entry> baseline, String kind) {
        Set<String> result = new HashSet<>();
        for (String holder : namesByHolder(baseline, kind).keySet()) {
            if (!kind.equals("item") || holder.startsWith("ItemRegistry.")) {
                result.add(holder.substring(holder.lastIndexOf('.') + 1));
            }
        }
        return result;
    }

    private static long count(List<Entry> baseline, String kind) {
        return baseline.stream().filter(e -> e.kind.equals(kind)).count();
    }

    private static Set<String> declaredFields(String registryClass) throws IOException {
        String owner = registryClass.equals("BlockRegistry") ? "block"
                : registryClass.equals("ItemRegistry") ? "item" : "part";
        String text = source("src/main/java/com/github/aeddddd/ae2enhanced/" + owner + "/" + registryClass + ".java");
        Set<String> fields = new HashSet<>();
        Matcher matcher = Pattern.compile("public\\s+static\\s+[\\w.]+\\s+([A-Z][A-Z0-9_]*)\\s*;").matcher(text);
        while (matcher.find()) fields.add(matcher.group(1));
        return fields;
    }

    private static String definitionSource() throws IOException {
        return source("src/main/java/com/github/aeddddd/ae2enhanced/registry/content/ContentDefinition.java");
    }

    private static String managerSource() throws IOException {
        return source("src/main/java/com/github/aeddddd/ae2enhanced/registry/GameRegistryManager.java");
    }

    private static String source(String relative) throws IOException {
        Path path = Paths.get(System.getProperty("ae2e.projectDir", "")).resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertThat(from).isGreaterThanOrEqualTo(0);
        assertThat(to).isGreaterThan(from);
        return source.substring(from, to);
    }

    @Test
    void contentFieldsAndDefinitionsMatchAll116BaselineEntries() throws IOException {
        List<Entry> baseline = loadBaseline();
        assertThat(baseline).hasSize(116);
        assertThat(baseline).filteredOn(e -> !e.kind.equals("alias")).hasSize(110);
        Map<String, String> definitions = contentDefinitions();

        for (String kind : new String[]{"block", "item", "part"}) {
            Map<String, String> expected = namesByHolder(baseline, kind);
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                String holder = entry.getKey().substring(0, entry.getKey().indexOf('.')) + "." + entry.getKey().substring(entry.getKey().indexOf('.') + 1);
                assertThat(definitions).as("声明清单: " + kind).containsEntry(holder, entry.getValue());
            }
            Set<String> actualHolders = new HashSet<>();
            for (Map.Entry<String, String> entry : definitions.entrySet()) {
                String holder = entry.getKey();
                if ((kind.equals("block") && holder.startsWith("BlockRegistry."))
                        || (kind.equals("item") && holder.startsWith("ItemRegistry."))
                        || (kind.equals("part") && holder.startsWith("PartRegistry."))) {
                    actualHolders.add(holder);
                }
            }
            Set<String> expectedHolders = new HashSet<>(expected.keySet());
            if (kind.equals("item")) {
                expectedHolders.removeIf(holder -> !holder.startsWith("ItemRegistry."));
            }
            assertThat(actualHolders).isEqualTo(expectedHolders);
        }

        assertThat(declaredFields("BlockRegistry")).isEqualTo(shortHolders(baseline, "block"));
        assertThat(declaredFields("ItemRegistry")).isEqualTo(shortHolders(baseline, "item"));
        assertThat(declaredFields("PartRegistry")).isEqualTo(shortHolders(baseline, "part"));

        Map<String, String> tiles = new HashMap<>();
        Matcher matcher = TILE.matcher(definitionSource());
        while (matcher.find()) {
            assertThat(tiles.put(matcher.group(2), "ae2enhanced:" + matcher.group(1))).isNull();
        }
        assertThat(tiles).isEqualTo(namesByHolder(baseline, "tile"));
    }

    @Test
    void registryCountsConditionsAndModelMetadataAreFrozen() throws IOException {
        List<Entry> baseline = loadBaseline();
        assertThat(count(baseline, "block")).isEqualTo(31);
        assertThat(count(baseline, "item")).isEqualTo(55);
        assertThat(count(baseline, "part")).isEqualTo(4);
        assertThat(count(baseline, "tile")).isEqualTo(20);
        assertThat(count(baseline, "alias")).isEqualTo(6);
        assertThat(baseline).filteredOn(e -> !e.condition.equals("always")).hasSize(13);

        String definitions = definitionSource();
        assertThat(definitions).contains(
                "Condition.THAUMCRAFT", "Condition.GAS", "Condition.BOTANIA",
                "Condition.ASTRAL_SORCERY", "Condition.EMC_INTERFACE",
                "ModelType.PART", "ModelType.UPGRADE_CARD", "ModelType.ESSENTIA_DROP",
                "ModelType.FLUID_DROP", "ModelType.GAS_DROP", "ModelType.STARLIGHT_DROP",
                "ModelType.VIRTUAL_PARALLEL_CARD", "ModelType.OMNI_TOOL", "ModelType.OMNI_UPGRADE_CARD",
                "\"upgrade_card_upload\"", "\"upgrade_card_efficiency\"", "\"virtual_parallel_card_8\"",
                "\"me_omni_tool_mode3\"", "\"omni_upgrade_card_picker\"");
        assertThat(definitions).contains("block(\"micro_singularity\"")
                .contains("Condition.ALWAYS, false)");

        String manager = managerSource();
        assertThat(section(manager, "public static void registerBlocks", "public static void registerItems"))
                .contains("ContentDefinition.BLOCKS", "ContentDefinition.TILES")
                .doesNotContain("new Block");
        assertThat(section(manager, "public static void initItems", "public static void registerBlocks"))
                .contains("ContentDefinition.ITEMS")
                .doesNotContain("new Item");
        String client = source("src/main/java/com/github/aeddddd/ae2enhanced/proxy/ClientProxy.java");
        assertThat(client).contains("ContentDefinition.BLOCKS", "ContentDefinition.ITEMS",
                "partModels().registerModels(definition.partModels.get())")
                .doesNotContain("registerItemModel(PartRegistry.");
    }

    @Test
    void missingMappingsRemapExactlyThreeLegacyNamesForBlockAndItem() throws IOException {
        List<Entry> baseline = loadBaseline();
        Set<String> expected = new HashSet<>();
        for (Entry entry : baseline) {
            if (entry.kind.equals("alias")) {
                expected.add(entry.holder + "|" + entry.name);
            }
        }
        assertThat(expected).hasSize(6);
        Set<String> expectedBlocks = new HashSet<>();
        Set<String> expectedItems = new HashSet<>();
        for (String entry : expected) {
            (entry.startsWith("block->") ? expectedBlocks : expectedItems).add(entry);
        }
        String manager = managerSource();
        assertThat(aliasRows(section(manager, "public static void onMissingBlockMappings",
                "public static void onMissingItemMappings"), "block")).isEqualTo(expectedBlocks);
        int itemMappings = manager.indexOf("public static void onMissingItemMappings");
        assertThat(aliasRows(manager.substring(itemMappings), "item")).isEqualTo(expectedItems);
    }

    private static final class Entry {
        final String kind;
        final String name;
        final String holder;
        final String condition;

        Entry(String kind, String name, String holder, String condition) {
            this.kind = kind;
            this.name = name;
            this.holder = holder;
            this.condition = condition;
        }
    }
}
