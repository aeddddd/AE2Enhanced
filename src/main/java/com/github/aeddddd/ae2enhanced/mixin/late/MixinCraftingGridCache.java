package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.ComputationCoreCPU;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mixin into {@link CraftingGridCache} to recognise {@link TileComputationCore} as a valid Crafting CPU.
 *
 * <p>AE2-UEL hard-codes {@code instanceof TileCraftingTile} inside {@code addNode} and stores CPUs in a
 * {@code Set<CraftingCPUCluster>}. This mixin injects a parallel {@code Set<ComputationCoreCPU>} and extends
 * all relevant accessors so the Computation Core appears in terminal CPU lists and can receive jobs.</p>
 */
@Mixin(value = CraftingGridCache.class, remap = false, priority = 1000)
public class MixinCraftingGridCache {

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    @Final
    private IGrid grid;

    @Unique
    private final Set<ComputationCoreCPU> ae2enhanced$computationCores = new HashSet<>();

    // ==================== Node Lifecycle ====================

    @Inject(method = "addNode", at = @At("HEAD"))
    private void ae2enhanced$onAddNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        if (host instanceof TileComputationCore) {
            TileComputationCore core = (TileComputationCore) host;
            if (core.isFormed()) {
                ComputationCoreCPU cpu = core.getCpuProxy();
                if (cpu != null) {
                    ae2enhanced$computationCores.add(cpu);
                    updateCPUClusters(new MENetworkCraftingCpuChange(node));
                }
            }
        }
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void ae2enhanced$onRemoveNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        if (host instanceof TileComputationCore) {
            TileComputationCore core = (TileComputationCore) host;
            ComputationCoreCPU cpu = core.getCpuProxy();
            if (cpu != null) {
                ae2enhanced$computationCores.remove(cpu);
                updateCPUClusters(new MENetworkCraftingCpuChange(node));
            }
        }
    }

    @Shadow
    public void updateCPUClusters(MENetworkCraftingCpuChange event) {
        // shadow
    }

    // ==================== CPU Enumeration ====================

    /**
     * @author AE2Enhanced
     * @reason Include ComputationCoreCPUs alongside native CraftingCPUClusters in CPU enumeration.
     */
    @SuppressWarnings("OverwriteModifiers")
    @org.spongepowered.asm.mixin.Overwrite
    public ImmutableSet<ICraftingCPU> getCpus() {
        List<ICraftingCPU> all = new ArrayList<>(craftingCPUClusters.size() + ae2enhanced$computationCores.size());
        all.addAll(craftingCPUClusters);
        all.addAll(ae2enhanced$computationCores);
        return ImmutableSet.copyOf(all);
    }

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (cpu instanceof ComputationCoreCPU && ae2enhanced$computationCores.contains(cpu)) {
            cir.setReturnValue(true);
        }
    }

    // ==================== Job Submission ====================

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$submitJob(ICraftingJob job, ICraftingRequester req, ICraftingCPU targetCpu,
                                        boolean prioritizePower, IActionSource src,
                                        CallbackInfoReturnable<ICraftingLink> cir) {
        if (targetCpu instanceof ComputationCoreCPU) {
            ICraftingLink link = ((ComputationCoreCPU) targetCpu).submitJob(grid, job, src, req);
            cir.setReturnValue(link);
        }
    }
}
