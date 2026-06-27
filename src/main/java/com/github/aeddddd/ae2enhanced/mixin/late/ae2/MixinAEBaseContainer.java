package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.helpers.InventoryAction;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E2a：阻止从 AE2 终端提取假物品(流体/气体/源质).
 * 无论左键、右键还是 Shift+点击,只要目标物品是假物品,直接取消 doAction.
 * 这是比 MixinNetworkMonitor.extractItems 更可靠的兜底拦截,因为某些终端实现
 * 可能使用自定义的 IMEMonitor,其 extractItems 不会走到 NetworkMonitor.
 *
 * 本 mixin 位于 mixins.ae2enhanced.late.json 中,无条件加载.
 * 源质检查使用字符串比较,避免 Thaumcraft 不存在时加载 ItemEssentiaDrop 导致 NoClassDefFoundError.
 */
@Mixin(value = AEBaseContainer.class, remap = false)
public class MixinAEBaseContainer {

    private static final String ESSENTIA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop";
    private static final String GAS_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemGasDrop";
    private static final String ENERGY_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop";
    private static final String MANA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemManaDrop";
    private static final String STARLIGHT_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop";
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$preventFakeItemExtraction(EntityPlayerMP player, InventoryAction action, int slot, long id,
                                                        CallbackInfo ci) {
        IAEItemStack slotItem = ((AEBaseContainer) (Object) this).getTargetStack();
        if (slotItem == null) return;

        ItemStack mcStack = slotItem.createItemStack();
        if (mcStack.isEmpty()) return;

        String itemClass = mcStack.getItem().getClass().getName();

        // 拦截源质假物品(避免直接引用 ItemEssentiaDrop 类)
        if (ESSENTIA_DROP_CLASS.equals(itemClass)) {
            ci.cancel();
            return;
        }

        // 拦截流体假物品
        if (ItemFluidDrop.isFluidDrop(mcStack)) {
            ci.cancel();
            return;
        }

        // 拦截气体假物品(避免直接引用 ItemGasDrop 类)
        if (GAS_DROP_CLASS.equals(itemClass)) {
            ci.cancel();
            return;
        }

        // 拦截 RF 能量假物品
        if (ENERGY_DROP_CLASS.equals(itemClass)) {
            ci.cancel();
            return;
        }

        // 拦截 Mana 假物品(Botania)
        if (MANA_DROP_CLASS.equals(itemClass)) {
            ci.cancel();
            return;
        }

        // 拦截 Starlight 假物品(Astral Sorcery)
        if (STARLIGHT_DROP_CLASS.equals(itemClass)) {
            ci.cancel();
        }
    }
}
