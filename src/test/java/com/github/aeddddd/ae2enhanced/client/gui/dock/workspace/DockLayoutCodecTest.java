/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.SplitNode;

class DockLayoutCodecTest {
    @Test
    void roundTripsCurrentVersionWithoutLosingRootOrderTreeStateOrPolicies() throws Exception {
        DockLayoutCodec codec = codec();
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace original =
                WorkspacePersistenceFixtures.workspace();

        String encoded = codec.encode(original);
        DockLayoutCodec.DecodedLayout decoded = codec.decode(encoded);

        assertEquals(DockLayoutDto.CURRENT_VERSION, decoded.sourceVersion());
        assertFalse(decoded.migrated());
        assertFalse(decoded.reconciled());
        assertFalse(decoded.needsRewrite());
        assertEquals(original, decoded.workspace());
        assertTrue(encoded.indexOf("root-a") < encoded.indexOf("root-bc"));
    }

    @Test
    void roundTripsPerModuleRuntimePolicy() throws Exception {
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace base =
                WorkspacePersistenceFixtures.workspace();
        Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>(base.policies());
        policies.put("a", new ModuleLayoutPolicy(true, false, true));
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace configured =
                new com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace(base.roots(), policies);

        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace decoded =
                codec().decode(codec().encode(configured)).workspace();

        assertEquals(new ModuleLayoutPolicy(true, false, true), decoded.policyFor("a"));
        assertEquals(ModuleLayoutPolicy.defaults(), decoded.policyFor("b"));
    }

    @Test
    void roundTripsRestoreSizes() throws Exception {
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace base =
                WorkspacePersistenceFixtures.workspace();
        Map<String, com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize> restoreSizes =
                new LinkedHashMap<String, com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize>();
        restoreSizes.put("leaf-a", new com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize(100, 80));
        restoreSizes.put("leaf-b", new com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize(90, 60));
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace original =
                new com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace(
                        base.roots(), base.policies(), restoreSizes);

        String encoded = codec().encode(original);
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace decoded =
                codec().decode(encoded).workspace();

        assertEquals(original.restoreSizes(), decoded.restoreSizes());
        assertTrue(encoded.contains("restoreSizes"));
        assertFalse(codec().decode(encoded).needsRewrite());
    }

    @Test
    void roundTripsWindowModes() throws Exception {
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace base =
                WorkspacePersistenceFixtures.workspace();
        ArrayList<com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot> roots =
                new ArrayList<com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot>(base.roots());
        roots.set(0, roots.get(0).withMode(true, true));
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace original = base.withRoots(roots);

        String encoded = codec().encode(original);
        DockLayoutCodec.DecodedLayout decoded = codec().decode(encoded);

        assertTrue(decoded.workspace().roots().get(0).floating());
        assertTrue(decoded.workspace().roots().get(0).pinned());
        assertFalse(decoded.workspace().roots().get(decoded.workspace().roots().size() - 1).floating());
        assertFalse(decoded.needsRewrite());
    }

    @Test
    void roundTripsCombinedModulesAndDropsDuplicateSlots() throws Exception {
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace base =
                WorkspacePersistenceFixtures.workspace();
        ArrayList<com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot> roots =
                new ArrayList<com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot>(base.roots());
        roots.set(0, roots.get(0).withMode(true, false).withGroup(2).withHidden(true));
        String encoded = codec().encode(base.withRoots(roots));

        com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot decoded =
                codec().decode(encoded).workspace().roots().get(0);
        assertEquals(2, decoded.group());
        assertTrue(decoded.hidden());

        int last = encoded.lastIndexOf("\"rootId\"");
        String duplicated = encoded.substring(0, last)
                + "\"group\": 2, \"floating\": true, " + encoded.substring(last);
        java.util.List<com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot> twice =
                codec().decode(duplicated).workspace().roots();
        assertEquals(1, twice.stream().filter(root -> root.group() == 2).count());
    }

    @Test
    void olderLayoutsTakeTheirWindowModeFromTheModules() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 6,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-a\",\n"
                + "    \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 100, \"height\": 80},\n"
                + "    \"content\": {\"type\": \"leaf\", \"nodeId\": \"leaf-a\", \"moduleId\": \"a\","
                + " \"visible\": true}\n"
                + "  }, {\n"
                + "    \"rootId\": \"root-bc\",\n"
                + "    \"bounds\": {\"x\": 160, \"y\": 40, \"width\": 220, \"height\": 140},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"split\", \"nodeId\": \"split-bc\", \"axis\": \"VERTICAL\", \"ratio\": 0.35,\n"
                + "      \"first\": {\"type\": \"leaf\", \"nodeId\": \"leaf-b\", \"moduleId\": \"b\","
                + " \"visible\": true},\n"
                + "      \"second\": {\"type\": \"leaf\", \"nodeId\": \"leaf-c\", \"moduleId\": \"c\","
                + " \"visible\": true}\n"
                + "    }\n"
                + "  }],\n"
                + "  \"policies\": {\n"
                + "    \"a\": {\"visible\": true, \"movable\": true, \"resizable\": true, \"floating\": false,"
                + " \"pinned\": false, \"showTerminalButton\": true},\n"
                + "    \"b\": {\"visible\": true, \"movable\": true, \"resizable\": true, \"floating\": true,"
                + " \"pinned\": true, \"showTerminalButton\": true},\n"
                + "    \"c\": {\"visible\": false, \"movable\": true, \"resizable\": true, \"floating\": false,"
                + " \"pinned\": false, \"showTerminalButton\": true}\n"
                + "  }\n"
                + "}\n";

        DockLayoutCodec.DecodedLayout decoded = codec().decode(persisted);

        assertFalse(decoded.workspace().roots().get(0).floating());
        // "c" is hidden, so only the visible, pinned "b" decides the window's mode.
        assertTrue(decoded.workspace().roots().get(decoded.workspace().roots().size() - 1).floating());
        assertTrue(decoded.workspace().roots().get(decoded.workspace().roots().size() - 1).pinned());
        assertTrue(decoded.needsRewrite());
    }

    @Test
    void noLongerWritesASpliceMode() throws Exception {
        String encoded = codec().encode(WorkspacePersistenceFixtures.workspace());

        assertFalse(encoded.contains("spliceMode"));
        assertFalse(encoded.contains("compactSplice"));
        assertFalse(codec().decode(encoded).needsRewrite());
    }

    @Test
    void roundTripsContentOffsets() throws Exception {
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace original =
                WorkspacePersistenceFixtures.workspace().withContentOffsets(
                        Collections.singletonMap(
                                "a",
                                new com.github.aeddddd.ae2enhanced.client.gui.dock.model.ContentOffset(6, 2)));

        String encoded = codec().encode(original);
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace decoded =
                codec().decode(encoded).workspace();

        assertEquals(original.contentOffsets(), decoded.contentOffsets());
        assertTrue(encoded.contains("contentOffsets"));
        assertEquals(6, decoded.contentOffset("a").x());
        assertEquals(2, decoded.contentOffset("a").y());
        assertEquals(com.github.aeddddd.ae2enhanced.client.gui.dock.model.ContentOffset.ZERO,
                decoded.contentOffset("b"));
    }

    @Test
    void missingContentOffsetsDecodeAsEmpty() throws Exception {
        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace original =
                WorkspacePersistenceFixtures.workspace();
        String encoded = codec().encode(original);
        assertFalse(encoded.contains("contentOffsets"));
        assertTrue(codec().decode(encoded).workspace().contentOffsets().isEmpty());
    }

    @Test
    void fitsOnlyLayoutsSavedOutsideTheShellMode() throws Exception {
        String stretch = legacySplicedDocument("");
        String compact = legacySplicedDocument(", \"compactSplice\": true");
        String shell = legacySplicedDocument(", \"spliceMode\": \"shell\"");
        ArrayList<com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace> fitted =
                new ArrayList<com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace>();
        DockLayoutCodec codec = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext(),
                workspace -> {
                    fitted.add(workspace);
                    return workspace;
                });

        codec.decode(stretch);
        codec.decode(compact);
        assertEquals(2, fitted.size());

        codec.decode(shell);
        DockLayoutCodec.DecodedLayout current = codec.decode(codec.encode(fitted.get(0)));
        assertEquals(2, fitted.size(), "shell and current-version documents keep their window sizes");
        assertFalse(current.needsRewrite());
        assertTrue(codec.decode(stretch).needsRewrite(), "legacy documents are rewritten as v6");
    }

    private static String legacySplicedDocument(String modeField) {
        return "{\n"
                + "  \"version\": 5,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-a\",\n"
                + "    \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 100, \"height\": 80},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"leaf\", \"nodeId\": \"leaf-a\", \"moduleId\": \"a\", \"visible\": true\n"
                + "    }\n"
                + "  }, {\n"
                + "    \"rootId\": \"root-bc\",\n"
                + "    \"bounds\": {\"x\": 160, \"y\": 40, \"width\": 220, \"height\": 140},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"split\", \"nodeId\": \"split-bc\", \"axis\": \"VERTICAL\", \"ratio\": 0.35,\n"
                + "      \"first\": {\"type\": \"leaf\", \"nodeId\": \"leaf-b\", \"moduleId\": \"b\","
                + " \"visible\": true},\n"
                + "      \"second\": {\"type\": \"leaf\", \"nodeId\": \"leaf-c\", \"moduleId\": \"c\","
                + " \"visible\": true}\n"
                + "    }\n"
                + "  }],\n"
                + "  \"policies\": {\n"
                + "    \"a\": {\"visible\": true, \"movable\": true, \"resizable\": true, \"floating\": false,"
                + " \"pinned\": false, \"showTerminalButton\": true},\n"
                + "    \"b\": {\"visible\": true, \"movable\": true, \"resizable\": true, \"floating\": false,"
                + " \"pinned\": false, \"showTerminalButton\": true},\n"
                + "    \"c\": {\"visible\": true, \"movable\": true, \"resizable\": true, \"floating\": false,"
                + " \"pinned\": false, \"showTerminalButton\": true}\n"
                + "  }" + modeField + "\n"
                + "}\n";
    }

    @Test
    void migratesV2LeafVisibilityIntoV3ModulePolicy() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 2,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-a\",\n"
                + "    \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 100, \"height\": 80},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"leaf\", \"nodeId\": \"leaf-a\", \"moduleId\": \"a\", \"visible\": false\n"
                + "    }\n"
                + "  }]\n"
                + "}\n";

        DockLayoutCodec.DecodedLayout decoded = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("a"),
                WorkspacePersistenceFixtures.migrationContext()).decode(persisted);
        LeafNode leaf = assertInstanceOf(LeafNode.class, decoded.workspace().roots().get(0).content());

        assertEquals(2, decoded.sourceVersion());
        assertTrue(decoded.needsRewrite());
        assertTrue(leaf.visible(), "v3 normalizes structural leaf visibility");
        assertFalse(decoded.workspace().policyFor("a").visible());
    }

    @Test
    void migratesOlderLayoutsToTheCurrentNetworkToolkitPlacement() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 3,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-nt\",\n"
                + "    \"bounds\": {\"x\": 501, \"y\": 284, \"width\": 71, \"height\": 66},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"leaf\", \"nodeId\": \"leaf-nt\", \"moduleId\": \"network_toolkit\",\n"
                + "      \"visible\": true\n"
                + "    }\n"
                + "  }],\n"
                + "  \"policies\": {\n"
                + "    \"network_toolkit\": {\"visible\": true, \"movable\": true, \"resizable\": true,\n"
                + "                        \"floating\": true, \"pinned\": true, \"showTerminalButton\": true}\n"
                + "  }\n"
                + "}\n";
        DockLayoutCodec codec = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("network_toolkit"),
                WorkspacePersistenceFixtures.migrationContext());

        DockLayoutCodec.DecodedLayout decoded = codec.decode(persisted);

        assertEquals(3, decoded.sourceVersion());
        assertTrue(decoded.needsRewrite());
        DockRect migrated = decoded.workspace().roots().get(0).bounds();
        assertEquals(501 - DockWorkspaceDefaults.NETWORK_TOOLKIT_SHIFT_X, migrated.x());
        assertEquals(284, migrated.y());
        assertEquals(71, migrated.width());
        assertEquals(66, migrated.height());

        // The migrated position is persisted, so the shift is only ever applied once.
        DockLayoutCodec.DecodedLayout again = codec.decode(codec.encode(decoded.workspace()));
        assertFalse(again.needsRewrite());
        assertEquals(migrated, again.workspace().roots().get(0).bounds());
    }

    @Test
    void leavesASplicedNetworkToolkitWindowWhereThePlayerPutIt() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 3,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-mixed\",\n"
                + "    \"bounds\": {\"x\": 501, \"y\": 284, \"width\": 160, \"height\": 66},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"split\", \"nodeId\": \"split-mixed\", \"axis\": \"HORIZONTAL\", \"ratio\": 0.5,\n"
                + "      \"first\": {\"type\": \"leaf\", \"nodeId\": \"leaf-nt\", \"moduleId\": \"network_toolkit\","
                + " \"visible\": true},\n"
                + "      \"second\": {\"type\": \"leaf\", \"nodeId\": \"leaf-a\", \"moduleId\": \"a\","
                + " \"visible\": true}\n"
                + "    }\n"
                + "  }],\n"
                + "  \"policies\": {\n"
                + "    \"network_toolkit\": {\"visible\": true, \"movable\": true, \"resizable\": true,\n"
                + "                        \"floating\": false, \"pinned\": false, \"showTerminalButton\": true},\n"
                + "    \"a\": {\"visible\": true, \"movable\": true, \"resizable\": true,\n"
                + "          \"floating\": false, \"pinned\": false, \"showTerminalButton\": true}\n"
                + "  }\n"
                + "}\n";
        DockLayoutCodec codec = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("network_toolkit", "a"),
                WorkspacePersistenceFixtures.migrationContext());

        DockRect bounds = codec.decode(persisted).workspace().roots().get(0).bounds();

        assertEquals(501, bounds.x());
    }

    @Test
    void v3PolicyIsTheSingleVisibilitySource() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 3,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-a\",\n"
                + "    \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 100, \"height\": 80},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"leaf\", \"nodeId\": \"leaf-a\", \"moduleId\": \"a\", \"visible\": false\n"
                + "    }\n"
                + "  }],\n"
                + "  \"policies\": {\n"
                + "    \"a\": {\"visible\": true, \"movable\": false, \"resizable\": true}\n"
                + "  }\n"
                + "}\n";

        DockLayoutCodec.DecodedLayout decoded = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("a"),
                WorkspacePersistenceFixtures.migrationContext()).decode(persisted);
        LeafNode leaf = assertInstanceOf(LeafNode.class, decoded.workspace().roots().get(0).content());

        assertTrue(decoded.needsRewrite());
        assertTrue(leaf.visible());
        assertEquals(new ModuleLayoutPolicy(true, false, true), decoded.workspace().policyFor("a"));
    }

    @Test
    void migratesLegacyPairsDeterministicallyAndPreservesFileOrder() throws Exception {
        String legacy = "{\n"
                + "  \"b+c\": {\n"
                + "    \"x\": 20,\n"
                + "    \"y\": 30,\n"
                + "    \"width\": 120,\n"
                + "    \"height\": 104,\n"
                + "    \"visible\": true,\n"
                + "    \"orientation\": \"VERTICAL\",\n"
                + "    \"split\": 40,\n"
                + "    \"children\": [\"b\", \"c\"]\n"
                + "  },\n"
                + "  \"a\": {\n"
                + "    \"x\": 4,\n"
                + "    \"y\": 5,\n"
                + "    \"width\": 80,\n"
                + "    \"height\": 60,\n"
                + "    \"visible\": false\n"
                + "  }\n"
                + "}\n";
        DockLayoutCodec codec = codec();

        DockLayoutCodec.DecodedLayout first = codec.decode(legacy);
        DockLayoutCodec.DecodedLayout second = codec.decode(legacy);

        assertEquals(1, first.sourceVersion());
        assertTrue(first.migrated());
        assertEquals(first.workspace(), second.workspace());
        SplitNode pair = assertInstanceOf(SplitNode.class, first.workspace().roots().get(0).content());
        assertEquals(0.4, pair.ratio(), 0.000001);
        assertEquals("b", ((LeafNode) pair.first()).moduleId());
        assertEquals("c", ((LeafNode) pair.second()).moduleId());
        LeafNode standalone = assertInstanceOf(
                LeafNode.class,
                first.workspace().roots().get(1).content());
        assertEquals("a", standalone.moduleId());
        assertTrue(standalone.visible());
        assertFalse(first.workspace().policyFor("a").visible());

        assertEquals(first.workspace(), codec.decode(codec.encode(first.workspace())).workspace());
    }

    @Test
    void appendsRegisteredModulesMissingFromLegacyDocument() throws Exception {
        String legacy = "{\n"
                + "  \"a\": {\"x\": 4, \"y\": 5, \"width\": 80, \"height\": 60, \"visible\": true},\n"
                + "  \"b\": {\"x\": 20, \"y\": 20, \"width\": 90, \"height\": 60, \"visible\": true}\n"
                + "}\n";

        com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace decoded =
                codec().decode(legacy).workspace();

        assertEquals(3, decoded.roots().size());
        assertEquals("c", ((LeafNode) decoded.roots().get(decoded.roots().size() - 1).content()).moduleId());
        assertTrue(((LeafNode) decoded.roots().get(decoded.roots().size() - 1).content()).visible());
    }

    @Test
    void rejectsDuplicateLegacyModulesAndUnknownV2Fields() {
        String duplicateLegacy = "{\n"
                + "  \"a+b\": {\n"
                + "    \"x\": 0, \"y\": 0, \"width\": 120, \"height\": 80,\n"
                + "    \"orientation\": \"HORIZONTAL\", \"split\": 40, \"children\": [\"a\", \"b\"]\n"
                + "  },\n"
                + "  \"a\": {\"x\": 0, \"y\": 0, \"width\": 80, \"height\": 60}\n"
                + "}\n";
        String unknownV2Field = "{\"version\": 2, \"roots\": [], \"extra\": true}\n";

        assertThrows(DockLayoutFormatException.class, () -> codec().decode(duplicateLegacy));
        assertThrows(DockLayoutFormatException.class, () -> codec().decode(unknownV2Field));
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThrows(
                DockLayoutFormatException.class,
                () -> codec().decode("{\"version\":4,\"roots\":[]}"));
    }

    @Test
    void reconcilesAddedAndRemovedModulesWithoutDiscardingRetainedRoots() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 2,\n"
                + "  \"roots\": [\n"
                + "    {\n"
                + "      \"rootId\": \"root-a\",\n"
                + "      \"bounds\": {\"x\": 10, \"y\": 20, \"width\": 100, \"height\": 80},\n"
                + "      \"content\": {\n"
                + "        \"type\": \"leaf\", \"nodeId\": \"leaf-a\", \"moduleId\": \"a\", \"visible\": false\n"
                + "      }\n"
                + "    },\n"
                + "    {\n"
                + "      \"rootId\": \"root-old\",\n"
                + "      \"bounds\": {\"x\": 120, \"y\": 20, \"width\": 100, \"height\": 80},\n"
                + "      \"content\": {\n"
                + "        \"type\": \"leaf\", \"nodeId\": \"leaf-old\", \"moduleId\": \"old_name\", \"visible\": true\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";

        DockLayoutCodec.DecodedLayout decoded = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("a", "new_name"),
                WorkspacePersistenceFixtures.migrationContext()).decode(persisted);

        assertTrue(decoded.reconciled());
        assertTrue(decoded.needsRewrite());
        assertEquals(2, decoded.workspace().roots().size());
        LeafNode retained = assertInstanceOf(
                LeafNode.class,
                decoded.workspace().roots().get(0).content());
        assertEquals("a", retained.moduleId());
        assertTrue(retained.visible());
        assertFalse(decoded.workspace().policyFor("a").visible());
        LeafNode added = assertInstanceOf(
                LeafNode.class,
                decoded.workspace().roots().get(decoded.workspace().roots().size() - 1).content());
        assertEquals("new_name", added.moduleId());
        assertTrue(added.visible());
    }

    @Test
    void collapsesSplitsWhenOnlyOneSideReferencesADeletedModule() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 2,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-main\",\n"
                + "    \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 200, \"height\": 100},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"split\", \"nodeId\": \"split-main\", \"axis\": \"HORIZONTAL\",\n"
                + "      \"ratio\": 0.5,\n"
                + "      \"first\": {\"type\": \"leaf\", \"nodeId\": \"leaf-old\", \"moduleId\": \"removed\","
                + " \"visible\": true},\n"
                + "      \"second\": {\"type\": \"leaf\", \"nodeId\": \"leaf-a\", \"moduleId\": \"a\","
                + " \"visible\": true}\n"
                + "    }\n"
                + "  }]\n"
                + "}\n";

        DockLayoutCodec.DecodedLayout decoded = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("a"),
                WorkspacePersistenceFixtures.migrationContext()).decode(persisted);

        DockWorkspaceCodecAssertions.assertSingleLeaf(decoded.workspace(), "a");
        assertEquals("root-main", decoded.workspace().roots().get(0).rootId());
        assertEquals(200, decoded.workspace().roots().get(0).bounds().width());
        assertEquals(100, decoded.workspace().roots().get(0).bounds().height());
        assertTrue(decoded.reconciled());
        assertTrue(decoded.needsRewrite());
    }

    @Test
    void renamedCatalogIdsFallBackToDefaultsInsteadOfMakingTheDocumentInvalid() throws Exception {
        String persisted = "{\n"
                + "  \"version\": 2,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"root-old\",\n"
                + "    \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 200, \"height\": 100},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"leaf\", \"nodeId\": \"leaf-old\", \"moduleId\": \"old_id\", \"visible\": false\n"
                + "    }\n"
                + "  }]\n"
                + "}\n";

        DockLayoutCodec.DecodedLayout decoded = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("new_id"),
                WorkspacePersistenceFixtures.migrationContext()).decode(persisted);
        assertEquals(1, decoded.workspace().roots().size());
        LeafNode leaf = assertInstanceOf(LeafNode.class, decoded.workspace().roots().get(0).content());
        assertEquals("new_id", leaf.moduleId());
        assertTrue(leaf.visible());
    }

    @Test
    void rejectsDuplicateModuleAndIdentifierStateBeforeReconciliation() {
        String duplicateModule = "{\n"
                + "  \"version\": 2,\n"
                + "  \"roots\": [\n"
                + "    {\n"
                + "      \"rootId\": \"root-a\",\n"
                + "      \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 100, \"height\": 80},\n"
                + "      \"content\": {\n"
                + "        \"type\": \"leaf\", \"nodeId\": \"leaf-a-1\", \"moduleId\": \"a\", \"visible\": true\n"
                + "      }\n"
                + "    },\n"
                + "    {\n"
                + "      \"rootId\": \"root-a-duplicate-module\",\n"
                + "      \"bounds\": {\"x\": 110, \"y\": 0, \"width\": 100, \"height\": 80},\n"
                + "      \"content\": {\n"
                + "        \"type\": \"leaf\", \"nodeId\": \"leaf-a-2\", \"moduleId\": \"a\", \"visible\": true\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
        String duplicateIdentifier = "{\n"
                + "  \"version\": 2,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"same-id\",\n"
                + "    \"bounds\": {\"x\": 0, \"y\": 0, \"width\": 200, \"height\": 100},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"split\", \"nodeId\": \"split-main\", \"axis\": \"HORIZONTAL\",\n"
                + "      \"ratio\": 0.5,\n"
                + "      \"first\": {\n"
                + "        \"type\": \"leaf\", \"nodeId\": \"same-id\", \"moduleId\": \"a\", \"visible\": true\n"
                + "      },\n"
                + "      \"second\": {\n"
                + "        \"type\": \"leaf\", \"nodeId\": \"leaf-b\", \"moduleId\": \"b\", \"visible\": true\n"
                + "      }\n"
                + "    }\n"
                + "  }]\n"
                + "}\n";

        assertThrows(DockLayoutFormatException.class, () -> codec().decode(duplicateModule));
        assertThrows(DockLayoutFormatException.class, () -> codec().decode(duplicateIdentifier));
    }

    @Test
    void allocatesCollisionFreeIdentifiersForNewDefaultRoots() throws Exception {
        String preferredNewRootId = DockWorkspaceDefaults.rootId("new");
        String preferredNewLeafId = DockWorkspaceDefaults.leafNodeId("new");
        String persisted = "{\n"
                + "  \"version\": 2,\n"
                + "  \"roots\": [{\n"
                + "    \"rootId\": \"" + preferredNewRootId + "\",\n"
                + "    \"bounds\": {\"x\": 7, \"y\": 9, \"width\": 100, \"height\": 80},\n"
                + "    \"content\": {\n"
                + "      \"type\": \"leaf\", \"nodeId\": \"" + preferredNewLeafId + "\", \"moduleId\": \"a\","
                + " \"visible\": true\n"
                + "    }\n"
                + "  }]\n"
                + "}\n";

        DockLayoutCodec.DecodedLayout decoded = new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog("a", "new"),
                WorkspacePersistenceFixtures.migrationContext()).decode(persisted);

        assertEquals(2, decoded.workspace().roots().size());
        assertEquals(preferredNewRootId, decoded.workspace().roots().get(0).rootId());
        assertEquals(preferredNewLeafId, decoded.workspace().roots().get(0).content().nodeId());
        assertFalse(preferredNewRootId.equals(
                decoded.workspace().roots().get(decoded.workspace().roots().size() - 1).rootId()));
        assertFalse(preferredNewLeafId.equals(
                decoded.workspace().roots().get(decoded.workspace().roots().size() - 1).content().nodeId()));
        assertTrue(decoded.reconciled());
    }

    private static final class DockWorkspaceCodecAssertions {
        private DockWorkspaceCodecAssertions() {
        }

        static void assertSingleLeaf(
                com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace workspace,
                String moduleId) {
            assertEquals(1, workspace.roots().size());
            LeafNode leaf = assertInstanceOf(LeafNode.class, workspace.roots().get(0).content());
            assertEquals(moduleId, leaf.moduleId());
        }
    }

    private static DockLayoutCodec codec() {
        return new DockLayoutCodec(
                WorkspacePersistenceFixtures.catalog(),
                WorkspacePersistenceFixtures.migrationContext());
    }
}
