package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.helpers.PatternHelper;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IPatternHelperAccess;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * 将 PatternHelper 中的 InventoryCrafting 在 processing 模式下从 4×4 扩展为 10×10,
 * 以支持超过 16 个输入的 processing pattern.
 * 同时实现 {@link IPatternHelperAccess}: 暴露构造时匹配的配方与编码输入模板,
 * 供配方返还物识别(CrT 不消耗物品)使用.
 */
@Mixin(value = PatternHelper.class, remap = false)
public class MixinPatternHelper implements IPatternHelperAccess {

    @Shadow
    @Final
    private boolean isCrafting;

    @Shadow
    @Final
    private IRecipe standardRecipe;

    @Shadow
    @Final
    private InventoryCrafting crafting;

    @Override
    public IRecipe ae2enhanced$standardRecipe() {
        return this.standardRecipe;
    }

    @Override
    public InventoryCrafting ae2enhanced$craftingTemplate() {
        return this.crafting;
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "net/minecraft/inventory/InventoryCrafting",
            remap = true
        )
    )
    public InventoryCrafting onNewInventoryCrafting(Container eventHandler, int width, int height) {
        if (!this.isCrafting) {
            return new InventoryCrafting(eventHandler, 10, 10);
        }
        return new InventoryCrafting(eventHandler, width, height);
    }

    // ---------- NBT 物品校验缓存 ----------

    /**
     * NBT 物品的 isValidItemForSlot 结果缓存.
     *
     * <p>原生 passCache/failCache 的 TestLookup 键只含 (slot,item,meta),
     * 且 markItemAs/getStatus 对带 NBT 的物品永不缓存——每次校验都要走一遍
     * standardRecipe.matches + getCraftingResult 全量模拟(CrT 脚本配方时是
     * MCRecipeShaped.checkRecipe 全网格遍历,spark 采样热点).</p>
     *
     * <p>等价性:对固定 PatternHelper 实例,校验结果是 (slot, item, meta, NBT, 维度)
     * 的纯函数——standardRecipe/correctOutput 均为构造期 final 快照,/ct reload 不改变
     * 已有实例的判定结果(原生行为本就如此),因此缓存与原生逐字节等价.
     * 失效随 PatternHelper 实例生命周期(样板重新解码 = 新实例).</p>
     */
    @Unique
    private final Map<NbtSlotKey, Boolean> ae2enhanced$nbtTestCache = new HashMap<>();

    @Inject(method = "isValidItemForSlot", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$nbtTestLookup(int slotIndex, ItemStack i, World w, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isCrafting || !i.hasTagCompound()) return;
        Boolean cached = this.ae2enhanced$nbtTestCache.get(new NbtSlotKey(slotIndex, i, w));
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "isValidItemForSlot", at = @At("RETURN"))
    private void ae2enhanced$nbtTestStore(int slotIndex, ItemStack i, World w, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isCrafting || !i.hasTagCompound()) return;
        this.ae2enhanced$nbtTestCache.put(new NbtSlotKey(slotIndex, i, w), cir.getReturnValue());
    }

    /** 缓存键:槽位 + 物品 + meta + NBT(拷贝防外部修改) + 维度. */
    private static final class NbtSlotKey {
        private final int slot;
        private final Item item;
        private final int meta;
        private final NBTTagCompound nbt;
        private final int dim;

        NbtSlotKey(int slot, ItemStack stack, World w) {
            this.slot = slot;
            this.item = stack.getItem();
            this.meta = stack.getItemDamage();
            NBTTagCompound tag = stack.getTagCompound();
            this.nbt = tag == null ? new NBTTagCompound() : (NBTTagCompound) tag.copy();
            this.dim = w == null || w.provider == null ? -1 : w.provider.getDimension();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NbtSlotKey)) return false;
            NbtSlotKey k = (NbtSlotKey) o;
            return this.slot == k.slot && this.meta == k.meta && this.dim == k.dim
                && this.item == k.item && this.nbt.equals(k.nbt);
        }

        @Override
        public int hashCode() {
            int h = this.slot;
            h = h * 31 + System.identityHashCode(this.item);
            h = h * 31 + this.meta;
            h = h * 31 + this.nbt.hashCode();
            h = h * 31 + this.dim;
            return h;
        }
    }
}
