package com.github.aeddddd.ae2enhanced.ring.crafting;

import com.github.aeddddd.ae2enhanced.omnitool.OmniToolRing;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/** 旧网络链接凭证 → 先进 ME 工具指环模块的无序转换配方。 */
public class RecipeRingModuleInstall extends ShapelessOreRecipe {
    private final Item tool;
    private final Item legacyRing;

    public RecipeRingModuleInstall(ResourceLocation group, Item tool, Item legacyRing) {
        super(group, new ItemStack(tool), new ItemStack(tool), new ItemStack(legacyRing));
        this.tool = tool;
        this.legacyRing = legacyRing;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        boolean foundTool = false;
        boolean foundRing = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == this.tool && !foundTool) {
                foundTool = true;
            } else if (stack.getItem() == this.legacyRing && !foundRing) {
                foundRing = true;
            } else {
                return false;
            }
        }
        return foundTool && foundRing;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack toolStack = ItemStack.EMPTY;
        ItemStack ringStack = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() == this.tool) {
                toolStack = stack;
            } else if (stack.getItem() == this.legacyRing) {
                ringStack = stack;
            }
        }
        return toolStack.isEmpty() || ringStack.isEmpty()
                ? ItemStack.EMPTY : OmniToolRing.install(toolStack, ringStack);
    }
}
