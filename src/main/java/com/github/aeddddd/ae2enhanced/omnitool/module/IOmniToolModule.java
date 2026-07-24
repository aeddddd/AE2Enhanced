package com.github.aeddddd.ae2enhanced.omnitool.module;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/**
 * 先进 ME 全能工具各模式逻辑模块接口。
 * 工具主类仅作为分发器，将事件调用转发给当前模式对应的模块。
 */
public interface IOmniToolModule {

    int getMode();

    default InteractionResult onItemUse(UseOnContext context) {
        return InteractionResult.PASS;
    }

    default InteractionResultHolder<ItemStack> onItemRightClick(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    default boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        return false;
    }

    default float getDestroySpeed(ItemStack stack, BlockState state) {
        return 1.0f;
    }

    default boolean canHarvestBlock(BlockState state, ItemStack stack) {
        return false;
    }

    default boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return false;
    }

    default void addTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {}

    default Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return HashMultimap.create();
    }
}
