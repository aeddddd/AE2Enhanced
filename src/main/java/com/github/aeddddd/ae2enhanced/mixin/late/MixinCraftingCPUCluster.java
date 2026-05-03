package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cache.CraftingGridCache;
import appeng.me.helpers.MachineSource;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public class MixinCraftingCPUCluster {

    // ==================== Computation Core Support ====================

    @Unique
    private TileComputationCore ae2enhanced$computationCore;

    public void ae2enhanced$setComputationCore(TileComputationCore core) {
        this.ae2enhanced$computationCore = core;
    }

    public TileComputationCore ae2enhanced$getComputationCore() {
        return this.ae2enhanced$computationCore;
    }

    @Shadow
    private MachineSource machineSrc;

    @Shadow
    private String myName;

    @Shadow
    private boolean isDestroyed;

    @Shadow
    private List<TileCraftingTile> tiles;

    @Shadow
    private List<TileCraftingMonitorTile> status;

    @Shadow
    private void updateCPU() {
    }

    // ==================== Overwrites for Virtual Clusters ====================

    /**
     * @author AE2Enhanced
     * @reason Redirect isActive to TileComputationCore proxy node when this cluster belongs to a Computation Core.
     */
    @Overwrite
    public boolean isActive() {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            return node != null && node.isActive();
        }
        TileCraftingTile core = this.getCore();
        if (core == null) {
            return false;
        }
        IGridNode node = core.getActionableNode();
        if (node == null) {
            return false;
        }
        return node.isActive();
    }

    /**
     * @author AE2Enhanced
     * @reason Redirect markDirty to TileComputationCore instead of TileCraftingTile for virtual clusters.
     */
    @Overwrite
    private void markDirty() {
        if (ae2enhanced$computationCore != null) {
            ae2enhanced$computationCore.markDirty();
            return;
        }
        this.getCore().saveChanges();
    }

    /**
     * @author AE2Enhanced
     * @reason Redirect getGrid to TileComputationCore proxy for virtual clusters.
     */
    @Overwrite
    private IGrid getGrid() {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            return node != null ? node.getGrid() : null;
        }
        for (TileCraftingTile r : this.tiles) {
            IGridNode gn = r.getActionableNode();
            if (gn == null || gn.getGrid() == null) continue;
            return r.getActionableNode().getGrid();
        }
        return null;
    }

    /**
     * @author AE2Enhanced
     * @reason Redirect getWorld to TileComputationCore for virtual clusters.
     */
    @Overwrite
    private World getWorld() {
        if (ae2enhanced$computationCore != null) {
            return ae2enhanced$computationCore.getWorld();
        }
        return this.getCore().getWorld();
    }

    /**
     * @author AE2Enhanced
     * @reason Prevent ClassCastException when machineSrc points to TileComputationCore instead of TileCraftingTile.
     */
    @Overwrite
    private TileCraftingTile getCore() {
        if (ae2enhanced$computationCore != null) {
            return null;
        }
        if (this.machineSrc == null) {
            return null;
        }
        return (TileCraftingTile) this.machineSrc.machine().orElse(null);
    }

    /**
     * @author AE2Enhanced
     * @reason Set fixed localized name for virtual clusters instead of reading from physical tiles.
     */
    @Overwrite
    public void updateName() {
        if (ae2enhanced$computationCore != null) {
            this.myName = net.minecraft.client.resources.I18n.format("tile.ae2enhanced.computation_core.name");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (TileCraftingTile te : this.tiles) {
            if (!te.hasCustomInventoryName()) continue;
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(te.getCustomInventoryName());
        }
        this.myName = sb.toString();
    }

    /**
     * @author AE2Enhanced
     * @reason No-op for virtual clusters — there is no physical cluster to break.
     */
    @Overwrite
    public void breakCluster() {
        if (ae2enhanced$computationCore != null) {
            return;
        }
        TileCraftingTile t = this.getCore();
        if (t != null) {
            t.breakCluster();
        }
    }

    /**
     * @author AE2Enhanced
     * @reason No-op for virtual clusters — there is no physical core tile to restore state on.
     */
    @Overwrite
    void done() {
        if (ae2enhanced$computationCore != null) {
            return;
        }
        TileCraftingTile core = this.getCore();
        core.setCoreBlock(true);
        if (core.getPreviousState() != null) {
            ((CraftingCPUCluster) (Object) this).readFromNBT(core.getPreviousState());
            core.setPreviousState(null);
        }
        this.updateCPU();
        this.updateName();
    }

    /**
     * @author AE2Enhanced
     * @reason No-op for virtual clusters — avoid marking them as destroyed so they remain visible in terminals.
     */
    @Overwrite
    public void destroy() {
        if (ae2enhanced$computationCore != null) {
            return;
        }
        if (this.isDestroyed) {
            return;
        }
        this.isDestroyed = true;
        boolean posted = false;
        for (TileCraftingTile r : this.tiles) {
            IGrid g;
            IGridNode n = r.getActionableNode();
            if (n != null && !posted && (g = n.getGrid()) != null) {
                g.postEvent(new appeng.api.networking.events.MENetworkCraftingCpuChange(n));
                posted = true;
            }
            r.updateStatus(null);
        }
    }

    /**
     * @author AE2Enhanced
     * @reason No-op for virtual clusters — there are no physical tiles to update metadata on.
     */
    @Overwrite
    public void updateStatus(boolean updateGrid) {
        if (ae2enhanced$computationCore != null) {
            return;
        }
        for (TileCraftingTile r : this.tiles) {
            r.updateMeta(true);
        }
    }

    // ==================== Batch Crafting (Assembly Hub) — retained ====================

    private static Field tasksField;
    private static Field remOpsField;
    private static Field remItemCountField;
    private static Field isCompleteField;
    private static Field waitingForField;
    private static Method postCraftingStatusChange;
    private static Method postChange;
    private static Field taskProgressValueField;
    private static Method completeJobMethod;
    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;

    private static int batchCallCount = 0;
    private static int batchSuccessCount = 0;
    private static int batchFailCount = 0;

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

    private static void tryInitReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            tasksField = CraftingCPUCluster.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            remOpsField = CraftingCPUCluster.class.getDeclaredField("remainingOperations");
            remOpsField.setAccessible(true);
            remItemCountField = CraftingCPUCluster.class.getDeclaredField("remainingItemCount");
            remItemCountField.setAccessible(true);
            isCompleteField = CraftingCPUCluster.class.getDeclaredField("isComplete");
            isCompleteField.setAccessible(true);
            waitingForField = CraftingCPUCluster.class.getDeclaredField("waitingFor");
            waitingForField.setAccessible(true);
            postCraftingStatusChange = CraftingCPUCluster.class.getDeclaredMethod("postCraftingStatusChange", IAEItemStack.class);
            postCraftingStatusChange.setAccessible(true);
            postChange = CraftingCPUCluster.class.getDeclaredMethod("postChange", IAEItemStack.class, appeng.api.networking.security.IActionSource.class);
            postChange.setAccessible(true);
            Class<?> taskProgressClass = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress");
            taskProgressValueField = taskProgressClass.getDeclaredField("value");
            taskProgressValueField.setAccessible(true);
            completeJobMethod = CraftingCPUCluster.class.getDeclaredMethod("completeJob");
            completeJobMethod.setAccessible(true);
            reflectionReady = true;
        } catch (Exception e) {
            reflectionFailed = true;
            AE2Enhanced.LOGGER.error("[AE2E] Mixin reflection init failed, batch crafting disabled. " +
                "This usually means AE2-UEL class/field names have changed. Details: {}", e.toString());
        }
    }

    @Redirect(
        method = "updateCraftingLogic",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/tile/crafting/TileCraftingTile;isActive()Z"
        )
    )
    private boolean redirectIsActive(TileCraftingTile instance) {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            return node != null && node.isActive();
        }
        return instance.isActive();
    }

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"))
    private void onUpdateCraftingLogicHead(IGrid grid, IEnergyGrid eg, CraftingGridCache cache, CallbackInfo ci) {
        if (reflectionFailed) return;
        try {
            tryInitReflection();
            if (!reflectionReady) return;
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            boolean isComplete = isCompleteField.getBoolean(cpu);

            if (!isComplete && tasks.isEmpty()) {
                @SuppressWarnings("unchecked")
                IItemList<IAEItemStack> waitingFor = (IItemList<IAEItemStack>) waitingForField.get(cpu);
                if (waitingFor == null || waitingFor.isEmpty()) {
                    completeJobMethod.invoke(cpu);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] onUpdateCraftingLogicHead unexpected error: {}", e.toString());
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        if (reflectionFailed) return;

        CraftingCPUCluster cpu;
        boolean anyOurTask = false;
        int virtualTasksFound = 0;
        int virtualTasksExecuted = 0;

        try {
            tryInitReflection();
            if (!reflectionReady) return;
            cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            if (tasks.isEmpty()) return;

            @SuppressWarnings("unchecked")
            IItemList<IAEItemStack> waitingFor = (IItemList<IAEItemStack>) waitingForField.get(cpu);

            boolean changed;
            int doWhileIterations = 0;
            do {
                changed = false;
                for (Map.Entry<ICraftingPatternDetails, Object> entry : new ArrayList<>(tasks.entrySet())) {
                    ICraftingPatternDetails details = entry.getKey();
                    Object progress = entry.getValue();

                    long remaining = taskProgressValueField.getLong(progress);
                    if (remaining <= 0) continue;

                    if (details.canSubstitute()) continue;

                    List<ICraftingMedium> mediums = cache.getMediums(details);
                    if (mediums == null || mediums.isEmpty()) continue;

                    for (ICraftingMedium medium : mediums) {
                        if (!(medium instanceof TileAssemblyMeInterface)) continue;
                        anyOurTask = true;

                        TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                        if (controller == null) continue;

                        if (!controller.isVirtualPattern(details)) {
                            if (details.canSubstitute()) break;
                            if (!controller.canBatch()) break;

                            long cap = controller.getParallelCap();
                            long batchSize = (cap >= Long.MAX_VALUE / 2) ? remaining : Math.min(remaining, cap);
                            long actualBatchSize = batchSize;

                            appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                            controller.setCurrentActionSource(source);
                            try {
                                appeng.crafting.MECraftingInventory meInv = (appeng.crafting.MECraftingInventory) cpu.getInventory();
                                appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                                appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                                TileAssemblyController.PatternBatchInfo info = controller.getPatternBatchInfo(details, meInv, source);
                                if (info == null || info.recipe == null) break;

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
                                        postChange.invoke(cpu, diff, source);
                                        postCraftingStatusChange.invoke(cpu, diff);
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
                                    }
                                }

                                long newRemaining = remaining - actualBatchSize;
                                taskProgressValueField.setLong(progress, newRemaining);

                                long oldRemOps = remOpsField.getLong(cpu);
                                remOpsField.setLong(cpu, oldRemOps - actualBatchSize);
                                long oldRemItemCount = remItemCountField.getLong(cpu);
                                long totalOutputCount = 0;
                                for (IAEItemStack out : details.getCondensedOutputs()) {
                                    if (out != null) totalOutputCount += out.getStackSize() * actualBatchSize;
                                }
                                remItemCountField.setLong(cpu, oldRemItemCount - totalOutputCount);

                                controller.setBatchBusy(true);
                                changed = true;
                                controller.resetBatchCooldown();

                                AE2Enhanced.LOGGER.debug("[AE2E Batch] Processed {}x real {} -> remaining={}",
                                    actualBatchSize, details.getOutput(null, null).getDisplayName(), newRemaining);
                            } catch (Exception e) {
                                AE2Enhanced.LOGGER.error("[AE2E] Real batch error: {}", e.toString());
                            } finally {
                                controller.setCurrentActionSource(null);
                            }
                            break;
                        }

                        if (!controller.canBatch()) continue;
                        virtualTasksFound++;

                        long cap = controller.getParallelCap();
                        long batchSize = (cap >= Long.MAX_VALUE / 2) ? remaining : Math.min(remaining, cap);

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

                            for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                long totalNeed = inputTemplate.getStackSize() * batchSize;
                                IAEItemStack need = inputTemplate.copy();
                                need.setStackSize(totalNeed);
                                IAEItemStack extracted = meInv.extractItems(need, MODULATE, source);
                                if (extracted != null && extracted.getStackSize() > 0) {
                                    IAEItemStack diff = extracted.copy();
                                    diff.setStackSize(-diff.getStackSize());
                                    postChange.invoke(cpu, diff, source);
                                    postCraftingStatusChange.invoke(cpu, diff);
                                }
                            }

                            long totalOutputItems = 0;
                            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                long totalCount = outputTemplate.getStackSize() * batchSize;
                                if (totalCount <= 0) continue;
                                totalOutputItems += totalCount;

                                IAEItemStack product = outputTemplate.copy();
                                product.setStackSize(totalCount);
                                itemList.add(product);
                                postChange.invoke(cpu, product.copy(), source);
                                postCraftingStatusChange.invoke(cpu, product.copy());

                                if (waitingFor != null) {
                                    IAEItemStack waiting = waitingFor.findPrecise(outputTemplate);
                                    if (waiting != null) {
                                        waiting.decStackSize(totalCount);
                                        if (waiting.getStackSize() <= 0) {
                                            waiting.setStackSize(0);
                                        }
                                    }
                                }
                            }

                            long newRemaining = remaining - batchSize;
                            taskProgressValueField.setLong(progress, newRemaining);

                            controller.setBatchBusy(true);

                            changed = true;
                            virtualTasksExecuted++;
                            controller.resetBatchCooldown();

                            AE2Enhanced.LOGGER.debug("[AE2E Batch] Processed {}x {} -> remaining={}",
                                batchSize, details.getOutput(null, null).getDisplayName(), newRemaining);
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
        } finally {
            batchCallCount++;
            if (virtualTasksExecuted > 0) {
                batchSuccessCount += virtualTasksExecuted;
                AE2Enhanced.LOGGER.debug("[AE2E Batch] Executed {} virtual task(s) (found {} candidates, total calls={}, successes={})",
                    virtualTasksExecuted, virtualTasksFound, batchCallCount, batchSuccessCount);
            } else if (anyOurTask && batchCallCount % 20 == 1) {
                batchFailCount++;
                AE2Enhanced.LOGGER.debug("[AE2E Batch] Call #{}: found our tasks but executed 0 (candidates={}, fails={}). " +
                    "Possible causes: insufficient CPU inventory, pattern not cached as virtual, or canSubstitute.",
                    batchCallCount, virtualTasksFound, batchFailCount);
            }
        }
    }
}
