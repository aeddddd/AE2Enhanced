package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.mixin.bridge.IPatternHelperAccess;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * 可合成（工作台）样板构造器：实现 {@link IPatternHelperAccess},配方级剩余物
 * 按槽返还指定输入（模拟 CraftTweaker {@code .reuse()} 的"仅本配方不消耗"语义;
 * 物品本身无容器物，原生 {@code Item.hasContainerItem} 路径识别不到）.
 * <p>与 {@link ProcessingPatternBuilder} 的关键差异：{@code isCraftable()=true}
 * （触发原生精确提取路径）且 {@code isValidItemForSlot=true}
 * （可合成样板的提取受槽位校验门控，见 CraftingTreeNode）.</p>
 */
public class ReusePatternBuilder {

    private final IAEItemStack[] outputs;
    private final List<IAEItemStack> inputs = new ArrayList<>();
    private final Set<IAEItemStack> reusedKeys = new HashSet<>();

    public ReusePatternBuilder(IAEItemStack... outputs) {
        this.outputs = outputs.clone();
    }

    public ReusePatternBuilder addPreciseInput(long count, IAEItemStack input) {
        IAEItemStack in = input.copy();
        in.setStackSize(count);
        this.inputs.add(in);
        return this;
    }

    /**
     * 标记某输入在本配方中不消耗（配方级返还，等同 CrT {@code .reuse()}).
     */
    public ReusePatternBuilder reused(IAEItemStack input) {
        this.reusedKeys.add(RecursiveCraftingHelper.canon(input));
        return this;
    }

    public ICraftingPatternDetails build() {
        final IAEItemStack[] condensedInputs = ProcessingPatternBuilder.condense(this.inputs);
        final IAEItemStack[] condensedOutputs = ProcessingPatternBuilder.condense(
                java.util.Arrays.asList(this.outputs));
        // 模板:每凝聚输入占一格(数量 1;配方级剩余物只关心槽位物品类型)
        final InventoryCrafting template = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < condensedInputs.length && i < template.getSizeInventory(); i++) {
            ItemStack def = condensedInputs[i].getDefinition().copy();
            def.setCount(1);
            template.setInventorySlotContents(i, def);
        }
        final Set<IAEItemStack> reused = new HashSet<>(this.reusedKeys);
        final IRecipe recipe = new TestRecipe(reused);
        return new TestReusePattern(condensedInputs, condensedOutputs, template, recipe);
    }

    /**
     * 测试配方：唯一语义是 getRemainingItems 按槽返还 reuse 标记的输入.
     */
    private static final class TestRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

        private final Set<IAEItemStack> reusedKeys;

        TestRecipe(Set<IAEItemStack> reusedKeys) {
            this.reusedKeys = reusedKeys;
        }

        @Override
        public boolean matches(InventoryCrafting inv, World worldIn) {
            return false; // 计划侧不调用
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canFit(int width, int height) {
            return true;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return ItemStack.EMPTY;
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
            NonNullList<ItemStack> out = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                IAEItemStack ae = AEItemStack.fromItemStack(stack);
                if (ae != null && this.reusedKeys.contains(RecursiveCraftingHelper.canon(ae))) {
                    out.set(i, stack.copy());
                }
            }
            return out;
        }
    }

    /**
     * 测试样板：可合成 + 携带配方/模板访问器.
     */
    private static final class TestReusePattern implements ICraftingPatternDetails, IPatternHelperAccess {

        private final IAEItemStack[] condensedInputs;
        private final IAEItemStack[] condensedOutputs;
        private final InventoryCrafting template;
        private final IRecipe recipe;

        TestReusePattern(IAEItemStack[] condensedInputs, IAEItemStack[] condensedOutputs,
                InventoryCrafting template, IRecipe recipe) {
            this.condensedInputs = condensedInputs;
            this.condensedOutputs = condensedOutputs;
            this.template = template;
            this.recipe = recipe;
        }

        @Override
        public ItemStack getPattern() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
            return true; // 可合成样板的提取受槽位校验门控,测试一律放行
        }

        @Override
        public boolean isCraftable() {
            return true;
        }

        @Override
        public IAEItemStack[] getInputs() {
            return this.condensedInputs.clone();
        }

        @Override
        public IAEItemStack[] getCondensedInputs() {
            return this.condensedInputs.clone();
        }

        @Override
        public IAEItemStack[] getCondensedOutputs() {
            return this.condensedOutputs.clone();
        }

        @Override
        public IAEItemStack[] getOutputs() {
            return this.condensedOutputs.clone();
        }

        @Override
        public boolean canSubstitute() {
            return false;
        }

        @Override
        public ItemStack getOutput(InventoryCrafting ic, World w) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public void setPriority(int p) {
        }

        @Nullable
        @Override
        public IRecipe ae2enhanced$standardRecipe() {
            return this.recipe;
        }

        @Nullable
        @Override
        public InventoryCrafting ae2enhanced$craftingTemplate() {
            return this.template;
        }
    }
}
