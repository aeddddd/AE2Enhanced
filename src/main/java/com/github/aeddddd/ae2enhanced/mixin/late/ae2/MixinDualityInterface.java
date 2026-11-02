package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.MultiCraftingTracker;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IMultiCraftingTrackerAccessor;
import com.google.common.primitives.Ints;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mixin DualityInterface：智能样板虚拟展开 + 合成高峰期性能优化.
 *
 * <h3>1. 智能样板展开</h3>
 * <p>当智能样板({@link ItemSmartPattern})被放入 ME 接口时,
 * 原版 AE2 的 {@code addToCraftingList} 只会将其视为单个样板.
 * 此 Mixin 在 {@code addToCraftingList} 的 HEAD 拦截,将智能样板动态展开为
 * 多个 {@link ICraftingPatternDetails},使 AE2 网络感知到全部配方.</p>
 *
 * <p>关键机制：</p>
 * <ul>
 *   <li>{@code updateCraftingList()} 第1轮循环使用 {@code details.getPattern() != is}
 *       做 identity 比较.{@link com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails#getPattern()}
 *       返回 parent ItemSmartPattern ItemStack,因此智能样板被移除时所有展开的 details
 *       会被正确清理.</li>
 *   <li>{@code craftingList} 是 {@code Set},展开的 details 通过独立的
 *       {@code equals()}/{@code hashCode()}(基于配方内容)确保不重复.</li>
 * </ul>
 *
 * <h3>2. isBusy 同 tick 去重</h3>
 * <p>blocking 模式下 isBusy 对所有朝向做 containsItems 全槽扫描（ae2fc 下还叠加
 * 流体罐/气体罐检查），而 CPU 每 tick × 每 task × 每 medium 调用一次。
 * 扫描结果在同 tick 内不可能有语义变化（库存消化最早也要等接口下一次 tick），
 * 因此按 totalWorldTime 记忆结果；pushPattern 成功会改变 hasItemsToSend 状态，
 * 此时主动失效缓存。</p>
 *
 * <h3>3. usePlan 去除冗余 findPrecise</h3>
 * <p>原生 usePlan 在正需求分支先做 {@code getStorageList().findPrecise(itemStack)}
 * 全网络存储列表扫描，紧接着 poweredExtraction 语义重叠（物品不存在时提取本就会
 * 返回 null）。重排为：先直接 poweredExtraction，仅提取失败时才回退 findPrecise
 * 判断 isCraftable，省掉"有货"路径（大型合成最常见路径）的全网络扫描。</p>
 */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class MixinDualityInterface {

    @Shadow
    private Set<ICraftingPatternDetails> craftingList;

    @Shadow
    private IInterfaceHost iHost;

    @Shadow
    private MultiCraftingTracker craftingTracker;

    @Shadow
    private AENetworkProxy gridProxy;

    @Shadow
    private IActionSource interfaceRequestSource;

    @Shadow
    private IMEInventory<IAEItemStack> destination;

    @Shadow
    private int isWorking;

    @Shadow
    private InventoryAdaptor getAdaptor(int slot) {
        return null;
    }

    @Shadow
    private boolean handleCrafting(int x, InventoryAdaptor d, IAEItemStack itemStack) {
        return false;
    }

    @Shadow
    private void updatePlan(int slot) {
    }

    @Unique
    private final java.util.Map<java.util.UUID, SmartPatternRepository.ListenerRegistration>
            ae2e$smartPatternListeners = new java.util.HashMap<>();

    @Shadow
    private void updateCraftingList() {
    }

    @Shadow
    public net.minecraftforge.items.IItemHandler getPatterns() {
        return null;
    }

    @Inject(method = "updateCraftingList", at = @At("RETURN"))
    private void ae2e$syncSmartPatternListeners(CallbackInfo ci) {
        World world = this.iHost.getTileEntity().getWorld();
        if (world == null || world.isRemote || !RuntimeScopes.currentServer().isPresent()) return;
        java.util.Set<java.util.UUID> deployed = new java.util.HashSet<>();
        net.minecraftforge.items.IItemHandler patterns = getPatterns();
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            java.util.UUID id = ItemSmartPattern.getPatternDataId(patterns.getStackInSlot(slot));
            if (id != null) deployed.add(id);
        }
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, SmartPatternRepository.ListenerRegistration>> iterator =
                ae2e$smartPatternListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<java.util.UUID, SmartPatternRepository.ListenerRegistration> entry = iterator.next();
            if (!deployed.contains(entry.getKey())) {
                entry.getValue().close();
                iterator.remove();
            }
        }
        for (java.util.UUID id : deployed) {
            if (ae2e$smartPatternListeners.containsKey(id)) continue;
            java.lang.ref.WeakReference<DualityInterface> self =
                    new java.lang.ref.WeakReference<>((DualityInterface) (Object) this);
            SmartPatternRepository.ListenerRegistration registration = SmartPatternRepository.get(world).listen(id,
                    (changedId, revision) -> {
                        DualityInterface duality = self.get();
                        if (duality != null) {
                            ((MixinDualityInterface) (Object) duality).ae2e$refreshSmartPattern(changedId);
                        }
                    });
            ae2e$smartPatternListeners.put(id, registration);
        }
    }

    @Unique
    private void ae2e$refreshSmartPattern(java.util.UUID id) {
        net.minecraftforge.items.IItemHandler patterns = getPatterns();
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            if (id.equals(ItemSmartPattern.getPatternDataId(patterns.getStackInSlot(slot)))) {
                updateCraftingList();
                return;
            }
        }
        SmartPatternRepository.ListenerRegistration registration = ae2e$smartPatternListeners.remove(id);
        if (registration != null) registration.close();
    }

    @Redirect(method = "readFromNBT", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/MultiCraftingTracker;readFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V"))
    private void ae2e$deferCraftingLinkRestore(MultiCraftingTracker tracker, net.minecraft.nbt.NBTTagCompound data) {
        // 配置恢复可能通过 updateSetting 取消任务，链接必须延后到 readFromNBT RETURN 恢复
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 0)
    private void ae2e$restoreCraftingLinksAfterConfig(net.minecraft.nbt.NBTTagCompound data, CallbackInfo ci) {
        this.craftingTracker.readFromNBT(data);
    }

    /**
     * 拦截 addToCraftingList,对智能样板进行虚拟展开.
     */
    @Inject(method = "addToCraftingList", at = @At("HEAD"), cancellable = true)
    private void onAddToCraftingList(ItemStack is, CallbackInfo ci) {
        if (!is.isEmpty() && is.getItem() instanceof ItemSmartPattern) {
            ci.cancel();

            if (this.craftingList == null) {
                this.craftingList = new HashSet<>();
            }

            World world = this.iHost.getTileEntity().getWorld();
            List<com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails> expanded =
                    ItemSmartPattern.expandPatterns(is, world);

            if (!expanded.isEmpty()) {
                this.craftingList.addAll(expanded);
            }
        }
    }

    // ==================== isBusy 同 tick 去重 ====================

    @Unique
    private long ae2e$isBusyCacheTick = Long.MIN_VALUE;

    @Unique
    private boolean ae2e$isBusyCacheValue;

    @Inject(method = "isBusy", at = @At("HEAD"), cancellable = true)
    private void ae2e$isBusyUseCachedResult(CallbackInfoReturnable<Boolean> cir) {
        World world = this.iHost.getTileEntity().getWorld();
        if (world != null && world.getTotalWorldTime() == this.ae2e$isBusyCacheTick) {
            cir.setReturnValue(this.ae2e$isBusyCacheValue);
        }
    }

    @Inject(method = "isBusy", at = @At("RETURN"))
    private void ae2e$isBusyStoreResult(CallbackInfoReturnable<Boolean> cir) {
        World world = this.iHost.getTileEntity().getWorld();
        if (world != null) {
            this.ae2e$isBusyCacheTick = world.getTotalWorldTime();
            this.ae2e$isBusyCacheValue = cir.getReturnValue();
        }
    }

    /**
     * pushPattern 成功会置位 hasItemsToSend（isBusy 快路径结果随之改变），主动失效缓存。
     */
    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2e$invalidateBusyCacheOnPushSuccess(ICraftingPatternDetails patternDetails, InventoryCrafting table,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            this.ae2e$isBusyCacheTick = Long.MIN_VALUE;
        }
    }

    // ==================== usePlan 去除冗余 findPrecise ====================

    /**
     * 重排 usePlan 正需求分支：先 poweredExtraction 直接提取，仅失败时回退
     * findPrecise 判断可合成性。负需求（回灌网络）分支与原生逐行一致。
     */
    @Inject(method = "usePlan", at = @At("HEAD"), cancellable = true)
    private void ae2e$usePlanSkipRedundantFindPrecise(int x, IAEItemStack itemStack,
            CallbackInfoReturnable<Boolean> cir) {
        InventoryAdaptor adaptor = this.getAdaptor(x);
        this.isWorking = x;
        boolean changed = false;
        try {
            IMEMonitor<IAEItemStack> monitor = this.gridProxy.getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            this.destination = monitor;
            IEnergyGrid src = this.gridProxy.getEnergy();
            if (itemStack.getStackSize() < 0L) {
                IAEItemStack toStore = itemStack.copy();
                toStore.setStackSize(-toStore.getStackSize());
                long diff = toStore.getStackSize();
                ItemStack canExtract = adaptor.simulateRemove((int) diff, toStore.getDefinition(), null);
                if (canExtract.isEmpty()) {
                    changed = true;
                    throw new GridAccessException();
                }
                toStore = Platform.poweredInsert(src, this.destination, toStore, this.interfaceRequestSource);
                if (toStore != null) {
                    diff -= toStore.getStackSize();
                }
                if (diff != 0L) {
                    changed = true;
                    ItemStack removed = adaptor.removeItems((int) diff, ItemStack.EMPTY, null);
                    if (removed.isEmpty()) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
                    }
                }
            }
            if (((IMultiCraftingTrackerAccessor) this.craftingTracker).ae2e$invokeIsBusy(x)) {
                changed = this.handleCrafting(x, adaptor, itemStack) || changed;
            } else if (itemStack.getStackSize() > 0L) {
                ItemStack inputStack = itemStack.getCachedItemStack(itemStack.getStackSize());
                ItemStack remaining = adaptor.simulateAdd(inputStack);
                if (!remaining.isEmpty()) {
                    itemStack.setCachedItemStack(remaining);
                    changed = true;
                    throw new GridAccessException();
                }
                // 物品在网络中时 poweredExtraction 必然成功，原 findPrecise 门控冗余
                IAEItemStack acquired = Platform.poweredExtraction(src, this.destination, itemStack,
                        this.interfaceRequestSource);
                if (acquired != null) {
                    changed = true;
                    inputStack.setCount(Ints.saturatedCast(acquired.getStackSize()));
                    ItemStack issue = adaptor.addItems(inputStack);
                    if (!issue.isEmpty()) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( addItems )");
                    }
                } else {
                    itemStack.setCachedItemStack(inputStack);
                    IAEItemStack storedStack = monitor.getStorageList().findPrecise(itemStack);
                    if (storedStack != null && storedStack.isCraftable()) {
                        changed = this.handleCrafting(x, adaptor, itemStack) || changed;
                    }
                }
            }
        } catch (GridAccessException ignored) {
            // 与原生一致：作为控制流跳到收尾
        }
        if (changed) {
            this.updatePlan(x);
        }
        this.isWorking = -1;
        cir.setReturnValue(changed);
    }
}
