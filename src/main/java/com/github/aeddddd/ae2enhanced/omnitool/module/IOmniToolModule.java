package com.github.aeddddd.ae2enhanced.omnitool.module;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.List;

/**
 * 先进 ME 全能工具各模式逻辑模块接口。
 * 工具主类仅作为分发器，将事件调用转发给当前模式对应的模块。
 */
public interface IOmniToolModule {

    int getMode();

    default EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                       EnumFacing facing, float hitX, float hitY, float hitZ) {
        return EnumActionResult.PASS;
    }

    default ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
    }

    default boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        return false;
    }

    default float getDestroySpeed(ItemStack stack, IBlockState state) {
        return 1.0f;
    }

    default boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        return false;
    }

    default int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, @Nullable IBlockState blockState) {
        return -1;
    }

    default boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        return false;
    }

    default boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        return false;
    }

    default void addTooltip(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {}

    default Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        return HashMultimap.create();
    }
}
