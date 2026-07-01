package com.github.aeddddd.ae2enhanced.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;

/**
 * 将 {@link com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPU}
 * 注入到 AE2 的 CraftingService 中，使其参与 CPU 选择与合成调度。
 */
@Mixin(value = CraftingService.class, remap = false)
public class MixinCraftingService {

    @Shadow(remap = false)
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow(remap = false)
    @Final
    private IGrid grid;

    @Inject(method = "onServerEndTick", at = @At("TAIL"), remap = false)
    private void ae2e$injectVirtualCpus(CallbackInfo ci) {
        // 先清理注册表中已失效或已销毁的虚拟 CPU，并把它们从本网格的集群集合中移除。
        // 再为本网格重新加入仍然活跃的虚拟集群，保证列表与注册表状态一致。
        this.craftingCPUClusters.removeAll(VirtualCraftingCPURegistry.getClusters());
        for (CraftingCPUCluster cluster : VirtualCraftingCPURegistry.getClusters()) {
            if (cluster.isDestroyed() || !cluster.isActive()) {
                VirtualCraftingCPURegistry.unregister(cluster);
                continue;
            }
            if (cluster.getGrid() == this.grid) {
                this.craftingCPUClusters.add(cluster);
            }
        }
    }
}
