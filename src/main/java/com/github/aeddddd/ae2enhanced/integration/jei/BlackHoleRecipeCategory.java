package com.github.aeddddd.ae2enhanced.integration.jei;

import java.util.Map;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.registry.ModItems;

/**
 * JEI 黑洞合成配方类别.
 * <p>布局移植自 1.12 BlackHoleRecipeCategory:左侧 3x3 输入槽,右侧输出槽,
 * 中间绘制"投入事件视界"提示文字.</p>
 */
public class BlackHoleRecipeCategory implements IRecipeCategory<BlackHoleRecipe> {

    public static final RecipeType<BlackHoleRecipe> RECIPE_TYPE = new RecipeType<>(
            new ResourceLocation(AE2Enhanced.MOD_ID, "black_hole"), BlackHoleRecipe.class);

    private static final int WIDTH = 140;
    private static final int HEIGHT = 50;

    private final IDrawable icon;

    public BlackHoleRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.CONFORMAL_INVARIANT_CHARGE.get()));
    }

    @Override
    public RecipeType<BlackHoleRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ae2enhanced.category.blackhole");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BlackHoleRecipe recipe, IFocusGroup focuses) {
        // 输入：左侧 0~8 槽位
        int inputIndex = 0;
        for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
            ItemStack stack = parseKeyToStack(entry.getKey(), entry.getValue());
            if (stack.isEmpty()) {
                continue;
            }
            int x = 10 + (inputIndex % 3) * 18;
            int y = 8 + (inputIndex / 3) * 18;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y).addItemStack(stack);
            inputIndex++;
            if (inputIndex >= 9) {
                break;
            }
        }

        // 输出：右侧
        builder.addSlot(RecipeIngredientRole.OUTPUT, 112, 16).addItemStack(recipe.getOutput());
    }

    @Override
    public void draw(BlackHoleRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
            double mouseX, double mouseY) {
        // 绘制中间的黑洞提示文字
        Component text = Component.translatable("jei.ae2enhanced.blackhole.hint");
        Font font = Minecraft.getInstance().font;
        int width = font.width(text);
        guiGraphics.drawString(font, text, (WIDTH - width) / 2, 22, 0xAA00DD, false);
    }

    /**
     * 解析 {@link BlackHoleRecipe#keyOf} 生成的输入键：注册名[#NBT].
     */
    private static ItemStack parseKeyToStack(String key, int count) {
        if (key == null || key.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int nbtStart = key.indexOf('#');
        String itemId = nbtStart >= 0 ? key.substring(0, nbtStart) : key;
        var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(itemId));
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item.get(), count);
        if (nbtStart >= 0) {
            try {
                stack.setTag(TagParser.parseTag(key.substring(nbtStart + 1)));
            } catch (CommandSyntaxException ignored) {
                // NBT 解析失败时仍返回无 NBT 的物品,保证基础显示
            }
        }
        return stack;
    }
}
