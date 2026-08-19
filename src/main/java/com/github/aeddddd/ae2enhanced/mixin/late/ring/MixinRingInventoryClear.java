package com.github.aeddddd.ae2enhanced.mixin.late.ring;

import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞升凭证防清除：
 * 1) /clear 等调用 InventoryPlayer.clear() 时保留飞升凭证(HEAD 快照,RETURN 还原).
 * 2) clearMatchingItems(/clear 指定物品)执行期间暂时藏匿飞升凭证,结束后放回.
 * MC 原生类; remap=false + MCP/SRG 双名数组(规避 jar 内 refmap 滞后问题).
 * 三个清单字段为 public 目标,不允许带别名 shadow,故直接强转访问
 * (普通字段引用由 reobf 按 mcp-srg 重命名,双环境兼容).
 */
@Mixin(value = InventoryPlayer.class, remap = false)
public class MixinRingInventoryClear {

    private final List<ItemStack> ae2e$preserved = new ArrayList<>();
    private final List<int[]> ae2e$preservedSlots = new ArrayList<>();

    @Inject(method = {"clear", "func_174888_l"}, at = @At("HEAD"))
    private void ae2e$snapshotRings(CallbackInfo ci) {
        ae2e$preserved.clear();
        ae2e$preservedSlots.clear();
        InventoryPlayer self = (InventoryPlayer) (Object) this;
        snapshotFrom(self.mainInventory, 0);
        snapshotFrom(self.armorInventory, 1);
        snapshotFrom(self.offHandInventory, 2);
    }

    @Inject(method = {"clear", "func_174888_l"}, at = @At("RETURN"))
    private void ae2e$restoreRings(CallbackInfo ci) {
        ae2e$restorePreserved();
    }

    /** clearMatchingItems(/clear 指定物品路径): HEAD 藏匿凭证,RETURN 放回. */
    @Inject(method = {"clearMatchingItems", "func_174925_a"}, at = @At("HEAD"))
    private void ae2e$stashBeforeMatchingClear(CallbackInfoReturnable<Integer> cir) {
        ae2e$snapshotRings(new CallbackInfo("clearMatchingItems", false));
        // 藏匿: 让方法体看不到凭证(不会被匹配/计数/移除)
        for (int i = 0; i < ae2e$preserved.size(); i++) {
            int[] loc = ae2e$preservedSlots.get(i);
            NonNullList<ItemStack> list = ae2e$list(loc[0]);
            if (loc[1] >= 0 && loc[1] < list.size() && list.get(loc[1]) == ae2e$preserved.get(i)) {
                list.set(loc[1], ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = {"clearMatchingItems", "func_174925_a"}, at = @At("RETURN"))
    private void ae2e$unstashAfterMatchingClear(CallbackInfoReturnable<Integer> cir) {
        ae2e$restorePreserved();
    }

    private void ae2e$restorePreserved() {
        for (int i = 0; i < ae2e$preserved.size(); i++) {
            int[] loc = ae2e$preservedSlots.get(i);
            NonNullList<ItemStack> list = ae2e$list(loc[0]);
            if (loc[1] >= 0 && loc[1] < list.size() && list.get(loc[1]).isEmpty()) {
                list.set(loc[1], ae2e$preserved.get(i));
            }
        }
        ae2e$preserved.clear();
        ae2e$preservedSlots.clear();
    }

    private NonNullList<ItemStack> ae2e$list(int which) {
        InventoryPlayer self = (InventoryPlayer) (Object) this;
        return which == 0 ? self.mainInventory : (which == 1 ? self.armorInventory : self.offHandInventory);
    }

    private void snapshotFrom(NonNullList<ItemStack> list, int which) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (!s.isEmpty() && s.getItem() instanceof ItemNetworkLinkCredential && RingNBT.isAscended(s)) {
                ae2e$preserved.add(s);
                ae2e$preservedSlots.add(new int[]{which, i});
            }
        }
    }
}
