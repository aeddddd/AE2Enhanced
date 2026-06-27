package com.github.aeddddd.ae2enhanced.item;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemPartStockingBus extends Item implements IPartItem<PartStockingBus> {

    public ItemPartStockingBus() {
        this.setMaxStackSize(64);
        this.setTranslationKey("ae2enhanced.part_stocking_bus");
        this.setRegistryName("ae2enhanced", "part_stocking_bus");
        this.setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }

    @Nullable
    @Override
    public PartStockingBus createPartFromItemStack(ItemStack is) {
        return new PartStockingBus(is);
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos,
                                       @Nonnull EnumHand hand, @Nonnull EnumFacing side,
                                       float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, side, player, hand, world);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("item.ae2enhanced.part_stocking_bus.tooltip"));
    }
}
