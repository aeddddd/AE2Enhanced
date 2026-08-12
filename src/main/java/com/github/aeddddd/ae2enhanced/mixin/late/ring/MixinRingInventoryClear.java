package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞升指环防清除：/clear 等调用 InventoryPlayer.clear() 时保留飞升指环.
 * HEAD 快照,RETURN 还原. MC 原生类,remap=true.
 */
@Mixin(value = InventoryPlayer.class, remap = true)
public class MixinRingInventoryClear {

    @Final
    @Shadow
    public NonNullList<ItemStack> mainInventory;
    @Final
    @Shadow
    public NonNullList<ItemStack> armorInventory;
    @Final
    @Shadow
    public NonNullList<ItemStack> offHandInventory;

    private final List<ItemStack> ae2e$preserved = new ArrayList<>();
    private final List<int[]> ae2e$preservedSlots = new ArrayList<>();

    @Inject(method = "clear", at = @At("HEAD"))
    private void ae2e$snapshotRings(CallbackInfo ci) {
        ae2e$preserved.clear();
        ae2e$preservedSlots.clear();
        snapshotFrom(mainInventory, 0);
        snapshotFrom(armorInventory, 1);
        snapshotFrom(offHandInventory, 2);
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void ae2e$restoreRings(CallbackInfo ci) {
        for (int i = 0; i < ae2e$preserved.size(); i++) {
            int[] loc = ae2e$preservedSlots.get(i);
            NonNullList<ItemStack> list = loc[0] == 0 ? mainInventory : (loc[0] == 1 ? armorInventory : offHandInventory);
            if (loc[1] >= 0 && loc[1] < list.size() && list.get(loc[1]).isEmpty()) {
                list.set(loc[1], ae2e$preserved.get(i));
            }
        }
        ae2e$preserved.clear();
        ae2e$preservedSlots.clear();
    }

    private void snapshotFrom(NonNullList<ItemStack> list, int listId) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemNetworkLinkCredential && RingNBT.isAscended(stack)) {
                ae2e$preserved.add(stack);
                ae2e$preservedSlots.add(new int[]{listId, i});
            }
        }
    }
}
