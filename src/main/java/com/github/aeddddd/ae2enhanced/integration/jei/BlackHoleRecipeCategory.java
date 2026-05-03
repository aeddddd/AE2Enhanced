package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * JEI 黑洞合成配方类别。
 */
public class BlackHoleRecipeCategory implements IRecipeCategory<BlackHoleRecipeWrapper> {

    public static final String UID = AE2Enhanced.MOD_ID + ".blackhole";
    public static final ResourceLocation BG = new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/jei_blackhole.png");

    private final IDrawable background;
    private final IDrawable icon;
    private final String localizedName;

    public BlackHoleRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(140, 50);
        this.icon = guiHelper.createDrawableIngredient(new ItemStack(ModItems.CONFORMAL_CHARGE));
        this.localizedName = I18n.format("jei.ae2enhanced.category.blackhole");
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return localizedName;
    }

    @Override
    public String getModName() {
        return AE2Enhanced.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void drawExtras(Minecraft minecraft) {
        // 绘制中间的黑洞提示文字
        String text = I18n.format("jei.ae2enhanced.blackhole.hint");
        int width = minecraft.fontRenderer.getStringWidth(text);
        minecraft.fontRenderer.drawString(text, (140 - width) / 2, 22, 0xAA00DD);
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, BlackHoleRecipeWrapper recipeWrapper, IIngredients ingredients) {
        IGuiItemStackGroup stacks = recipeLayout.getItemStacks();
        BlackHoleRecipe recipe = recipeWrapper.getRecipe();

        // 输入：左侧 0~8 槽位
        int inputIndex = 0;
        for (List<ItemStack> inputs : ingredients.getInputs(ItemStack.class)) {
            if (inputs.isEmpty()) continue;
            int x = 10 + (inputIndex % 3) * 18;
            int y = 8 + (inputIndex / 3) * 18;
            stacks.init(inputIndex, true, x, y);
            stacks.set(inputIndex, inputs.get(0));
            inputIndex++;
            if (inputIndex >= 9) break;
        }

        // 输出：右侧
        stacks.init(9, false, 112, 16);
        stacks.set(9, recipe.getOutput());
    }
}
