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
import appeng.api.storage.data.IAEItemStack;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.crafting.CraftingLink;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Mixin into {@link CraftingGridCache} to recognise {@link TileComputationCore} virtual CraftingCPUClusters.
 *
 * <p>AE2-UEL stores CPUs in {@code Set<CraftingCPUCluster>} and rebuilds it from physical
 * {@link appeng.tile.crafting.TileCraftingStorageTile} machines. This mixin:</p>
 * <ul>
 *   <li>Tracks {@link TileComputationCore} instances via addNode/removeNode</li>
 *   <li>Re-injects virtual clusters into {@code craftingCPUClusters} after each rebuild</li>
 *   <li>Provides fallback job submission that dynamically spawns new virtual clusters</li>
 * </ul>
 */
@Mixin(value = CraftingGridCache.class, remap = false)
public class MixinCraftingGridCache {

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    public void updateCPUClusters(MENetworkCraftingCpuChange event) {
        // shadow
    }

    @Shadow
    public void addLink(CraftingLink link) {
        // shadow
    }

    @Unique
    private final Set<TileComputationCore> ae2enhanced$computationCores = new HashSet<>();

    // ==================== Node Lifecycle ====================

    @Inject(method = "addNode", at = @At("HEAD"))
    private void ae2enhanced$onAddNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        if (host instanceof TileComputationCore) {
            TileComputationCore core = (TileComputationCore) host;
            ae2enhanced$computationCores.add(core);
            if (core.isFormed()) {
                updateCPUClusters(new MENetworkCraftingCpuChange(node));
            }
        }
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void ae2enhanced$onRemoveNode(IGridNode node, IGridHost host, CallbackInfo ci) {
        if (host instanceof TileComputationCore) {
            TileComputationCore core = (TileComputationCore) host;
            ae2enhanced$computationCores.remove(core);
            updateCPUClusters(new MENetworkCraftingCpuChange(node));
        }
    }

    // ==================== CPU Cluster Rebuild ====================

    @Inject(method = "updateCPUClusters()V", at = @At("TAIL"))
    private void ae2enhanced$injectComputationCores(CallbackInfo ci) {
        int injected = 0;
        for (TileComputationCore core : ae2enhanced$computationCores) {
            if (core.isFormed()) {
                for (CraftingCPUCluster cpu : core.getCpuPool()) {
                    this.craftingCPUClusters.add(cpu);
                    injected++;
                    if (cpu.getLastCraftingLink() != null) {
                        this.addLink((CraftingLink) cpu.getLastCraftingLink());
                    }
                }
            }
        }
        // virtual CPUs injected silently
    }

    // ==================== Job Submission Fallback ====================

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true)
    private void ae2enhanced$submitJobFallback(ICraftingJob job, ICraftingRequester requestingMachine,
                                                ICraftingCPU target, boolean prioritizePower, IActionSource src,
                                                CallbackInfoReturnable<ICraftingLink> cir) {
        if (cir.getReturnValue() != null) {
            return; // original already succeeded
        }
        if (job == null || job.isSimulation()) {
            return;
        }
        if (target != null) {
            return; // explicit target was busy or invalid; do not spawn behind user's back
        }
        for (TileComputationCore core : ae2enhanced$computationCores) {
            if (!core.isFormed()) continue;
            ICraftingLink link = core.trySpawnAndSubmitJob(grid, job, src, requestingMachine);
            if (link != null) {
                cir.setReturnValue(link);
                return;
            }
        }
    }

    // ==================== hasCpu ====================

    @Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (cpu instanceof CraftingCPUCluster) {
            for (TileComputationCore core : ae2enhanced$computationCores) {
                if (core.getCpuPool().contains(cpu)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
