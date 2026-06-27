package com.github.aeddddd.ae2enhanced.item;

import appeng.api.features.INetworkEncodable;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * ME 放置工具 —— 从 ME 网络直接放置方块、AE2 Part、Facade、线缆。
 * 通过 AE2 安全终端绑定。
 *
 * 重做后特性：
 * - 无配置 GUI，使用 G 键径向菜单 + 鼠标中键选取。
 * - 自动检测线缆并进入线缆放置模式。
 * - 右键方块放置，右键空气无动作。
 */
public class ItemMEPlacementTool extends Item implements INetworkEncodable {

    public ItemMEPlacementTool() {
        setMaxStackSize(1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setTranslationKey("ae2enhanced.me_placement_tool");
        setRegistryName("me_placement_tool");
    }

    // ==================== INetworkEncodable ====================

    @Override
    public String getEncryptionKey(ItemStack item) {
        return SecurityTerminalBindingHelper.getEncryptionKey(item);
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        SecurityTerminalBindingHelper.setEncryptionKey(item, encKey);
    }

    // ==================== Item Use ====================

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        // 普通右键执行放置
        if (!player.isSneaking()) {
            PlacementConfig config = new PlacementConfig(stack);
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
            } else {
                ok = PlacementToolHelper.placeSingle(player, world, pos, facing, hand, stack, hitX, hitY, hitZ);
            }
            return ok ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
        }

        return EnumActionResult.PASS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        // 重做后：右键空气无动作（GUI 已删除）
        RayTraceResult ray = rayTrace(world, player, false);
        if (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        // 潜行右键可清除线缆起点
        if (player.isSneaking()) {
            PlacementConfig config = new PlacementConfig(stack);
            if (config.getCableStart() != null) {
                config.setCableStart(null);
                return new ActionResult<>(EnumActionResult.SUCCESS, stack);
            }
        }

        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    // ==================== Tooltip ====================

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        if (SecurityTerminalBindingHelper.isLinked(stack)) {
            tooltip.add(TextFormatting.DARK_AQUA + "● " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_placement_tool.linked"));
        } else {
            tooltip.add(TextFormatting.RED + "● " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_placement_tool.unlinked"));
        }

        PlacementConfig config = new PlacementConfig(stack);
        ItemStack selected = config.getSelectedStack();
        if (!selected.isEmpty()) {
            tooltip.add(I18n.format("item.ae2enhanced.me_placement_tool.selected",
                    selected.getDisplayName(), 1));
        } else {
            tooltip.add(I18n.format("item.ae2enhanced.me_placement_tool.no_selection"));
        }
    }
}
