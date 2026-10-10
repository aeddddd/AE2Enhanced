package com.github.aeddddd.ae2enhanced.centralinterface.recipe;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台类配方的公共逻辑：产物匹配 + 输入成本折算.
 *
 * <p>ExtendedCrafting 工作台（{@code ExtendedCraftingTableHandler}）与 Avaritia 终焉工作台
 * （{@code ExtremeCraftingTableHandler}）共用本类的产物匹配与成本折算。两者的差异只在
 * "配方对象从哪来"：EC 是真实 {@link net.minecraft.item.crafting.IRecipe}，Avaritia 是反射
 * handle（{@code Object}）+ {@code IExtremeRecipe#getIngredients}——所以本类只接收已经取出的
 * ingredient 列表与配方产物，不关心配方对象类型。</p>
 *
 * <p>两者的配方都按<b>处理样板</b>对待：成本按配方每个非空 ingredient 槽固定 1 个物品计算，
 * <b>不</b>乘样板声明的输入数量；样板输入的真实性由 {@link IngredientMatcher} 单独校验。</p>
 */
public final class RecipeCosts {

    private RecipeCosts() {
    }

    /**
     * 配方产物与样板声明的产物是否一致（决定按哪条配方折算成本）.
     *
     * <p>比较 Item + Metadata + Count + NBT：仅当 expected 带 NBT 时才要求 NBT 相同（配方产物
     * NBT 常由机器后补，严格比较会漏配）。<b>不得</b>改用 {@code HandlerUtils#matchesLoosely} 或
     * {@code ProductFilter#matchesExpected}——它们不比较 count，会放宽匹配口径。</p>
     *
     * <p>{@code null} 保护把"配方对象返回 null 产物"降级为"跳过该配方"，对合法配方行为不变。</p>
     *
     * @param recipeOutput   配方表里的产物（ItemStack 语义，可为空或 null）
     * @param expectedOutput 样板声明的产物（来自 {@code IAEItemStack#createItemStack()}）
     * @return 一致返回 true；任一为空或 count/NBT 口径不吻合返回 false
     */
    public static boolean outputMatches(ItemStack recipeOutput, ItemStack expectedOutput) {
        if (recipeOutput == null || recipeOutput.isEmpty()
                || expectedOutput == null || expectedOutput.isEmpty()) {
            return false;
        }
        if (recipeOutput.getItem() != expectedOutput.getItem()) return false;
        if (recipeOutput.getMetadata() != expectedOutput.getMetadata()) return false;
        if (recipeOutput.getCount() != expectedOutput.getCount()) return false;
        if (expectedOutput.hasTagCompound() && !ItemStack.areItemStackTagsEqual(recipeOutput, expectedOutput)) {
            return false;
        }
        return true;
    }

    /**
     * 把配方的 ingredients 折算成 {@code count} 份的输入成本.
     *
     * <p>每个非空 ingredient 只按<b>第一个</b>匹配栈计 1 个物品（原料标签多选一时取代表项），
     * (Item, Metadata, NBT) 相同的槽位合并数量。</p>
     *
     * @param ingredients 配方的 ingredient 列表；{@code null}（如反射调用失败）视为无法计价
     * @param count       份数（并行批次大小）
     * @return 合并后的成本列表；无有效 ingredient 时为空列表；不返回 {@code null}
     */
    public static List<IAEStack> fromIngredients(NonNullList<Ingredient> ingredients, long count) {
        if (ingredients == null) {
            return Collections.emptyList();
        }

        Map<ItemKey, Long> merged = new HashMap<>();
        for (Ingredient ing : ingredients) {
            if (ing == null || ing == Ingredient.EMPTY) continue;
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching == null || matching.length == 0) continue;
            for (ItemStack stack : matching) {
                if (stack != null && !stack.isEmpty()) {
                    merged.merge(new ItemKey(stack), count, Long::sum);
                    break;
                }
            }
        }

        List<IAEStack> costs = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> entry : merged.entrySet()) {
            IAEItemStack cost = AEItemStack.fromItemStack(entry.getKey().representative.copy());
            if (cost != null) {
                cost.setStackSize(entry.getValue());
                costs.add(cost);
            }
        }
        return costs;
    }

    /**
     * 成本合并键：(Item, Metadata, NBT) 三元组，另存一份单件代表栈用于构造 {@code IAEItemStack}.
     *
     * <p>{@code nbt} 取<b>防御性拷贝</b>而非引用：键可能在表中长期持有，而
     * {@code Ingredient#getMatchingStacks()} 给出的是 ingredient 持有的内部栈，
     * 外部改写其 NBT 会污染已入表的键（{@code hashCode} 随 NBT 变化）。</p>
     */
    private static final class ItemKey {
        final Item item;
        final int meta;
        final NBTTagCompound nbt;
        final ItemStack representative;

        ItemKey(ItemStack stack) {
            this.item = stack.getItem();
            this.meta = stack.getMetadata();
            this.nbt = stack.hasTagCompound() ? stack.getTagCompound().copy() : null;
            this.representative = stack.copy();
            this.representative.setCount(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey)) return false;
            ItemKey other = (ItemKey) o;
            return this.item == other.item && this.meta == other.meta &&
                    (this.nbt == null ? other.nbt == null : this.nbt.equals(other.nbt));
        }

        @Override
        public int hashCode() {
            int result = item.hashCode();
            result = 31 * result + meta;
            result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
            return result;
        }
    }
}
