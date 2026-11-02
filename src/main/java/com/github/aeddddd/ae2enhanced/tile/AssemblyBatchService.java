package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.AssemblyBatchSupport;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.ICraftingGridCacheAccess;
import com.github.aeddddd.ae2enhanced.crafting.IMeInventoryVersionAccess;
import com.github.aeddddd.ae2enhanced.craftingapi.SpecialCraftingPorts;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidPatternHelper;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.*;

public final class AssemblyBatchService {

    private AssemblyBatchService() {
    }

    public static void updateCraftingLogic(CraftingCPUCluster cpu) {
        try {
            CraftingCpuAdapter adapter = CraftingCpuAdapter.of(cpu);
            if (SpecialCraftingPorts.get().isSpecial(adapter.context())) {
                SpecialCraftingPorts.get().tickSettle(adapter, cpu.getActionSource());
                return;
            }
            if (adapter.isComplete() || !adapter.tasks().isEmpty() || !waitingForEmpty(adapter.waitingFor())) {
                return;
            }
            adapter.completeJob();
            adapter.clearFinalOutput();
            adapter.updateCPU();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] onUpdateCraftingLogicHead unexpected error: {}", e.toString());
        }
    }

    private static boolean waitingForEmpty(IItemList<IAEItemStack> waitingFor) {
        if (waitingFor == null) {
            return true;
        }
        for (IAEItemStack stack : waitingFor) {
            if (stack != null && stack.getStackSize() > 0) {
                return false;
            }
        }
        return true;
    }

    public static void executeCrafting(CraftingCPUCluster cpu, CraftingGridCache cache) {
        CraftingCpuAdapter adapter = CraftingCpuAdapter.of(cpu);
        ICraftingGridCacheAccess access = (ICraftingGridCacheAccess) cache;
        CpuOperations operations = new CpuOperations() {
            @Override
            public IActionSource actionSource() {
                return cpu.getActionSource();
            }

            @Override
            public IAEItemStack inject(IAEItemStack stack, Actionable mode, IActionSource source) {
                return cpu.injectItems(stack, mode, source);
            }

            @Override
            public IAEItemStack fetchFromNetwork(IAEItemStack request, IActionSource source) {
                return AssemblyBatchService.fetchFromNetwork(cpu, request, source);
            }
        };
        execute(adapter, operations, new MediumAccess() {
            @Override
            public boolean hasAssemblyHub() {
                return access.ae2enhanced$hasAssemblyHub();
            }

            @Override
            public List<ICraftingMedium> mediums(ICraftingPatternDetails details) {
                return access.ae2enhanced$getMediumsMemo(details);
            }
        });
    }

    public static void execute(CraftingCpuAdapter adapter, CpuOperations cpu, MediumAccess cache) {
        if (!cache.hasAssemblyHub() || SpecialCraftingPorts.get().isSpecial(adapter.context())) {
            return;
        }
        try {
            Map<ICraftingPatternDetails, Object> tasks = adapter.tasks();
            if (tasks.isEmpty()) {
                return;
            }
            IItemList<IAEItemStack> waitingFor = adapter.waitingFor();
            boolean changed;
            int doWhileIterations = 0;
            do {
                changed = false;
                Map<TileAssemblyController, List<ICraftingPatternDetails>> pendingByController =
                        collectPending(adapter, cache);
                for (Map.Entry<TileAssemblyController, List<ICraftingPatternDetails>> pending
                        : pendingByController.entrySet()) {
                    pending.getKey().beginParallelPass(pending.getValue());
                }
                List<Map.Entry<ICraftingPatternDetails, Object>> ordered = new ArrayList<>(tasks.entrySet());
                if (ordered.size() > 1) {
                    World world = adapter.world();
                    if (world != null) {
                        Collections.rotate(ordered, -(int) (world.getTotalWorldTime() % ordered.size()));
                    }
                }
                for (Map.Entry<ICraftingPatternDetails, Object> entry : ordered) {
                    ICraftingPatternDetails details = entry.getKey();
                    Object progress = entry.getValue();
                    long remaining = adapter.taskRemaining(details);
                    if (remaining <= 0) {
                        continue;
                    }
                    List<ICraftingMedium> mediums = cache.mediums(details);
                    if (mediums == null || mediums.isEmpty()) {
                        continue;
                    }
                    for (ICraftingMedium medium : mediums) {
                        if (!(medium instanceof TileAssemblyMeInterface)) {
                            continue;
                        }
                        TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                        if (controller == null) {
                            continue;
                        }
                        if (controller.isFluidPattern(details)) {
                            if (processFluid(adapter, cpu, controller, details, progress, remaining, waitingFor)) {
                                changed = true;
                            }
                            break;
                        }
                        if (!controller.isVirtualPattern(details)) {
                            if (processReal(adapter, cpu, controller, details, progress, remaining, waitingFor)) {
                                changed = true;
                            }
                            break;
                        }
                        if (processVirtual(adapter, cpu, controller, details, progress, remaining, waitingFor)) {
                            changed = true;
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

    private static Map<TileAssemblyController, List<ICraftingPatternDetails>> collectPending(
            CraftingCpuAdapter adapter, MediumAccess cache) {
        Map<TileAssemblyController, List<ICraftingPatternDetails>> pending = new IdentityHashMap<>();
        for (Map.Entry<ICraftingPatternDetails, Object> entry : adapter.tasks().entrySet()) {
            if (entry.getValue() == null || adapter.taskRemaining(entry.getKey()) <= 0) {
                continue;
            }
            List<ICraftingMedium> mediums = cache.mediums(entry.getKey());
            if (mediums == null || mediums.isEmpty()) {
                continue;
            }
            for (ICraftingMedium medium : mediums) {
                if (!(medium instanceof TileAssemblyMeInterface)) {
                    continue;
                }
                TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                if (controller == null) {
                    continue;
                }
                pending.computeIfAbsent(controller, ignored -> new ArrayList<>()).add(entry.getKey());
                break;
            }
        }
        return pending;
    }

    private static boolean processFluid(CraftingCpuAdapter adapter, CpuOperations cpu,
            TileAssemblyController controller, ICraftingPatternDetails details, Object progress,
            long remaining, IItemList<IAEItemStack> waitingFor) {
        long allowance = controller.allowanceFor(details);
        if (allowance <= 0) {
            return false;
        }
        long batchSize = Math.min(remaining, allowance);
        IActionSource source = cpu.actionSource();
        controller.setCurrentActionSource(source);
        try {
            MECraftingInventory meInv = (MECraftingInventory) adapter.inventory();
            IAEItemStack[] fluidInputs = details.getInputs();
            IAEItemStack[] originInputs = Ae2fcFluidPatternHelper.getOriginInputs(details);
            if (fluidInputs == null || originInputs == null) {
                return false;
            }
            InventoryCrafting inventory = craftingInventory();
            for (int i = 0; i < originInputs.length && i < 9; i++) {
                inventory.setInventorySlotContents(i,
                        originInputs[i] != null ? originInputs[i].getDefinition() : ItemStack.EMPTY);
            }
            IRecipe recipe = CraftingManager.findMatchingRecipe(inventory, controller.getWorld());
            if (recipe == null) {
                return false;
            }
            NonNullList<ItemStack> recipeRemaining = recipe.getRemainingItems(inventory);
            int[] returnCounts = new int[9];
            boolean forceSingle = false;
            int estimatedStacks = 1;
            for (int i = 0; i < 9; i++) {
                if (i >= fluidInputs.length || fluidInputs[i] == null) {
                    continue;
                }
                if (Ae2fcFluidPatternHelper.isAe2fcFluidDrop(fluidInputs[i])) {
                    continue;
                }
                ItemStack remainder = i < recipeRemaining.size() ? recipeRemaining.get(i) : ItemStack.EMPTY;
                if (remainder.isEmpty()) {
                    estimatedStacks++;
                    continue;
                }
                ItemStack input = fluidInputs[i].getDefinition();
                if (ItemStack.areItemsEqual(input, remainder) && input.getMetadata() == remainder.getMetadata()) {
                    forceSingle = true;
                } else {
                    returnCounts[i] = remainder.getCount();
                    estimatedStacks++;
                }
            }
            long actualBatchSize = forceSingle ? 1 : batchSize;
            if (!controller.canAcceptRealBatch(estimatedStacks)) {
                return false;
            }
            boolean canExtract = true;
            for (int retry = 0; retry < 5; retry++) {
                canExtract = true;
                for (int i = 0; i < 9; i++) {
                    if (i >= fluidInputs.length || fluidInputs[i] == null) {
                        continue;
                    }
                    long perOperation = fluidInputs[i].getStackSize();
                    if (perOperation <= 0) {
                        continue;
                    }
                    long totalNeed = AssemblyBatchSupport.checkedAmount(perOperation, actualBatchSize);
                    IAEItemStack need = fluidInputs[i].copy();
                    need.setStackSize(totalNeed);
                    IAEItemStack simulated = meInv.extractItems(need, Actionable.SIMULATE, source);
                    if (simulated == null || simulated.getStackSize() < totalNeed) {
                        long available = simulated != null ? simulated.getStackSize() : 0;
                        long missing = totalNeed - available;
                        if (missing > 0) {
                            IAEItemStack toFetch = fluidInputs[i].copy();
                            toFetch.setStackSize(missing);
                            IAEItemStack fetched = cpu.fetchFromNetwork(toFetch, source);
                            if (fetched != null && fetched.getStackSize() > 0) {
                                meInv.injectItems(fetched, Actionable.MODULATE, source);
                                simulated = meInv.extractItems(need, Actionable.SIMULATE, source);
                                if (simulated != null && simulated.getStackSize() >= totalNeed) {
                                    continue;
                                }
                                available = simulated != null ? simulated.getStackSize() : 0;
                            }
                        }
                        long maxBatch = available / perOperation;
                        if (maxBatch > 0 && !forceSingle) {
                            actualBatchSize = Math.min(actualBatchSize, maxBatch);
                            canExtract = false;
                        } else {
                            canExtract = false;
                            actualBatchSize = 0;
                            break;
                        }
                    }
                }
                if (canExtract) {
                    break;
                }
            }
            if (!canExtract || actualBatchSize <= 0) {
                return false;
            }
            List<IAEItemStack> requests = new ArrayList<>();
            List<IAEItemStack> outputs = new ArrayList<>();
            for (IAEItemStack input : fluidInputs) {
                if (input != null && input.getStackSize() > 0) {
                    requests.add(AssemblyBatchSupport.scaledCopy(input, actualBatchSize));
                }
            }
            for (IAEItemStack output : details.getCondensedOutputs()) {
                if (output != null && output.getStackSize() > 0) {
                    outputs.add(AssemblyBatchSupport.scaledCopy(output, actualBatchSize));
                }
            }
            for (int i = 0; i < 9; i++) {
                if (returnCounts[i] <= 0) {
                    continue;
                }
                ItemStack remainder = recipeRemaining.get(i).copy();
                remainder.setCount(returnCounts[i]);
                IAEItemStack template = appeng.util.item.AEItemStack.fromItemStack(remainder);
                if (template != null) {
                    outputs.add(AssemblyBatchSupport.scaledCopy(template, actualBatchSize));
                }
            }
            if (!controller.canAcceptPendingOutputs(outputs)) {
                return false;
            }
            List<IAEItemStack> extracted = AssemblyBatchSupport.extractExact(meInv, requests, source);
            if (extracted == null) {
                return false;
            }
            if (!controller.addPendingOutputs(outputs)) {
                AssemblyBatchSupport.rollbackExtracted(meInv, extracted, source);
                return false;
            }
            postExtraction(adapter, extracted, source);
            if (waitingFor != null) {
                for (IAEItemStack output : outputs) {
                    waitingFor.add(output.copy());
                }
            }
            controller.chargeCraftEnergy(actualBatchSize);
            adapter.setTaskRemaining(progress, remaining - actualBatchSize);
            controller.setBatchBusy(true);
            controller.claimParallel(details, actualBatchSize);
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Fluid batch error: {}", e.toString());
            return false;
        } finally {
            controller.setCurrentActionSource(null);
        }
    }

    private static boolean processReal(CraftingCpuAdapter adapter, CpuOperations cpu,
            TileAssemblyController controller, ICraftingPatternDetails details, Object progress,
            long remaining, IItemList<IAEItemStack> waitingFor) {
        long allowance = controller.allowanceFor(details);
        if (allowance <= 0) {
            return false;
        }
        long actualBatchSize = Math.min(remaining, allowance);
        IActionSource source = cpu.actionSource();
        controller.setCurrentActionSource(source);
        try {
            MECraftingInventory meInv = (MECraftingInventory) adapter.inventory();
            TileAssemblyController.PatternBatchInfo info = controller.getPatternBatchInfo(details, meInv, source);
            if (info == null || info.recipe == null || info.slotTemplates == null || info.catalystSlots == null) {
                return false;
            }
            if (info.transformSlots != null && info.transformSlots.cardinality() > 0) {
                actualBatchSize = 1;
            }
            int estimatedStacks = 1;
            for (int i = 0; i < info.slotTemplates.length; i++) {
                if (info.slotTemplates[i] != null && !info.catalystSlots.get(i)) {
                    estimatedStacks++;
                }
            }
            if (!controller.canAcceptRealBatch(estimatedStacks)) {
                return false;
            }
            boolean canExtract = true;
            for (int i = 0; i < info.slotTemplates.length; i++) {
                if (info.slotTemplates[i] == null) {
                    continue;
                }
                long needCount = info.catalystSlots.get(i) || info.transformSlots.get(i) ? 1 : actualBatchSize;
                IAEItemStack need = info.slotTemplates[i].copy();
                need.setStackSize(needCount);
                IAEItemStack simulated = meInv.extractItems(need, Actionable.SIMULATE, source);
                if (simulated == null || simulated.getStackSize() < needCount) {
                    if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                        IAEItemStack toFetch = info.slotTemplates[i].copy();
                        toFetch.setStackSize(1);
                        IAEItemStack fetched = cpu.fetchFromNetwork(toFetch, source);
                        if (fetched != null && fetched.getStackSize() > 0) {
                            meInv.injectItems(fetched, Actionable.MODULATE, source);
                            simulated = meInv.extractItems(need, Actionable.SIMULATE, source);
                            if (simulated == null || simulated.getStackSize() < needCount) {
                                canExtract = false;
                            }
                        } else {
                            canExtract = false;
                        }
                    } else {
                        long missing = needCount - (simulated != null ? simulated.getStackSize() : 0);
                        IAEItemStack toFetch = info.slotTemplates[i].copy();
                        toFetch.setStackSize(missing);
                        IAEItemStack fetched = cpu.fetchFromNetwork(toFetch, source);
                        if (fetched != null && fetched.getStackSize() > 0) {
                            meInv.injectItems(fetched, Actionable.MODULATE, source);
                            long nowAvailable = (simulated != null ? simulated.getStackSize() : 0)
                                    + fetched.getStackSize();
                            actualBatchSize = Math.min(actualBatchSize, nowAvailable);
                        } else {
                            actualBatchSize = Math.min(actualBatchSize,
                                    simulated != null ? simulated.getStackSize() : 0);
                        }
                    }
                }
            }
            if (!canExtract || actualBatchSize <= 0) {
                return false;
            }
            InventoryCrafting inventory = craftingInventory();
            List<IAEItemStack> requests = new ArrayList<>();
            for (int i = 0; i < info.slotTemplates.length; i++) {
                IAEItemStack template = info.slotTemplates[i];
                if (template == null) {
                    continue;
                }
                ItemStack stack = template.createItemStack();
                stack.setCount(1);
                inventory.setInventorySlotContents(i, stack);
                if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                    IAEItemStack request = template.copy();
                    request.setStackSize(1);
                    requests.add(request);
                } else {
                    requests.add(AssemblyBatchSupport.scaledCopy(template, actualBatchSize));
                }
            }
            ItemStack output = info.recipe.getCraftingResult(inventory);
            NonNullList<ItemStack> recipeRemaining = info.recipe.getRemainingItems(inventory);
            List<IAEItemStack> outputs = new ArrayList<>();
            List<IAEItemStack> waitingOutputs = new ArrayList<>();
            if (!output.isEmpty()) {
                IAEItemStack template = appeng.util.item.AEItemStack.fromItemStack(output);
                if (template != null) {
                    outputs.add(AssemblyBatchSupport.scaledCopy(template, actualBatchSize));
                }
            }
            for (IAEItemStack expected : details.getCondensedOutputs()) {
                if (expected != null && expected.getStackSize() > 0) {
                    waitingOutputs.add(AssemblyBatchSupport.scaledCopy(expected, actualBatchSize));
                }
            }
            for (int i = 0; i < recipeRemaining.size(); i++) {
                ItemStack remainder = recipeRemaining.get(i);
                if (remainder.isEmpty() || info.catalystSlots.get(i)) {
                    continue;
                }
                IAEItemStack template = appeng.util.item.AEItemStack.fromItemStack(remainder);
                if (template != null) {
                    IAEItemStack scaled = AssemblyBatchSupport.scaledCopy(template, actualBatchSize);
                    outputs.add(scaled);
                    waitingOutputs.add(scaled.copy());
                }
            }
            if (!controller.canAcceptPendingOutputs(outputs)) {
                return false;
            }
            List<IAEItemStack> extracted = AssemblyBatchSupport.extractExact(meInv, requests, source);
            if (extracted == null) {
                return false;
            }
            if (!controller.addPendingOutputs(outputs)) {
                AssemblyBatchSupport.rollbackExtracted(meInv, extracted, source);
                return false;
            }
            postExtraction(adapter, extracted, source);
            if (waitingFor != null) {
                for (IAEItemStack expected : waitingOutputs) {
                    waitingFor.add(expected.copy());
                }
            }
            for (int i = 0; i < recipeRemaining.size(); i++) {
                if (!recipeRemaining.get(i).isEmpty() && info.catalystSlots.get(i)) {
                    IAEItemStack catalystReturn = info.slotTemplates[i].copy();
                    catalystReturn.setStackSize(1);
                    meInv.injectItems(catalystReturn, Actionable.MODULATE, source);
                }
            }
            controller.chargeCraftEnergy(actualBatchSize);
            adapter.setTaskRemaining(progress, remaining - actualBatchSize);
            controller.setBatchBusy(true);
            controller.claimParallel(details, actualBatchSize);
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Real batch error: {}", e.toString());
            return false;
        } finally {
            controller.setCurrentActionSource(null);
        }
    }

    private static boolean processVirtual(CraftingCpuAdapter adapter, CpuOperations cpu,
            TileAssemblyController controller, ICraftingPatternDetails details, Object progress,
            long remaining, IItemList<IAEItemStack> waitingFor) {
        long allowance = controller.allowanceFor(details);
        if (allowance <= 0) {
            return false;
        }
        long batchSize = Math.min(remaining, allowance);
        IActionSource source = cpu.actionSource();
        controller.setCurrentActionSource(source);
        try {
            appeng.api.storage.IMEInventory<IAEItemStack> meInv = adapter.inventory();
            IItemList<IAEItemStack> itemList = meInv instanceof MECraftingInventory
                    ? ((MECraftingInventory) meInv).getItemList() : null;
            boolean canExtract = true;
            for (int retry = 0; retry < 5; retry++) {
                canExtract = true;
                for (IAEItemStack input : details.getCondensedInputs()) {
                    if (input == null || input.getStackSize() <= 0) {
                        continue;
                    }
                    long totalNeed = AssemblyBatchSupport.checkedAmount(input.getStackSize(), batchSize);
                    if (totalNeed <= 0) {
                        canExtract = false;
                        batchSize = 0;
                        break;
                    }
                    IAEItemStack need = input.copy();
                    need.setStackSize(totalNeed);
                    IAEItemStack simulated = meInv.extractItems(need, Actionable.SIMULATE, source);
                    if (simulated == null || simulated.getStackSize() < totalNeed) {
                        long available = simulated != null ? simulated.getStackSize() : 0;
                        long missing = totalNeed - available;
                        if (missing > 0) {
                            IAEItemStack toFetch = input.copy();
                            toFetch.setStackSize(missing);
                            IAEItemStack fetched = cpu.fetchFromNetwork(toFetch, source);
                            if (fetched != null && fetched.getStackSize() > 0) {
                                meInv.injectItems(fetched, Actionable.MODULATE, source);
                                simulated = meInv.extractItems(need, Actionable.SIMULATE, source);
                                if (simulated != null && simulated.getStackSize() >= totalNeed) {
                                    continue;
                                }
                                available = simulated != null ? simulated.getStackSize() : 0;
                            }
                        }
                        long maxBatch = available / input.getStackSize();
                        if (maxBatch > 0) {
                            batchSize = Math.min(batchSize, maxBatch);
                            canExtract = false;
                        } else {
                            canExtract = false;
                            batchSize = 0;
                            break;
                        }
                    }
                }
                if (canExtract) {
                    break;
                }
            }
            if (!canExtract || batchSize <= 0) {
                return false;
            }
            List<IAEItemStack> requests = new ArrayList<>();
            List<IAEItemStack> products = new ArrayList<>();
            for (IAEItemStack input : details.getCondensedInputs()) {
                if (input != null && input.getStackSize() > 0) {
                    requests.add(AssemblyBatchSupport.scaledCopy(input, batchSize));
                }
            }
            for (IAEItemStack output : details.getCondensedOutputs()) {
                if (output != null && output.getStackSize() > 0) {
                    products.add(AssemblyBatchSupport.scaledCopy(output, batchSize));
                }
            }
            List<IAEItemStack> extracted = AssemblyBatchSupport.extractExact(meInv, requests, source);
            if (extracted == null) {
                return false;
            }
            postExtraction(adapter, extracted, source);
            controller.chargeCraftEnergy(batchSize);
            for (IAEItemStack product : products) {
                if (waitingFor != null) {
                    waitingFor.add(product.copy());
                }
                IAEItemStack leftover = cpu.inject(product.copy(), Actionable.MODULATE, source);
                if (leftover != null && leftover.getStackSize() > 0) {
                    itemList.add(leftover);
                    ((IMeInventoryVersionAccess) meInv).ae2e$bumpInjectVersion();
                    adapter.postChange(leftover.copy(), source);
                    adapter.postCraftingStatusChange(leftover.copy());
                }
            }
            adapter.setTaskRemaining(progress, remaining - batchSize);
            controller.setBatchBusy(true);
            controller.claimParallel(details, batchSize);
            return true;
        } finally {
            controller.setCurrentActionSource(null);
        }
    }

    private static void postExtraction(CraftingCpuAdapter adapter, List<IAEItemStack> extracted,
            IActionSource source) {
        for (IAEItemStack actual : extracted) {
            IAEItemStack diff = actual.copy();
            diff.setStackSize(-diff.getStackSize());
            adapter.postChange(diff, source);
            adapter.postCraftingStatusChange(diff);
        }
    }

    private static InventoryCrafting craftingInventory() {
        return new InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return false;
            }
        }, 3, 3);
    }

    private static IAEItemStack fetchFromNetwork(CraftingCPUCluster cpu, IAEItemStack request,
            IActionSource source) {
        try {
            IActionSource actionSource = cpu.getActionSource();
            if (actionSource instanceof MachineSource) {
                Optional<IActionHost> host = ((MachineSource) actionSource).machine();
                if (host.isPresent()) {
                    IGridNode node = host.get().getActionableNode();
                    if (node != null) {
                        IGrid grid = node.getGrid();
                        if (grid != null) {
                            appeng.api.networking.storage.IStorageGrid storageGrid =
                                    grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
                            IItemStorageChannel channel = AEApi.instance().storage()
                                    .getStorageChannel(IItemStorageChannel.class);
                            IMEMonitor<IAEItemStack> storage = storageGrid.getInventory(channel);
                            return storage.extractItems(request, Actionable.MODULATE, source);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] fetchFromNetwork failed: {}", e.toString());
        }
        return null;
    }

    public interface CpuOperations {
        IActionSource actionSource();

        IAEItemStack inject(IAEItemStack stack, Actionable mode, IActionSource source);

        IAEItemStack fetchFromNetwork(IAEItemStack request, IActionSource source);
    }

    public interface MediumAccess {
        boolean hasAssemblyHub();

        List<ICraftingMedium> mediums(ICraftingPatternDetails details);
    }
}
