package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingLink;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.gridcrafting.CraftingGridExecutionContext;
import com.github.aeddddd.ae2enhanced.gridcrafting.CraftingGridService;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** CraftingGridCache 注入壳；状态与业务位于普通 Java context/service。 */
@Mixin(value = CraftingGridCache.class, remap = false)
public class MixinCraftingGridCache implements ICraftingGridCacheAccess {

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;
    @Shadow
    @Final
    private static ExecutorService CRAFTING_POOL;
    @Shadow
    @Final
    private Set<IAEItemStack> emitableItems;
    @Shadow
    @Final
    private IGrid grid;
    @Unique
    private final CraftingGridExecutionContext ae2e$context = new CraftingGridExecutionContext();
    @Shadow
    @Final
    private it.unimi.dsi.fastutil.objects.Object2ObjectMap<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItems;

    @Shadow
    public void updateCPUClusters(MENetworkCraftingCpuChange event) { }
    @Shadow
    public void addLink(CraftingLink link) { }
    @Shadow
    public List<ICraftingMedium> getMediums(ICraftingPatternDetails key) {
        return null;
    }

    @Override
    public boolean ae2enhanced$hasAssemblyHub() {
        return ae2e$context.hasAssemblyHub();
    }

    @Override
    public List<ICraftingMedium> ae2enhanced$getMediumsMemo(ICraftingPatternDetails details) {
        return CraftingGridService.mediums(ae2e$context, details, () -> getMediums(details));
    }

    @Override
    public Set<IAEItemStack> ae2enhanced$craftableKeys() {
        return CraftingGridService.craftableKeys(craftableItems);
    }

    @Override
    public Map<IAEItemStack, List<ICraftingPatternDetails>> ae2enhanced$craftableSnapshot() {
        return CraftingGridService.craftableSnapshot(ae2e$context, craftableItems);
    }

    @Override
    public boolean ae2enhanced$canEmit(IAEItemStack canonKey) {
        return CraftingGridService.canEmit(ae2e$context, emitableItems, canonKey);
    }

    @Override
    public NetworkPatternIndex ae2enhanced$patternIndex() {
        return (NetworkPatternIndex) CraftingGridService.patternIndex(ae2e$context, (ICraftingGrid) (Object) this);
    }

    @Inject(method = "recalculateCraftingPatterns", at = @At("TAIL"))
    private void ae2enhanced$invalidatePatternIndex(CallbackInfo ci) {
        CraftingGridService.recalculated(ae2e$context, craftableItems, emitableItems);
    }

    @Inject(method = "setEmitable", at = @At("RETURN"))
    private void ae2enhanced$onSetEmitable(IAEItemStack someItem, CallbackInfo ci) {
        CraftingGridService.emitableAdded(ae2e$context, someItem);
    }

    @Inject(method = "addCraftingOption", at = @At("HEAD"))
    private void ae2enhanced$invalidateMediumsMemoOnOption(ICraftingMedium medium,
            ICraftingPatternDetails api, CallbackInfo ci) {
        CraftingGridService.craftingOptionAdded(ae2e$context);
    }

    @Inject(method = "beginCraftingJob", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$routeSpecialCalculation(World world, IGrid grid, IActionSource actionSrc,
            IAEItemStack slotItem, ICraftingCallback cb, CallbackInfoReturnable<Future<ICraftingJob>> cir) {
        Future<ICraftingJob> result = CraftingGridService.beginJob(CRAFTING_POOL, world, grid, actionSrc, slotItem, cb);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$routeSpecialJob(ICraftingJob job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource src,
            CallbackInfoReturnable<ICraftingLink> cir) {
        CraftingGridService.RouteResult result = CraftingGridService.routeSpecial(
                ae2e$context, grid, job, requestingMachine, target, src);
        if (result.handled) {
            cir.setReturnValue(result.link);
        }
    }

    @Inject(method = "addNode", at = @At("HEAD"))
    private void ae2enhanced$onAddNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        CraftingGridService.addNode(ae2e$context, node, host, this::updateCPUClusters);
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void ae2enhanced$onRemoveNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        CraftingGridService.removeNode(ae2e$context, node, host, this::updateCPUClusters);
    }

    @Inject(method = "updateCPUClusters()V", at = @At("TAIL"))
    private void ae2enhanced$injectComputationCores(CallbackInfo ci) {
        CraftingGridService.injectCores(ae2e$context, craftingCPUClusters, this::addLink);
    }

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true)
    private void ae2enhanced$submitJobFallback(ICraftingJob job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource src,
            CallbackInfoReturnable<ICraftingLink> cir) {
        CraftingGridService.RouteResult result = CraftingGridService.submitFallback(
                ae2e$context, grid, cir.getReturnValue(), job, requestingMachine, target, src);
        if (result.handled) {
            cir.setReturnValue(result.link);
        }
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (CraftingGridService.hasCpu(ae2e$context, cpu)) {
            cir.setReturnValue(true);
        }
    }
}
