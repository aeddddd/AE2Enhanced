package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.prioritylist.IPartitionList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.item.ItemStack;

import java.util.*;

/**
 * 物品存储适配器,继承 {@link AbstractStorageAdapter}.
 * 内部使用 {@link HugeCount} 混合精度计数,突破 long 上限.
 *
 * <p>同时维护服务端搜索索引（nameIndex / modIndex），支持基于关键词的快速筛选。
 * 索引在物品存入/取出时增量更新，避免遍历时的 O(N) 分词开销。
 */
public class ItemStorageAdapter extends AbstractStorageAdapter<IAEItemStack, ItemDescriptor>
        implements IMEMonitorHandlerReceiver<IAEItemStack> {

    // 服务端搜索索引：keyword -> Set<ItemDescriptor>
    private final Object2ObjectOpenHashMap<String, ObjectOpenHashSet<ItemDescriptor>> nameIndex =
            new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<String, ObjectOpenHashSet<ItemDescriptor>> modIndex =
            new Object2ObjectOpenHashMap<>();

    // R3: 已排序列表缓存
    private List<IAEItemStack> sortedList = new ArrayList<>();
    private boolean sortedListDirty = true;
    /** 异步首加载完成后置位：索引在主线程首次搜索时惰性重建（fastutil 索引非线程安全） */
    private volatile boolean indexDirty = true;
    private final Object indexBuildLock = new Object();
    private int cachedSortBy = -1;
    private int cachedSortDir = -1;
    private int cachedViewMode = -1;
    /** 上次全量重建时间戳（去抖：高频变更下避免每次翻页都全量重建+排序） */
    private long lastSortedListRebuildMs = 0L;

    // R3: 打开的终端玩家列表（用于发送 UPDATE_NOTIFY）
    private final Set<net.minecraft.entity.player.EntityPlayerMP> openPlayers =
            Collections.newSetFromMap(new WeakHashMap<>());

    // 外部 ME 网络 monitor（普通驱动器、外部存储等）
    private appeng.api.storage.IMEMonitor<IAEItemStack> externalMonitor;

    // 外部存储差集缓存（externalMonitor 中不在 adapter 中的物品，通常只有几百个）
    private final List<IAEItemStack> externalOnlyCache = new ArrayList<>();
    private boolean externalOnlyDirty = true;

    private long lastExternalNotifyMs = 0;

    // descriptor 快照，加速外部存储差集判定（避免每次查 ConcurrentHashMap）
    private java.util.HashSet<ItemDescriptor> descriptorSnapshot = null;

    // 搜索缓存：避免相同搜索词重复索引
    private static final int MAX_SEARCH_CACHE = 32;
    private final java.util.HashMap<SearchCacheKey, List<IAEItemStack>> searchCache = new java.util.HashMap<>();

    private static class SearchCacheKey {
        final String query;
        final byte searchMode;
        final byte viewMode;

        SearchCacheKey(String query, byte searchMode, byte viewMode) {
            this.query = query;
            this.searchMode = searchMode;
            this.viewMode = viewMode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SearchCacheKey)) return false;
            SearchCacheKey other = (SearchCacheKey) o;
            return searchMode == other.searchMode && viewMode == other.viewMode
                    && query.equals(other.query);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(query, searchMode, viewMode);
        }
    }

    public ItemStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        file.load(storage);
        file.registerPostLoadHook(() -> {
            recalcTotal();      // IO 线程：仅遍历 ConcurrentHashMap，线程安全
            indexDirty = true;  // 索引延迟到主线程首次搜索时重建（fastutil 索引非线程安全）
        });
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.ITEM;
    }

    @Override
    protected ItemDescriptor createDescriptor(IAEItemStack input) {
        // 使用 getDefinition() 避免 count=0 的 craftable 物品被识别为 air
        // ItemDescriptor 构造器内部已做 NBT 深拷贝,此处无需再 copy
        ItemStack definition = input.getDefinition();
        return new ItemDescriptor(definition != null ? definition : input.createItemStack());
    }

    @Override
    protected IAEItemStack createResult(IAEItemStack request, HugeCount amount) {
        IAEItemStack result = ((IItemStorageChannel) channel).createStack(request.createItemStack());
        if (result == null) return null;
        result.setStackSize(amount.toLongSaturated());
        return result;
    }

    @Override
    protected IAEItemStack getAETemplate(ItemDescriptor descriptor) {
        return descriptor.getAETemplate((IItemStorageChannel) channel);
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return (IStorageChannel<IAEItemStack>) channel;
    }

    public void setExternalMonitor(appeng.api.storage.IMEMonitor<IAEItemStack> monitor) {
        if (this.externalMonitor == monitor) {
            return;
        }
        if (this.externalMonitor != null) {
            this.externalMonitor.removeListener(this);
        }
        this.externalMonitor = monitor;
        markSortedListDirty();
        if (this.externalMonitor != null) {
            this.externalMonitor.addListener(this, this);
        }
    }

    public appeng.api.storage.IMEMonitor<IAEItemStack> getExternalMonitor() {
        return this.externalMonitor;
    }

    @Override
    public boolean isValid(Object verificationToken) {
        return verificationToken == this;
    }

    @Override
    public void postChange(IBaseMonitor<IAEItemStack> monitor, Iterable<IAEItemStack> changes, IActionSource src) {
        markSortedListDirty();
        notifyOpenPlayersExternal();
    }

    @Override
    public void onListUpdate() {
        markSortedListDirty();
        notifyOpenPlayersExternal();
    }

    private void notifyOpenPlayersExternal() {
        long now = System.currentTimeMillis();
        if (now - lastExternalNotifyMs < 200) {
            return;
        }
        lastExternalNotifyMs = now;
        for (net.minecraft.entity.player.EntityPlayerMP player : this.openPlayers) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendTo(
                    new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniUpdateNotify(), player);
        }
    }

    public void markSortedListDirty() {
        this.sortedListDirty = true;
        this.externalOnlyDirty = true;
        this.descriptorSnapshot = null;
        this.searchCache.clear();
    }

    private void ensureDescriptorSnapshot() {
        if (this.descriptorSnapshot == null) {
            this.descriptorSnapshot = new java.util.HashSet<>(this.storage.keySet());
        }
    }

    /**
     * 重建外部存储差集缓存。
     * 只在 externalOnlyDirty 时执行，通常外部存储只有几百个物品类型。
     */
    private void ensureExternalOnlyCache() {
        if (!this.externalOnlyDirty) return;
        this.externalOnlyCache.clear();
        if (this.externalMonitor == null) {
            this.externalOnlyDirty = false;
            return;
        }
        ensureDescriptorSnapshot();
        for (IAEItemStack stack : this.externalMonitor.getStorageList()) {
            if (!stack.isMeaningful()) continue;
            ItemDescriptor desc = createDescriptor(stack);
            if (this.descriptorSnapshot.contains(desc)) continue;
            this.externalOnlyCache.add(stack.copy());
        }
        this.externalOnlyDirty = false;
    }

    // ---- 索引维护 ----

    @Override
    protected void onDescriptorAdded(ItemDescriptor descriptor) {
        String name = descriptor.getDisplayName().toLowerCase();
        String modId = descriptor.getModId().toLowerCase();

        for (String word : splitWords(name)) {
            ObjectOpenHashSet<ItemDescriptor> set = this.nameIndex.get(word);
            if (set == null) {
                set = new ObjectOpenHashSet<>();
                this.nameIndex.put(word, set);
            }
            set.add(descriptor);
        }
        ObjectOpenHashSet<ItemDescriptor> modSet = this.modIndex.get(modId);
        if (modSet == null) {
            modSet = new ObjectOpenHashSet<>();
            this.modIndex.put(modId, modSet);
        }
        modSet.add(descriptor);
    }

    @Override
    protected void onDescriptorRemoved(ItemDescriptor descriptor) {
        String name = descriptor.getDisplayName().toLowerCase();
        String modId = descriptor.getModId().toLowerCase();

        for (String word : splitWords(name)) {
            ObjectOpenHashSet<ItemDescriptor> set = this.nameIndex.get(word);
            if (set != null) {
                set.remove(descriptor);
                if (set.isEmpty()) {
                    this.nameIndex.remove(word);
                }
            }
        }
        ObjectOpenHashSet<ItemDescriptor> set = this.modIndex.get(modId);
        if (set != null) {
            set.remove(descriptor);
            if (set.isEmpty()) {
                this.modIndex.remove(modId);
            }
        }
    }

    /**
     * 从现有 storage 重建索引（用于初始化或异常恢复）.
     */
    private void rebuildIndex() {
        this.nameIndex.clear();
        this.modIndex.clear();
        for (ItemDescriptor desc : storage.keySet()) {
            onDescriptorAdded(desc);
        }
    }

    /** 首加载完成后索引待重建时，在主线程搜索路径上惰性重建一次。 */
    private void ensureIndex() {
        if (!indexDirty) return;
        synchronized (indexBuildLock) {
            if (!indexDirty) return;
            rebuildIndex();
            indexDirty = false;
        }
    }

    // ---- 分词工具（与 OmniItemRegistry 保持逻辑一致） ----

    private static List<String> splitWords(String input) {
        List<String> words = new ArrayList<>();
        if (input == null || input.isEmpty()) return words;

        StringBuilder asciiPart = new StringBuilder();
        StringBuilder cnPart = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isChinese(c)) {
                if (asciiPart.length() > 0) {
                    processAsciiPart(asciiPart.toString(), words);
                    asciiPart.setLength(0);
                }
                cnPart.append(c);
            } else {
                if (cnPart.length() > 0) {
                    processCnPart(cnPart.toString(), words);
                    cnPart.setLength(0);
                }
                asciiPart.append(c);
            }
        }

        if (asciiPart.length() > 0) {
            processAsciiPart(asciiPart.toString(), words);
        }
        if (cnPart.length() > 0) {
            processCnPart(cnPart.toString(), words);
        }

        return words;
    }

    private static boolean isChinese(char c) {
        return (c >= '\u4e00' && c <= '\u9fa5')
            || (c >= '\u3400' && c <= '\u4dbf')
            || (c >= '\u2e80' && c <= '\u2eff');
    }

    private static void processAsciiPart(String input, List<String> words) {
        String[] tokens = input.split("[^a-zA-Z0-9]+");
        for (String token : tokens) {
            if (token.length() <= 1) continue;
            String lower = token.toLowerCase();
            words.add(lower);

            if (token.length() > 2) {
                StringBuilder current = new StringBuilder();
                current.append(token.charAt(0));
                for (int i = 1; i < token.length(); i++) {
                    char c = token.charAt(i);
                    if (Character.isUpperCase(c)) {
                        String word = current.toString().toLowerCase();
                        if (word.length() > 1) {
                            words.add(word);
                        }
                        current = new StringBuilder();
                    }
                    current.append(Character.toLowerCase(c));
                }
                String last = current.toString().toLowerCase();
                if (last.length() > 1 && !last.equals(lower)) {
                    words.add(last);
                }
            }
        }
    }

    private static void processCnPart(String input, List<String> words) {
        if (input.length() <= 1) return;
        words.add(input);
        for (int i = 0; i < input.length(); i++) {
            words.add(String.valueOf(input.charAt(i)));
        }
    }

    // ==================== R3: 分页查询 ====================

    public PageResult query(String search, byte searchMode, byte sortBy,
                            byte sortDir, byte viewMode, int offset, int limit) {
        return query(search, searchMode, sortBy, sortDir, viewMode, offset, limit, null, null);
    }

    public PageResult query(String search, byte searchMode, byte sortBy,
                            byte sortDir, byte viewMode, int offset, int limit,
                            IPartitionList<IAEItemStack> viewCellFilter,
                            Set<ItemDescriptor> clientFilter) {
        ensureIndex(); // 保留惰性索引重建：首次全量加载后由本活跃分页路径在主线程触发一次
        ensureSortedList(sortBy, sortDir, viewMode);

        if (search == null || search.isEmpty()) {
            List<IAEItemStack> filtered = applyFilters(this.sortedList, viewCellFilter, clientFilter);
            int total = filtered.size();
            int start = Math.min(offset, total);
            int end = Math.min(offset + limit, total);

            List<IAEItemStack> result = new ArrayList<>(Math.max(0, end - start));
            for (int i = start; i < end; i++) {
                result.add(filtered.get(i).copy());
            }
            return new PageResult(total, offset, result);
        } else if (searchMode == 0 && clientFilter != null && !clientFilter.isEmpty()) {
            // 名称搜索且客户端 HEI/JEI 已返回匹配列表时，直接以该列表为白名单过滤，
            // 避免服务端字面搜索不识拼音/首字母/HECH 语义导致结果为空。
            List<IAEItemStack> filtered = applyFilters(this.sortedList, viewCellFilter, clientFilter);
            int total = filtered.size();
            int start = Math.min(offset, total);
            int end = Math.min(offset + limit, total);

            List<IAEItemStack> result = new ArrayList<>(Math.max(0, end - start));
            for (int i = start; i < end; i++) {
                result.add(filtered.get(i).copy());
            }
            return new PageResult(total, offset, result);
        } else {
            return performSearchPaged(search, searchMode, offset, limit, viewCellFilter, clientFilter);
        }
    }

    private List<IAEItemStack> applyFilters(List<IAEItemStack> source,
                                            IPartitionList<IAEItemStack> viewCellFilter,
                                            Set<ItemDescriptor> clientFilter) {
        if (viewCellFilter == null && clientFilter == null) {
            return source;
        }
        List<IAEItemStack> filtered = new ArrayList<>(source.size());
        for (IAEItemStack stack : source) {
            if (viewCellFilter != null && !viewCellFilter.isListed(stack)) {
                continue;
            }
            if (clientFilter != null && !clientFilter.contains(createDescriptor(stack))) {
                continue;
            }
            filtered.add(stack);
        }
        return filtered;
    }

    private void ensureSortedList(byte sortBy, byte sortDir, byte viewMode) {
        if (!this.sortedListDirty && sortBy == this.cachedSortBy
                && sortDir == this.cachedSortDir && viewMode == this.cachedViewMode) {
            return;
        }
        // 去抖：高频变更下避免每次翻页都全量重建+排序（N 极大时单次重建 O(N log N) 达数百 ms）。
        // 仅在排序参数不变时生效（参数变更立即重建）；窗口内复用旧列表——无搜索词时
        // 分页显示的成员与数量均可能滞后一个窗口（仅展示层，提取走 storage 实时校验，
        // 不影响存取正确性）。首次构建（列表为空）不受去抖限制。
        long debounceMs = com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.terminalRebuildDebounceMs;
        long nowMs = System.currentTimeMillis();
        boolean sameParams = sortBy == this.cachedSortBy && sortDir == this.cachedSortDir && viewMode == this.cachedViewMode;
        if (!this.sortedList.isEmpty() && sameParams && debounceMs > 0 && nowMs - this.lastSortedListRebuildMs < debounceMs) {
            return;
        }
        this.lastSortedListRebuildMs = nowMs;

        this.sortedList.clear();

        appeng.api.storage.data.IItemList<IAEItemStack> externalList = null;
        if (this.externalMonitor != null) {
            externalList = this.externalMonitor.getStorageList();
        }

        for (java.util.Map.Entry<ItemDescriptor, HugeCount> entry : storage.entrySet()) {
            HugeCount count = entry.getValue();
            if (count == null || count.isZero()) continue;

            IAEItemStack stack = getAETemplate(entry.getKey());
            if (stack == null) continue;
            stack = stack.copy();
            stack.setStackSize(count.toLongSaturated());

            // 合并外部 ME monitor 的 craftable 标记，确保存储中的物品也能中键下单/在 CRAFTABLE 视图中显示
            if (externalList != null) {
                IAEItemStack precise = externalList.findPrecise(stack);
                if (precise != null && precise.isCraftable()) {
                    stack.setCraftable(true);
                }
            }

            if (viewMode == 2 && !stack.isCraftable()) continue;
            if (viewMode == 0 && stack.getStackSize() <= 0L) continue;

            this.sortedList.add(stack);
        }

        // 合并外部 ME 网络 monitor 的物品（使用差集缓存，避免每次遍历 50 万+ 物品）
        if (this.externalMonitor != null) {
            ensureExternalOnlyCache();
            for (IAEItemStack stack : this.externalOnlyCache) {
                if (!stack.isMeaningful()) continue;
                IAEItemStack copy = stack.copy();
                if (viewMode == 2 && !copy.isCraftable()) continue;
                if (viewMode == 0 && copy.getStackSize() <= 0L) continue;
                this.sortedList.add(copy);
            }
        }

        Comparator<IAEItemStack> comparator = getComparator(sortBy);
        appeng.util.ItemSorters.setDirection(sortDir == 1 ? appeng.api.config.SortDir.DESCENDING : appeng.api.config.SortDir.ASCENDING);
        appeng.util.ItemSorters.init();
        this.sortedList.sort(comparator);

        this.cachedSortBy = sortBy;
        this.cachedSortDir = sortDir;
        this.cachedViewMode = viewMode;
        this.sortedListDirty = false;
    }

    /**
     * 分页搜索：adapter 索引 + externalOnlyCache 线性扫描，合并后排序分页。
     * 使用搜索缓存避免重复索引（适合终端保持搜索、JEI 返回等场景）。
     */
    private PageResult performSearchPaged(String search, byte searchMode, int offset, int limit,
                                          IPartitionList<IAEItemStack> viewCellFilter,
                                          Set<ItemDescriptor> clientFilter) {
        SearchCacheKey cacheKey = new SearchCacheKey(search, searchMode, (byte) this.cachedViewMode);
        List<IAEItemStack> allMatched = this.searchCache.get(cacheKey);

        if (allMatched == null) {
            allMatched = new ArrayList<>();
            String query = search.toLowerCase();

            if (searchMode == 1) {
                boolean fuzzyEnabled = com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.modSearchFuzzyThreshold <= 0
                        || this.storage.size() <= com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.modSearchFuzzyThreshold;

                // 1. adapter MOD 搜索（索引加速）
                if (fuzzyEnabled) {
                    for (java.util.Map.Entry<String, ObjectOpenHashSet<ItemDescriptor>> entry : this.modIndex.entrySet()) {
                        if (!entry.getKey().contains(query)) continue;
                        for (ItemDescriptor desc : entry.getValue()) {
                            HugeCount count = storage.get(desc);
                            if (count == null || count.isZero()) continue;
                            IAEItemStack stack = getAETemplate(desc);
                            if (stack == null) continue;
                            stack = stack.copy();
                            stack.setStackSize(count.toLongSaturated());
                            allMatched.add(stack);
                        }
                    }
                } else {
                    ObjectOpenHashSet<ItemDescriptor> set = this.modIndex.get(query);
                    if (set != null) {
                        for (ItemDescriptor desc : set) {
                            HugeCount count = storage.get(desc);
                            if (count == null || count.isZero()) continue;
                            IAEItemStack stack = getAETemplate(desc);
                            if (stack == null) continue;
                            stack = stack.copy();
                            stack.setStackSize(count.toLongSaturated());
                            allMatched.add(stack);
                        }
                    }
                }

                // 2. externalOnlyCache MOD 搜索（只有几百个物品，线性扫描很快）
                ensureExternalOnlyCache();
                for (IAEItemStack stack : this.externalOnlyCache) {
                    String modId = stack.asItemStackRepresentation().getItem().getRegistryName().getNamespace().toLowerCase();
                    if (fuzzyEnabled ? !modId.contains(query) : !modId.equals(query)) continue;
                    allMatched.add(stack.copy());
                }
            } else {
                // 1. adapter NAME 搜索（索引加速）
                String[] terms = query.split(" ");
                ObjectOpenHashSet<ItemDescriptor> candidates = null;
                for (String term : terms) {
                    if (term.isEmpty()) continue;
                    ObjectOpenHashSet<ItemDescriptor> set = this.nameIndex.get(term);
                    if (set == null) {
                        candidates = null;
                        break;
                    }
                    if (candidates == null) {
                        candidates = new ObjectOpenHashSet<>(set);
                    } else {
                        candidates.retainAll(set);
                        if (candidates.isEmpty()) {
                            candidates = null;
                            break;
                        }
                    }
                }
                if (candidates != null) {
                    for (ItemDescriptor desc : candidates) {
                        HugeCount count = storage.get(desc);
                        if (count == null || count.isZero()) continue;
                        IAEItemStack stack = getAETemplate(desc);
                        if (stack == null) continue;
                        stack = stack.copy();
                        stack.setStackSize(count.toLongSaturated());
                        allMatched.add(stack);
                    }
                }

                // 2. externalOnlyCache NAME 搜索（只有几百个物品）
                ensureExternalOnlyCache();
                for (IAEItemStack stack : this.externalOnlyCache) {
                    String name = stack.asItemStackRepresentation().getDisplayName().toLowerCase();
                    boolean matches = true;
                    for (String term : terms) {
                        if (term.isEmpty()) continue;
                        if (!name.contains(term)) {
                            matches = false;
                            break;
                        }
                    }
                    if (!matches) continue;
                    allMatched.add(stack.copy());
                }
            }

            // 统一排序
            Comparator<IAEItemStack> comparator = getComparator((byte) this.cachedSortBy);
            appeng.util.ItemSorters.setDirection(this.cachedSortDir == 1 ? appeng.api.config.SortDir.DESCENDING : appeng.api.config.SortDir.ASCENDING);
            appeng.util.ItemSorters.init();
            allMatched.sort(comparator);

            // 缓存完整匹配列表（避免上限过大导致内存爆炸）
            if (allMatched.size() <= 10000 && this.searchCache.size() < MAX_SEARCH_CACHE) {
                this.searchCache.put(cacheKey, allMatched);
            }
        }

        // 应用 view cell / HEI 客户端过滤
        if (viewCellFilter != null || clientFilter != null) {
            Iterator<IAEItemStack> it = allMatched.iterator();
            while (it.hasNext()) {
                IAEItemStack stack = it.next();
                if (viewCellFilter != null && !viewCellFilter.isListed(stack)) {
                    it.remove();
                    continue;
                }
                if (clientFilter != null && !clientFilter.contains(createDescriptor(stack))) {
                    it.remove();
                }
            }
        }

        // 分页（allMatched 已是全局排序后的完整列表）
        int total = allMatched.size();
        int start = Math.min(offset, total);
        int end = Math.min(offset + limit, total);
        List<IAEItemStack> page = new ArrayList<>(Math.max(0, end - start));
        for (int i = start; i < end; i++) {
            page.add(allMatched.get(i).copy());
        }
        return new PageResult(total, offset, page);
    }

    private static Comparator<IAEItemStack> getComparator(byte sortBy) {
        if (sortBy == 2) {
            return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_MOD;
        } else if (sortBy == 1) {
            return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_SIZE;
        } else if (sortBy == 3) {
            return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS;
        }
        return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_NAME;
    }

    // 用于同一 tick 内去重通知，避免多玩家开终端时 N×N 网络包
    private long lastNotifyTick = -1;

    /**
     * 通知所有打开该终端的玩家刷新。同一 tick 内多次调用只发送一次包。
     *
     * @param currentTick 当前世界总 tick 数
     */
    public void onStorageChanged(long currentTick) {
        if (currentTick == lastNotifyTick) {
            return;
        }
        lastNotifyTick = currentTick;
        markSortedListDirty();
        for (net.minecraft.entity.player.EntityPlayerMP player : this.openPlayers) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendTo(
                    new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniUpdateNotify(), player);
        }
    }

    public void addOpenPlayer(net.minecraft.entity.player.EntityPlayerMP player) {
        this.openPlayers.add(player);
    }

    public void removeOpenPlayer(net.minecraft.entity.player.EntityPlayerMP player) {
        this.openPlayers.remove(player);
    }

    public static class PageResult {
        public final int totalCount;
        public final int offset;
        public final List<IAEItemStack> items;

        public PageResult(int totalCount, int offset, List<IAEItemStack> items) {
            this.totalCount = totalCount;
            this.offset = offset;
            this.items = items;
        }
    }
}
