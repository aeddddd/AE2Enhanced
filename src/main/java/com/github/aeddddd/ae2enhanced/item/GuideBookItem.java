package com.github.aeddddd.ae2enhanced.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.core.AppEng;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 模组指南书: 右键打开 AE2 指南中的 AE2Enhanced 章节.
 * <p>页面内容位于 {@code assets/ae2enhanced/ae2guide/ae2enhanced/**.md}, 由 AE2 内置
 * guidebook 引擎统一加载(该引擎跨命名空间扫描 ae2guide 目录), 无需自注册 Guide 实例.</p>
 */
public class GuideBookItem extends Item {

    /** AE2 指南中本模组章节首页的页面 id. */
    private static final ResourceLocation CHAPTER_INDEX = new ResourceLocation(AE2Enhanced.MOD_ID,
            "ae2enhanced/index.md");

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            AppEng.instance().openGuideAtPreviousPage(CHAPTER_INDEX);
        }
        return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
    }
}
