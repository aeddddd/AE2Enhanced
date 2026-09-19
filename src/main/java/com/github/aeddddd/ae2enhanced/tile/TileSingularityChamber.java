package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.me.GridAccessException;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex;
import com.github.aeddddd.ae2enhanced.chamber.LongItemStore;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 奇点处理仓 — 后期单方块高并行处理机器.
 *
 * <p><b>输入</b>：管道插入或 GUI 手动倒入,进入 Long 级缓存槽；GUI 可点击取回.</p>
 * <p><b>并行</b>：安装虚拟并行卡,卡值即并行通道数；同一配方聚合为单任务,
 * 任务批次数即占用通道数.</p>
 * <p><b>耗时</b>：处理时间 + 并行通道,基准耗时沿用 AE2 原版,加速卡缩短时间.</p>
 * <p><b>能量</b>：独立 FE 缓冲（上限 int）,任务启动预付能耗,单 tick 启动能耗有上限.</p>
 * <p><b>输出</b>：优先注入 ME 网络,网络不可用时进入输出缓冲,持续重试网络并向相邻容器弹出;
 * 输出缓冲无空间时任务挂起,不会销毁产物.</p>
 * <p><b>配方过滤</b>：GUI 配方页可逐条禁用配方（默认全开）.</p>
 * <p><b>红石</b>：支持 忽略/高电平运行/低电平运行 三种模式.</p>
 */
public class TileSingularityChamber extends TileAENetworkBase implements ITickable, IActionHost {

    public static final int INPUT_TYPES = 27;
    public static final int OUTPUT_TYPES = 9;
    public static final int CARD_SLOTS = 5;
    /** 活动任务上限 = GUI 任务槽位数,保证每个任务都有可见显示槽位 */
    public static final int MAX_JOBS = 9;
    /** 卡片槽 0：虚拟并行卡；1-4：升级卡（加速/容量,接受 AE2 生态 IUpgradeModule） */
    public static final int SLOT_PARALLEL = 0;

    private static final int SCAN_INTERVAL = 5;
    private static final int FLUSH_INTERVAL = 10;

    /** 红石模式 */
    public enum RedstoneMode {
        IGNORE, HIGH, LOW;

        public RedstoneMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private long energy = 0;
    private final LongItemStore inputStore = new LongItemStore(INPUT_TYPES);
    private final LongItemStore outputStore = new LongItemStore(OUTPUT_TYPES);
    private final ItemStackHandler cardSlots = new MarkDirtyItemHandler(CARD_SLOTS);
    private final List<Job> jobs = new ArrayList<>();
    /** 存档中尚未解析为配方对象的任务（等配方索引/CT 就绪后懒恢复） */
    private final NBTTagList pendingJobs = new NBTTagList();
    /** 输入/能量/任务事件标记：无变化时跳过配方扫描（ExtendedAE dirty/stuck 思路） */
    private boolean inputsDirty = true;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private MachineSource actionSource;
    private boolean flagsApplied = false;

    // ---- 能量接口：仅接收 ----

    private final IEnergyStorage energyWrapper = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            long room = getMaxEnergy() - energy;
            int accepted = (int) Math.min(maxReceive, Math.min(Integer.MAX_VALUE, Math.max(0, room)));
            if (!simulate && accepted > 0) {
                energy += accepted;
                inputsDirty = true;
                markDirty();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energy);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, getMaxEnergy());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    /** 管道输入口：仅允许插入到输入缓存；GUI 取回走动作包,不对管道开放抽取 */
    private final IItemHandler inputWrapper = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            // 插入过滤：只接受能参与配方的物品
            if (!ChamberRecipeIndex.isValidInput(normalizeInput(stack))) {
                return stack;
            }
            if (simulate) {
                String key = LongItemStore.keyOf(normalizeInput(stack));
                boolean known = inputStore.getCount(key) > 0;
                if (!known && inputStore.getTypeCount() >= INPUT_TYPES) {
                    return stack;
                }
                return ItemStack.EMPTY;
            }
            long remaining = insertInput(stack, stack.getCount());
            if (remaining <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack rem = stack.copy();
            rem.setCount((int) remaining);
            return rem;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    };

    // ---- 任务 ----

    private static class Job {
        final ChamberRecipe recipe;
        final long batches;
        final long requiredTime;
        final long totalCost;
        long progress;
        long paid;

        Job(ChamberRecipe recipe, long batches, long requiredTime, long totalCost) {
            this.recipe = recipe;
            this.batches = batches;
            this.requiredTime = Math.max(1, requiredTime);
            this.totalCost = totalCost;
        }

        /** 本 tick 应付能耗：剩余费用按剩余 tick 均摊（向上取整）,保证完成时恰好付清. */
        long costThisTick() {
            long remainingTicks = requiredTime - progress;
            long remainingCost = totalCost - paid;
            if (remainingTicks <= 0 || remainingCost <= 0) {
                return 0;
            }
            return (remainingCost + remainingTicks - 1) / remainingTicks;
        }
    }

    // ---- 基类抽象实现 ----

    @Override
    protected String getProxyName() {
        return "singularity_chamber";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.SINGULARITY_CHAMBER);
    }

    @Override
    public void disassemble() {
        // 单方块机器,无结构可拆解
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public void securityBreak() {
        // 单方块机器,无安全破坏逻辑
    }

    @Override
    public appeng.api.util.AECableType getCableConnectionType(@Nonnull appeng.api.util.AEPartLocation dir) {
        return appeng.api.util.AECableType.SMART;
    }

    // ---- 主循环 ----

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (needsReady()) {
            getProxy().onReady();
            clearNeedsReady();
        }
        if (!flagsApplied) {
            flagsApplied = true;
            if (AE2EnhancedConfig.chamber.requireChannel) {
                getProxy().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            }
        }

        ChamberRecipeIndex.ensureBuilt();
        resolvePendingJobs();

        boolean paused = isPaused();

        // 推进并结算任务：能耗随 tick 均摊支付（缓冲不足时从 ME 网络拉取）,付不起则挂起
        if (!paused && !jobs.isEmpty()) {
            Iterator<Job> it = jobs.iterator();
            while (it.hasNext()) {
                Job job = it.next();
                long cost = job.costThisTick();
                if (tryPay(cost)) {
                    job.paid += cost;
                    job.progress++;
                }
                // 付不起本 tick 的能耗：任务原地挂起
                if (job.progress >= job.requiredTime && canAcceptOutput(job)) {
                    completeJob(job);
                    it.remove();
                    inputsDirty = true;
                    markDirty();
                }
            }
        }

        if (!paused && inputsDirty && world.getTotalWorldTime() % SCAN_INTERVAL == 0) {
            inputsDirty = false;
            startJobs();
        }

        // 输出冲刷不受红石暂停影响,避免缓冲堆积
        if (world.getTotalWorldTime() % FLUSH_INTERVAL == 0 && !outputStore.isEmpty()) {
            flushOutputs();
        }
    }

    // ---- 红石 ----

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void cycleRedstoneMode() {
        redstoneMode = redstoneMode.next();
        markDirty();
    }

    public boolean isPaused() {
        if (redstoneMode == RedstoneMode.IGNORE || world == null) {
            return false;
        }
        boolean powered = world.isBlockPowered(pos);
        return redstoneMode == RedstoneMode.HIGH ? !powered : powered;
    }

    // ---- 配方过滤已移除：冲突由固定优先级解决（见 ChamberRecipeIndex 与 startJobs） ----

    // ---- 任务调度 ----

    /**
     * 升级卡判定：走 AE2 升级模块生态（IUpgradeModule）,
     * 同时接受 AE2 原生加速/容量卡与本 mod 的升级卡.
     */
    public static boolean isUpgradeCard(ItemStack stack, appeng.api.config.Upgrades type) {
        return !stack.isEmpty()
                && stack.getItem() instanceof appeng.api.implementations.items.IUpgradeModule
                && ((appeng.api.implementations.items.IUpgradeModule) stack.getItem()).getType(stack) == type;
    }

    /**
     * 并行通道数 = 虚拟并行卡值 × 4^容量卡数（饱和至 Long.MAX_VALUE）.
     */
    public long getParallelChannels() {
        long base = 1;
        ItemStack card = cardSlots.getStackInSlot(SLOT_PARALLEL);
        if (!card.isEmpty() && card.getItem() instanceof ItemVirtualParallelCard) {
            base = ItemVirtualParallelCard.getParallel(card);
        }
        return saturatingMul(base, saturatingPow(4, getCapacityCards()));
    }

    public int getSpeedCards() {
        int count = 0;
        for (int i = 1; i < CARD_SLOTS; i++) {
            if (isUpgradeCard(cardSlots.getStackInSlot(i), appeng.api.config.Upgrades.SPEED)) {
                count++;
            }
        }
        return count;
    }

    public int getCapacityCards() {
        int count = 0;
        for (int i = 1; i < CARD_SLOTS; i++) {
            if (isUpgradeCard(cardSlots.getStackInSlot(i), appeng.api.config.Upgrades.CAPACITY)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 任务耗时 = 配方基础时间 × 8^容量卡 / 4^加速卡.
     * 容量卡以耗时换通道（变相摊薄每 tick 能耗）,加速卡以每 tick 能耗换速度.
     */
    private long computeJobTime(int baseTicks) {
        long time = saturatingMul(baseTicks, saturatingPow(8, getCapacityCards()));
        long divisor = saturatingPow(4, getSpeedCards());
        return Math.max(1, time / divisor);
    }

    private static long saturatingPow(long base, int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) {
            result = saturatingMul(result, base);
        }
        return result;
    }

    private static long saturatingMul(long a, long b) {
        if (a <= 0 || b <= 0) {
            return a <= 0 && b <= 0 ? a : Math.max(a, b);
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    /**
     * 能量缓冲上限：2.1G FE（int 上限）.
     * 超出缓冲的能耗在支付时直接从 ME 网络按 long 规模拉取（见 {@link #tryPay}）.
     */
    public long getMaxEnergy() {
        return Integer.MAX_VALUE;
    }

    /**
     * 支付一笔能耗：优先扣内部缓冲,不足部分从 ME 网络实时拉取补足（long 规模）.
     * 缓冲 + 网络仍不足则返回 false（任务挂起）,已拉取的能量留在缓冲内供下次使用.
     */
    private boolean tryPay(long cost) {
        if (cost <= 0) {
            return true;
        }
        long missing = cost - energy;
        if (missing > 0) {
            try {
                appeng.api.networking.energy.IEnergyGrid grid = getProxy().getEnergy();
                // 1 AE = 2 FE;上取整避免换算截断导致差 1 FE 永远付不起
                double aeNeeded = Math.ceil(
                        appeng.api.config.PowerUnits.RF.convertTo(appeng.api.config.PowerUnits.AE, missing));
                double pulled = grid.extractAEPower(aeNeeded, Actionable.MODULATE,
                        appeng.api.config.PowerMultiplier.CONFIG);
                energy += (long) appeng.api.config.PowerUnits.AE.convertTo(
                        appeng.api.config.PowerUnits.RF, pulled);
            } catch (GridAccessException | IllegalStateException ignored) {
                // 未联网/节点未就绪：仅内部缓冲可用
            }
        }
        if (energy >= cost) {
            energy -= cost;
            return true;
        }
        return false;
    }

    public long getUsedChannels() {
        long used = 0;
        for (Job job : jobs) {
            used = safeAdd(used, job.batches);
        }
        return used;
    }

    public int getActiveJobCount() {
        return jobs.size();
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    private boolean hasActiveJob(ChamberRecipe recipe) {
        for (Job job : jobs) {
            if (job.recipe.getId().equals(recipe.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描输入缓存,为可执行配方启动任务.
     *
     * <p><b>选配方</b>：每个输入 key 只归属一条配方——优先级列表中第一条
     * "已有活动任务"或"材料可执行"的配方。高优先级配方已激活时,低优先级配方
     * 不得抢料（合并链完全优先于单步电路板配方,避免原料被误做成电路板）.</p>
     * <p><b>跨配方并行</b>：剩余通道在所有候选配方间水位均分（材料受限的配方
     * 让出余额进入下一轮）,不再由单一配方独占全部通道.</p>
     * <p><b>槽位分散</b>：任务数上限 = GUI 任务槽位数（{@link #MAX_JOBS}）,
     * 保证每个活动任务都有可见的显示槽位.</p>
     * <p>能耗不再启动时预付,而是随任务进度逐 tick 均摊支付;启动批次受
     * 当前能量可负担总量约束（energyPerBatch × batches ≤ energy）.</p>
     */
    private void startJobs() {
        long free = getParallelChannels() - getUsedChannels();
        if (free <= 0 || jobs.size() >= MAX_JOBS) {
            return;
        }
        int perBatch = AE2EnhancedConfig.chamber.energyPerBatch;
        long remaining = Math.min(free, energy / perBatch);
        if (remaining <= 0) {
            return;
        }

        Map<String, Long> available = new HashMap<>();
        for (LongItemStore.Entry entry : inputStore.getEntries()) {
            available.put(LongItemStore.keyOf(entry.getTemplate()), entry.getCount());
        }

        // 每个输入 key 选定一条配方（优先级最高且 已激活/可执行）
        List<ChamberRecipe> candidates = new ArrayList<>();
        for (LongItemStore.Entry entry : new ArrayList<>(inputStore.getEntries())) {
            String key = LongItemStore.keyOf(entry.getTemplate());
            for (ChamberRecipe recipe : ChamberRecipeIndex.recipesForInput(key, entry.getTemplate())) {
                if (hasActiveJob(recipe)) {
                    break;
                }
                if (recipe.maxBatches(available) > 0) {
                    if (!candidates.contains(recipe)) {
                        candidates.add(recipe);
                    }
                    break;
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        // 按全局优先级排序,槽位不足时高优先级配方先占位
        candidates.sort(TileSingularityChamber::compareRecipePriority);
        int slotsLeft = MAX_JOBS - jobs.size();
        if (candidates.size() > slotsLeft) {
            candidates = new ArrayList<>(candidates.subList(0, slotsLeft));
        }

        // 水位均分：每轮各配方分得 remaining/存活数,材料受限者让出余额进入下一轮
        Map<ChamberRecipe, Long> allocated = new LinkedHashMap<>();
        List<ChamberRecipe> pending = new ArrayList<>(candidates);
        while (remaining > 0 && !pending.isEmpty()) {
            long share = Math.max(1, remaining / pending.size());
            boolean progressed = false;
            for (Iterator<ChamberRecipe> it = pending.iterator(); it.hasNext() && remaining > 0; ) {
                ChamberRecipe recipe = it.next();
                long batches = Math.min(recipe.maxBatches(available), Math.min(share, remaining));
                if (batches <= 0) {
                    it.remove();
                    continue;
                }
                // 按输入组消耗：组内替代按序抽取直至满足
                for (ChamberRecipe.InputGroup group : recipe.getInputGroups()) {
                    long need = group.getCount() * batches;
                    for (String inputKey : group.getKeys()) {
                        if (need <= 0) {
                            break;
                        }
                        long got = inputStore.extract(inputKey, need);
                        available.merge(inputKey, -got, Long::sum);
                        need -= got;
                    }
                }
                allocated.merge(recipe, batches, Long::sum);
                remaining -= batches;
                progressed = true;
                if (batches < share) {
                    it.remove();
                }
            }
            if (!progressed) {
                break;
            }
        }

        for (Map.Entry<ChamberRecipe, Long> entry : allocated.entrySet()) {
            ChamberRecipe recipe = entry.getKey();
            long batches = entry.getValue();
            long requiredTime = computeJobTime(recipe.getTimeTicks());
            jobs.add(new Job(recipe, batches, requiredTime, (long) perBatch * batches));
        }
        if (!allocated.isEmpty()) {
            inputsDirty = true;
            markDirty();
        }
    }

    /**
     * 配方全局优先级比较：与 {@link ChamberRecipeIndex} 的索引顺序一致.
     */
    private static int compareRecipePriority(ChamberRecipe a, ChamberRecipe b) {
        int rankA = ChamberRecipeIndex.recipePriorityRank(a.getId());
        int rankB = ChamberRecipeIndex.recipePriorityRank(b.getId());
        return rankA != rankB ? Integer.compare(rankA, rankB) : a.getId().compareTo(b.getId());
    }

    // ---- 任务完成与输出 ----

    /**
     * 输出缓冲是否能接纳该任务的产物类型（已有同类型或类型数未满）.
     */
    private boolean canAcceptOutput(Job job) {
        ItemStack output = job.recipe.getOutput();
        if (output.isEmpty()) {
            return true;
        }
        String key = LongItemStore.keyOf(output);
        return outputStore.getCount(key) > 0 || outputStore.getTypeCount() < OUTPUT_TYPES;
    }

    private void completeJob(Job job) {
        ItemStack output = job.recipe.getOutput();
        if (output.isEmpty()) {
            return;
        }
        long total = (long) output.getCount() * job.batches;
        long remaining = injectToNetwork(output, total);
        if (remaining > 0) {
            long left = outputStore.insert(output, remaining);
            if (left > 0) {
                // canAcceptOutput 已前置校验,此处兜底不应发生；记录警告避免静默销毁
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                        "[AE2E] Chamber at {} failed to buffer {}x {}", pos, left, output.getDisplayName());
            }
        }
        // 完成特效
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, false,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    6, 0.3, 0.2, 0.3, 0.02);
            world.playSound(null, pos,
                    net.minecraft.util.SoundEvent.REGISTRY.getObject(
                            new ResourceLocation("block.beacon.power_select")),
                    SoundCategory.BLOCKS, 0.3f, 2.0f);
        }
    }

    private MachineSource getActionSource() {
        if (actionSource == null) {
            actionSource = new MachineSource(this);
        }
        return actionSource;
    }

    private long injectToNetwork(ItemStack template, long amount) {
        if (amount <= 0) {
            return 0;
        }
        try {
            IMEMonitor<IAEItemStack> inv = getProxy().getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            IAEItemStack ais = AEItemStack.fromItemStack(template);
            if (ais == null) {
                return amount;
            }
            ais.setStackSize(amount);
            IAEItemStack rem = inv.injectItems(ais, Actionable.MODULATE, getActionSource());
            return rem == null ? 0 : rem.getStackSize();
        } catch (GridAccessException | IllegalStateException e) {
            return amount;
        }
    }

    private void flushOutputs() {
        for (LongItemStore.Entry entry : new ArrayList<>(outputStore.getEntries())) {
            long count = entry.getCount();
            long remaining = injectToNetwork(entry.getTemplate(), count);
            if (remaining < count) {
                outputStore.extract(LongItemStore.keyOf(entry.getTemplate()), count - remaining);
                markDirty();
            }
        }
        if (outputStore.isEmpty()) {
            return;
        }
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (outputStore.isEmpty()) {
                return;
            }
            TileEntity neighbor = world.getTileEntity(pos.offset(facing));
            if (neighbor == null) {
                continue;
            }
            IItemHandler handler = neighbor.getCapability(
                    CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
            if (handler == null) {
                continue;
            }
            for (LongItemStore.Entry entry : new ArrayList<>(outputStore.getEntries())) {
                ItemStack template = entry.getTemplate();
                int maxStack = template.getMaxStackSize();
                long toMove = Math.min(entry.getCount(), (long) maxStack * 4);
                long moved = 0;
                while (moved < toMove) {
                    ItemStack stack = template.copy();
                    stack.setCount((int) Math.min(maxStack, toMove - moved));
                    ItemStack rem = insertAll(handler, stack);
                    moved += stack.getCount() - rem.getCount();
                    if (!rem.isEmpty()) {
                        break;
                    }
                }
                if (moved > 0) {
                    outputStore.extract(LongItemStore.keyOf(template), moved);
                    markDirty();
                }
            }
        }
    }

    private static ItemStack insertAll(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack;
        for (int i = 0; i < handler.getSlots(); i++) {
            remaining = handler.insertItem(i, remaining, false);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    // ---- 输入归一化 ----

    /**
     * 水晶种子的生长进度 NBT 对机器无意义（机器内一次性完成生长）,
     * 插入时剥离 NBT,避免部分生长的种子因 key 不同而卡死.
     */
    private static ItemStack normalizeInput(ItemStack stack) {
        Item crystalSeed = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "crystal_seed"));
        if (crystalSeed != null && stack.getItem() == crystalSeed && stack.hasTagCompound()) {
            ItemStack copy = stack.copy();
            copy.setTagCompound(null);
            return copy;
        }
        return stack;
    }

    // ---- 外部访问 ----

    public long getEnergy() {
        return energy;
    }

    public LongItemStore getInputStore() {
        return inputStore;
    }

    public LongItemStore getOutputStore() {
        return outputStore;
    }

    public ItemStackHandler getCardSlots() {
        return cardSlots;
    }

    /**
     * 倒入原料（归一化后进入缓存）,返回未接收数量.
     */
    public long insertInput(ItemStack stack, long amount) {
        // 插入过滤：只接受能参与配方的物品（管道路径在 wrapper 已过滤,此处兜底 GUI 倒入路径）
        if (!ChamberRecipeIndex.isValidInput(normalizeInput(stack))) {
            return amount;
        }
        long rem = inputStore.insert(normalizeInput(stack), amount);
        if (rem < amount) {
            // 关键：原料变化必须置脏,否则调度扫描不会触发,任务永远不会启动
            inputsDirty = true;
            markDirty();
        }
        return rem;
    }

    /**
     * 从输入缓存取回物品（GUI 动作用）,返回实际取出的物品堆.
     */
    public ItemStack withdrawInput(String key, int maxCount) {
        // 先取模板再抽取：全部取空时条目会被移除,顺序颠倒会丢失物品
        ItemStack template = inputStore.getTemplate(key);
        if (template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long taken = inputStore.extract(key, maxCount);
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        inputsDirty = true;
        markDirty();
        template.setCount((int) taken);
        return template;
    }

    /**
     * 查询输入缓存中某 key 的模板物品（不消耗）.
     */
    public ItemStack getInputTemplate(String key) {
        return inputStore.getTemplate(key);
    }

    /**
     * 从输出缓冲取回物品（GUI 动作用）,返回实际取出的物品堆.
     */
    public ItemStack withdrawOutput(String key, int maxCount) {
        ItemStack template = outputStore.getTemplate(key);
        if (template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long taken = outputStore.extract(key, maxCount);
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        markDirty();
        template.setCount((int) taken);
        return template;
    }

    /**
     * 活动任务快照（GUI 同步用）.
     */
    public List<com.github.aeddddd.ae2enhanced.network.packet.PacketChamberSync.JobView> getJobViews() {
        List<com.github.aeddddd.ae2enhanced.network.packet.PacketChamberSync.JobView> views = new ArrayList<>();
        for (Job job : jobs) {
            views.add(new com.github.aeddddd.ae2enhanced.network.packet.PacketChamberSync.JobView(
                    job.recipe.getOutput(), job.batches, job.progress, job.requiredTime));
        }
        return views;
    }

    // ---- 任务懒恢复 ----

    /**
     * 存档任务在配方索引与 CT 注册就绪后解析,避免加载时序丢弃任务.
     */
    private void resolvePendingJobs() {
        if (pendingJobs.tagCount() == 0) {
            return;
        }
        Map<String, ChamberRecipe> byId = new HashMap<>();
        for (ChamberRecipe r : ChamberRecipeIndex.allRecipes()) {
            byId.put(r.getId(), r);
        }
        for (int i = pendingJobs.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound tag = pendingJobs.getCompoundTagAt(i);
            ChamberRecipe recipe = byId.get(tag.getString("Id"));
            if (recipe != null) {
                Job job = new Job(recipe, tag.getLong("Batches"), tag.getLong("Required"), tag.getLong("Cost"));
                job.progress = tag.getLong("Progress");
                job.paid = tag.getLong("Paid");
                jobs.add(job);
                pendingJobs.removeTag(i);
            }
        }
    }

    // ---- Capability ----

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY || capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) energyWrapper;
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) inputWrapper;
        }
        return super.getCapability(capability, facing);
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        energy = Math.min(compound.getLong("Energy"), getMaxEnergy());
        inputStore.readFromNBT(compound.getTagList("Input", Constants.NBT.TAG_COMPOUND));
        outputStore.readFromNBT(compound.getTagList("Output", Constants.NBT.TAG_COMPOUND));
        cardSlots.deserializeNBT(compound.getCompoundTag("Cards"));
        jobs.clear();
        // 任务不立即解析,挂入待恢复队列（等配方索引/CT 就绪）
        NBTTagList jobList = compound.getTagList("Jobs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < jobList.tagCount(); i++) {
            pendingJobs.appendTag(jobList.getCompoundTagAt(i).copy());
        }
        redstoneMode = RedstoneMode.values()[compound.getInteger("RedstoneMode") % RedstoneMode.values().length];
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        compound.setLong("Energy", energy);
        compound.setTag("Input", inputStore.writeToNBT());
        compound.setTag("Output", outputStore.writeToNBT());
        compound.setTag("Cards", cardSlots.serializeNBT());
        NBTTagList jobList = new NBTTagList();
        for (Job job : jobs) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Id", job.recipe.getId());
            tag.setLong("Batches", job.batches);
            tag.setLong("Progress", job.progress);
            tag.setLong("Required", job.requiredTime);
            tag.setLong("Cost", job.totalCost);
            tag.setLong("Paid", job.paid);
            jobList.appendTag(tag);
        }
        for (int i = 0; i < pendingJobs.tagCount(); i++) {
            jobList.appendTag(pendingJobs.getCompoundTagAt(i).copy());
        }
        compound.setTag("Jobs", jobList);
        compound.setInteger("RedstoneMode", redstoneMode.ordinal());
        return compound;
    }

    private class MarkDirtyItemHandler extends ItemStackHandler {
        MarkDirtyItemHandler(int size) {
            super(size);
        }

        /** 卡片槽统一限 1 堆叠：并行卡按 NBT 档位计费,升级卡按槽位计数,堆叠无意义且误导 */
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    }
}
