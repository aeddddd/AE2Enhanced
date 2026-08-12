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
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
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

    public static final int ENERGY_CAPACITY = Integer.MAX_VALUE;
    public static final int INPUT_TYPES = 27;
    public static final int OUTPUT_TYPES = 9;
    public static final int CARD_SLOTS = 5;
    /** 卡片槽 0：虚拟并行卡；1-4：加速卡 */
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

    private int energy = 0;
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
            int accepted = Math.min(maxReceive, ENERGY_CAPACITY - energy);
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
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return ENERGY_CAPACITY;
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

        // 推进并结算任务：能耗随 tick 均摊支付,付不起则挂起（不销毁产物）
        if (!paused && !jobs.isEmpty()) {
            int tickBudget = AE2EnhancedConfig.chamber.maxEnergyPerTick;
            Iterator<Job> it = jobs.iterator();
            while (it.hasNext()) {
                Job job = it.next();
                long cost = job.costThisTick();
                if (cost <= 0 || (energy >= cost && tickBudget >= cost)) {
                    energy -= (int) cost;
                    tickBudget -= (int) cost;
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

    // ---- 配方过滤已移除：冲突由固定优先级解决（见 ChamberRecipeIndex） ----

    // ---- 任务调度 ----

    public long getParallelChannels() {
        ItemStack card = cardSlots.getStackInSlot(SLOT_PARALLEL);
        if (!card.isEmpty() && card.getItem() instanceof ItemVirtualParallelCard) {
            return ItemVirtualParallelCard.getParallel(card);
        }
        return 1;
    }

    public int getSpeedCards() {
        int count = 0;
        for (int i = 1; i < CARD_SLOTS; i++) {
            ItemStack stack = cardSlots.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemUpgradeCard
                    && stack.getMetadata() == ItemUpgradeCard.META_SPEED) {
                count++;
            }
        }
        return count;
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
     * 扫描输入缓存,为可执行配方启动聚合任务.
     * 批次上限 = 剩余通道 与 材料 与 当前能量可负担总量（energyPerBatch × batches ≤ energy）,
     * 能耗不再启动时预付,而是随任务进度逐 tick 均摊支付.
     */
    private void startJobs() {
        long free = getParallelChannels() - getUsedChannels();
        if (free <= 0) {
            return;
        }
        int perBatch = AE2EnhancedConfig.chamber.energyPerBatch;

        Map<String, Long> available = new HashMap<>();
        for (LongItemStore.Entry entry : inputStore.getEntries()) {
            available.put(LongItemStore.keyOf(entry.getTemplate()), entry.getCount());
        }

        int speedDivisor = 1 + getSpeedCards();

        for (LongItemStore.Entry entry : new ArrayList<>(inputStore.getEntries())) {
            if (free <= 0) {
                break;
            }
            String key = LongItemStore.keyOf(entry.getTemplate());
            // 每个输入 key 只启动优先级最高且可执行的一条配方（ExtendedAE：无逐配方开关,
            // 冲突由注册顺序决定的固定优先级解决）
            for (ChamberRecipe recipe : ChamberRecipeIndex.recipesForInput(key, entry.getTemplate())) {
                if (free <= 0) {
                    return;
                }
                if (hasActiveJob(recipe)) {
                    continue;
                }
                long batches = recipe.maxBatches(available);
                batches = Math.min(batches, free);
                batches = Math.min(batches, (long) (energy / perBatch));
                if (batches <= 0) {
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
                free -= batches;

                long requiredTime = Math.max(1, recipe.getTimeTicks() / speedDivisor);
                jobs.add(new Job(recipe, batches, requiredTime, (long) perBatch * batches));
                inputsDirty = true;
                break;
            }
        }
        markDirty();
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

    public int getEnergy() {
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
        energy = compound.getInteger("Energy");
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
        compound.setInteger("Energy", energy);
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

        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    }
}
