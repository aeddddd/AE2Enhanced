package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * EMC 接口向 AE 网络暴露的存储处理器.
 *
 * <p>单向源: 只根据玩家 EMC 余额把已学知识物品“生成”到网络,不接受物品注入.</p>
 */
public class EMCInventoryHandler implements IMEInventoryHandler<IAEItemStack>, IMEMonitor<IAEItemStack> {

    /**
     * 终端/存储列表中单种物品的最大显示数量。
     * 使用 Long.MAX_VALUE / 2 是为了避免 AE2 内部或终端渲染时发生 long 溢出。
     */
    private static final long MAX_TERMINAL_STACK = Long.MAX_VALUE / 2;

    private final TileEMCInterface tile;

    // 缓存
    private List<IAEItemStack> availableCache = Collections.emptyList();
    private long availableCacheTick = -100;
    // 有效标志与 TTL 分离: 空列表同样缓存(避免合法空结果时每次调用全量重建);
    // 创造模式列表为静态内容,命中后不做 TTL 过期,仅由 invalidateAvailableCache 事件驱动重建
    private boolean availableCacheValid = false;
    private BigInteger emcBalanceCache = BigInteger.ZERO;
    private long emcBalanceCacheTick = -100;

    // 物品 EMC 值缓存,避免每次刷新都对每个物品做反射
    private final Map<ItemDescriptor, Long> emcValueCache = new HashMap<>();

    // 已学知识集合缓存: null 表示待重建,由 invalidateAvailableCache 在
    // 知识变更/换绑/EMC remap 时置空(全量反序列化知识列表开销大,不能按 5-tick TTL 重建)
    private Set<ItemDescriptor> knownSetCache = null;

    // 待冲刷的离线 EMC 扣减累计: 离线玩家扣减需要读写并压缩整个 playerdata 文件,
    // 改为在此累计,由 TileEMCInterface.update() 按节流间隔一次性落盘
    private BigInteger pendingOfflineEmc = BigInteger.ZERO;

    public EMCInventoryHandler(TileEMCInterface tile) {
        this.tile = tile;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        // EMC 接口为单向源,拒绝接收物品
        return input;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable type, IActionSource src) {
        if (request == null || request.getStackSize() <= 0 || !tile.isBound()) return null;
        ItemStack definition = request.getDefinition();
        if (definition.isEmpty()) return null;

        long itemEmc = getCachedEmcValue(definition);
        if (itemEmc <= 0) return null;

        Object provider = tile.getKnowledgeProvider();
        if (provider == null) return null;

        if (tile.isCreativeMode()) {
            return extractCreative(request, definition, provider);
        }

        // 白名单校验
        if (!tile.isWhitelisted(definition)) return null;

        // MODULATE 强制刷新余额保证不超提;SIMULATE 走 20-tick 缓存,
        // 避免总线高频探测时每轮都做反射读取(缓存会被 MODULATE 后的 refreshEmcCache 同步)
        BigInteger balance = getEmcBalance(provider, type != Actionable.SIMULATE);
        BigInteger itemEmcBI = BigInteger.valueOf(itemEmc);
        BigInteger maxAffordable = balance.divide(itemEmcBI);
        if (maxAffordable.signum() <= 0) return null;

        long extractCount = Math.min(request.getStackSize(), maxAffordable.min(BigInteger.valueOf(MAX_TERMINAL_STACK)).longValue());
        if (extractCount <= 0) return null;

        if (type == Actionable.SIMULATE) {
            IAEItemStack result = request.copy();
            result.setStackSize(extractCount);
            return result;
        }

        // MODULATE: 扣减 EMC（使用 BigInteger 避免 extractCount * itemEmc 溢出）
        BigInteger cost = BigInteger.valueOf(extractCount).multiply(itemEmcBI);
        BigInteger newBalance = balance.subtract(cost);
        if (tile.isOwnerOnline()) {
            ProjectEHelper.subtractEmcBig(provider, cost);
            refreshEmcCache(newBalance);
            // 标记待同步,由 TileEMCInterface.update() 按配置间隔节流冲刷,
            // 避免每次提取都全量序列化知识列表并发包
            tile.markSyncDirty();
        } else {
            // 离线玩家: provider 为只读快照. 扣减先累计到 pendingOfflineEmc,
            // 由 TileEMCInterface.update() 节流批量落盘;余额核算在 getEmcBalance
            // 刷新时扣除待冲刷量,保证不超提
            pendingOfflineEmc = pendingOfflineEmc.add(cost);
            refreshEmcCache(newBalance);
        }

        ItemStack real = definition.copy();
        real.setCount((int) Math.min(extractCount, definition.getMaxStackSize()));
        IAEItemStack result = AEItemStack.fromItemStack(real);
        if (result != null) {
            result.setStackSize(extractCount);
            // 向网络监视器上报负变化,使终端实时刷新
            postAlterationToNetwork(result, src);
        }
        return result;
    }

    /**
     * 创造模式提取: 不消耗 EMC, 不读写余额, 不向监视器上报负变化(数量恒定).
     * 白名单非空时按白名单过滤,为空时允许提取全部已学知识.
     * 行为对齐创造 ME 元件(CreativeCellInventory): 请求多少给多少.
     */
    @javax.annotation.Nullable
    private IAEItemStack extractCreative(@Nonnull IAEItemStack request,
                                         @Nonnull ItemStack definition, @Nonnull Object provider) {
        if (tile.isWhitelistActive()) {
            if (!tile.isWhitelisted(definition)) return null;
        } else if (!getKnownSet(provider).contains(new ItemDescriptor(definition))) {
            return null;
        }

        long extractCount = Math.min(request.getStackSize(), MAX_TERMINAL_STACK);
        if (extractCount <= 0) return null;

        ItemStack real = definition.copy();
        real.setCount((int) Math.min(extractCount, definition.getMaxStackSize()));
        IAEItemStack result = AEItemStack.fromItemStack(real);
        if (result != null) {
            result.setStackSize(extractCount);
        }
        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        if (!tile.isBound()) return out;
        Object provider = tile.getKnowledgeProvider();
        if (provider == null) return out;

        BigInteger balance = tile.isCreativeMode() ? null : getEmcBalance(provider, false);
        List<IAEItemStack> cached = getAvailableCache(provider, balance);
        for (IAEItemStack stack : cached) {
            out.add(stack.copy());
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        return false;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        return getAvailableItems(getChannel().createList());
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver<IAEItemStack> l, Object verificationToken) {
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<IAEItemStack> l) {
    }

    // ---- 缓存控制 ----

    public void invalidateAvailableCache() {
        availableCacheValid = false;
        availableCacheTick = -100;
        emcBalanceCacheTick = -100;
        knownSetCache = null;
    }

    public void invalidateEmcCache() {
        emcValueCache.clear();
    }

    private BigInteger getEmcBalance(Object provider, boolean forceRefresh) {
        long now = tile.getWorld().getTotalWorldTime();
        if (forceRefresh || now - emcBalanceCacheTick >= 20) {
            BigInteger fresh = ProjectEHelper.getEmcBig(provider);
            // 离线扣减尚未落盘时,provider 快照余额偏高,需扣除待冲刷量防止超提
            if (pendingOfflineEmc.signum() > 0) {
                fresh = fresh.subtract(pendingOfflineEmc);
                if (fresh.signum() < 0) fresh = BigInteger.ZERO;
            }
            emcBalanceCache = fresh;
            emcBalanceCacheTick = now;
        }
        return emcBalanceCache;
    }

    public boolean hasPendingOfflineEmc() {
        return pendingOfflineEmc.signum() > 0;
    }

    /**
     * 冲刷累计的离线 EMC 扣减. 玩家已上线时改走在线通道(并标记待同步),
     * 离线时一次性改写 playerdata 存档. 落盘失败保留累计量下轮重试.
     * 由 TileEMCInterface 在节流 tick / 换绑 / 卸载时调用.
     */
    public void flushPendingOfflineEmc() {
        if (pendingOfflineEmc.signum() <= 0) return;
        UUID owner = tile.getOwnerUUID();
        if (owner == null) return;
        BigInteger pending = pendingOfflineEmc;
        boolean flushed;
        if (tile.isOwnerOnline()) {
            Object provider = tile.getKnowledgeProvider();
            if (provider == null) return;
            ProjectEHelper.subtractEmcBig(provider, pending);
            tile.markSyncDirty();
            flushed = true;
        } else {
            flushed = ProjectEHelper.subtractEmcOffline(owner, pending);
            if (!flushed) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to flush {} offline EMC for {}, will retry", pending, owner);
            }
        }
        if (flushed) {
            pendingOfflineEmc = BigInteger.ZERO;
            // 强制下次访问按落盘后的真实余额重建缓存
            emcBalanceCacheTick = -100;
            availableCacheTick = -100;
        }
    }

    private void refreshEmcCache(BigInteger balance) {
        emcBalanceCache = balance;
        emcBalanceCacheTick = tile.getWorld().getTotalWorldTime();
    }

    @Nonnull
    private List<IAEItemStack> getAvailableCache(Object provider, BigInteger balance) {
        boolean creative = tile.isCreativeMode();
        if (availableCacheValid) {
            // 创造模式列表内容只随白名单/知识/remap/模式切换变化(均有事件驱动失效),不做周期重建
            if (creative) return availableCache;
            long now = tile.getWorld().getTotalWorldTime();
            if (now - availableCacheTick < 5) return availableCache;
        }

        long now = tile.getWorld().getTotalWorldTime();
        List<IAEItemStack> list = new ArrayList<>();
        // 普通模式空白名单 = 不暴露任何物品;
        // 创造模式空白名单 = 暴露全部已学知识(全量动态列表仅创造模式开放,避免普通模式卡顿)
        if (!creative && !tile.isWhitelistActive()) {
            availableCache = list;
            availableCacheValid = true;
            availableCacheTick = now;
            return list;
        }

        Set<ItemDescriptor> added = new HashSet<>();
        if (creative && !tile.isWhitelistActive()) {
            // 创造模式 + 空白名单: 复用已学知识 descriptor 缓存全量暴露,
            // 避免每次重建对每条知识重新分配 ItemDescriptor/解析 ItemStack
            IItemStorageChannel channel = (IItemStorageChannel) getChannel();
            for (ItemDescriptor desc : getKnownSet(provider)) {
                addCreativeEntry(list, desc, channel);
            }
        } else {
            // 将已学知识转换为 HashSet,用 O(1) 判断白名单物品是否已学.
            // 全量反序列化知识列表开销大,缓存后仅由知识变更事件驱动重建
            Set<ItemDescriptor> knownSet = getKnownSet(provider);

            // 只遍历白名单,不再遍历全部已学物品
            IItemStorageChannel channel = creative ? (IItemStorageChannel) getChannel() : null;
            for (ItemStack whitelistItem : tile.getWhitelist()) {
                if (whitelistItem.isEmpty()) continue;
                ItemDescriptor desc = new ItemDescriptor(whitelistItem);
                if (!added.add(desc)) continue; // 相同标记去重(防御历史遗留数据)
                if (!knownSet.contains(desc)) continue;

                if (creative) {
                    addCreativeEntry(list, desc, channel);
                    continue;
                }

                long itemEmc = getCachedEmcValue(desc, whitelistItem);
                if (itemEmc <= 0) continue;
                BigInteger itemEmcBI = BigInteger.valueOf(itemEmc);
                BigInteger maxCount = balance.divide(itemEmcBI);
                if (maxCount.signum() <= 0) continue;

                IAEItemStack ae = AEItemStack.fromItemStack(whitelistItem);
                if (ae == null) continue;
                ae.setStackSize(maxCount.min(BigInteger.valueOf(MAX_TERMINAL_STACK)).longValue());
                list.add(ae);
            }
        }
        availableCache = list;
        availableCacheValid = true;
        availableCacheTick = now;
        return list;
    }

    /**
     * 创造模式条目: 显示数量 = EMC 上限/物品 EMC, 封顶 4.6E18
     * (与创造 ME 元件标记量级一致,同时避免 AE2 合并计数时 long 溢出).
     * 复用 descriptor 缓存的 AE 模板,避免重复解析 ItemStack.
     */
    private void addCreativeEntry(@Nonnull List<IAEItemStack> list, @Nonnull ItemDescriptor desc,
                                  @Nonnull IItemStorageChannel channel) {
        long itemEmc = getCachedEmcValue(desc, null);
        if (itemEmc <= 0) return;
        long count = Math.min(Long.MAX_VALUE / itemEmc, MAX_TERMINAL_STACK);
        IAEItemStack template = desc.getAETemplate(channel);
        if (template == null) return;
        IAEItemStack ae = template.copy();
        ae.setStackSize(count);
        list.add(ae);
    }

    /**
     * 已学知识集合(缓存). 全量反序列化知识列表开销大,
     * 仅由 invalidateAvailableCache(知识变更/换绑/EMC remap)驱动重建.
     */
    @Nonnull
    private Set<ItemDescriptor> getKnownSet(@Nonnull Object provider) {
        Set<ItemDescriptor> knownSet = knownSetCache;
        if (knownSet == null) {
            knownSet = new HashSet<>();
            for (ItemStack knowledge : ProjectEHelper.getKnowledge(provider)) {
                if (!knowledge.isEmpty()) {
                    knownSet.add(new ItemDescriptor(knowledge));
                }
            }
            knownSetCache = knownSet;
        }
        return knownSet;
    }

    private long getCachedEmcValue(@Nonnull ItemStack stack) {
        return getCachedEmcValue(new ItemDescriptor(stack), stack);
    }

    /**
     * 物品 EMC 值缓存查询. stack 为空时由 descriptor 重建(count=1).
     */
    private long getCachedEmcValue(@Nonnull ItemDescriptor key, @javax.annotation.Nullable ItemStack stack) {
        Long cached = emcValueCache.get(key);
        if (cached != null) return cached;
        long value = ProjectEHelper.getEmcValue(stack != null ? stack : key.toItemStack());
        emcValueCache.put(key, value);
        return value;
    }

    /**
     * 通过 IStorageGrid.postAlterationOfStoredItems 上报提取产生的负变化.
     * AE2-UEL 中网格不会向 cell handler 注册监听器,这是规范的变更上报入口.
     */
    private void postAlterationToNetwork(@Nonnull IAEItemStack extracted, @Nonnull IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = tile.getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid =
                    grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            IAEItemStack delta = extracted.copy();
            delta.setStackSize(-extracted.getStackSize());
            storageGrid.postAlterationOfStoredItems(getChannel(),
                    java.util.Collections.singletonList(delta), src);
        } catch (appeng.me.GridAccessException e) {
            // grid 尚未就绪,忽略本次上报
        }
    }
}
