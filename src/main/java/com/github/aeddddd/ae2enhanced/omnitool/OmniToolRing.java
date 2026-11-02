package com.github.aeddddd.ae2enhanced.omnitool;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** 指环全局模块的安装与 NBT 兼容入口。 */
public final class OmniToolRing {
    public static final String MODULE_KEY = "OmniRingModule";

    private OmniToolRing() {
    }

    public static ItemStack install(ItemStack tool, ItemStack legacyRing) {
        ItemStack result = tool.copy();
        NBTTagCompound root = result.hasTagCompound() ? result.getTagCompound() : new NBTTagCompound();
        NBTTagCompound module = legacyRing.hasTagCompound()
                ? legacyRing.getTagCompound().copy() : new NBTTagCompound();
        root.setTag(MODULE_KEY, module);
        result.setTagCompound(root);
        return result;
    }

    public static boolean hasModule(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTagCompound() && stack.getTagCompound().hasKey(MODULE_KEY, 10);
    }
}
