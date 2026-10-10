package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IMeInventoryVersionAccess;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.ITaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.specialcrafting.SelfRefOutputGate;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingRuntime;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidPatternHelper;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Assembly Hub 批量合成（虚拟/真实批量任务处理）.
 *
 * <p>在 executeCrafting HEAD 中批量处理经由 TileAssemblyMeInterface 推送的任务：
 * 虚拟样板直接按并行上限结算产出，真实样板按配方批量核算材料与产物。
 * 同时在 updateCraftingLogic HEAD 处理任务收官与 Crafting Monitor 清空修复。</p>
 *
 * <h3>跨配方并行（本 Mixin 的调度职责）</h3>
 * <p>并行上限不再等同「单次批量大小 + 整枢纽冷却」，而是枢纽可同时持有的在途份数，
 * 由 {@link TileAssemblyController} 的并行预算分配器在<b>同一周期内的多个样板</b>间分摊：
 * 每趟先把本趟待服务样板按控制器汇总（{@code beginParallelPass}），再逐任务取额度
 * （{@code allowanceFor}），结算成功后登记实际占用（{@code claimParallel}）。
 * 并发配方数由跨配方并行模块限制（无卡 = 1，等价「一次只处理一种配方」）。
 * 某个样板剩余量/材料/供电不足时，用不完的额度会在同趟后续轮次回流给其他样板。</p>
 *
 * <h3>能耗</h3>
 * <p>材料预检通过、MODULATE 扣料之前调用 {@code chargeCraftEnergy} 计费（只计费不限流,
 * 网络能付多少付多少,不阻塞合成）。</p>
 *
 * <h3>产物回收（必须与原生同口径）</h3>
 * <p>结算产物<b>不得</b>直接写 CPU 库存：必须先 {@code waitingFor.add(...)} 登记预期,
 * 再让产物经 {@code cpu.injectItems(...)}（虚拟分支）或 pendingOutputs → 网络回流
 * （真实/流体分支）进入 CPU。只有这条路径会递减 {@code finalOutput} 与剩余计数、
 * 交付合成链并在到达 0 时收官；绕过它会让合成监视器残留"正在合成"。</p>
 *
 * <p>注意：批量结算<b>不得</b>扣减 {@code remainingOperations}（原生每 tick 发配配额）。
 * 原生的 {@code usedOps[3]} 环形记账把它当作「本拍消耗」，一旦批量把配额扣成负数，
 * 后续 1~3 tick 会算出非正配额而整段跳过 {@code executeCrafting}（连带跳过本 Mixin 的
 * 批量结算），表现为枢纽周期性停摆。枢纽的节流由并行预算承担，原生配额只描述物理发配。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public abstract class MixinCraftingCPUClusterBatch {

    @Shadow
    private boolean isComplete;

    @Shadow
    private Map<ICraftingPatternDetails, Object> tasks;

    @Shadow
    private IItemList<IAEItemStack> waitingFor;

    @Shadow
    private IAEItemStack finalOutput;

    /**
     * 汇总本趟各装配中枢的待服务样板（每个样板只计入首个可服务它的中枢）.
     *
     * <p>预扫描的目的是让并行预算分配器知道「有多少个配方在竞争同一周期的额度」，
     * 从而把额度切成多份并行推进，而不是让迭代顺序靠前的样板吃满预算。</p>
     */
    private static Map<TileAssemblyController, List<ICraftingPatternDetails>> collectPending(
            Map<ICraftingPatternDetails, Object> tasks, CraftingGridCache cache) {
        Map<TileAssemblyController, List<ICraftingPatternDetails>> pending = new java.util.IdentityHashMap<>();
        for (Map.Entry<ICraftingPatternDetails, Object> entry : tasks.entrySet()) {
            Object progress = entry.getValue();
            if (progress == null) continue;
            if (((ITaskProgressAccessor) progress).ae2e$getValue() <= 0) continue;

            List<ICraftingMedium> mediums = ((ICraftingGridCacheAccess) cache).ae2enhanced$getMediumsMemo(entry.getKey());
            if (mediums == null || mediums.isEmpty()) continue;

            for (ICraftingMedium medium : mediums) {
                if (!(medium instanceof TileAssemblyMeInterface)) continue;
                TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                if (controller == null) continue;
                pending.computeIfAbsent(controller, k -> new ArrayList<>()).add(entry.getKey());
                break;
            }
        }
        return pending;
    }

    @Shadow
    private void postChange(IAEItemStack diff, appeng.api.networking.security.IActionSource src) {
    }

    @Shadow
    private void postCraftingStatusChange(IAEItemStack diff) {
    }

    @Shadow
    private void completeJob() {
    }

    @Shadow
    private void updateCPU() {
    }

    // ==================== Batch Crafting (Assembly Hub) ====================

    private static appeng.api.storage.data.IAEItemStack fetchFromNetwork(
            CraftingCPUCluster cpu,
            appeng.api.storage.data.IAEItemStack request,
            appeng.api.networking.security.IActionSource source) {
        try {
            appeng.api.networking.security.IActionSource src = cpu.getActionSource();
            if (src instanceof appeng.me.helpers.MachineSource) {
                java.util.Optional<appeng.api.networking.security.IActionHost> hostOpt =
                    ((appeng.me.helpers.MachineSource) src).machine();
                if (hostOpt.isPresent()) {
                    appeng.api.networking.IGridNode node = hostOpt.get().getActionableNode();
                    if (node != null) {
                        appeng.api.networking.IGrid grid = node.getGrid();
                        if (grid != null) {
                            appeng.api.networking.storage.IStorageGrid sg =
                                grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
                            appeng.api.storage.channels.IItemStorageChannel channel =
                                appeng.api.AEApi.instance().storage().getStorageChannel(
                                    appeng.api.storage.channels.IItemStorageChannel.class);
                            appeng.api.storage.IMEMonitor<appeng.api.storage.data.IAEItemStack> storage =
                                sg.getInventory(channel);
                            return storage.extractItems(request, appeng.api.config.Actionable.MODULATE, source);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] fetchFromNetwork failed: {}", e.toString());
        }
        return null;
    }

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"))
    private void onUpdateCraftingLogicHead(IGrid grid, IEnergyGrid eg, CraftingGridCache cache, CallbackInfo ci) {
        try {
            CraftingCPUCluster self = (CraftingCPUCluster) (Object) this;
            if (SpecialCraftingRuntime.isSpecialCluster(self)) {
                // 特殊 job:收官由 SelfRefOutputGate 负责(任务全推送+无在途后一次性交付),
                // 不走下方的提前 completeJob——否则 waitingFor 先排空时会提前 markDone.
                SelfRefOutputGate.tickSettle(self);
                return;
            }
            if (!this.isComplete && this.tasks.isEmpty()) {
                IItemList<IAEItemStack> waitingFor = this.waitingFor;
                boolean waitingForEmpty = true;
                if (waitingFor != null) {
                    for (IAEItemStack is : waitingFor) {
                        if (is != null && is.getStackSize() > 0) {
                            waitingForEmpty = false;
                            break;
                        }
                    }
                }
                if (waitingForEmpty) {
                    this.completeJob();
                    // 修复：completeJob() 不重置 finalOutput 也不调用 updateCPU(),
                    // 导致 Crafting Monitor 在任务完成后不清空
                    this.finalOutput = null;
                    updateCPU();
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] onUpdateCraftingLogicHead unexpected error: {}", e.toString());
        }
    }

    /**
     * AE2 原生 executeCrafting 内 visitedMediums 补充队列时也会调 getMediums
     * （CraftingCPUCluster 第 520 行）, mediums 队列每 tick 可能重建,
     * 同样走 memo 避免重复的 equals 语义 map 查找(深层 NBT 比较热点).
     */
    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE",
        target = "Lappeng/me/cache/CraftingGridCache;getMediums(Lappeng/api/networking/crafting/ICraftingPatternDetails;)Ljava/util/List;"),
        require = 0)
    private List<ICraftingMedium> ae2enhanced$memoizeNativeGetMediums(CraftingGridCache cache,
            ICraftingPatternDetails details) {
        return ((ICraftingGridCacheAccess) cache).ae2enhanced$getMediumsMemo(details);
    }

    @Shadow
    private net.minecraft.world.World getWorld() {
        return null;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        // 性能早退：网络中不存在装配中枢时,批量结算不可能命中,
        // 直接跳过——getMediums 的 map 查找会对每个任务触发样板 equals/hashCode
        // 的深层 NBT 比较,是合成 CPU 每 tick 的主要开销之一(spark 采样确认).
        if (!((ICraftingGridCacheAccess) cache).ae2enhanced$hasAssemblyHub()) return;

        // 特殊集群(超因果计算核心上的自消耗 job)豁免批量结算:批量分支直写
        // itemList 会绕过 SelfRefOutputGate 门控记账(remainingItemCount/脏标记
        // 口径不一),与 MixinCraftingCPUClusterAggregate 的特殊集群守卫同口径
        if (com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingRuntime
                .isSpecialCluster((CraftingCPUCluster) (Object) this)) return;

        CraftingCPUCluster cpu;

        try {
            cpu = (CraftingCPUCluster) (Object) this;

            Map<ICraftingPatternDetails, Object> tasks = this.tasks;
            if (tasks.isEmpty()) return;

            IItemList<IAEItemStack> waitingFor = this.waitingFor;

            boolean changed;
            int doWhileIterations = 0;
            do {
                changed = false;
                // 每趟重算各控制器的待服务样板集合并开启一趟预算分配：
                // 并行额度按本趟竞争者数量分摊，已有在途的样板让位于首轮竞争者。
                Map<TileAssemblyController, List<ICraftingPatternDetails>> pendingByController =
                        collectPending(tasks, cache);
                for (Map.Entry<TileAssemblyController, List<ICraftingPatternDetails>> pe : pendingByController.entrySet()) {
                    pe.getKey().beginParallelPass(pe.getValue());
                }
                // 任务迭代顺序按世界时间轮转：并行上限/并发槽位小于待服务样板数时，额度受预算
                // 耗尽影响只落在迭代顺序靠前的样板，逐 tick 轮转才能覆盖全部样板（否则靠后的
                // 样板永远拿不到额度 = 变相卡死）。
                List<Map.Entry<ICraftingPatternDetails, Object>> ordered = new ArrayList<>(tasks.entrySet());
                if (ordered.size() > 1) {
                    net.minecraft.world.World batchWorld = getWorld();
                    if (batchWorld != null) {
                        Collections.rotate(ordered, -(int) (batchWorld.getTotalWorldTime() % ordered.size()));
                    }
                }
                for (Map.Entry<ICraftingPatternDetails, Object> entry : ordered) {
                    ICraftingPatternDetails details = entry.getKey();
                    Object progress = entry.getValue();

                    long remaining = ((ITaskProgressAccessor) progress).ae2e$getValue();
                    if (remaining <= 0) continue;

                    List<ICraftingMedium> mediums = ((ICraftingGridCacheAccess) cache).ae2enhanced$getMediumsMemo(details);
                    if (mediums == null || mediums.isEmpty()) continue;

                    for (ICraftingMedium medium : mediums) {
                        if (!(medium instanceof TileAssemblyMeInterface)) continue;

                        TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                        if (controller == null) continue;

                        // ===== ae2fc 流体替换合成样板：流体感知批量分支 =====
                        // 输入模板中 FLUID_DROP 按 mB 计量、普通物品按个数计量；
                        // 配方用容器形态输入(getOriginInputs)重建,被替换的流体槽无空容器返还.
                        if (controller.isFluidPattern(details)) {
                            long allowance = controller.allowanceFor(details);
                            if (allowance <= 0) continue; // 本趟无额度：让其他中枢/装配室接手

                            long batchSize = Math.min(remaining, allowance);

                            appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                            controller.setCurrentActionSource(source);
                            try {
                                appeng.crafting.MECraftingInventory meInv = (appeng.crafting.MECraftingInventory) cpu.getInventory();
                                appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                                appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                                IAEItemStack[] fluidInputs = details.getInputs();
                                IAEItemStack[] originInputs = Ae2fcFluidPatternHelper.getOriginInputs(details);
                                if (fluidInputs == null || originInputs == null) break; // 反射失败,安全降级到逐次派发

                                // 用容器形态输入重建原版配方,取得每槽空容器返还表
                                InventoryCrafting ic = new InventoryCrafting(new net.minecraft.inventory.Container() {
                                    @Override
                                    public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                                        return false;
                                    }
                                }, 3, 3);
                                for (int i = 0; i < originInputs.length && i < 9; i++) {
                                    ic.setInventorySlotContents(i, originInputs[i] != null ? originInputs[i].getDefinition() : ItemStack.EMPTY);
                                }
                                IRecipe recipe = CraftingManager.findMatchingRecipe(ic, controller.getWorld());
                                if (recipe == null) break;
                                NonNullList<ItemStack> recipeRemaining = recipe.getRemainingItems(ic);

                                // 逐槽分类：Drop 槽=流体直接消耗(无返还)；普通槽 remaining 为空=纯消耗；
                                // remaining 与输入同物品=催化剂/转换槽,降级为单份结算
                                int[] returnCounts = new int[9];
                                boolean forceSingle = false;
                                int estimatedStacks = 1;
                                for (int i = 0; i < 9; i++) {
                                    if (i >= fluidInputs.length || fluidInputs[i] == null) continue;
                                    if (Ae2fcFluidPatternHelper.isAe2fcFluidDrop(fluidInputs[i])) continue;
                                    ItemStack rem = i < recipeRemaining.size() ? recipeRemaining.get(i) : ItemStack.EMPTY;
                                    if (rem.isEmpty()) {
                                        estimatedStacks++;
                                        continue;
                                    }
                                    ItemStack inputDef = fluidInputs[i].getDefinition();
                                    if (ItemStack.areItemsEqual(inputDef, rem) && inputDef.getMetadata() == rem.getMetadata()) {
                                        forceSingle = true;
                                    } else {
                                        returnCounts[i] = rem.getCount();
                                        estimatedStacks++;
                                    }
                                }
                                long actualBatchSize = forceSingle ? 1 : batchSize;
                                if (!controller.canAcceptRealBatch(estimatedStacks)) break;

                                // SIMULATE 预检 + 网络补取,自适应缩小批量(与虚拟批量同策略)
                                boolean canExtract = true;
                                for (int retry = 0; retry < 5; retry++) {
                                    canExtract = true;
                                    for (int i = 0; i < 9; i++) {
                                        if (i >= fluidInputs.length || fluidInputs[i] == null) continue;
                                        long perOp = fluidInputs[i].getStackSize();
                                        if (perOp <= 0) continue;
                                        long totalNeed = perOp * actualBatchSize;
                                        IAEItemStack need = fluidInputs[i].copy();
                                        need.setStackSize(totalNeed);
                                        IAEItemStack simResult = meInv.extractItems(need, SIMULATE, source);
                                        if (simResult == null || simResult.getStackSize() < totalNeed) {
                                            long available = simResult != null ? simResult.getStackSize() : 0;
                                            long missing = totalNeed - available;
                                            if (missing > 0) {
                                                IAEItemStack toFetch = fluidInputs[i].copy();
                                                toFetch.setStackSize(missing);
                                                IAEItemStack fetched = fetchFromNetwork(cpu, toFetch, source);
                                                if (fetched != null && fetched.getStackSize() > 0) {
                                                    meInv.injectItems(fetched, MODULATE, source);
                                                    simResult = meInv.extractItems(need, SIMULATE, source);
                                                    if (simResult != null && simResult.getStackSize() >= totalNeed) {
                                                        continue;
                                                    }
                                                    available = simResult != null ? simResult.getStackSize() : 0;
                                                }
                                            }
                                            long maxBatch = available / perOp;
                                            if (maxBatch > 0 && !forceSingle) {
                                                actualBatchSize = Math.min(actualBatchSize, maxBatch);
                                                canExtract = false; // 需要重试
                                            } else {
                                                canExtract = false;
                                                actualBatchSize = 0;
                                                break;
                                            }
                                        }
                                    }
                                    if (canExtract) break;
                                }
                                if (!canExtract || actualBatchSize <= 0) break;

                                // 能耗：按实际结算份数计费（只计费不限流,不阻塞合成）
                                controller.chargeCraftEnergy(actualBatchSize);

                                // MODULATE 实际扣料(Drop 扣 mB,物品扣个数)
                                for (int i = 0; i < 9; i++) {
                                    if (i >= fluidInputs.length || fluidInputs[i] == null) continue;
                                    long totalNeed = fluidInputs[i].getStackSize() * actualBatchSize;
                                    if (totalNeed <= 0) continue;
                                    IAEItemStack need = fluidInputs[i].copy();
                                    need.setStackSize(totalNeed);
                                    IAEItemStack extracted = meInv.extractItems(need, MODULATE, source);
                                    if (extracted != null && extracted.getStackSize() > 0) {
                                        IAEItemStack diff = extracted.copy();
                                        diff.setStackSize(-diff.getStackSize());
                                        this.postChange(diff, source);
                                        this.postCraftingStatusChange(diff);
                                    }
                                }

                                // 产物：与原生发配同口径(登记预期 → 经网络回流 → cpu.injectItems 递减
                                // finalOutput/剩余计数); 直接写 itemList 会导致监视器残留"正在合成"
                                if (waitingFor != null) {
                                    for (IAEItemStack outTemplate : details.getCondensedOutputs()) {
                                        if (outTemplate == null || outTemplate.getStackSize() <= 0) continue;
                                        IAEItemStack expect = outTemplate.copy();
                                        expect.setStackSize(outTemplate.getStackSize() * actualBatchSize);
                                        waitingFor.add(expect);
                                    }
                                }
                                for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                    if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                    long totalCount = outputTemplate.getStackSize() * actualBatchSize;
                                    if (totalCount <= 0) continue;
                                    IAEItemStack product = outputTemplate.copy();
                                    product.setStackSize(totalCount);
                                    controller.addPendingOutput(product.createItemStack());
                                }

                                // 空容器返还：仅非 Drop 的容器槽,Drop 槽流体被直接消耗无空桶
                                for (int i = 0; i < 9; i++) {
                                    if (returnCounts[i] <= 0) continue;
                                    ItemStack rem = recipeRemaining.get(i).copy();
                                    rem.setCount(returnCounts[i] * (int) actualBatchSize);
                                    controller.addPendingOutput(rem);
                                    if (waitingFor != null) {
                                        IAEItemStack expectRem =
                                                appeng.util.item.AEItemStack.fromItemStack(rem);
                                        if (expectRem != null) {
                                            waitingFor.add(expectRem);
                                        }
                                    }
                                }

                                long newRemaining = remaining - actualBatchSize;
                                ((ITaskProgressAccessor) progress).ae2e$setValue(newRemaining);

                                controller.setBatchBusy(true);
                                controller.claimParallel(details, actualBatchSize);
                                changed = true;
                            } catch (Exception e) {
                                AE2Enhanced.LOGGER.error("[AE2E] Fluid batch error: {}", e.toString());
                            } finally {
                                controller.setCurrentActionSource(null);
                            }
                            break;
                        }

                        if (!controller.isVirtualPattern(details)) {
                            long allowance = controller.allowanceFor(details);
                            if (allowance <= 0) continue; // 本趟无额度：让其他中枢/装配室接手

                            long actualBatchSize = Math.min(remaining, allowance);

                            appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                            controller.setCurrentActionSource(source);
                            try {
                                appeng.crafting.MECraftingInventory meInv = (appeng.crafting.MECraftingInventory) cpu.getInventory();
                                appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                                appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                                TileAssemblyController.PatternBatchInfo info = controller.getPatternBatchInfo(details, meInv, source);
                                if (info == null || info.recipe == null || info.slotTemplates == null || info.catalystSlots == null) break;

                                if (info.transformSlots != null && info.transformSlots.cardinality() > 0) {
                                    actualBatchSize = 1;
                                }

                                int estimatedStacks = 1;
                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] != null && !info.catalystSlots.get(i)) {
                                        estimatedStacks++;
                                    }
                                }
                                if (!controller.canAcceptRealBatch(estimatedStacks)) break;

                                boolean canExtract = true;
                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] == null) continue;
                                    long needCount;
                                    if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                                        needCount = 1;
                                    } else {
                                        needCount = actualBatchSize;
                                    }
                                    IAEItemStack need = info.slotTemplates[i].copy();
                                    need.setStackSize(needCount);
                                    IAEItemStack simResult = meInv.extractItems(need, SIMULATE, source);
                                    if (simResult == null || simResult.getStackSize() < needCount) {
                                        if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                                            IAEItemStack toFetch = info.slotTemplates[i].copy();
                                            toFetch.setStackSize(1);
                                            IAEItemStack fetched = fetchFromNetwork(cpu, toFetch, source);
                                            if (fetched != null && fetched.getStackSize() > 0) {
                                                meInv.injectItems(fetched, MODULATE, source);
                                                simResult = meInv.extractItems(need, SIMULATE, source);
                                                if (simResult == null || simResult.getStackSize() < needCount) {
                                                    canExtract = false;
                                                }
                                            } else {
                                                canExtract = false;
                                            }
                                        } else {
                                            long missing = needCount - (simResult != null ? simResult.getStackSize() : 0);
                                            IAEItemStack toFetch = info.slotTemplates[i].copy();
                                            toFetch.setStackSize(missing);
                                            IAEItemStack fetched = fetchFromNetwork(cpu, toFetch, source);
                                            if (fetched != null && fetched.getStackSize() > 0) {
                                                meInv.injectItems(fetched, MODULATE, source);
                                                long nowAvailable = (simResult != null ? simResult.getStackSize() : 0)
                                                    + fetched.getStackSize();
                                                actualBatchSize = Math.min(actualBatchSize, nowAvailable);
                                            } else {
                                                actualBatchSize = Math.min(actualBatchSize,
                                                    simResult != null ? simResult.getStackSize() : 0);
                                            }
                                        }
                                    }
                                }
                                if (!canExtract || actualBatchSize <= 0) break;

                                // 能耗：按实际结算份数计费（只计费不限流,不阻塞合成）
                                controller.chargeCraftEnergy(actualBatchSize);

                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] == null) continue;
                                    long needCount;
                                    if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                                        needCount = 1;
                                    } else {
                                        needCount = actualBatchSize;
                                    }
                                    IAEItemStack need = info.slotTemplates[i].copy();
                                    need.setStackSize(needCount);
                                    IAEItemStack extracted = meInv.extractItems(need, MODULATE, source);
                                    if (extracted != null && extracted.getStackSize() > 0) {
                                        IAEItemStack diff = extracted.copy();
                                        diff.setStackSize(-diff.getStackSize());
                                        this.postChange(diff, source);
                                        this.postCraftingStatusChange(diff);
                                    }
                                }

                                InventoryCrafting ic = new InventoryCrafting(new net.minecraft.inventory.Container() {
                                    @Override
                                    public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                                        return false;
                                    }
                                }, 3, 3);
                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] == null) continue;
                                    ItemStack stack = info.slotTemplates[i].createItemStack();
                                    stack.setCount(1);
                                    ic.setInventorySlotContents(i, stack);
                                }

                                ItemStack output = info.recipe.getCraftingResult(ic);
                                NonNullList<ItemStack> recipeRemaining = info.recipe.getRemainingItems(ic);

                                // 产物经 pendingOutputs → 网络回流, 与原生发配同口径登记预期:
                                // 回流时递减 finalOutput / 剩余计数, 否则监视器残留"正在合成"
                                if (waitingFor != null) {
                                    for (IAEItemStack outTemplate : details.getCondensedOutputs()) {
                                        if (outTemplate == null || outTemplate.getStackSize() <= 0) continue;
                                        IAEItemStack expect = outTemplate.copy();
                                        expect.setStackSize(outTemplate.getStackSize() * actualBatchSize);
                                        waitingFor.add(expect);
                                    }
                                }
                                if (!output.isEmpty()) {
                                    ItemStack batchOutput = output.copy();
                                    batchOutput.setCount(output.getCount() * (int) actualBatchSize);
                                    controller.addPendingOutput(batchOutput);
                                }

                                for (int i = 0; i < recipeRemaining.size(); i++) {
                                    ItemStack rem = recipeRemaining.get(i);
                                    if (rem.isEmpty()) continue;
                                    if (info.catalystSlots.get(i)) {
                                        IAEItemStack catalystReturn = info.slotTemplates[i].copy();
                                        catalystReturn.setStackSize(1);
                                        meInv.injectItems(catalystReturn, MODULATE, source);
                                    } else {
                                        ItemStack batchRem = rem.copy();
                                        batchRem.setCount(rem.getCount() * (int) actualBatchSize);
                                        controller.addPendingOutput(batchRem);
                                        if (waitingFor != null) {
                                            IAEItemStack expectRem =
                                                    appeng.util.item.AEItemStack.fromItemStack(batchRem);
                                            if (expectRem != null) {
                                                waitingFor.add(expectRem);
                                            }
                                        }
                                    }
                                }

                                long newRemaining = remaining - actualBatchSize;
                                ((ITaskProgressAccessor) progress).ae2e$setValue(newRemaining);

                                controller.setBatchBusy(true);
                                controller.claimParallel(details, actualBatchSize);
                                changed = true;


                            } catch (Exception e) {
                                AE2Enhanced.LOGGER.error("[AE2E] Real batch error: {}", e.toString());
                            } finally {
                                controller.setCurrentActionSource(null);
                            }
                            break;
                        }

                        long allowance = controller.allowanceFor(details);
                        if (allowance <= 0) continue; // 本趟无额度：让其他中枢/装配室接手

                        long batchSize = Math.min(remaining, allowance);

                        appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                        controller.setCurrentActionSource(source);
                        try {
                            appeng.crafting.MECraftingInventory meInv = (appeng.crafting.MECraftingInventory) cpu.getInventory();
                            IItemList<IAEItemStack> itemList = meInv.getItemList();
                            appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                            appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                            boolean canExtract = true;
                            for (int retry = 0; retry < 5; retry++) {
                                canExtract = true;
                                for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                    if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                    long totalNeed = inputTemplate.getStackSize() * batchSize;
                                    if (totalNeed <= 0) { canExtract = false; batchSize = 0; break; }
                                    IAEItemStack need = inputTemplate.copy();
                                    need.setStackSize(totalNeed);
                                    IAEItemStack simResult = meInv.extractItems(need, SIMULATE, source);
                                    if (simResult == null || simResult.getStackSize() < totalNeed) {
                                        long available = simResult != null ? simResult.getStackSize() : 0;
                                        long missing = totalNeed - available;
                                        if (missing > 0) {
                                            IAEItemStack toFetch = inputTemplate.copy();
                                            toFetch.setStackSize(missing);
                                            IAEItemStack fetched = fetchFromNetwork(cpu, toFetch, source);
                                            if (fetched != null && fetched.getStackSize() > 0) {
                                                meInv.injectItems(fetched, MODULATE, source);
                                                simResult = meInv.extractItems(need, SIMULATE, source);
                                                if (simResult != null && simResult.getStackSize() >= totalNeed) {
                                                    continue;
                                                }
                                                available = simResult != null ? simResult.getStackSize() : 0;
                                            }
                                        }
                                        long maxBatch = available / inputTemplate.getStackSize();
                                        if (maxBatch > 0) {
                                            batchSize = Math.min(batchSize, maxBatch);
                                            canExtract = false; // 需要重试
                                        } else {
                                            canExtract = false;
                                            batchSize = 0;
                                            break;
                                        }
                                    }
                                }
                                if (canExtract) break;
                            }
                            if (!canExtract || batchSize <= 0) {
                                continue;
                            }

                            // 能耗：按实际结算份数计费（只计费不限流,不阻塞合成）
                            controller.chargeCraftEnergy(batchSize);

                            for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                long totalNeed = inputTemplate.getStackSize() * batchSize;
                                if (totalNeed <= 0) continue;
                                IAEItemStack need = inputTemplate.copy();
                                need.setStackSize(totalNeed);
                                IAEItemStack extracted = meInv.extractItems(need, MODULATE, source);
                                if (extracted != null && extracted.getStackSize() > 0) {
                                    IAEItemStack diff = extracted.copy();
                                    diff.setStackSize(-diff.getStackSize());
                                    this.postChange(diff, source);
                                    this.postCraftingStatusChange(diff);
                                }
                            }

                            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                long totalCount = outputTemplate.getStackSize() * batchSize;
                                if (totalCount <= 0) continue;

                                IAEItemStack product = outputTemplate.copy();
                                product.setStackSize(totalCount);
                                // 与原生发配同口径: 先登记 waitingFor 预期, 再经 cpu.injectItems 回流。
                                // 最终产物由此递减 finalOutput 并交付合成链, 中间产物进入 CPU 库存并
                                // 递增注入版本号; 不得直接写 itemList(否则监视器残留"正在合成")。
                                if (waitingFor != null) {
                                    waitingFor.add(product.copy());
                                }
                                IAEItemStack leftover = cpu.injectItems(product.copy(), MODULATE, source);
                                if (leftover != null && leftover.getStackSize() > 0) {
                                    // 预期被其它路径消耗等极端情况: 退回直接入栏, 保证产物不丢
                                    itemList.add(leftover);
                                    ((IMeInventoryVersionAccess) meInv).ae2e$bumpInjectVersion();
                                    this.postChange(leftover.copy(), source);
                                    this.postCraftingStatusChange(leftover.copy());
                                }
                            }

                            long newRemaining = remaining - batchSize;
                            ((ITaskProgressAccessor) progress).ae2e$setValue(newRemaining);

                            controller.setBatchBusy(true);
                            controller.claimParallel(details, batchSize);
                            changed = true;


                        } finally {
                            controller.setCurrentActionSource(null);
                        }
                        break;
                    }
                }
                doWhileIterations++;
            } while (changed && doWhileIterations < 100000);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] batchProcessVirtualTasks unexpected error: {}", e.toString());
        }
    }
}
