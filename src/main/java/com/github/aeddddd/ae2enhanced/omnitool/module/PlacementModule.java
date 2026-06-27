package com.github.aeddddd.ae2enhanced.omnitool.module;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 放置模式：从 AE 网络提取并放置方块/线缆/Part/Facade。
 */
public class PlacementModule implements IOmniToolModule {

    private static final java.util.UUID REACH_MODIFIER_UUID = java.util.UUID.fromString("ae2e0000-0000-0000-0000-000000000001");

    @Override
    public int getMode() {
        return ItemAdvancedMEOmniTool.MODE_PLACEMENT;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return EnumActionResult.SUCCESS;

        PlacementConfig config = new PlacementConfig(stack);
        PlacementMode subMode = config.getPlacementMode();
        ItemStack target = PlacementTargetResolver.resolveSingleOrCable(player, config, world, pos);

        boolean ok;
        if (PlacementTargetResolver.isCable(target)) {
            // 线缆模式：右键设置起点；若已有起点则设终点并放置
            BlockPos start = config.getCableStart();
            if (start == null) {
                config.setCableStart(pos.offset(facing));
                return EnumActionResult.SUCCESS;
            } else {
                BlockPos end = pos.offset(facing);
                ok = PlacementToolHelper.placeCableBetween(player, world, start, end, hand, stack);
                config.setCableStart(null);
                return ok ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
            }
        } else if (subMode == PlacementMode.BULK) {
            ok = PlacementToolHelper.placeBulk(player, world, pos, facing, hand, stack, hitX, hitY, hitZ);
        } else {
            ok = PlacementToolHelper.placeSingle(player, world, pos, facing, hand, stack, hitX, hitY, hitZ);
        }
        return ok ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        // 重做后：右键空气无动作；潜行右键清除线缆起点
        if (player.isSneaking()) {
            PlacementConfig config = new PlacementConfig(stack);
            if (config.getCableStart() != null) {
                config.setCableStart(null);
                return new ActionResult<>(EnumActionResult.SUCCESS, stack);
            }
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    @Override
    public void addTooltip(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        PlacementConfig config = new PlacementConfig(stack);
        ItemStack selected = config.getSelectedStack();
        if (!selected.isEmpty()) {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.placement.selected",
                    TextFormatting.YELLOW + selected.getDisplayName(),
                    net.minecraft.client.resources.I18n.format("gui.ae2enhanced.placement.mode." + config.getPlacementMode().name().toLowerCase())));
        } else {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.placement.no_selection"));
        }
        if (SecurityTerminalBindingHelper.isLinked(stack)) {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.placement.linked"));
        } else {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.placement.unlinked"));
        }
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        if (slot == EntityEquipmentSlot.MAINHAND) {
            PlacementConfig config = new PlacementConfig(stack);
            float reach = config.getReachDistance();
            // 玩家基础触及距离为 5.0，因此 modifier = reach - 5.0
            double modifier = Math.max(0.0, reach - 5.0);
            multimap.put(EntityPlayer.REACH_DISTANCE.getName(),
                    new AttributeModifier(REACH_MODIFIER_UUID, "AE2Enhanced OmniTool reach", modifier, 0));
        }
        return multimap;
    }
}
