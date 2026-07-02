package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * 黑洞事件视界配方。
 * <p>以物品注册名 + NBT 字符串为输入键，匹配成功后产出指定物品。</p>
 */
public class BlackHoleRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final Map<String, Integer> inputs;
    private final ItemStack output;

    public BlackHoleRecipe(ResourceLocation id, Map<String, Integer> inputs, ItemStack output) {
        this.id = id;
        this.inputs = new HashMap<>(inputs);
        this.output = output.copy();
    }

    /**
     * 获取配方字符串 ID（用于代码级注册表）。
     */
    public String getStringId() {
        return id.toString();
    }

    public Map<String, Integer> getInputs() {
        return new HashMap<>(inputs);
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    /**
     * 检查当前区域内的物品是否满足本配方输入。
     */
    public boolean matches(Map<String, Integer> found) {
        for (Map.Entry<String, Integer> entry : inputs.entrySet()) {
            int have = found.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 生成 ItemStack 的物品键：注册名 + NBT 字符串（若存在）。
     */
    public static String keyOf(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        StringBuilder sb = new StringBuilder(key.toString());
        if (stack.hasTag()) {
            sb.append('#').append(stack.getTag().toString());
        }
        return sb.toString();
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return output.copy();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.BLACK_HOLE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.BLACK_HOLE_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
