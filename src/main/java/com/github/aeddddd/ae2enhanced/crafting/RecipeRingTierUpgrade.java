package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.item.ItemNetworkLinkCredential;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.HashMap;
import java.util.Map;

/**
 * 指环阶段升级配方：指环 + 指定数量的高级材料 → 阶段 +1.
 * 材料按多重集合精确匹配(3x3 摆满),输出完整保留指环 NBT(绑定/能量/配置).
 */
public class RecipeRingTierUpgrade extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    /** 材料需求: 物品 → 数量 */
    private final Map<Item, Integer> materials = new HashMap<>();
    private final int requiredTier;
    private final int totalSlots;

    /**
     * @param name         注册名
     * @param requiredTier 要求指环当前阶段(升级后为 requiredTier+1)
     * @param materials    材料需求,成对传入 (Item, count)
     */
    public RecipeRingTierUpgrade(ResourceLocation name, int requiredTier, Object... materials) {
        setRegistryName(name);
        this.requiredTier = requiredTier;
        int total = 1; // 指环本体
        for (int i = 0; i + 1 < materials.length; i += 2) {
            Item item = (Item) materials[i];
            int count = (Integer) materials[i + 1];
            this.materials.merge(item, count, Integer::sum);
            total += count;
        }
        this.totalSlots = total;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        ItemStack ring = ItemStack.EMPTY;
        Map<Item, Integer> found = new HashMap<>();
        int nonEmpty = 0;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            nonEmpty++;
            if (stack.getItem() instanceof ItemNetworkLinkCredential) {
                if (!ring.isEmpty()) return false;
                ring = stack;
            } else if (materials.containsKey(stack.getItem())) {
                found.merge(stack.getItem(), 1, Integer::sum);
            } else {
                return false;
            }
        }

        if (nonEmpty != totalSlots || ring.isEmpty()) return false;
        if (RingNBT.isAscended(ring) || RingNBT.getTier(ring) != requiredTier) return false;
        return found.equals(materials);
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() instanceof ItemNetworkLinkCredential) {
                ItemStack result = stack.copy();
                result.setCount(1);
                RingNBT.setTier(result, requiredTier + 1);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= totalSlots;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
