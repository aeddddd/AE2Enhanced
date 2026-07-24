package com.github.aeddddd.ae2enhanced.omnitool.module;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;

/**
 * 放置模式：从 AE 网络提取并放置方块/线缆/Part/Facade。
 */
public class PlacementModule implements IOmniToolModule {

    private static final UUID REACH_MODIFIER_UUID = UUID.fromString("ae2e0000-0000-0000-0000-000000000001");

    @Override
    public int getMode() {
        return AdvancedMEOmniToolItem.MODE_PLACEMENT;
    }

    @Override
    public InteractionResult onItemUse(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        InteractionHand hand = context.getHand();
        BlockPos pos = context.getClickedPos();
        var facing = context.getClickedFace();
        Vec3Fraction hit = new Vec3Fraction(context);

        PlacementConfig config = new PlacementConfig(stack);
        PlacementMode subMode = config.getPlacementMode();
        ItemStack target = PlacementTargetResolver.resolveSingleOrCable(player, config, level, pos);

        boolean ok;
        if (PlacementTargetResolver.isCable(target)) {
            // 线缆模式：右键设置起点；若已有起点则设终点并放置
            BlockPos start = config.getCableStart();
            if (start == null) {
                config.setCableStart(pos.relative(facing));
                return InteractionResult.SUCCESS;
            } else {
                BlockPos end = pos.relative(facing);
                ok = PlacementToolHelper.placeCableBetween(player, level, start, end, hand, stack);
                config.setCableStart(null);
                return ok ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
        } else if (subMode == PlacementMode.BULK) {
            ok = PlacementToolHelper.placeBulk(player, level, pos, facing, hand, stack, hit.x, hit.y, hit.z);
        } else {
            ok = PlacementToolHelper.placeSingle(player, level, pos, facing, hand, stack, hit.x, hit.y, hit.z);
        }
        return ok ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> onItemRightClick(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 重做后：右键空气无动作；潜行右键清除线缆起点
        if (player.isShiftKeyDown()) {
            PlacementConfig config = new PlacementConfig(stack);
            if (config.getCableStart() != null) {
                config.setCableStart(null);
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void addTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        PlacementConfig config = new PlacementConfig(stack);
        ItemStack selected = config.getSelectedStack();
        if (!selected.isEmpty()) {
            tooltip.add(bullet(Component.translatable("item.ae2enhanced.me_omni_tool.placement.selected",
                    Component.empty().append(selected.getHoverName()).withStyle(ChatFormatting.YELLOW),
                    Component.translatable(
                            "gui.ae2enhanced.placement.mode." + config.getPlacementMode().name().toLowerCase()))));
        } else {
            tooltip.add(bullet(Component.translatable("item.ae2enhanced.me_omni_tool.placement.no_selection")));
        }
        if (OmniToolNetworkLink.isLinked(stack)) {
            tooltip.add(bullet(Component.translatable("item.ae2enhanced.me_omni_tool.placement.linked")));
        } else {
            tooltip.add(bullet(Component.translatable("item.ae2enhanced.me_omni_tool.placement.unlinked")));
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> multimap = HashMultimap.create();
        if (slot == EquipmentSlot.MAINHAND) {
            PlacementConfig config = new PlacementConfig(stack);
            float reach = config.getReachDistance();
            // 玩家基础触及距离为 5.0，因此 modifier = reach - 5.0
            double modifier = Math.max(0.0, reach - 5.0);
            multimap.put(ForgeMod.BLOCK_REACH.get(),
                    new AttributeModifier(REACH_MODIFIER_UUID, "AE2Enhanced OmniTool reach", modifier,
                            AttributeModifier.Operation.ADDITION));
        }
        return multimap;
    }

    private static Component bullet(Component content) {
        return Component.literal("▸ ").withStyle(ChatFormatting.GRAY)
                .append(Component.empty().append(content).withStyle(ChatFormatting.WHITE));
    }

    /**
     * 命中点局部坐标（0~1），从 UseOnContext 中提取。
     */
    private static class Vec3Fraction {
        final float x, y, z;

        Vec3Fraction(UseOnContext context) {
            BlockPos pos = context.getClickedPos();
            this.x = (float) (context.getClickLocation().x - pos.getX());
            this.y = (float) (context.getClickLocation().y - pos.getY());
            this.z = (float) (context.getClickLocation().z - pos.getZ());
        }
    }
}
