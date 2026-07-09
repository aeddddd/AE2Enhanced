package com.github.aeddddd.ae2enhanced.assembly;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.block.AssemblyControllerBlock;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleCraftingHelper;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.util.MathUtils;

/**
 * 负责装配枢纽的批量合成执行、黑洞事件、产物缓冲与 Mixin 缓存。
 */
public class AssemblyCraftingProcessor {

    private static final int MAX_PENDING_OUTPUTS = 4096;

    private final AssemblyControllerBlockEntity controller;
    private final AssemblyUpgradeManager upgradeManager;
    private final AssemblyPatternManager patternManager;

    private final List<Integer> jobTimers = new ArrayList<>();
    private final List<GenericStack> pendingOutputs = new ArrayList<>();
    private final Map<String, Integer> blackHoleBuffer = new HashMap<>();
    private int blackHoleTick = 0;
    private boolean batchBusy = false;

    @Nullable
    private IActionSource currentActionSource = null;

    /**
     * 缓存样板是否为纯虚拟合成（无剩余物品）。用于 Mixin 在批量处理前选择虚拟/真实轨道。
     */
    private final Map<IPatternDetails, Boolean> patternVirtualCache = new HashMap<>();

    /**
     * 真实合成 batch 信息缓存：配方、催化剂槽位、槽位物品模板。
     */
    private final Map<IPatternDetails, AssemblyControllerBlockEntity.PatternBatchInfo> patternBatchInfoCache = new HashMap<>();

    public AssemblyCraftingProcessor(AssemblyControllerBlockEntity controller,
            AssemblyUpgradeManager upgradeManager,
            AssemblyPatternManager patternManager) {
        this.controller = controller;
        this.upgradeManager = upgradeManager;
        this.patternManager = patternManager;
    }

    /**
     * 设置当前执行样板的动作来源。由 Mixin 在批量处理前设置，确保 AE2 网络操作归因正确。
     */
    public void setCurrentActionSource(@Nullable IActionSource source) {
        this.currentActionSource = source;
    }

    /**
     * 获取实际应使用的动作来源。优先使用 Mixin 设置的临时来源，否则回退到机器源。
     */
    public IActionSource getEffectiveActionSource() {
        return currentActionSource != null ? currentActionSource : controller.getActionSource();
    }

    /**
     * 当前 tick 是否还能接受新的 batch。batchBusy 会在每个 tick 的服务器端刷新。
     */
    public boolean canBatch() {
        return !batchBusy;
    }

    /**
     * 标记 batch 忙碌状态。由 Mixin 在批量处理前后调用。
     */
    public void setBatchBusy(boolean busy) {
        this.batchBusy = busy;
    }

    public void resetBatchCooldown() {
        this.batchBusy = true;
    }

    /**
     * 供 Mixin 调用：检查指定样板是否已被缓存为纯虚拟合成(无剩余物品或加工样板)。
     */
    public boolean isVirtualPattern(IPatternDetails details) {
        Boolean cached = patternVirtualCache.get(details);
        return cached != null && cached;
    }

    /**
     * 供 Mixin 调用：检查 pendingOutputs 是否还能接受指定数量的 stack。
     */
    public boolean canAcceptRealBatch(int stackCount) {
        return pendingOutputs.size() + stackCount <= MAX_PENDING_OUTPUTS;
    }

    /**
     * 供 Mixin 调用：安全地将产物加入 pendingOutputs。
     */
    public void addPendingOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        GenericStack gs = GenericStack.fromItemStack(stack);
        if (gs != null && gs.amount() > 0) {
            addPendingOutput(gs);
        }
    }

    /**
     * 安全地将 GenericStack 产物加入 pendingOutputs。
     */
    public void addPendingOutput(GenericStack stack) {
        if (stack == null || stack.amount() <= 0) {
            return;
        }
        if (pendingOutputs.size() >= MAX_PENDING_OUTPUTS) {
            AE2Enhanced.LOGGER.error("[AE2E] pendingOutputs overflow, dropping {}", stack);
            return;
        }
        pendingOutputs.add(stack);
    }

    /**
     * 供 Mixin 调用：获取或创建 PatternBatchInfo(含催化剂识别)。
     * 首次调用时从 CPU 本地库存 SIMULATE 提取 1 份原料构造 CraftingInput，
     * 执行 getRemainingItems() 识别催化剂槽位。
     */
    public AssemblyControllerBlockEntity.PatternBatchInfo getPatternBatchInfo(IPatternDetails details, ListCraftingInventory meInv,
            IActionSource source) {
        AssemblyControllerBlockEntity.PatternBatchInfo cached = patternBatchInfoCache.get(details);
        if (cached != null) {
            return cached;
        }

        IPatternDetails.IInput[] inputs = details.getInputs();
        if (inputs == null) {
            return null;
        }

        AssemblyControllerBlockEntity.PatternBatchInfo info = new AssemblyControllerBlockEntity.PatternBatchInfo();
        info.slotTemplates = new AEKey[inputs.length];

        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

        // SIMULATE 提取 1 份原料填充 CraftingInput
        for (int i = 0; i < inputs.length && i < 9; i++) {
            if (inputs[i] == null) {
                continue;
            }
            GenericStack[] possible = inputs[i].getPossibleInputs();
            if (possible.length == 0) {
                continue;
            }
            AEKey key = possible[0].what();
            long extracted = meInv.extract(key, 1, Actionable.SIMULATE);
            if (extracted >= 1) {
                info.slotTemplates[i] = key;
                if (key instanceof AEItemKey itemKey) {
                    items.set(i, itemKey.toStack());
                }
            }
        }

        Level level = controller.getLevel();
        if (level == null) {
            patternVirtualCache.put(details, true);
            patternBatchInfoCache.put(details, info);
            return info;
        }

        TransientCraftingContainer container = new TransientCraftingContainer(null, 3, 3);
        for (int i = 0; i < items.size() && i < 9; i++) {
            container.setItem(i, items.get(i));
        }
        Optional<CraftingRecipe> optional = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, container, level);
        if (optional.isEmpty()) {
            patternVirtualCache.put(details, true);
            patternBatchInfoCache.put(details, info); // 缓存 null recipe 避免重复查找
            return info;
        }
        info.recipe = optional.get();

        NonNullList<ItemStack> remaining = info.recipe.getRemainingItems(container);
        patternVirtualCache.put(details, remaining.stream().allMatch(ItemStack::isEmpty));
        info.catalystSlots = new BitSet(inputs.length);
        info.transformSlots = new BitSet(inputs.length);
        for (int i = 0; i < items.size(); i++) {
            ItemStack inputStack = items.get(i);
            ItemStack rem = i < remaining.size() ? remaining.get(i) : ItemStack.EMPTY;
            if (rem.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItem(inputStack, rem)) {
                if (areNbtEquivalent(inputStack.getTag(), rem.getTag())) {
                    info.catalystSlots.set(i);
                } else if (!inputStack.isDamageableItem()) {
                    // 不可损坏物品但 NBT 有差异：视为催化剂
                    info.catalystSlots.set(i);
                } else if (inputStack.getItem().hasCraftingRemainingItem(inputStack)
                        && ItemStack.isSameItemSameTags(inputStack.getCraftingRemainingItem(), rem)) {
                    info.catalystSlots.set(i);
                } else {
                    info.transformSlots.set(i);
                }
            }
        }

        patternBatchInfoCache.put(details, info);
        return info;
    }

    /**
     * 宽松比较两个 NBT：null 与空 tag 视为等价。
     */
    private static boolean areNbtEquivalent(@Nullable CompoundTag a, @Nullable CompoundTag b) {
        if (a == b) {
            return true;
        }
        if (a == null) {
            return b == null || b.isEmpty();
        }
        if (b == null) {
            return a.isEmpty();
        }
        return a.equals(b);
    }

    public int getJobCount() {
        return jobTimers.size();
    }

    public void tickJobTimers() {
        List<Integer> nextTimers = new ArrayList<>();
        for (int ticks : jobTimers) {
            int next = ticks - 1;
            if (next > 0) {
                nextTimers.add(next);
            }
        }
        jobTimers.clear();
        jobTimers.addAll(nextTimers);

        // 每 tick 重置 batchBusy，允许下一 tick 继续接收 pushPattern
        this.batchBusy = false;
    }

    public void tickBlackHole() {
        Level level = controller.getLevel();
        if (level == null || level.isClientSide() || !controller.isFormed()) {
            return;
        }
        blackHoleTick++;
        if (blackHoleTick % 5 != 0) {
            return;
        }
        BlockState state = level.getBlockState(controller.getBlockPos());
        Direction facing = state.getValue(AssemblyControllerBlock.FACING);
        BlockPos center = AssemblyStructure.getOriginFromController(controller.getBlockPos(), facing);
        BlockPos outputPos = center.above();

        BlackHoleCraftingHelper.killLivingEntities(level, center);
        BlackHoleCraftingHelper.suckItems(level, center);

        AABB craftBox = new AABB(
                center.getX() - 1, center.getY() - 1, center.getZ() - 1,
                center.getX() + 1, center.getY() + 1, center.getZ() + 1);
        var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, craftBox);
        Map<String, Integer> preTypes = new HashMap<>();
        for (var entity : items) {
            String key = BlackHoleRecipe.keyOf(entity.getItem());
            preTypes.merge(key, entity.getItem().getCount(), Integer::sum);
        }

        boolean crafted = !preTypes.isEmpty() && BlackHoleCraftingHelper.tryCraft(level, center, outputPos, true);
        if (crafted) {
            blackHoleBuffer.clear();
        } else if (!preTypes.isEmpty()) {
            for (Map.Entry<String, Integer> entry : preTypes.entrySet()) {
                blackHoleBuffer.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            if (blackHoleBuffer.size() > 5) {
                BlackHoleCraftingHelper.explode(level, center);
                blackHoleBuffer.clear();
                if (AE2EnhancedConfig.COMMON.debugMode.get()) {
                    AE2Enhanced.LOGGER.info("[AE2E] 黑洞过载爆炸于 {}", center);
                }
            }
        }
        controller.setChanged();
    }

    public void tryInjectPendingOutputs() {
        if (pendingOutputs.isEmpty()) {
            return;
        }
        IManagedGridNode targetNode = controller.resolveNode(null);
        if (targetNode == null || targetNode.getGrid() == null) {
            return;
        }
        IStorageService storageService = targetNode.getGrid().getStorageService();
        if (storageService == null) {
            return;
        }
        MEStorage storage = storageService.getInventory();
        var source = getEffectiveActionSource();

        Map<AEKey, Long> merged = new HashMap<>();
        for (GenericStack stack : pendingOutputs) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            merged.merge(stack.what(), stack.amount(), Long::sum);
        }
        pendingOutputs.clear();

        List<GenericStack> leftovers = new ArrayList<>();
        for (Map.Entry<AEKey, Long> entry : merged.entrySet()) {
            AEKey key = entry.getKey();
            long count = entry.getValue();
            while (count > 0) {
                long remainder = storage.insert(key, count, Actionable.MODULATE, source);
                if (remainder >= count) {
                    leftovers.add(new GenericStack(key, count));
                    break;
                }
                count = remainder;
            }
        }

        if (pendingOutputs.size() + leftovers.size() > MAX_PENDING_OUTPUTS) {
            AE2Enhanced.LOGGER.error("[AE2E] pendingOutputs overflow in AssemblyController at {}, dropping {}",
                    controller.getBlockPos(), pendingOutputs.size() + leftovers.size() - MAX_PENDING_OUTPUTS);
            while (pendingOutputs.size() + leftovers.size() > MAX_PENDING_OUTPUTS) {
                if (!leftovers.isEmpty()) {
                    leftovers.remove(leftovers.size() - 1);
                } else {
                    pendingOutputs.remove(pendingOutputs.size() - 1);
                }
            }
        }
        pendingOutputs.addAll(leftovers);
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return pushPattern(pattern, inputs, null);
    }

    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, @Nullable IManagedGridNode node) {
        return pushPatternBatch(pattern, inputs, node, 1);
    }

    /**
     * 批量执行样板任务，一次性处理 {@code batchSize} 个副本。
     * <p>作为 AE2 调用 pushPattern 时的回退处理路径，负责将产物与剩余物加入缓冲，
     * 并在可能的情况下将催化剂直接返回网络，避免催化剂被错误地延迟注入。</p>
     *
     * @param pattern   原始样板（未缩放）
     * @param inputs    单副本输入
     * @param node      优先使用的网络节点（可为 null）
     * @param batchSize 要批量处理的副本数量
     * @return 是否成功执行
     */
    public boolean pushPatternBatch(IPatternDetails pattern, KeyCounter[] inputs, @Nullable IManagedGridNode node, long batchSize) {
        Level level = controller.getLevel();
        if (level == null || level.isClientSide() || !controller.isFormed() || batchSize <= 0) {
            return false;
        }

        long cap = upgradeManager.getParallelCap();
        int intCap = cap >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cap;
        if (jobTimers.size() >= intCap || batchBusy) {
            return false;
        }

        // 确保至少能连上网络，用于后续注入产物与返还催化剂
        IManagedGridNode targetNode = controller.resolveNode(node);
        if (targetNode == null || targetNode.getGrid() == null) {
            return false;
        }
        IStorageService storageService = targetNode.getGrid().getStorageService();
        MEStorage storage = storageService != null ? storageService.getInventory() : null;
        IActionSource source = getEffectiveActionSource();

        IPatternDetails.IInput[] patternInputs = pattern.getInputs();
        if (patternInputs == null) {
            patternInputs = new IPatternDetails.IInput[0];
        }

        // 估算产物与剩余物堆叠数，防止 pendingOutputs 溢出
        int estimatedStacks = 0;
        for (GenericStack output : pattern.getOutputs()) {
            if (output != null && output.amount() > 0) {
                estimatedStacks++;
            }
        }
        for (int i = 0; i < inputs.length && i < patternInputs.length; i++) {
            IPatternDetails.IInput input = patternInputs[i];
            if (input == null) {
                continue;
            }
            for (var entry : inputs[i]) {
                AEKey remaining = input.getRemainingKey(entry.getKey());
                if (remaining != null) {
                    estimatedStacks++;
                }
            }
        }
        if (!canAcceptRealBatch(estimatedStacks)) {
            return false;
        }

        // 将产物按 batchSize 倍率加入缓冲
        for (GenericStack output : pattern.getOutputs()) {
            if (output != null && output.amount() > 0) {
                long amount = MathUtils.safeMultiply(output.amount(), batchSize);
                if (amount > 0) {
                    addPendingOutput(new GenericStack(output.what(), amount));
                }
            }
        }

        // 处理剩余物：催化剂立即返回网络，其它剩余物加入缓冲
        for (int i = 0; i < inputs.length && i < patternInputs.length; i++) {
            IPatternDetails.IInput input = patternInputs[i];
            if (input == null) {
                continue;
            }
            for (var entry : inputs[i]) {
                AEKey key = entry.getKey();
                AEKey remaining = input.getRemainingKey(key);
                if (remaining == null) {
                    continue;
                }
                long perCraftRemaining = 1;
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length > 0 && possible[0].amount() > 0) {
                    perCraftRemaining = entry.getLongValue() / possible[0].amount();
                }
                long remainingAmount = MathUtils.safeMultiply(perCraftRemaining, batchSize);
                if (remainingAmount <= 0) {
                    continue;
                }
                if (remaining.equals(key) && storage != null) {
                    // 催化剂：尝试直接返还网络，剩余部分回退到缓冲
                    long notInserted = storage.insert(remaining, remainingAmount, Actionable.MODULATE, source);
                    if (notInserted > 0) {
                        addPendingOutput(new GenericStack(remaining, notInserted));
                    }
                } else {
                    addPendingOutput(new GenericStack(remaining, remainingAmount));
                }
            }
        }

        jobTimers.add(upgradeManager.getCraftingTicks());
        batchBusy = true;
        return true;
    }

    public boolean isBusy() {
        long cap = upgradeManager.getParallelCap();
        int intCap = cap >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cap;
        return jobTimers.size() >= intCap || batchBusy;
    }

    public void clearState() {
        pendingOutputs.clear();
        jobTimers.clear();
        patternVirtualCache.clear();
        patternBatchInfoCache.clear();
    }

    public void load(CompoundTag data) {
        if (data.contains("pendingOutputs", ListTag.TAG_LIST)) {
            pendingOutputs.clear();
            ListTag list = data.getList("pendingOutputs", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                GenericStack stack = GenericStack.readTag(list.getCompound(i));
                if (stack != null && stack.amount() > 0) {
                    pendingOutputs.add(stack);
                }
            }
        }
        if (data.contains("blackHoleBuffer", ListTag.TAG_LIST)) {
            blackHoleBuffer.clear();
            ListTag list = data.getList("blackHoleBuffer", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                blackHoleBuffer.put(entryTag.getString("key"), entryTag.getInt("count"));
            }
        }
    }

    public void save(CompoundTag data) {
        ListTag list = new ListTag();
        for (GenericStack stack : pendingOutputs) {
            if (stack != null && stack.amount() > 0) {
                list.add(GenericStack.writeTag(stack));
            }
        }
        data.put("pendingOutputs", list);

        ListTag bufferList = new ListTag();
        for (Map.Entry<String, Integer> entry : blackHoleBuffer.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("key", entry.getKey());
            entryTag.putInt("count", entry.getValue());
            bufferList.add(entryTag);
        }
        data.put("blackHoleBuffer", bufferList);
    }
}
