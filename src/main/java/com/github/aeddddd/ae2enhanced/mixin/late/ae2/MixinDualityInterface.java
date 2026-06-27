package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mixin DualityInterface：支持智能样板的虚拟展开.
 *
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
 */
@Mixin(value = DualityInterface.class, remap = false)
public class MixinDualityInterface {

    @Shadow
    private Set<ICraftingPatternDetails> craftingList;

    @Shadow
    private IInterfaceHost iHost;

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
}
