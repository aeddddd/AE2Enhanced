package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.prioritylist.IPartitionList;

import java.util.*;

/** 每个终端容器独立持有，直接查询所属网络的聚合物品视图。 */
public final class TerminalQueryService implements IMEMonitorHandlerReceiver<IAEItemStack> {
    private static final int MAX_SEARCH_CACHE = 32;

    private final IMEMonitor<IAEItemStack> monitor;
    private final Runnable changeSubscriber;
    private final Map<QueryKey, List<IAEItemStack>> sortedCache = new HashMap<>();
    private final LinkedHashMap<QueryKey, List<IAEItemStack>> searchCache =
            new LinkedHashMap<QueryKey, List<IAEItemStack>>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<QueryKey, List<IAEItemStack>> eldest) {
                    return size() > MAX_SEARCH_CACHE;
                }
            };
    private final Map<String, Set<ItemDescriptor>> nameIndex = new HashMap<>();
    private final Map<String, Set<ItemDescriptor>> modIndex = new HashMap<>();
    private List<IAEItemStack> networkSnapshot = Collections.emptyList();
    private boolean dirty = true;
    private boolean closed;

    public TerminalQueryService(IMEMonitor<IAEItemStack> monitor, Runnable changeSubscriber) {
        this.monitor = monitor;
        this.changeSubscriber = changeSubscriber;
        if (monitor != null) {
            monitor.addListener(this, this);
        }
    }

    public TerminalQueryService(IMEMonitor<IAEItemStack> monitor) {
        this(monitor, null);
    }

    private static void index(Map<String, Set<ItemDescriptor>> index, String word,
            ItemDescriptor descriptor) {
        Set<ItemDescriptor> descriptors = index.get(word);
        if (descriptors == null) {
            descriptors = new HashSet<>();
            index.put(word, descriptors);
        }
        descriptors.add(descriptor);
    }

    private static List<String> splitWords(String input) {
        List<String> words = new ArrayList<>();
        if (input == null || input.isEmpty()) return words;
        StringBuilder ascii = new StringBuilder();
        StringBuilder chinese = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean isChinese = (c >= '\u4e00' && c <= '\u9fa5')
                    || (c >= '\u3400' && c <= '\u4dbf') || (c >= '\u2e80' && c <= '\u2eff');
            if (isChinese) {
                processAscii(ascii, words);
                chinese.append(c);
            } else {
                processChinese(chinese, words);
                ascii.append(c);
            }
        }
        processAscii(ascii, words);
        processChinese(chinese, words);
        return words;
    }

    private static void processAscii(StringBuilder input, List<String> words) {
        if (input.length() == 0) return;
        String[] tokens = input.toString().split("[^a-zA-Z0-9]+");
        input.setLength(0);
        for (String token : tokens) {
            if (token.length() <= 1) continue;
            String lower = token.toLowerCase(Locale.ROOT);
            words.add(lower);
            if (token.length() <= 2) continue;
            StringBuilder current = new StringBuilder().append(token.charAt(0));
            for (int i = 1; i < token.length(); i++) {
                char c = token.charAt(i);
                if (Character.isUpperCase(c)) {
                    if (current.length() > 1) words.add(current.toString().toLowerCase(Locale.ROOT));
                    current.setLength(0);
                }
                current.append(Character.toLowerCase(c));
            }
            if (current.length() > 1 && !current.toString().equals(lower)) {
                words.add(current.toString().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static void processChinese(StringBuilder input, List<String> words) {
        if (input.length() == 0) return;
        String value = input.toString();
        input.setLength(0);
        if (value.length() <= 1) return;
        words.add(value);
        for (int i = 0; i < value.length(); i++) words.add(String.valueOf(value.charAt(i)));
    }

    private static void sort(List<IAEItemStack> stacks, byte sortBy, byte sortDir) {
        SortOrder order = sortBy == 2 ? SortOrder.MOD
                : sortBy == 1 ? SortOrder.AMOUNT
                : sortBy == 3 ? SortOrder.INVTWEAKS : SortOrder.NAME;
        com.github.aeddddd.ae2enhanced.util.TerminalItemSort.sort(stacks, order,
                sortDir == 1 ? SortDir.DESCENDING : SortDir.ASCENDING);
    }

    public PageResult query(Query query) {
        ensureSnapshot();
        List<IAEItemStack> sorted = sorted(query);
        List<IAEItemStack> matched = search(sorted, query);
        List<IAEItemStack> filtered = applyFilters(matched, query.viewCellFilter, query.clientFilter);
        int total = filtered.size();
        int start = Math.min(query.offset, total);
        int end = Math.min(query.offset + query.limit, total);
        List<IAEItemStack> page = new ArrayList<>(Math.max(0, end - start));
        for (int i = start; i < end; i++) {
            page.add(filtered.get(i).copy());
        }
        return new PageResult(total, query.offset, page);
    }

    private List<IAEItemStack> sorted(Query query) {
        QueryKey key = QueryKey.forSort(query);
        List<IAEItemStack> result = sortedCache.get(key);
        if (result != null) return result;
        result = new ArrayList<>(networkSnapshot.size());
        for (IAEItemStack original : networkSnapshot) {
            if (original == null || !original.isMeaningful()) continue;
            if (query.viewMode == 2 && !original.isCraftable()) continue;
            if (query.viewMode == 0 && original.getStackSize() <= 0L) continue;
            result.add(original.copy());
        }
        sort(result, query.sortBy, query.sortDir);
        sortedCache.put(key, result);
        return result;
    }

    private List<IAEItemStack> search(List<IAEItemStack> sorted, Query query) {
        if (query.search.isEmpty()) return sorted;
        if (query.searchMode == 0 && query.clientFilter != null && !query.clientFilter.isEmpty()) {
            return sorted;
        }
        QueryKey key = QueryKey.forSearch(query);
        List<IAEItemStack> result = searchCache.get(key);
        if (result != null) return result;
        String needle = query.search.toLowerCase(Locale.ROOT);
        Set<ItemDescriptor> candidates = query.searchMode == 1
                ? modCandidates(needle) : nameCandidates(needle);
        result = new ArrayList<>();
        for (IAEItemStack stack : sorted) {
            if (candidates.contains(new ItemDescriptor(stack.getDefinition()))) {
                result.add(stack.copy());
            }
        }
        searchCache.put(key, result);
        return result;
    }

    private Set<ItemDescriptor> modCandidates(String needle) {
        boolean fuzzy = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().terminal.modSearchFuzzyThreshold <= 0
                || networkSnapshot.size() <= com.github.aeddddd.ae2enhanced.config.ServerConfig.current().terminal.modSearchFuzzyThreshold;
        if (!fuzzy) {
            Set<ItemDescriptor> exact = modIndex.get(needle);
            return exact == null ? Collections.emptySet() : exact;
        }
        Set<ItemDescriptor> result = new HashSet<>();
        for (Map.Entry<String, Set<ItemDescriptor>> entry : modIndex.entrySet()) {
            if (entry.getKey().contains(needle)) result.addAll(entry.getValue());
        }
        return result;
    }

    private Set<ItemDescriptor> nameCandidates(String needle) {
        Set<ItemDescriptor> result = null;
        for (String term : needle.split(" ")) {
            if (term.isEmpty()) continue;
            Set<ItemDescriptor> matches = new HashSet<>();
            Set<ItemDescriptor> exact = nameIndex.get(term);
            if (exact != null) {
                matches.addAll(exact);
            }
            for (Map.Entry<String, Set<ItemDescriptor>> entry : nameIndex.entrySet()) {
                if (entry.getKey().contains(term)) {
                    matches.addAll(entry.getValue());
                }
            }
            for (Set<ItemDescriptor> descriptors : nameIndex.values()) {
                for (ItemDescriptor descriptor : descriptors) {
                    if (com.github.aeddddd.ae2enhanced.util.PinyinSearch.contains(
                            descriptor.getDisplayName(), term)) {
                        matches.add(descriptor);
                    }
                }
            }
            if (matches.isEmpty()) return Collections.emptySet();
            if (result == null) {
                result = matches;
            } else {
                result.retainAll(matches);
            }
            if (result.isEmpty()) return Collections.emptySet();
        }
        return result == null ? Collections.emptySet() : result;
    }

    private List<IAEItemStack> applyFilters(List<IAEItemStack> source,
            IPartitionList<IAEItemStack> viewCellFilter, Set<ItemDescriptor> clientFilter) {
        if (viewCellFilter == null && clientFilter == null) return source;
        List<IAEItemStack> result = new ArrayList<>(source.size());
        for (IAEItemStack stack : source) {
            if (stack == null) continue;
            if (viewCellFilter != null && !viewCellFilter.isListed(stack)) continue;
            if (clientFilter != null && !clientFilter.contains(new ItemDescriptor(stack.getDefinition()))) continue;
            result.add(stack);
        }
        return result;
    }

    private void ensureSnapshot() {
        if (!dirty) return;
        List<IAEItemStack> snapshot = new ArrayList<>();
        if (monitor != null && monitor.getStorageList() != null) {
            for (IAEItemStack stack : monitor.getStorageList()) {
                if (stack != null && stack.isMeaningful()) snapshot.add(stack.copy());
            }
        }
        networkSnapshot = snapshot;
        nameIndex.clear();
        modIndex.clear();
        for (IAEItemStack stack : snapshot) {
            ItemDescriptor descriptor = new ItemDescriptor(stack.getDefinition());
            for (String word : splitWords(descriptor.getDisplayName().toLowerCase(Locale.ROOT))) {
                index(nameIndex, word, descriptor);
            }
            index(modIndex, descriptor.getModId().toLowerCase(Locale.ROOT), descriptor);
        }
        sortedCache.clear();
        searchCache.clear();
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    private void changed() {
        markDirty();
        if (changeSubscriber != null) changeSubscriber.run();
    }

    @Override
    public boolean isValid(Object verificationToken) {
        return !closed && verificationToken == this;
    }

    @Override
    public void postChange(IBaseMonitor<IAEItemStack> changedMonitor,
            Iterable<IAEItemStack> change, IActionSource source) {
        changed();
    }

    @Override
    public void onListUpdate() {
        changed();
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (monitor != null) monitor.removeListener(this);
        sortedCache.clear();
        searchCache.clear();
        nameIndex.clear();
        modIndex.clear();
        networkSnapshot = Collections.emptyList();
    }

    public static final class Query {
        private final String search;
        private final byte searchMode;
        private final byte sortBy;
        private final byte sortDir;
        private final byte viewMode;
        private final int offset;
        private final int limit;
        private final IPartitionList<IAEItemStack> viewCellFilter;
        private final Set<ItemDescriptor> clientFilter;

        public Query(String search, byte searchMode, byte sortBy, byte sortDir, byte viewMode,
                int offset, int limit, IPartitionList<IAEItemStack> viewCellFilter,
                Set<ItemDescriptor> clientFilter) {
            this.search = search == null ? "" : search;
            this.searchMode = searchMode;
            this.sortBy = sortBy;
            this.sortDir = sortDir;
            this.viewMode = viewMode;
            this.offset = offset;
            this.limit = limit;
            this.viewCellFilter = viewCellFilter;
            this.clientFilter = clientFilter;
        }
    }

    private static final class QueryKey {
        private final String search;
        private final byte searchMode;
        private final byte sortBy;
        private final byte sortDir;
        private final byte viewMode;

        private QueryKey(String search, byte searchMode, byte sortBy, byte sortDir, byte viewMode) {
            this.search = search;
            this.searchMode = searchMode;
            this.sortBy = sortBy;
            this.sortDir = sortDir;
            this.viewMode = viewMode;
        }

        static QueryKey forSort(Query query) {
            return new QueryKey("", (byte) 0, query.sortBy, query.sortDir, query.viewMode);
        }

        static QueryKey forSearch(Query query) {
            return new QueryKey(query.search, query.searchMode, query.sortBy, query.sortDir, query.viewMode);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof QueryKey)) return false;
            QueryKey key = (QueryKey) other;
            return searchMode == key.searchMode && sortBy == key.sortBy && sortDir == key.sortDir
                    && viewMode == key.viewMode && search.equals(key.search);
        }

        @Override
        public int hashCode() {
            int result = search.hashCode();
            result = 31 * result + searchMode;
            result = 31 * result + sortBy;
            result = 31 * result + sortDir;
            return 31 * result + viewMode;
        }
    }

    public static final class PageResult {
        public final int totalCount;
        public final int offset;
        public final List<IAEItemStack> items;

        PageResult(int totalCount, int offset, List<IAEItemStack> items) {
            this.totalCount = totalCount;
            this.offset = offset;
            this.items = items;
        }
    }
}
