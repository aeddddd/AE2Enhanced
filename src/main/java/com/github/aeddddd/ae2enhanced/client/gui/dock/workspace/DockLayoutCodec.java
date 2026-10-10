/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ContentOffset;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockAxis;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LayoutTrees;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleDefaults;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleLayoutPolicy;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.NodeIds;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.SplitNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.WorkspaceValidationException;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.WorkspaceValidator;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutDto.LeafDto;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutDto.NodeDto;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutDto.PolicyDto;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutDto.RootDto;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutDto.SplitDto;

/** Strict versioned codec plus deterministic migration of legacy v1 and v2 layouts. */
public final class DockLayoutCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> V2_FIELDS = fieldSet("version", "roots");
    /**
     * {@code spliceMode} and the older {@code compactSplice} are no longer written; they are still
     * accepted so pre-v6 documents decode and can tell whether they were already a shell.
     */
    private static final Set<String> V3_FIELDS =
            fieldSet("version", "roots", "policies", "restoreSizes", "name", "spliceMode", "compactSplice",
                    "contentOffsets");
    private static final Set<String> OFFSET_FIELDS = fieldSet("x", "y");
    private static final Set<String> POLICY_FIELDS =
            fieldSet("visible", "movable", "resizable", "floating", "pinned", "showTerminalButton");
    private static final Set<String> ROOT_FIELDS =
            fieldSet("rootId", "bounds", "content", "floating", "pinned", "group", "hidden");
    private static final Set<String> BOUNDS_FIELDS = fieldSet("x", "y", "width", "height");
    private static final Set<String> SIZE_FIELDS = fieldSet("width", "height");
    private static final Set<String> LEAF_FIELDS = fieldSet("type", "nodeId", "moduleId", "visible");
    private static final Set<String> SPLIT_FIELDS = fieldSet("type", "nodeId", "axis", "ratio", "first", "second");
    private static final Set<String> V1_ENTRY_FIELDS =
            fieldSet("x", "y", "width", "height", "visible", "orientation", "split", "children");

    private final ModuleCatalog catalog;
    private final LegacyMigrationContext migrationContext;
    private final UnaryOperator<DockWorkspace> shellFit;

    public DockLayoutCodec(ModuleCatalog catalog, LegacyMigrationContext migrationContext) {
        this(catalog, migrationContext, UnaryOperator.identity());
    }

    /**
     * @param shellFit applied to documents saved in the removed stretch or compact splice modes,
     *                 whose windows were sized for those modes rather than for their content
     */
    public DockLayoutCodec(ModuleCatalog catalog, LegacyMigrationContext migrationContext,
            UnaryOperator<DockWorkspace> shellFit) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.migrationContext = Objects.requireNonNull(migrationContext, "migrationContext");
        this.shellFit = Objects.requireNonNull(shellFit, "shellFit");
    }

    /** Java 8 等价实现：{@code Set.of(...)}（不可变字段名集合，仅用于 contains 判定）。 */
    private static Set<String> fieldSet(String... fields) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(fields)));
    }

    public String encode(DockWorkspace workspace) {
        return GSON.toJson(writeDto(toDto(workspace)));
    }

    public DecodedLayout decode(String json) throws DockLayoutFormatException {
        if (json == null || isBlank(json)) {
            throw new DockLayoutFormatException("layout document is empty");
        }
        try {
            JsonElement parsed = parseJson(json);
            if (!parsed.isJsonObject()) {
                throw new DockLayoutFormatException("layout document must be a JSON object");
            }
            JsonObject object = parsed.getAsJsonObject();
            if (object.has("version")) {
                DockLayoutDto dto = readVersioned(object);
                Reconciliation reconciliation = reconcile(fromDtoRaw(dto));
                DockWorkspace workspace = wasShell(object, dto.version())
                        ? reconciliation.workspace()
                        : shellFit.apply(reconciliation.workspace());
                if (dto.version() < 7) {
                    workspace = deriveWindowModes(workspace);
                }
                return new DecodedLayout(
                        workspace,
                        dto.version(),
                        false,
                        dto.version() != DockLayoutDto.CURRENT_VERSION || reconciliation.changed());
            }
            Reconciliation reconciliation = reconcile(migrateV1(object));
            return new DecodedLayout(
                    deriveWindowModes(shellFit.apply(reconciliation.workspace())), 1, true, reconciliation.changed());
        } catch (DockLayoutFormatException e) {
            throw e;
        } catch (JsonParseException | ArithmeticException | ClassCastException e) {
            throw new DockLayoutFormatException("invalid layout JSON", e);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DockLayoutFormatException("invalid layout: " + e.getMessage(), e);
        }
    }

    public DockLayoutDto toDto(DockWorkspace workspace) {
        WorkspaceValidator.validateStrict(workspace, catalog);
        ArrayList<RootDto> roots = new ArrayList<RootDto>();
        for (FloatingRoot root : workspace.roots()) {
            roots.add(new RootDto(root.rootId(), root.bounds(), toDto(root.content()), root.floating(), root.pinned(),
                    root.group(), root.hidden()));
        }
        Map<String, PolicyDto> policies = new LinkedHashMap<String, PolicyDto>();
        for (String moduleId : catalog.moduleIds()) {
            ModuleLayoutPolicy policy = workspace.policyFor(moduleId);
            policies.put(moduleId, new PolicyDto(
                    policy.visible(),
                    policy.movable(),
                    policy.resizable(),
                    policy.floating(),
                    policy.pinned(),
                    policy.showTerminalButton()));
        }
        return new DockLayoutDto(
                DockLayoutDto.CURRENT_VERSION,
                roots,
                policies,
                workspace.restoreSizes(),
                workspace.contentOffsets());
    }

    public DockWorkspace fromDto(DockLayoutDto dto) throws DockLayoutFormatException {
        return reconcile(fromDtoRaw(dto)).workspace();
    }

    /**
     * Placement tweaks: an older document may hold the network-tool panel further right than the
     * current default. Only a window holding nothing but that panel is moved; one it was spliced
     * into was arranged by the player, and shifting it would drag every other module along.
     */
    /** A hand-edited file may reuse a combined-module slot; the first window keeps it. */
    private static void dropDuplicateGroups(List<FloatingRoot> roots) {
        HashSet<Integer> used = new HashSet<Integer>();
        for (int i = 0; i < roots.size(); i++) {
            FloatingRoot root = roots.get(i);
            if (root.group() != 0 && !used.add(root.group())) {
                roots.set(i, root.withGroup(0));
            }
        }
    }

    private static void migrateNetworkToolkitPlacement(List<FloatingRoot> roots, int version) {
        int shift = DockWorkspaceDefaults.networkToolkitShiftFrom(version);
        if (shift <= 0) {
            return;
        }
        for (int index = 0; index < roots.size(); index++) {
            FloatingRoot root = roots.get(index);
            if (root.content() instanceof LeafNode
                    && DockWorkspaceDefaults.NETWORK_TOOLKIT_MODULE.equals(((LeafNode) root.content()).moduleId())) {
                roots.set(index, root.withBounds(
                        DockWorkspaceDefaults.networkToolkitBounds(root.bounds(), shift)));
            }
        }
    }

    private DockWorkspace fromDtoRaw(DockLayoutDto dto) throws DockLayoutFormatException {
        if (dto == null) {
            throw new DockLayoutFormatException("layout DTO must not be null");
        }
        if (dto.version() < DockLayoutDto.MIN_SUPPORTED_VERSION
                || dto.version() > DockLayoutDto.CURRENT_VERSION) {
            throw new DockLayoutFormatException("unsupported layout version: " + dto.version());
        }
        if (dto.roots() == null) {
            throw new DockLayoutFormatException("roots must not be null");
        }

        DecodeBudget budget = new DecodeBudget();
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>();
        try {
            for (RootDto root : dto.roots()) {
                if (root == null || root.bounds() == null || root.content() == null) {
                    throw new DockLayoutFormatException("root DTO fields must not be null");
                }
                roots.add(new FloatingRoot(
                        root.rootId(),
                        root.bounds(),
                        fromDto(root.content(), budget, 1),
                        root.floating(),
                        root.pinned(),
                        root.group(),
                        root.hidden()));
            }
            dropDuplicateGroups(roots);
            migrateNetworkToolkitPlacement(roots, dto.version());
            if (dto.version() == 2) {
                DockWorkspace workspace = new DockWorkspace(roots);
                WorkspaceValidator.validateStructure(workspace);
                return workspace;
            }
            Map<String, ModuleLayoutPolicy> policies = new LinkedHashMap<String, ModuleLayoutPolicy>();
            if (dto.policies() != null) {
                dto.policies().forEach((moduleId, policy) -> {
                    if (policy != null) {
                        policies.put(moduleId, new ModuleLayoutPolicy(
                                policy.visible(),
                                policy.movable(),
                                policy.resizable(),
                                policy.floating(),
                                policy.pinned(),
                                policy.showTerminalButton()));
                    }
                });
            }
            DockWorkspace workspace = new DockWorkspace(
                    roots,
                    policies,
                    DockWorkspace.retainRestoreSizes(dto.restoreSizes(), roots),
                    dto.contentOffsets());
            WorkspaceValidator.validateStructure(workspace);
            return workspace;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DockLayoutFormatException("invalid versioned DTO: " + e.getMessage(), e);
        }
    }

    /**
     * Reconciles a structurally valid persisted workspace with the modules registered by the
     * current terminal screen.
     *
     * <p>Module ids are the only stable association between persisted leaves and runtime panels.
     * Unknown leaves are removed (collapsing now-empty split branches), while newly registered
     * modules receive deterministic default roots appended in catalog order. This keeps a single
     * added, removed, or renamed module from invalidating an otherwise usable layout.</p>
     */
    public DockWorkspace reconcileWorkspace(DockWorkspace workspace) throws DockLayoutFormatException {
        return reconcile(workspace).workspace();
    }

    private Reconciliation reconcile(DockWorkspace source) throws DockLayoutFormatException {
        try {
            WorkspaceValidator.validateStructure(source);
        } catch (RuntimeException e) {
            throw new DockLayoutFormatException("invalid persisted layout structure: " + e.getMessage(), e);
        }

        ArrayList<FloatingRoot> retainedRoots = new ArrayList<FloatingRoot>();
        for (FloatingRoot root : source.roots()) {
            LayoutNode retained = reconcileNode(root.content());
            if (retained != null) {
                retainedRoots.add(root.withContent(retained));
            }
        }

        Map<String, ModuleLayoutPolicy> retainedPolicies = new LinkedHashMap<String, ModuleLayoutPolicy>();
        for (String moduleId : catalog.moduleIds()) {
            retainedPolicies.put(moduleId, source.policyFor(moduleId));
        }
        DockWorkspace result = new DockWorkspace(
                retainedRoots,
                retainedPolicies,
                DockWorkspace.retainRestoreSizes(source.restoreSizes(), retainedRoots),
                source.contentOffsets());
        Set<String> usedModules = WorkspaceValidator.validateStructure(result);
        Set<String> usedIdentifiers = collectIdentifiers(result);
        int defaultIndex = retainedRoots.size();
        for (String moduleId : catalog.moduleIds()) {
            if (usedModules.contains(moduleId)) {
                continue;
            }
            String rootId = uniqueIdentifier(DockWorkspaceDefaults.rootId(moduleId), usedIdentifiers, "root");
            usedIdentifiers.add(rootId);
            String leafId = uniqueIdentifier(DockWorkspaceDefaults.leafNodeId(moduleId), usedIdentifiers, "leaf");
            usedIdentifiers.add(leafId);
            ModuleDefaults defaults = catalog.metrics(moduleId).defaults();
            boolean visible = defaults.visible();
            retainedPolicies.put(moduleId, defaults.policy());
            retainedRoots.add(new FloatingRoot(
                    rootId,
                    DockWorkspaceDefaults.defaultBounds(
                            moduleId,
                            migrationContext.defaultRootBounds(catalog.metrics(moduleId), defaultIndex++)),
                    new LeafNode(leafId, moduleId, visible),
                    defaults.floating(),
                    defaults.pinned()));
        }

        result = new DockWorkspace(
                retainedRoots,
                retainedPolicies,
                DockWorkspace.retainRestoreSizes(source.restoreSizes(), retainedRoots),
                source.contentOffsets());
        try {
            WorkspaceValidator.validateStrict(result, catalog);
        } catch (RuntimeException e) {
            throw new DockLayoutFormatException("invalid reconciled layout: " + e.getMessage(), e);
        }
        return new Reconciliation(result, !result.equals(source));
    }

    private LayoutNode reconcileNode(LayoutNode node) {
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            return catalog.contains(leaf.moduleId()) ? leaf.withVisible(true) : null;
        }
        SplitNode split = (SplitNode) node;
        LayoutNode first = reconcileNode(split.first());
        LayoutNode second = reconcileNode(split.second());
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return split.withChildren(first, second);
    }

    private static Set<String> collectIdentifiers(DockWorkspace workspace) {
        Set<String> identifiers = new LinkedHashSet<String>();
        for (FloatingRoot root : workspace.roots()) {
            identifiers.add(root.rootId());
            collectIdentifiers(root.content(), identifiers);
        }
        return identifiers;
    }

    private static void collectIdentifiers(LayoutNode node, Set<String> identifiers) {
        identifiers.add(node.nodeId());
        if (node instanceof SplitNode) {
            SplitNode split = (SplitNode) node;
            collectIdentifiers(split.first(), identifiers);
            collectIdentifiers(split.second(), identifiers);
        }
    }

    private static String uniqueIdentifier(String preferred, Set<String> used, String namespace) {
        if (!used.contains(preferred)) {
            return preferred;
        }
        int suffix = 1;
        String candidate;
        do {
            candidate = NodeIds.deterministic(namespace, preferred + ":" + suffix++);
        } while (used.contains(candidate));
        return candidate;
    }

    private NodeDto toDto(LayoutNode node) {
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            return new LeafDto(leaf.nodeId(), leaf.moduleId(), leaf.visible());
        }
        SplitNode split = (SplitNode) node;
        return new SplitDto(
                split.nodeId(),
                split.axis(),
                split.ratio(),
                toDto(split.first()),
                toDto(split.second()));
    }

    private LayoutNode fromDto(NodeDto dto, DecodeBudget budget, int depth) throws DockLayoutFormatException {
        budget.enter(depth);
        if (dto instanceof LeafDto) {
            LeafDto leaf = (LeafDto) dto;
            return new LeafNode(leaf.nodeId(), leaf.moduleId(), leaf.visible());
        }
        if (!(dto instanceof SplitDto)) {
            throw new DockLayoutFormatException("unsupported node DTO type");
        }
        SplitDto split = (SplitDto) dto;
        return new SplitNode(
                split.nodeId(),
                split.axis(),
                split.ratio(),
                fromDto(split.first(), budget, depth + 1),
                fromDto(split.second(), budget, depth + 1));
    }

    private JsonObject writeDto(DockLayoutDto dto) {
        JsonObject object = new JsonObject();
        object.addProperty("version", dto.version());
        JsonArray roots = new JsonArray();
        for (RootDto root : dto.roots()) {
            JsonObject rootObject = new JsonObject();
            rootObject.addProperty("rootId", root.rootId());
            rootObject.add("bounds", writeBounds(root.bounds()));
            rootObject.add("content", writeNode(root.content()));
            if (root.floating()) {
                rootObject.addProperty("floating", true);
            }
            if (root.pinned()) {
                rootObject.addProperty("pinned", true);
            }
            if (root.group() != 0) {
                rootObject.addProperty("group", root.group());
            }
            if (root.hidden()) {
                rootObject.addProperty("hidden", true);
            }
            roots.add(rootObject);
        }
        object.add("roots", roots);
        JsonObject policies = new JsonObject();
        for (Map.Entry<String, PolicyDto> entry : dto.policies().entrySet()) {
            JsonObject policy = new JsonObject();
            policy.addProperty("visible", entry.getValue().visible());
            policy.addProperty("movable", entry.getValue().movable());
            policy.addProperty("resizable", entry.getValue().resizable());
            policy.addProperty("floating", entry.getValue().floating());
            policy.addProperty("pinned", entry.getValue().pinned());
            policy.addProperty("showTerminalButton", entry.getValue().showTerminalButton());
            policies.add(entry.getKey(), policy);
        }
        object.add("policies", policies);
        if (dto.restoreSizes() != null && !dto.restoreSizes().isEmpty()) {
            JsonObject restoreSizes = new JsonObject();
            for (Map.Entry<String, DockSize> entry : dto.restoreSizes().entrySet()) {
                restoreSizes.add(entry.getKey(), writeSize(entry.getValue()));
            }
            object.add("restoreSizes", restoreSizes);
        }
        if (dto.contentOffsets() != null && !dto.contentOffsets().isEmpty()) {
            JsonObject offsets = new JsonObject();
            for (Map.Entry<String, ContentOffset> entry : dto.contentOffsets().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isZero()) {
                    offsets.add(entry.getKey(), writeOffset(entry.getValue()));
                }
            }
            if (!offsets.entrySet().isEmpty()) {
                object.add("contentOffsets", offsets);
            }
        }
        return object;
    }

    private JsonObject writeBounds(DockRect bounds) {
        JsonObject object = new JsonObject();
        object.addProperty("x", bounds.x());
        object.addProperty("y", bounds.y());
        object.addProperty("width", bounds.width());
        object.addProperty("height", bounds.height());
        return object;
    }

    private JsonObject writeSize(DockSize size) {
        JsonObject object = new JsonObject();
        object.addProperty("width", size.width());
        object.addProperty("height", size.height());
        return object;
    }

    private JsonObject writeOffset(ContentOffset offset) {
        JsonObject object = new JsonObject();
        object.addProperty("x", offset.x());
        object.addProperty("y", offset.y());
        return object;
    }

    private JsonObject writeNode(NodeDto node) {
        JsonObject object = new JsonObject();
        if (node instanceof LeafDto) {
            LeafDto leaf = (LeafDto) node;
            object.addProperty("type", "leaf");
            object.addProperty("nodeId", leaf.nodeId());
            object.addProperty("moduleId", leaf.moduleId());
            object.addProperty("visible", leaf.visible());
            return object;
        }
        SplitDto split = (SplitDto) node;
        object.addProperty("type", "split");
        object.addProperty("nodeId", split.nodeId());
        object.addProperty("axis", split.axis().name());
        object.addProperty("ratio", split.ratio());
        object.add("first", writeNode(split.first()));
        object.add("second", writeNode(split.second()));
        return object;
    }

    private DockLayoutDto readVersioned(JsonObject object) throws DockLayoutFormatException {
        int version = integer(object, "version", "document");
        if (version < DockLayoutDto.MIN_SUPPORTED_VERSION || version > DockLayoutDto.CURRENT_VERSION) {
            throw new DockLayoutFormatException("unsupported layout version: " + version);
        }
        requireOnlyFields(object, version == 2 ? V2_FIELDS : V3_FIELDS, "document");
        JsonArray rootArray = array(object, "roots", "document");
        ArrayList<RootDto> roots = new ArrayList<RootDto>();
        DecodeBudget budget = new DecodeBudget();
        for (int i = 0; i < rootArray.size(); i++) {
            String path = "roots[" + i + "]";
            JsonObject root = object(rootArray.get(i), path);
            requireOnlyFields(root, ROOT_FIELDS, path);
            roots.add(new RootDto(
                    string(root, "rootId", path),
                    readBounds(objectField(root, "bounds", path), path + ".bounds"),
                    readNode(objectField(root, "content", path), path + ".content", budget, 1),
                    optionalBoolean(root, "floating", false, path),
                    optionalBoolean(root, "pinned", false, path),
                    root.has("group") ? integer(root, "group", path) : 0,
                    optionalBoolean(root, "hidden", false, path)));
        }
        if (version == 2) {
            return new DockLayoutDto(version, roots);
        }
        JsonObject policyObject = objectField(object, "policies", "document");
        Map<String, PolicyDto> policies = new LinkedHashMap<String, PolicyDto>();
        for (Map.Entry<String, JsonElement> entry : policyObject.entrySet()) {
            String path = "policies." + entry.getKey();
            JsonObject policy = object(entry.getValue(), path);
            requireOnlyFields(policy, POLICY_FIELDS, path);
            policies.put(entry.getKey(), new PolicyDto(
                    bool(policy, "visible", path),
                    bool(policy, "movable", path),
                    bool(policy, "resizable", path),
                    policy.has("floating") && bool(policy, "floating", path),
                    policy.has("pinned") && bool(policy, "pinned", path),
                    policy.has("showTerminalButton")
                            ? bool(policy, "showTerminalButton", path)
                            : !catalog.contains(entry.getKey())
                                    || catalog.metrics(entry.getKey()).defaults().showTerminalButton()));
        }
        return new DockLayoutDto(
                version,
                roots,
                policies,
                readRestoreSizes(object),
                readContentOffsets(object));
    }

    /**
     * Before v7 a window's mode followed its modules: it was anchored while any visible module was
     * non-floating, and pinned while a visible floating module was pinned. Keeps old layouts as-is.
     */
    static DockWorkspace deriveWindowModes(DockWorkspace workspace) {
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>();
        for (FloatingRoot root : workspace.roots()) {
            List<LeafNode> leaves = LayoutTrees.leaves(root.content());
            List<LeafNode> shown = leaves.stream()
                    .filter(leaf -> workspace.policyFor(leaf.moduleId()).visible())
                    .collect(Collectors.<LeafNode>toList());
            List<LeafNode> considered = shown.isEmpty() ? leaves : shown;
            boolean floating = considered.stream()
                    .allMatch(leaf -> workspace.policyFor(leaf.moduleId()).floating());
            boolean pinned = floating
                    && considered.stream().anyMatch(leaf -> workspace.policyFor(leaf.moduleId()).pinned());
            roots.add(root.withMode(floating, pinned));
        }
        return workspace.withRoots(roots);
    }

    /**
     * Whether the document already laid its windows out as shells. Before v6 that was only true
     * for {@code "spliceMode": "shell"}; the stretch and compact modes (including the older
     * {@code compactSplice} flag) sized windows differently.
     */
    private static boolean wasShell(JsonObject document, int version) {
        if (version >= 6) {
            return true;
        }
        JsonElement mode = document.get("spliceMode");
        return mode != null && mode.isJsonPrimitive() && "shell".equalsIgnoreCase(mode.getAsString().trim());
    }

    private DockRect readBounds(JsonObject object, String path) throws DockLayoutFormatException {
        requireOnlyFields(object, BOUNDS_FIELDS, path);
        int x = integer(object, "x", path);
        int y = integer(object, "y", path);
        int width = integer(object, "width", path);
        int height = integer(object, "height", path);
        if (width <= 0 || height <= 0) {
            throw new DockLayoutFormatException(path + " width and height must be positive");
        }
        return new DockRect(x, y, width, height);
    }

    private Map<String, ContentOffset> readContentOffsets(JsonObject document) throws DockLayoutFormatException {
        if (!document.has("contentOffsets")) {
            return Collections.emptyMap();
        }
        JsonObject object = objectField(document, "contentOffsets", "document");
        Map<String, ContentOffset> offsets = new LinkedHashMap<String, ContentOffset>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String path = "contentOffsets." + entry.getKey();
            JsonObject offset = object(entry.getValue(), path);
            requireOnlyFields(offset, OFFSET_FIELDS, path);
            int x = integer(offset, "x", path);
            int y = integer(offset, "y", path);
            if (x < 0 || y < 0) {
                throw new DockLayoutFormatException(path + " x and y must be non-negative");
            }
            if (x != 0 || y != 0) {
                offsets.put(entry.getKey(), new ContentOffset(x, y));
            }
        }
        return offsets;
    }

    private Map<String, DockSize> readRestoreSizes(JsonObject document) throws DockLayoutFormatException {
        if (!document.has("restoreSizes")) {
            return Collections.emptyMap();
        }
        JsonObject object = objectField(document, "restoreSizes", "document");
        Map<String, DockSize> restoreSizes = new LinkedHashMap<String, DockSize>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String path = "restoreSizes." + entry.getKey();
            JsonObject size = object(entry.getValue(), path);
            requireOnlyFields(size, SIZE_FIELDS, path);
            int width = integer(size, "width", path);
            int height = integer(size, "height", path);
            if (width <= 0 || height <= 0) {
                throw new DockLayoutFormatException(path + " width and height must be positive");
            }
            restoreSizes.put(entry.getKey(), new DockSize(width, height));
        }
        return restoreSizes;
    }

    private NodeDto readNode(JsonObject object, String path, DecodeBudget budget, int depth)
            throws DockLayoutFormatException {
        budget.enter(depth);
        String type = string(object, "type", path);
        if (type.equals("leaf")) {
            requireOnlyFields(object, LEAF_FIELDS, path);
            return new LeafDto(
                    string(object, "nodeId", path),
                    string(object, "moduleId", path),
                    bool(object, "visible", path));
        }
        if (type.equals("split")) {
            requireOnlyFields(object, SPLIT_FIELDS, path);
            DockAxis axis;
            try {
                axis = DockAxis.valueOf(string(object, "axis", path));
            } catch (IllegalArgumentException e) {
                throw new DockLayoutFormatException(path + ".axis is invalid", e);
            }
            return new SplitDto(
                    string(object, "nodeId", path),
                    axis,
                    decimal(object, "ratio", path),
                    readNode(objectField(object, "first", path), path + ".first", budget, depth + 1),
                    readNode(objectField(object, "second", path), path + ".second", budget, depth + 1));
        }
        throw new DockLayoutFormatException(path + ".type is unsupported: " + type);
    }

    private DockWorkspace migrateV1(JsonObject object) throws DockLayoutFormatException {
        ArrayList<FloatingRoot> roots = new ArrayList<FloatingRoot>();
        Set<String> consumedModules = new LinkedHashSet<String>();
        int index = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String path = "v1[" + entry.getKey() + "]";
            JsonObject panel = object(entry.getValue(), path);
            requireOnlyFields(panel, V1_ENTRY_FIELDS, path);
            DockRect bounds = migrationContext.clampToViewport(readLegacyBounds(panel, path));
            boolean visible = optionalBoolean(panel, "visible", true, path);

            boolean hasOrientation = panel.has("orientation");
            boolean hasSplit = panel.has("split");
            boolean hasChildren = panel.has("children");
            boolean composite = hasOrientation || hasSplit || hasChildren;
            LayoutNode content;
            if (composite) {
                if (!(hasOrientation && hasSplit && hasChildren)) {
                    throw new DockLayoutFormatException(path + " has incomplete composite fields");
                }
                DockAxis axis;
                try {
                    axis = DockAxis.valueOf(string(panel, "orientation", path));
                } catch (IllegalArgumentException e) {
                    throw new DockLayoutFormatException(path + ".orientation is invalid", e);
                }
                JsonArray children = array(panel, "children", path);
                if (children.size() != 2) {
                    throw new DockLayoutFormatException(path + ".children must contain exactly two module ids");
                }
                String firstModule = string(children.get(0), path + ".children[0]");
                String secondModule = string(children.get(1), path + ".children[1]");
                if (firstModule.equals(secondModule)) {
                    throw new DockLayoutFormatException(path + " repeats the same child module");
                }
                consumeLegacyModule(firstModule, consumedModules, path);
                consumeLegacyModule(secondModule, consumedModules, path);

                int available = Math.max(
                        1,
                        axis.extent(bounds.inset(migrationContext.rootInsets()))
                                - migrationContext.dividerThickness());
                int legacySplit = integer(panel, "split", path);
                double ratio = Math.max(0.000001, Math.min(0.999999, (double) legacySplit / available));
                content = new SplitNode(
                        NodeIds.deterministic("split", "v1:" + index + ":" + entry.getKey()),
                        axis,
                        ratio,
                        new LeafNode(DockWorkspaceDefaults.leafNodeId(firstModule), firstModule, visible),
                        new LeafNode(DockWorkspaceDefaults.leafNodeId(secondModule), secondModule, visible));
            } else {
                String moduleId = entry.getKey();
                consumeLegacyModule(moduleId, consumedModules, path);
                content = new LeafNode(DockWorkspaceDefaults.leafNodeId(moduleId), moduleId, visible);
            }
            roots.add(new FloatingRoot(
                    NodeIds.deterministic("root", "v1:" + index + ":" + entry.getKey()),
                    bounds,
                    content));
            index++;
        }

        DockWorkspace workspace = new DockWorkspace(roots);
        try {
            WorkspaceValidator.validateStructure(workspace);
        } catch (WorkspaceValidationException e) {
            throw new DockLayoutFormatException("invalid migrated v1 layout: " + e.getMessage(), e);
        }
        return workspace;
    }

    private DockRect readLegacyBounds(JsonObject panel, String path) throws DockLayoutFormatException {
        int width = integer(panel, "width", path);
        int height = integer(panel, "height", path);
        if (width <= 0 || height <= 0) {
            throw new DockLayoutFormatException(path + " width and height must be positive");
        }
        return new DockRect(integer(panel, "x", path), integer(panel, "y", path), width, height);
    }

    private void consumeLegacyModule(String moduleId, Set<String> consumed, String path)
            throws DockLayoutFormatException {
        if (!consumed.add(moduleId)) {
            throw new DockLayoutFormatException(path + " duplicates module " + moduleId);
        }
    }

    private static void requireOnlyFields(JsonObject object, Set<String> allowed, String path)
            throws DockLayoutFormatException {
        // Java 8 兼容：Gson 2.8.0 的 JsonObject 没有 keySet()，用 entrySet() 取得键。
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String field = entry.getKey();
            if (!allowed.contains(field)) {
                throw new DockLayoutFormatException(path + " contains unknown field " + field);
            }
        }
    }

    private static JsonObject objectField(JsonObject parent, String field, String path)
            throws DockLayoutFormatException {
        JsonElement element = required(parent, field, path);
        return object(element, path + "." + field);
    }

    private static JsonObject object(JsonElement element, String path) throws DockLayoutFormatException {
        if (!element.isJsonObject()) {
            throw new DockLayoutFormatException(path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String field, String path) throws DockLayoutFormatException {
        JsonElement element = required(parent, field, path);
        if (!element.isJsonArray()) {
            throw new DockLayoutFormatException(path + "." + field + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonObject parent, String field, String path)
            throws DockLayoutFormatException {
        return string(required(parent, field, path), path + "." + field);
    }

    private static String string(JsonElement element, String path) throws DockLayoutFormatException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new DockLayoutFormatException(path + " must be a string");
        }
        String value = element.getAsString();
        if (isBlank(value)) {
            throw new DockLayoutFormatException(path + " must not be blank");
        }
        return value;
    }

    private static boolean bool(JsonObject parent, String field, String path) throws DockLayoutFormatException {
        JsonElement element = required(parent, field, path);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new DockLayoutFormatException(path + "." + field + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static boolean optionalBoolean(
            JsonObject parent,
            String field,
            boolean defaultValue,
            String path) throws DockLayoutFormatException {
        return parent.has(field) ? bool(parent, field, path) : defaultValue;
    }

    private static int integer(JsonObject parent, String field, String path) throws DockLayoutFormatException {
        JsonElement element = required(parent, field, path);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DockLayoutFormatException(path + "." + field + " must be an integer");
        }
        try {
            return new BigDecimal(element.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new DockLayoutFormatException(path + "." + field + " must be a 32-bit integer", e);
        }
    }

    private static double decimal(JsonObject parent, String field, String path) throws DockLayoutFormatException {
        JsonElement element = required(parent, field, path);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DockLayoutFormatException(path + "." + field + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new DockLayoutFormatException(path + "." + field + " must be finite");
        }
        return value;
    }

    private static JsonElement required(JsonObject parent, String field, String path)
            throws DockLayoutFormatException {
        JsonElement element = parent.get(field);
        if (element == null || element.isJsonNull()) {
            throw new DockLayoutFormatException(path + " is missing " + field);
        }
        return element;
    }

    /**
     * Java 8 / Gson 2.8 兼容：{@code JsonParser.parseString(json)} 自 Gson 2.8.6 才提供；
     * 1.12.2 运行时自带 Gson 2.8.0，因此改用等价的实例 API（对全部输入行为一致）。
     */
    private static JsonElement parseJson(String json) {
        return new JsonParser().parse(json);
    }

    /** Java 8 等价实现：{@code String.isBlank()}，按 Unicode 码点判定空白。 */
    private static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    public static final class DecodedLayout {
        private final DockWorkspace workspace;
        private final int sourceVersion;
        private final boolean migrated;
        private final boolean reconciled;

        public DecodedLayout(DockWorkspace workspace, int sourceVersion, boolean migrated) {
            this(workspace, sourceVersion, migrated, false);
        }

        public DecodedLayout(DockWorkspace workspace, int sourceVersion, boolean migrated, boolean reconciled) {
            Objects.requireNonNull(workspace, "workspace");
            this.workspace = workspace;
            this.sourceVersion = sourceVersion;
            this.migrated = migrated;
            this.reconciled = reconciled;
        }

        public DockWorkspace workspace() {
            return workspace;
        }

        public int sourceVersion() {
            return sourceVersion;
        }

        public boolean migrated() {
            return migrated;
        }

        public boolean reconciled() {
            return reconciled;
        }

        public boolean needsRewrite() {
            return migrated || reconciled;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DecodedLayout other = (DecodedLayout) o;
            return sourceVersion == other.sourceVersion
                    && migrated == other.migrated
                    && reconciled == other.reconciled
                    && Objects.equals(workspace, other.workspace);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Objects.hashCode(workspace);
            result = 31 * result + Integer.hashCode(sourceVersion);
            result = 31 * result + Boolean.hashCode(migrated);
            result = 31 * result + Boolean.hashCode(reconciled);
            return result;
        }

        @Override
        public String toString() {
            return "DecodedLayout[workspace=" + workspace + ", sourceVersion=" + sourceVersion
                    + ", migrated=" + migrated + ", reconciled=" + reconciled + "]";
        }
    }

    private static final class Reconciliation {
        private final DockWorkspace workspace;
        private final boolean changed;

        private Reconciliation(DockWorkspace workspace, boolean changed) {
            this.workspace = workspace;
            this.changed = changed;
        }

        private DockWorkspace workspace() {
            return workspace;
        }

        private boolean changed() {
            return changed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Reconciliation other = (Reconciliation) o;
            return changed == other.changed && Objects.equals(workspace, other.workspace);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Objects.hashCode(workspace);
            result = 31 * result + Boolean.hashCode(changed);
            return result;
        }

        @Override
        public String toString() {
            return "Reconciliation[workspace=" + workspace + ", changed=" + changed + "]";
        }
    }

    private static final class DecodeBudget {
        private int nodes;

        private void enter(int depth) throws DockLayoutFormatException {
            if (depth > WorkspaceValidator.MAX_DEPTH) {
                throw new DockLayoutFormatException(
                        "layout exceeds maximum depth " + WorkspaceValidator.MAX_DEPTH);
            }
            if (++nodes > WorkspaceValidator.MAX_NODES) {
                throw new DockLayoutFormatException(
                        "layout exceeds maximum node count " + WorkspaceValidator.MAX_NODES);
            }
        }
    }
}
