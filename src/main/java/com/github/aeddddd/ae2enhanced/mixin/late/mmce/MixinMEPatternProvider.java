package com.github.aeddddd.ae2enhanced.mixin.late.mmce;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import com.github.aeddddd.ae2enhanced.crafting.compat.MMCEPatternProviderReflect;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import github.kasuminova.mmce.common.tile.MEPatternProvider;
import github.kasuminova.mmce.common.tile.base.MEMachineComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** MMCE 机械样板供应器的智能样板展开、阻挡模式与部署刷新适配。 */
@Mixin(value = MEPatternProvider.class, remap = false)
public abstract class MixinMEPatternProvider {

    @Shadow
    protected AppEngInternalInventory patterns;

    @Shadow
    protected MEPatternProvider.WorkModeSetting workMode;

    @Shadow
    protected int currentPatternIdx;

    @Shadow
    protected ICraftingPatternDetails currentPattern;

    @Shadow
    private void resetCurrentPattern() {
    }

    @Unique
    private final Map<Integer, List<ICraftingPatternDetails>> ae2e$extraDetails = new TreeMap<>();
    @Unique
    private final Map<UUID, SmartPatternRepository.ListenerRegistration> ae2e$smartPatternListeners = new HashMap<>();

    @Shadow
    protected abstract void refreshPatterns();

    @Inject(method = "refreshPattern", at = @At("HEAD"), cancellable = true)
    private void ae2e$refreshSmartPattern(int slot, CallbackInfo ci) {
        ItemStack stack = this.patterns.getStackInSlot(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSmartPattern)) {
            this.ae2e$extraDetails.remove(slot);
            return;
        }
        ci.cancel();

        World world = ((TileEntity) (Object) this).getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        List<SmartPatternSubDetails> expanded = new ArrayList<>();
        for (SmartPatternSubDetails detail : ItemSmartPattern.expandPatterns(stack, world)) {
            if (!detail.isCraftable()) {
                expanded.add(detail);
            }
        }
        MMCEPatternProviderReflect.DetailsAccess details = MMCEPatternProviderReflect.details(this);
        this.ae2e$extraDetails.remove(slot);
        ICraftingPatternDetails first = expanded.isEmpty() ? null : expanded.get(0);
        details.set(slot, first);
        if (expanded.size() > 1) {
            this.ae2e$extraDetails.put(slot, new ArrayList<>(expanded.subList(1, expanded.size())));
        }

        if (this.workMode == MEPatternProvider.WorkModeSetting.ENHANCED_BLOCKING_MODE
                && slot == this.currentPatternIdx) {
            if (this.currentPattern == null) {
                this.currentPattern = first;
            } else {
                boolean stillValid = false;
                for (ICraftingPatternDetails detail : expanded) {
                    if (detail.equals(this.currentPattern)) {
                        stillValid = true;
                        break;
                    }
                }
                if (!stillValid) {
                    this.resetCurrentPattern();
                }
            }
        }
    }

    @Inject(method = "provideCrafting", at = @At("RETURN"))
    private void ae2e$provideSmartPatternExtras(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        if (this.ae2e$extraDetails.isEmpty()) {
            return;
        }
        AENetworkProxy proxy = ((MEMachineComponent) (Object) this).getProxy();
        if (!proxy.isActive() || !proxy.isPowered()) {
            return;
        }
        for (List<ICraftingPatternDetails> extras : this.ae2e$extraDetails.values()) {
            for (ICraftingPatternDetails detail : extras) {
                craftingTracker.addCraftingOption((ICraftingMedium) this, detail);
            }
        }
    }

    @Inject(method = "setCurrentPattern", at = @At("HEAD"), cancellable = true)
    private void ae2e$setCurrentPatternSmart(ICraftingPatternDetails pattern, CallbackInfo ci) {
        if (pattern == null || this.ae2e$extraDetails.isEmpty()) {
            return;
        }
        if (MMCEPatternProviderReflect.details(this).indexOf(pattern) >= 0) {
            return;
        }
        for (Map.Entry<Integer, List<ICraftingPatternDetails>> entry : this.ae2e$extraDetails.entrySet()) {
            for (ICraftingPatternDetails detail : entry.getValue()) {
                if (detail.equals(pattern)) {
                    ci.cancel();
                    this.currentPatternIdx = entry.getKey();
                    this.currentPattern = pattern;
                    return;
                }
            }
        }
    }

    @Inject(method = "refreshPatterns", at = @At("RETURN"))
    private void ae2e$syncSmartPatternListeners(CallbackInfo ci) {
        World world = ((TileEntity) (Object) this).getWorld();
        if (world == null || world.isRemote || !RuntimeScopes.currentServer().isPresent()) {
            return;
        }

        Set<UUID> deployed = new HashSet<>();
        for (int slot = 0; slot < this.patterns.getSlots(); slot++) {
            UUID id = ItemSmartPattern.getPatternDataId(this.patterns.getStackInSlot(slot));
            if (id != null) {
                deployed.add(id);
            }
        }

        java.util.Iterator<Map.Entry<UUID, SmartPatternRepository.ListenerRegistration>> iterator =
                this.ae2e$smartPatternListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SmartPatternRepository.ListenerRegistration> entry = iterator.next();
            if (!deployed.contains(entry.getKey())) {
                entry.getValue().close();
                iterator.remove();
            }
        }

        for (UUID id : deployed) {
            if (this.ae2e$smartPatternListeners.containsKey(id)) {
                continue;
            }
            WeakReference<MEPatternProvider> self = new WeakReference<>((MEPatternProvider) (Object) this);
            SmartPatternRepository.ListenerRegistration registration = SmartPatternRepository.get(world).listen(id,
                    (changedId, revision) -> {
                        MEPatternProvider provider = self.get();
                        if (provider != null) {
                            ((MixinMEPatternProvider) (Object) provider).ae2e$onSmartPatternChanged(changedId);
                        }
                    });
            this.ae2e$smartPatternListeners.put(id, registration);
        }
    }

    @Unique
    private void ae2e$onSmartPatternChanged(UUID id) {
        for (int slot = 0; slot < this.patterns.getSlots(); slot++) {
            if (id.equals(ItemSmartPattern.getPatternDataId(this.patterns.getStackInSlot(slot)))) {
                this.refreshPatterns();
                return;
            }
        }
        SmartPatternRepository.ListenerRegistration registration = this.ae2e$smartPatternListeners.remove(id);
        if (registration != null) {
            registration.close();
        }
    }
}
