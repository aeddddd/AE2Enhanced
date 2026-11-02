package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.terminal.access.IItemRepoAccessor;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.container.CraftingStatus;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequestV2;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResultV2;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Omni Terminal 专用 ItemRepo —— R3 服务端分页架构。
 *
 * <p><b>核心改变：客户端不再维护完整物品列表。</b>
 * 只缓存当前可见页 ±1 页（最多 135 个物品）。排序/搜索/过滤全部在服务端执行。
 * 滚动、搜索时向服务端发送 {@link PacketOmniPageRequest}，服务端返回
 * {@link PacketOmniPageResult}。
 *
 * <p>当 HEI/JEI 可用且玩家输入搜索词时，客户端会先通过 {@link JeiSearchHelper}
 * 获取 HEI 的匹配结果，并将 {@link ItemDescriptor} 列表附加到分页请求中，
 * 从而支持 JECH/HECH 拼音搜索以及 HEI 原生的 tooltip/mod 搜索语义。
 */
public class OmniItemRepo extends ItemRepo {

    // ==================== 常量 ====================
    public static final int CACHE_PAGES = 3;             // 当前页 + 上一页 + 下一页
    /** 默认页容量（12 col × 5 row）；用于缓存数组的初始分配。 */
    public static final int DEFAULT_SLOTS_PER_PAGE = 60;
    private static final long REFRESH_COOLDOWN_MS = 200;

    // ==================== R3: 三页缓存 ====================
    /**
     * 缓存容量：只能是"页容量 × {@link #CACHE_PAGES}"。
     *
     * <p>可拼接终端的 repo 面板尺寸可变（列数 = 内容宽度 / 18），容量只能通过
     * {@link #setSlotsPerPage(int)} 变更，且变更时会立刻重新拉取当前可见页。
     * V2 请求按 {@link PacketOmniPageRequest#MAX_LIMIT} 分块串行发送，线上 limit 不等于整个缓存长度。</p>
     */
    private IAEItemStack[] cache = new IAEItemStack[capacityFor(DEFAULT_SLOTS_PER_PAGE)];
    private int cacheOffset = -1;   // cache[0] 对应服务端列表的哪个 offset，-1 表示未初始化
    private long renderViewVersion;
    private int totalCount = 0;
    private int scrollOffset = 0;   // 当前 GUI 滚动位置（物品索引，不是页数）
    private int slotsPerPage = DEFAULT_SLOTS_PER_PAGE;  // 每页可见槽位数（默认 12 col * 5 row）

    // ==================== 当前查询参数 ====================
    private String currentSearch = "";
    private byte searchMode = 0;    // 0=NAME, 1=MOD, 2=TOOLTIP(已禁用)
    private byte sortBy = 0;        // 0=NAME, 1=AMOUNT, 2=MOD, 3=INVTWEAKS
    private byte sortDir = 0;       // 0=ASC, 1=DESC
    private byte viewMode = 0;      // 0=STORED, 1=ALL, 2=CRAFTABLE
    private List<ItemDescriptor> currentClientFilter = null;

    // ==================== 刷新节流 ====================
    private volatile boolean pendingRefresh = false;
    private long lastRefreshTime = 0;
    /** 已发出分页请求尚未收到结果时的 (offset, limit)，用于抑制同一页的重复请求。 */
    private boolean awaitingPage = false;
    private int awaitingTicks;
    private int inFlightOffset = -1;
    private int inFlightLimit = -1;
    private int inFlightEnd = -1;
    private int nextChunkOffset = -1;
    private int inFlightChunkOffset = -1;

    private ContainerOmniTerm queryContainer;
    private long containerSessionId;
    private long queryGeneration;
    private long nextRequestId;
    private long currentRequestId;

    // ==================== 原有字段 ====================
    private final OmniItemRegistry registry = new OmniItemRegistry();
    private List<CraftingStatus> activeCrafting = Collections.emptyList();
    private List<IAEItemStack> normalView = Collections.emptyList();

    public OmniItemRepo(IScrollSource src, ISortSource sortSrc) {
        super(src, sortSrc);
    }

    public void bindContainer(ContainerOmniTerm container) {
        this.queryContainer = container;
        synchronizeSession();
    }

    public void tick() {
        synchronizeSession();
        if (this.queryContainer != null && this.containerSessionId != 0L
                && !this.awaitingPage && this.cacheOffset < 0) {
            requestPage(Math.max(0, this.scrollOffset - this.slotsPerPage), cacheCapacity());
        }
        if (this.awaitingPage && ++this.awaitingTicks >= 40) {
            int offset = this.inFlightOffset;
            int limit = this.inFlightLimit;
            this.awaitingPage = false;
            requestPage(offset, limit);
        }
        if (this.pendingRefresh) handleUpdateNotify();
    }

    private void synchronizeSession() {
        if (this.queryContainer == null) return;
        long session = this.queryContainer.getContainerSessionId();
        if (session == 0L || session == this.containerSessionId) return;
        this.containerSessionId = session;
        this.queryGeneration = this.queryContainer.getQueryGeneration();
        this.nextRequestId = 0L;
        this.currentRequestId = 0L;
        this.awaitingPage = false;
        TerminalQueryClient.register(session, this);
    }

    // ==================== 公共访问器 ====================

    public String harnessQueryState() {
        IAEItemStack visible = getSlotStack(0);
        String visibleId = visible == null ? "null" : visible.getDefinition().getItem().getRegistryName()
                + "@" + visible.getDefinition().getMetadata();
        return "session=" + this.containerSessionId + " generation=" + this.queryGeneration
                + " request=" + this.currentRequestId + " next=" + this.nextRequestId
                + " awaiting=" + this.awaitingPage + " cacheOffset=" + this.cacheOffset
                + " scrollOffset=" + this.scrollOffset + " total=" + this.totalCount
                + " visible=" + visibleId
                + " containerSession=" + (this.queryContainer == null ? "none"
                        : this.queryContainer.getContainerSessionId())
                + " containerGeneration=" + (this.queryContainer == null ? "none"
                        : this.queryContainer.getQueryGeneration());
    }

    public void setActiveCrafting(List<CraftingStatus> list) {
        this.activeCrafting = list != null ? new ArrayList<>(list) : Collections.emptyList();
        if (this.cacheOffset >= 0) {
            updateNormalView();
        }
    }

    public boolean hasPendingRefresh() {
        return this.pendingRefresh;
    }

    /** 给定页容量所需的缓存槽位数（纯函数，便于单测）。 */
    public static int capacityFor(int slotsPerPage) {
        return Math.max(1, slotsPerPage) * CACHE_PAGES;
    }

    /** 当前缓存数组容量；V2 按协议上限分块请求整个区间。 */
    public int cacheCapacity() {
        return this.cache.length;
    }

    public int getSlotsPerPage() {
        return this.slotsPerPage;
    }

    /**
     * 设置每页可见槽位数（由 repo 面板在列/行变化时调用）。
     *
     * <p>容量变化时旧缓存整段失效（缓存偏移语义不变，但长度不同），因此这里直接重新分配并
     * 以当前可见页为中心重新拉取；容量不变则是空操作。</p>
     */
    public void setSlotsPerPage(int slotsPerPage) {
        int next = Math.max(1, slotsPerPage);
        if (next == this.slotsPerPage) {
            return;
        }
        this.slotsPerPage = next;
        int capacity = capacityFor(next);
        if (capacity == this.cache.length) {
            return;
        }
        this.cache = new IAEItemStack[capacity];
        this.cacheOffset = -1;
        this.totalCount = 0;
        // 立即以当前可见页为中心重新拉取，否则界面会停在空缓存上
        onScrollChanged(this.scrollOffset);
    }

    // ==================== R3: 槽位数据获取 ====================

    /**
     * GUI 通过此方法获取指定槽位的物品。
     * slotIndex 是 GUI 中的槽位索引 (0-44)。
     */
    public IAEItemStack getSlotStack(int slotIndex) {
        int globalIndex = this.scrollOffset + slotIndex;
        int cacheIdx = globalIndex - this.cacheOffset;
        if (cacheIdx >= 0 && cacheIdx < this.cache.length) {
            return this.cache[cacheIdx];
        }
        return null;
    }

    @Override
    public IAEItemStack getReferenceItem(int idx) {
        if (this.activeCrafting.isEmpty()) {
            return getSlotStack(idx);
        }

        int rowSize = this.getRowSize();
        int row = idx / rowSize;
        int col = idx % rowSize;

        if (row == 0) {
            if (col < this.activeCrafting.size()) {
                return this.activeCrafting.get(col).output;
            }
            return null;
        }

        int cacheIdx = this.scrollOffset + (idx - rowSize) - this.cacheOffset;
        if (cacheIdx < 0 || cacheIdx >= this.cache.length) {
            return null;
        }
        int skipped = 0;
        for (int index = 0; index < cacheIdx; index++) {
            IAEItemStack stack = this.cache[index];
            if (stack != null && isInActiveCrafting(stack)) {
                skipped++;
            }
        }
        int viewIdx = cacheIdx - skipped;
        return viewIdx < this.normalView.size() ? this.normalView.get(viewIdx) : null;
    }

    // ==================== R3: 分页结果处理 ====================

    /**
     * 处理服务端返回的页数据。
     */
    public void handlePageResult(int totalCount, int offset, List<PacketOmniPageResult.Entry> entries) {
        if (this.queryContainer != null) return;
        applyPage(totalCount, offset, entries);
    }

    public void handlePageResult(PacketOmniPageResultV2 packet) {
        synchronizeSession();
        if (this.queryContainer == null || packet.getContainerSessionId() != this.containerSessionId
                || packet.getQueryGeneration() != this.queryGeneration
                || packet.getRequestId() != this.currentRequestId || !this.awaitingPage) return;
        List<PacketOmniPageResult.Entry> entries = new ArrayList<>(packet.getEntries().size());
        for (PacketOmniPageResultV2.Entry entry : packet.getEntries()) {
            if (entry.stack != null) {
                entries.add(new PacketOmniPageResult.Entry(entry.id, entry.stack, entry.count));
            }
        }
        applyPage(packet.getTotalCount(), packet.getOffset(), entries);
    }

    private void applyPage(int totalCount, int offset, List<PacketOmniPageResult.Entry> entries) {
        boolean chunked = this.queryContainer != null && this.awaitingPage;
        if (chunked && offset != this.inFlightChunkOffset) {
            AE2Enhanced.LOGGER.warn("[AE2E] Ignoring V2 chunk with unexpected offset: expected={}, actual={}",
                    this.inFlightChunkOffset, offset);
            return;
        }
        this.totalCount = totalCount;
        this.renderViewVersion++;
        this.cacheOffset = chunked ? this.inFlightOffset : offset;
        if (!chunked || offset == this.inFlightOffset) {
            Arrays.fill(this.cache, null);
        }

        if (entries.size() > this.cache.length) {
            // 服务端按更大的 limit 回包：说明客户端缓存容量与实际请求 limit 不一致。
            // 只记录一行（不再静默），多余条目会被丢弃。
            AE2Enhanced.LOGGER.warn("[AE2E] Omni page result larger than cache: entries={}, capacity={}",
                    entries.size(), this.cache.length);
        }

        int destination = chunked ? offset - this.inFlightOffset : 0;
        for (int i = 0; i < entries.size(); i++) {
            int index = destination + i;
            if (index < 0 || index >= this.cache.length) continue;
            PacketOmniPageResult.Entry e = entries.get(i);
            if (e == null || e.stack == null) continue;
            this.registry.register(e.id, e.stack, e.count);
            IAEItemStack stack = this.registry.getStack(e.id);
            if (stack != null) {
                this.cache[index] = stack;
            }
        }

        updateNormalView();
        setChanged(true);
        syncToParentView();
        if (chunked && this.nextChunkOffset < this.inFlightEnd) {
            sendNextChunk();
        } else {
            this.awaitingPage = false;
        }
    }

    /**
     * 处理变化通知。
     */
    public void handleUpdateNotify() {
        long now = System.currentTimeMillis();
        if (now - this.lastRefreshTime < REFRESH_COOLDOWN_MS) {
            this.pendingRefresh = true;
            return;
        }
        this.lastRefreshTime = now;
        this.pendingRefresh = false;
        // 以当前可见页为中心刷新，确保上下都有缓冲
        int targetOffset = Math.max(0, this.scrollOffset - this.slotsPerPage);
        requestPage(targetOffset, cacheCapacity());
    }

    /**
     * 滚动条变化时调用。
     * <p>
     * 缓存策略：始终以当前可见页为中心，覆盖「上一页 + 当前页 + 下一页」。
     * 避免向上或向下滚动到缓存边界时出现空白延迟。
     */
    public void onScrollChanged(int newOffset) {
        this.scrollOffset = newOffset;
        int visibleStart = newOffset;
        int visibleEnd = newOffset + this.slotsPerPage;
        int cacheStart = this.cacheOffset;
        int cacheEnd = (this.cacheOffset < 0) ? -1 : this.cacheOffset + this.cache.length;

        // 缓存未覆盖可见区域：以当前可见页为中心重新请求（确保上下都有缓冲）
        if (this.cacheOffset < 0 || visibleStart < cacheStart || visibleEnd > cacheEnd) {
            int targetOffset = Math.max(0, visibleStart - this.slotsPerPage);
            requestPage(targetOffset, cacheCapacity());
            return;
        }

        // 接近缓存末尾，预加载下一页（以当前可见页为中心）
        if (visibleEnd > cacheEnd - this.slotsPerPage && cacheEnd < this.totalCount) {
            int targetOffset = Math.max(0, cacheEnd - this.slotsPerPage);
            requestPage(targetOffset, cacheCapacity());
            return;
        }

        // 接近缓存开头，预加载上一页（以当前可见页为中心）
        if (visibleStart < cacheStart + this.slotsPerPage && cacheStart > 0) {
            int targetOffset = Math.max(0, visibleStart - this.slotsPerPage);
            requestPage(targetOffset, cacheCapacity());
        }
    }

    /**
     * 搜索/排序/视图模式变化时调用。
     */
    public void onQueryParamsChanged(String search, byte searchMode, byte sortBy,
                                      byte sortDir, byte viewMode) {
        this.currentSearch = search != null ? search : "";
        this.searchMode = searchMode;
        this.sortBy = sortBy;
        this.sortDir = sortDir;
        this.viewMode = viewMode;

        // 对名称搜索尝试使用 HEI/JEI 过滤器（支持拼音/tooltip 等语义）
        this.currentClientFilter = computeClientFilter(this.currentSearch, this.searchMode);
        advanceQueryGeneration();

        // 清空缓存，重新请求第一页
        this.cacheOffset = -1;
        this.scrollOffset = 0;
        this.totalCount = 0;
        this.awaitingPage = false;
        Arrays.fill(this.cache, null);
        requestPage(0, cacheCapacity());
    }

    private List<ItemDescriptor> computeClientFilter(String search, byte mode) {
        if (mode != 0 || search == null || search.isEmpty()) {
            return null;
        }
        try {
            return JeiSearchHelper.getMatchingDescriptors(search);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to compute HEI client filter", e);
            return null;
        }
    }

    private void requestPage(int offset, int limit) {
        synchronizeSession();
        if (this.awaitingPage && offset == this.inFlightOffset && limit == this.inFlightLimit) return;
        if (this.queryContainer != null && this.containerSessionId == 0L) return;
        this.awaitingPage = true;
        this.awaitingTicks = 0;
        this.inFlightOffset = offset;
        this.inFlightLimit = limit;
        if (this.queryContainer != null) {
            this.inFlightEnd = offset + limit;
            this.nextChunkOffset = offset;
            sendNextChunk();
        } else {
            sendToServer(new PacketOmniPageRequest(this.currentSearch, this.searchMode, this.sortBy,
                    this.sortDir, this.viewMode, offset, limit, this.currentClientFilter));
        }
    }

    private void sendNextChunk() {
        this.awaitingTicks = 0;
        this.inFlightChunkOffset = this.nextChunkOffset;
        int limit = Math.min(PacketOmniPageRequest.MAX_LIMIT, this.inFlightEnd - this.nextChunkOffset);
        this.nextChunkOffset += limit;
        this.currentRequestId = ++this.nextRequestId;
        sendToServer(new PacketOmniPageRequestV2(this.containerSessionId, this.queryGeneration,
                this.currentRequestId, this.currentSearch, this.searchMode, this.sortBy,
                this.sortDir, this.viewMode, this.inFlightChunkOffset, limit, this.currentClientFilter));
    }

    private void advanceQueryGeneration() {
        if (this.queryContainer == null) return;
        synchronizeSession();
        this.queryGeneration = Math.max(this.queryGeneration,
                this.queryContainer.getQueryGeneration()) + 1;
    }

    protected void sendToServer(PacketOmniPageRequest request) {
        AE2Enhanced.network.sendToServer(request);
    }

    protected void sendToServer(PacketOmniPageRequestV2 request) {
        AE2Enhanced.network.sendToServer(request);
    }

    // ==================== 父类兼容性覆盖 ====================

    @Override
    public void setSearchString(String search) {
        super.setSearchString(search);
        String rawSearch = search != null ? search : "";
        byte newMode = 0;
        // 仅 @ 前缀触发 mod 搜索；# 前缀暂时禁用 tooltip 搜索，去掉 # 后作为普通名称搜索
        if (rawSearch.startsWith("@")) {
            newMode = 1; // MOD
            rawSearch = rawSearch.substring(1).trim();
        } else if (rawSearch.startsWith("#")) {
            rawSearch = rawSearch.substring(1).trim();
        }
        if (!rawSearch.equals(this.currentSearch) || newMode != this.searchMode
                || this.currentClientFilter != null) {
            this.currentSearch = rawSearch;
            this.searchMode = newMode;
            this.currentClientFilter = computeClientFilter(this.currentSearch, this.searchMode);
            advanceQueryGeneration();
            // 搜索词变化时重新请求第一页
            this.cacheOffset = -1;
            this.scrollOffset = 0;
            this.totalCount = 0;
            this.awaitingPage = false;
            Arrays.fill(this.cache, null);
            requestPage(0, cacheCapacity());
        }
    }

    @Override
    public void updateView() {
        // R3 不再调用父类的 updateView()（那会导致 IItemList 遍历）
        // 而是直接同步当前缓存到父类 view 字段
        syncToParentView();
    }

    @Override
    public int size() {
        // 必须返回服务端报告的总数，否则 GUI 滚动条范围会基于 local cache 大小，
        // 导致 activeCrafting 存在时无法滚动到后面的物品。
        return this.totalCount;
    }

    protected void syncToParentView() {
        IItemRepoAccessor acc = (IItemRepoAccessor) this;
        List<IAEItemStack> viewList = Arrays.asList(this.cache);
        acc.ae2e$setView(viewList);
        acc.ae2e$setChanged(false);
        acc.ae2e$setResort(false);
    }

    // ==================== activeCrafting / normalView ====================

    private void updateNormalView() {
        this.normalView = new ArrayList<>();
        for (IAEItemStack stack : this.cache) {
            if (stack == null) continue;
            if (!isInActiveCrafting(stack)) {
                this.normalView.add(stack);
            }
        }
    }

    private boolean isInActiveCrafting(IAEItemStack stack) {
        for (CraftingStatus status : this.activeCrafting) {
            if (status.output.equals(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getRowSize() {
        // 直接调用父类方法，避免反射访问私有字段时因缺少 setAccessible(true) 而 fallback 到 9
        return super.getRowSize();
    }

    protected void setChanged(boolean value) {
        ((IItemRepoAccessor) this).ae2e$setChanged(value);
    }

    // ==================== 向后兼容存根 ====================

    public long getRenderViewVersion() {
        return this.renderViewVersion;
    }
}
