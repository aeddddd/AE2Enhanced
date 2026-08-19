package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.util.CraftFuzzyCandidateCache;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 修复 AE2-UEL 合成终端 shift 合成一组时随库存规模线性膨胀的卡顿.
 *
 * 原实现: 只要精确物品缺失且模板注册了矿物词典(或带 NBT/耐久通配),
 * fuzzy 分支会对全网络物品列表做 O(N) 遍历并附带配方重匹配;
 * shift 合成时外层 SlotCraftingTerm.doClick 单 tick 内最多 64 次合成 × 9 个原料格,
 * 形成 64×9×N 的级联开销.
 *
 * 本 mixin 完整复刻原方法逻辑, 仅将 fuzzy 分支的全表扫描结果按 tick 缓存
 * (见 CraftFuzzyCandidateCache), 候选被抽空时自动失效并重扫, 语义与原实现一致.
 *
 * 位于 mixins.ae2enhanced.late.json, 无条件加载.
 */
@Mixin(value = Platform.class, remap = false)
public abstract class MixinPlatformCraftExtract {

    @Inject(method = "extractItemsByRecipe", at = @At("HEAD"), cancellable = true)
    private static void ae2enhanced$cacheFuzzyCandidates(IEnergySource energySrc, IActionSource mySrc,
                                                         IMEMonitor<IAEItemStack> src, World w, IRecipe r,
                                                         ItemStack output, InventoryCrafting ci,
                                                         ItemStack providedTemplate, int slot,
                                                         IItemList<IAEItemStack> items, Actionable realForFake,
                                                         IPartitionList<IAEItemStack> filter,
                                                         CallbackInfoReturnable<ItemStack> cir) {
        // ===== 与原方法一致的快速路径 =====
        if (energySrc.extractAEPower(1.0, Actionable.SIMULATE, PowerMultiplier.CONFIG) <= 0.9) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (providedTemplate == null) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        AEItemStack aeReq = AEItemStack.fromItemStack(providedTemplate);
        aeReq.setStackSize(1L);
        if (filter == null || filter.isListed(aeReq)) {
            IAEItemStack aeExt = src.extractItems(aeReq, realForFake, mySrc);
            if (aeExt != null) {
                ItemStack extracted = aeExt.createItemStack();
                if (!extracted.isEmpty()) {
                    energySrc.extractAEPower(1.0, realForFake, PowerMultiplier.CONFIG);
                    cir.setReturnValue(extracted);
                    return;
                }
            }
        }

        boolean checkFuzzy = aeReq.getOre().isPresent()
                || providedTemplate.getItemDamage() == Short.MAX_VALUE
                || providedTemplate.hasTagCompound()
                || providedTemplate.isItemStackDamageable();
        if (items == null || !checkFuzzy) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        // ===== fuzzy 分支: 优先使用本 tick 内缓存的候选列表 =====
        List<IAEItemStack> cached = CraftFuzzyCandidateCache.get(w, items, aeReq);
        if (cached != null) {
            if (cached.isEmpty()) {
                // 本 tick 已确认无任何候选
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            ItemStack result = ae2enhanced$tryExtract(cached, energySrc, mySrc, src, w, r, output, ci,
                    providedTemplate, slot, realForFake, filter);
            if (result != null) {
                cir.setReturnValue(result);
                return;
            }
            // 缓存候选全部失效(被抽空或配方上下文变化), 移除后全量重扫
            CraftFuzzyCandidateCache.invalidate(w, items, aeReq);
        }

        // ===== 全量扫描(原 fuzzy 逻辑), 同时记录候选 =====
        List<IAEItemStack> candidates = new ArrayList<>();
        for (IAEItemStack x : items) {
            ItemStack sh = x.getDefinition();
            if ((!Platform.itemComparisons().isEqualItemType(providedTemplate, sh) && !aeReq.sameOre(x))
                    || ItemStack.areItemsEqual(sh, output)) {
                continue;
            }
            candidates.add(x);
            ItemStack cp = sh.copy();
            cp.setCount(1);
            ci.setInventorySlotContents(slot, cp);
            if (r.matches(ci, w) && ItemStack.areItemsEqual(r.getCraftingResult(ci), output)) {
                IAEItemStack ax = x.copy();
                ax.setStackSize(1L);
                if (filter == null || filter.isListed(ax)) {
                    IAEItemStack ex = src.extractItems(ax, realForFake, mySrc);
                    if (ex != null) {
                        energySrc.extractAEPower(1.0, realForFake, PowerMultiplier.CONFIG);
                        CraftFuzzyCandidateCache.put(w, items, aeReq, candidates);
                        cir.setReturnValue(ex.createItemStack());
                        return;
                    }
                }
            }
            ci.setInventorySlotContents(slot, providedTemplate);
        }
        // 扫描完整结束仍未提取成功: 缓存已发现的候选(空列表表示确认无候选, 本 tick 内不再重扫)
        CraftFuzzyCandidateCache.put(w, items, aeReq, candidates);
        cir.setReturnValue(ItemStack.EMPTY);
    }

    /**
     * 遍历缓存候选并尝试提取, 候选判定逻辑与原 fuzzy 循环体一致.
     * 返回 null 表示所有候选均不可用.
     */
    @Unique
    private static ItemStack ae2enhanced$tryExtract(List<IAEItemStack> candidates, IEnergySource energySrc,
                                                    IActionSource mySrc, IMEMonitor<IAEItemStack> src, World w,
                                                    IRecipe r, ItemStack output, InventoryCrafting ci,
                                                    ItemStack providedTemplate, int slot, Actionable realForFake,
                                                    IPartitionList<IAEItemStack> filter) {
        for (IAEItemStack x : candidates) {
            ItemStack cp = x.getDefinition().copy();
            cp.setCount(1);
            ci.setInventorySlotContents(slot, cp);
            if (r.matches(ci, w) && ItemStack.areItemsEqual(r.getCraftingResult(ci), output)) {
                IAEItemStack ax = x.copy();
                ax.setStackSize(1L);
                if (filter == null || filter.isListed(ax)) {
                    IAEItemStack ex = src.extractItems(ax, realForFake, mySrc);
                    if (ex != null) {
                        energySrc.extractAEPower(1.0, realForFake, PowerMultiplier.CONFIG);
                        return ex.createItemStack();
                    }
                }
            }
            ci.setInventorySlotContents(slot, providedTemplate);
        }
        return null;
    }
}
