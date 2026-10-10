package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IItemRepoAccessor;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult;
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
    /** 旧 Omni 终端的默认页容量（18 col × 5 row）；用于缓存数组的初始分配。 */
    public static final int DEFAULT_SLOTS_PER_PAGE = 90;
    private static final long REFRESH_COOLDOWN_MS = 200;

    // ==================== R3: 三页缓存 ====================
    /**
     * 缓存容量：只能是"页容量 × {@link #CACHE_PAGES}"。
     *
     * <p>可拼接终端的 repo 面板尺寸可变（列数 = 内容宽度 / 18），但缓存数组的长度同时也是
     * 分页请求的 {@code limit}，两者必须始终一致，否则服务端会按旧 limit 回包而被静默截断。
     * 因此容量只能通过 {@link #setSlotsPerPage(int)} 变更，且变更时会立刻重新拉取当前可见页。</p>
     */
    private IAEItemStack[] cache = new IAEItemStack[capacityFor(DEFAULT_SLOTS_PER_PAGE)];
    private int cacheOffset = -1;   // cache[0] 对应服务端列表的哪个 offset，-1 表示未初始化
    private int totalCount = 0;
    private int scrollOffset = 0;   // 当前 GUI 滚动位置（物品索引，不是页数）
    private int slotsPerPage = DEFAULT_SLOTS_PER_PAGE;  // 每页可见槽位数（默认 18 col * 5 row）

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
    private int inFlightOffset = -1;
    private int inFlightLimit = -1;

    // ==================== 原有字段 ====================
    private final OmniItemRegistry registry = new OmniItemRegistry();
    private List<CraftingStatus> activeCrafting = Collections.emptyList();
    private List<IAEItemStack> normalView = Collections.emptyList();

    public OmniItemRepo(IScrollSource src, ISortSource sortSrc) {
        super(src, sortSrc);
    }

    // ==================== 公共访问器 ====================

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

    /** 当前缓存数组容量（= 分页请求的 limit）。 */
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

        int normalIdx = this.scrollOffset + (idx - rowSize);
        int cacheIdx = normalIdx - this.cacheOffset;
        if (cacheIdx < 0 || cacheIdx >= this.normalView.size()) {
            return null;
        }
        return this.normalView.get(cacheIdx);
    }

    // ==================== R3: 分页结果处理 ====================

    /**
     * 处理服务端返回的页数据。
     */
    public void handlePageResult(int totalCount, int offset, List<PacketOmniPageResult.Entry> entries) {
        this.totalCount = totalCount;
        this.cacheOffset = offset;
        this.awaitingPage = false;
        Arrays.fill(this.cache, null);

        if (entries.size() > this.cache.length) {
            // 服务端按更大的 limit 回包：说明客户端缓存容量与实际请求 limit 不一致。
            // 只记录一行（不再静默），多余条目会被丢弃。
            AE2Enhanced.LOGGER.warn("[AE2E] Omni page result larger than cache: entries={}, capacity={}",
                    entries.size(), this.cache.length);
        }

        for (int i = 0; i < entries.size() && i < this.cache.length; i++) {
            PacketOmniPageResult.Entry e = entries.get(i);
            this.registry.register(e.id, e.stack, e.count);
            IAEItemStack stack = this.registry.getStack(e.id);
            if (stack != null) {
                this.cache[i] = stack;
            }
        }

        // 更新 normalView（排除 activeCrafting 中的物品）
        updateNormalView();

        // 通知 GUI 刷新
        setChanged(true);
        syncToParentView();
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
        if (this.awaitingPage && offset == this.inFlightOffset && limit == this.inFlightLimit) {
            // 同一页的请求已在途：再来一次只会拿到同样的结果，直接抑制。
            // 拖动缩放面板时"容量变化"与"滚动范围重算"会在同一 tick 各自请求一次，靠这里合并。
            return;
        }
        PacketOmniPageRequest req = new PacketOmniPageRequest(
            this.currentSearch, this.searchMode, this.sortBy,
            this.sortDir, this.viewMode, offset, limit,
            this.currentClientFilter
        );
        this.awaitingPage = true;
        this.inFlightOffset = offset;
        this.inFlightLimit = limit;
        sendToServer(req);
    }

    /**
     * 分页请求的发送出口。
     *
     * <p>单独成方法是为了给无头单测留一个覆写点（测试环境没有网络初始化），
     * 生产路径上就是 {@code AE2Enhanced.network.sendToServer}。</p>
     */
    protected void sendToServer(PacketOmniPageRequest request) {
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

    private void syncToParentView() {
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

    private void setChanged(boolean value) {
        ((IItemRepoAccessor) this).ae2e$setChanged(value);
    }

    // ==================== 向后兼容存根 ====================

    public long getRenderViewVersion() {
        // R3: 不再使用 renderView 双缓冲
        return this.cacheOffset;
    }
}
