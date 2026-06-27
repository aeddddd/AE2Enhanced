package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapelessOreRecipe;

public class RecipeOmniToolUpgrade extends ShapelessOreRecipe {

    private final String upgradeType;

    public RecipeOmniToolUpgrade(ResourceLocation group, ItemStack result, String upgradeType, Object... input) {
        super(group, result, input);
        this.upgradeType = upgradeType;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        ItemStack omniTool = ItemStack.EMPTY;
        ItemStack upgradeItem = ItemStack.EMPTY;
        int nonEmptyCount = 0;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                if (!omniTool.isEmpty()) return false;
                omniTool = stack;
            } else if (matchesUpgradeItem(stack)) {
                if (!upgradeItem.isEmpty()) return false;
                upgradeItem = stack;
            } else {
                return false;
            }
        }

        if (nonEmptyCount != 2 || omniTool.isEmpty() || upgradeItem.isEmpty()) {
            return false;
        }

        // 防止重复升级
        if ("chaos".equals(upgradeType) && OmniToolUpgrades.hasChaosCore(omniTool)) {
            return false;
        }
        if ("bedrock".equals(upgradeType) && OmniToolUpgrades.hasBedrockBreaker(omniTool)) {
            return false;
        }

        return true;
    }

    private boolean matchesUpgradeItem(ItemStack stack) {
        if ("chaos".equals(upgradeType)) {
            return stack.getItem().getRegistryName() != null
                    && "draconicevolution:chaotic_core".equals(stack.getItem().getRegistryName().toString());
        } else if ("enchanted_book".equals(upgradeType)) {
            if (stack.getItem() != Items.ENCHANTED_BOOK) return false;
            return OmniToolEnchantments.copyEnchantmentsFromBook(stack).tagCount() > 0;
        } else if ("bedrock".equals(upgradeType)) {
            return stack.getItem().getRegistryName() != null
                    && "minecraft:bedrock".equals(stack.getItem().getRegistryName().toString());
        }
        return false;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack omniTool = ItemStack.EMPTY;
        ItemStack book = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                omniTool = stack;
            } else if (matchesUpgradeItem(stack)) {
                book = stack;
            }
        }
        if (omniTool.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = omniTool.copy();
        result.setCount(1);
        if ("chaos".equals(upgradeType)) {
            OmniToolUpgrades.setChaosCore(result, true);
        } else if ("bedrock".equals(upgradeType)) {
            OmniToolUpgrades.setBedrockBreaker(result, true);
        } else if ("enchanted_book".equals(upgradeType)) {
            // 将原书上的附魔合并到工具的存储附魔区，等级上限取合成时书本等级
            NBTTagList fromBook = OmniToolEnchantments.copyEnchantmentsFromBook(book);
            NBTTagList current = OmniToolEnchantments.getStoredEnchantments(result);

            for (int i = 0; i < fromBook.tagCount(); i++) {
                NBTTagCompound src = fromBook.getCompoundTagAt(i);
                short id = src.getShort("id");
                short bookLvl = src.getShort("lvl");
                short bookMax = src.hasKey("max", net.minecraftforge.common.util.Constants.NBT.TAG_SHORT)
                        ? src.getShort("max") : bookLvl;

                boolean found = false;
                for (int j = 0; j < current.tagCount(); j++) {
                    NBTTagCompound entry = current.getCompoundTagAt(j);
                    if (entry.getShort("id") == id) {
                        short oldMax = entry.hasKey("max", net.minecraftforge.common.util.Constants.NBT.TAG_SHORT)
                                ? entry.getShort("max") : entry.getShort("lvl");
                        short newMax = (short) Math.max(oldMax, bookMax);
                        short newLvl = (short) Math.max(entry.getShort("lvl"), bookLvl);
                        if (newLvl > newMax) newLvl = newMax;
                        entry.setShort("lvl", newLvl);
                        entry.setShort("max", newMax);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    NBTTagCompound entry = new NBTTagCompound();
                    entry.setShort("id", id);
                    entry.setShort("lvl", bookLvl);
                    entry.setShort("max", bookMax);
                    current.appendTag(entry);
                }
            }
            OmniToolEnchantments.setStoredEnchantments(result, current);
        }
        return result;
    }
}
