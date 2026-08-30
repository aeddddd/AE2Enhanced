package com.github.aeddddd.ae2enhanced.mixin.late.mmce;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import github.kasuminova.mmce.common.tile.MEPatternProvider;
import github.kasuminova.mmce.common.tile.base.MEMachineComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mixin MEPatternProvider：支持智能样板的虚拟展开.
 *
 * <p>MMCE 机械样板供应器的 {@code details} 列表与 36 个样板槽位一一对应,
 * 原版 {@code refreshPattern} 通过 {@code getPatternForItem} 每槽只能获得单个 detail,
 * 而智能样板({@link ItemSmartPattern})的 {@code getPatternForItem} 返回 null.</p>
 *
 * <p>适配策略：</p>
 * <ul>
 *   <li>{@code refreshPattern} HEAD 拦截：智能样板展开为多个
 *       {@link SmartPatternSubDetails},首个存入 {@code details}(保持槽位索引语义,
 *       供 {@code setCurrentPattern}/{@code refreshPatterns} 使用),其余存入
 *       {@code ae2e$extraDetails}(按槽位分组).</li>
 *   <li>{@code provideCrafting} RETURN 追加：把 {@code ae2e$extraDetails} 中的
 *       展开 detail 全部注册为 AE2 合成选项,使控制器能读取到智能样板中的全部配方.</li>
 *   <li>{@code setCurrentPattern} HEAD 适配：增强阻挡模式下,若被推送的 detail
 *       是智能样板的非首个展开项,按配方内容在 {@code ae2e$extraDetails} 中反查所属槽位,
 *       保证 {@code currentPatternIdx} 正确持久化.</li>
 * </ul>
 */
@Mixin(value = MEPatternProvider.class, remap = false)
public abstract class MixinMEPatternProvider {

    @Shadow
    protected AppEngInternalInventory patterns;

    /** MMCE-CE 2.3.2 起为定长数组(36 槽位),旧版 MMCE 为 List——本 mixin 仅适配 CE. */
    @Shadow
    protected ICraftingPatternDetails[] details;

    @Shadow
    protected MEPatternProvider.WorkModeSetting workMode;

    @Shadow
    protected int currentPatternIdx;

    @Shadow
    protected ICraftingPatternDetails currentPattern;

    @Shadow
    private void resetCurrentPattern() {
    }

    /**
     * 每个样板槽位展开出的额外配方详情(首个存于 details,其余存于此).
     */
    private final Map<Integer, List<ICraftingPatternDetails>> ae2e$extraDetails = new HashMap<>();

    /**
     * 拦截 refreshPattern,对智能样板进行虚拟展开.
     */
    @Inject(method = "refreshPattern", at = @At("HEAD"), cancellable = true)
    private void ae2e$refreshSmartPattern(int slot, CallbackInfo ci) {
        // 无论槽位内容如何,先清理该槽位的智能样板展开缓存
        this.ae2e$extraDetails.remove(slot);

        ItemStack stack = this.patterns.getStackInSlot(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSmartPattern)) {
            return; // 非智能样板：走原逻辑
        }
        ci.cancel();

        World world = ((TileEntity) (Object) this).getWorld();
        List<SmartPatternSubDetails> expanded = ItemSmartPattern.expandPatterns(stack, world);

        ICraftingPatternDetails first = expanded.isEmpty() ? null : expanded.get(0);
        this.details[slot] = first;
        if (expanded.size() > 1) {
            this.ae2e$extraDetails.put(slot, new ArrayList<>(expanded.subList(1, expanded.size())));
        }

        // 复刻原版增强阻挡模式的 currentPattern 维护逻辑,
        // 但 currentPattern 只需命中该智能样板的任意一个展开项即视为仍有效
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

    /**
     * 追加注册智能样板展开出的额外配方详情,使 AE2 网络感知全部配方.
     */
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

    /**
     * 增强阻挡模式适配：被推送的 detail 是智能样板非首个展开项时,
     * 按配方内容反查所属槽位,保证 currentPatternIdx 正确.
     */
    @Inject(method = "setCurrentPattern", at = @At("HEAD"), cancellable = true)
    private void ae2e$setCurrentPatternSmart(ICraftingPatternDetails pattern, CallbackInfo ci) {
        if (pattern == null || this.ae2e$extraDetails.isEmpty()) {
            return;
        }
        // MMCE-CE 2.3.2 起 details 为定长数组,手动线性查找替代 List.indexOf
        for (ICraftingPatternDetails d : this.details) {
            if (pattern.equals(d)) {
                return; // 原逻辑可处理
            }
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
}
