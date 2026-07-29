package com.github.aeddddd.ae2enhanced.assembly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleCraftingHelper;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.util.MathUtils;

/**
 * 负责装配枢纽的批量合成执行、黑洞事件、产物缓冲与 Mixin 缓存.
 */
public class AssemblyCraftingProcessor {

    private final AssemblyControllerBlockEntity controller;
    private final AssemblyUpgradeManager upgradeManager;
    private final AssemblyPatternManager patternManager;

    private final List<Integer> jobTimers = new ArrayList<>();
    private final List<GenericStack> pendingOutputs = new ArrayList<>();
    private final Map<String, Integer> blackHoleBuffer = new HashMap<>();
    private int blackHoleTick = 0;
    private boolean batchBusy = false;

    /**
     * 批量冷却计数（tick）.每次批量处理后重置为 {@link AssemblyUpgradeManager#getCraftingTicks()},
     * 速度升级卡可缩短该冷却,与主分支 1.12 的节奏控制一致.
     */
    private int batchCooldown = 0;

    @Nullable
    private IActionSource currentActionSource = null;

    /**
     * 样板批量信息缓存：虚拟/真实轨道分类,基于 {@link IPatternDetails.IInput#getRemainingKey(AEKey)} 判定.
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
     * 产物缓冲上限,从配置动态读取.
     */
    private static int getMaxPendingOutputs() {
        return AE2EnhancedConfig.COMMON.assemblyMaxPendingOutputs.get();
    }

    /**
     * 设置当前执行样板的动作来源.由 Mixin 在批量处理前设置,确保 AE2 网络操作归因正确.
     */
    public void setCurrentActionSource(@Nullable IActionSource source) {
        this.currentActionSource = source;
    }

    /**
     * 获取实际应使用的动作来源.优先使用 Mixin 设置的临时来源,否则回退到机器源.
     */
    public IActionSource getEffectiveActionSource() {
        return currentActionSource != null ? currentActionSource : controller.getActionSource();
    }

    /**
     * 当前 tick 是否还能接受新的 batch.batchBusy 每个服务器 tick 刷新,
     * batchCooldown 按速度升级卡决定的周期间隔批量.
     */
    public boolean canBatch() {
        return !batchBusy && batchCooldown <= 0;
    }

    /**
     * 标记 batch 忙碌状态.由 Mixin 在批量处理前后调用.
     */
    public void setBatchBusy(boolean busy) {
        this.batchBusy = busy;
    }

    /**
     * 批量处理成功后重置冷却,冷却期间不再接受新的批量（复刻主分支速度卡节奏）.
     */
    public void resetBatchCooldown() {
        this.batchCooldown = upgradeManager.getCraftingTicks();
    }

    /**
     * 供 Mixin 调用：检查 pendingOutputs 是否还能接受指定数量的 stack.
     */
    public boolean canAcceptRealBatch(int stackCount) {
        return pendingOutputs.size() + stackCount <= getMaxPendingOutputs();
    }

    /**
     * 供 Mixin 调用：安全地将产物加入 pendingOutputs.
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
     * 安全地将 GenericStack 产物加入 pendingOutputs.
     */
    public void addPendingOutput(GenericStack stack) {
        if (stack == null || stack.amount() <= 0) {
            return;
        }
        if (pendingOutputs.size() >= getMaxPendingOutputs()) {
            AE2Enhanced.LOGGER.error("[AE2E] pendingOutputs overflow, dropping {}", stack);
            return;
        }
        pendingOutputs.add(stack);
    }

    /**
     * 供 Mixin 调用：获取或创建样板的批量信息（虚拟/真实轨道分类）.
     * <p>分类完全基于 AE2 官方 API {@link IPatternDetails.IInput#getRemainingKey(AEKey)}：
     * 任一输入槽存在剩余物（容器物、催化剂、耐久转换）即判定为真实轨道,
     * 否则（普通合成、处理样板）为虚拟轨道.不再重建 3×3 合成容器反查配方——
     * {@code getInputs()} 返回的是压缩合并输入,按索引填充几乎必然匹配失败.</p>
     */
    public AssemblyControllerBlockEntity.PatternBatchInfo getPatternBatchInfo(IPatternDetails details) {
        AssemblyControllerBlockEntity.PatternBatchInfo cached = patternBatchInfoCache.get(details);
        if (cached != null) {
            return cached;
        }

        AssemblyControllerBlockEntity.PatternBatchInfo info = new AssemblyControllerBlockEntity.PatternBatchInfo();
        IPatternDetails.IInput[] inputs = details.getInputs();
        if (inputs != null) {
            for (IPatternDetails.IInput input : inputs) {
                if (input == null) {
                    continue;
                }
                for (GenericStack possible : input.getPossibleInputs()) {
                    if (input.getRemainingKey(possible.what()) != null) {
                        info.virtual = false;
                        break;
                    }
                }
                if (!info.virtual) {
                    break;
                }
            }
        }

        patternBatchInfoCache.put(details, info);
        return info;
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

        // 批量冷却按速度升级卡周期递减
        if (batchCooldown > 0) {
            batchCooldown--;
        }
        // 每 tick 重置 batchBusy,允许下一 tick 继续接收 pushPattern
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
        // 击杀/吸入/合成区域以黑洞渲染位置为中心(结构几何中心),而非控制器位置
        BlockPos center = BlockPos.containing(AssemblyStructure.getBlackHoleCenter(level, controller.getBlockPos()));
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
                // 注意：MEStorage.insert 返回的是【已插入】数量,而非剩余数量
                long inserted = storage.insert(key, count, Actionable.MODULATE, source);
                count -= inserted;
                if (inserted <= 0) {
                    break;
                }
            }
            if (count > 0) {
                leftovers.add(new GenericStack(key, count));
            }
        }

        if (pendingOutputs.size() + leftovers.size() > getMaxPendingOutputs()) {
            AE2Enhanced.LOGGER.error("[AE2E] pendingOutputs overflow in AssemblyController at {}, dropping {}",
                    controller.getBlockPos(), pendingOutputs.size() + leftovers.size() - getMaxPendingOutputs());
            while (pendingOutputs.size() + leftovers.size() > getMaxPendingOutputs()) {
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
     * 批量执行样板任务,一次性处理 {@code batchSize} 个副本.
     * <p>作为 AE2 调用 pushPattern 时的回退处理路径,负责将产物与剩余物加入缓冲,
     * 并在可能的情况下将催化剂直接返回网络,避免催化剂被错误地延迟注入.</p>
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

        // 确保至少能连上网络,用于后续注入产物与返还催化剂
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

        // 估算产物与剩余物堆叠数,防止 pendingOutputs 溢出
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

        // 处理剩余物：催化剂立即返回网络,其它剩余物加入缓冲
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
                    // 催化剂：尝试直接返还网络,未插入部分回退到缓冲
                    // 注意：MEStorage.insert 返回的是【已插入】数量,而非剩余数量
                    long inserted = storage.insert(remaining, remainingAmount, Actionable.MODULATE, source);
                    long notInserted = remainingAmount - inserted;
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
        patternBatchInfoCache.clear();
        batchCooldown = 0;
        batchBusy = false;
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
