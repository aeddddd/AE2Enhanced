package com.github.aeddddd.ae2enhanced.guide.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * 指南书物品 —— 测试用（JEI 隐藏、无创造栏、无配方）.
 * 手持右键打开指南首页。
 */
public class ItemGuideBook extends Item {

    public ItemGuideBook() {
        setRegistryName(AE2Enhanced.MOD_ID, "guide_book");
        setTranslationKey(AE2Enhanced.MOD_ID + ".guide_book");
        setMaxStackSize(1);
        // 测试物品：不加入创造栏，JEI 中隐藏
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        if (worldIn.isRemote && com.github.aeddddd.ae2enhanced.config.ClientPreferences.current().guide.enabled) {
            // 客户端打开指南（引用客户端类，仅在 isRemote 分支执行，服务端不会加载）
            com.github.aeddddd.ae2enhanced.guide.client.GuiGuide.open(null);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
