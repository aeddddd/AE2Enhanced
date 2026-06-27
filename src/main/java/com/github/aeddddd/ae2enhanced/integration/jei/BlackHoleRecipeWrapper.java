package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JEI 黑洞合成配方包装器.
 */
public class BlackHoleRecipeWrapper implements IRecipeWrapper {

    private final BlackHoleRecipe recipe;

    public BlackHoleRecipeWrapper(BlackHoleRecipe recipe) {
        this.recipe = recipe;
    }

    public BlackHoleRecipe getRecipe() {
        return recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();
            ItemStack stack = parseKeyToStack(key, count);
            if (stack.isEmpty()) continue;
            List<ItemStack> subList = new ArrayList<>();
            subList.add(stack);
            inputs.add(subList);
        }
        ingredients.setInputLists(ItemStack.class, inputs);
        ingredients.setOutput(ItemStack.class, recipe.getOutput());
    }

    /**
     * 解析 BlackHoleRecipe.keyOf 生成的 key：registryName:meta[?NBT].
     * <p>
     * 对于带 NBT 的物品（如不同 Tier 的虚拟并行卡），key 形如
     * {@code ae2enhanced:virtual_parallel_card:0{Tier:0}}，需要正确拆出
     * registryName、meta 与 NBT，否则 JEI 中无法显示输入。
     * </p>
     */
    private static ItemStack parseKeyToStack(String key, int count) {
        if (key == null || key.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // registryName 一定包含一个冒号（modid:name），meta 在第二个冒号之后
        int firstColon = key.indexOf(':');
        if (firstColon <= 0) {
            return ItemStack.EMPTY;
        }
        int secondColon = key.indexOf(':', firstColon + 1);
        if (secondColon < 0) {
            return ItemStack.EMPTY;
        }
        String registryName = key.substring(0, secondColon);
        String metaAndNbt = key.substring(secondColon + 1);

        int meta = 0;
        String nbtString = null;
        int nbtStart = -1;
        for (int i = 0; i < metaAndNbt.length(); i++) {
            char c = metaAndNbt.charAt(i);
            if (c == '{' || c == '[') {
                nbtStart = i;
                break;
            }
            if (c < '0' || c > '9') {
                // meta 之后紧跟的不是 NBT，格式异常
                return ItemStack.EMPTY;
            }
        }
        if (nbtStart >= 0) {
            meta = Integer.parseInt(metaAndNbt.substring(0, nbtStart));
            nbtString = metaAndNbt.substring(nbtStart);
        } else {
            meta = Integer.parseInt(metaAndNbt);
        }

        Item item = Item.REGISTRY.getObject(new ResourceLocation(registryName));
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count, meta);
        if (nbtString != null && !nbtString.isEmpty()) {
            try {
                NBTTagCompound tag = JsonToNBT.getTagFromJson(nbtString);
                if (tag != null && !tag.isEmpty()) {
                    stack.setTagCompound(tag);
                }
            } catch (net.minecraft.nbt.NBTException ignored) {
                // NBT 解析失败时仍返回无 NBT 的物品，保证基础显示
            }
        }
        return stack;
    }
}
