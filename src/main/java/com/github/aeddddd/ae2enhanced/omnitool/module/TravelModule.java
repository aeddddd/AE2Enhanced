package com.github.aeddddd.ae2enhanced.omnitool.module;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.util.TravelAnchorHelper;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 旅行模式：Travel Anchor 绑定与闪烁传送。
 */
public class TravelModule implements IOmniToolModule {

    private static final int BLINK_COOLDOWN_TICKS = 1;

    @Override
    public int getMode() {
        return ItemAdvancedMEOmniTool.MODE_TRAVEL;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        return doTravel(player, world, pos, hand, player.getHeldItem(hand));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.fallDistance = 0.0f; // 每次尝试位移都重置摔落伤害

        // 安装旅行手杖且已绑定锚点时，右键空气传送到绑定锚点
        if (ItemAdvancedMEOmniTool.hasTravelStaff(stack) && isTravelAnchorBound(stack)) {
            BlockPos target = getBoundTravelAnchorPos(stack);
            int targetDim = getBoundTravelAnchorDim(stack);
            if (target != null && world.provider.getDimension() == targetDim
                    && TravelAnchorHelper.teleportToAnchor(player, world, target)) {
                player.swingArm(hand);
                return new ActionResult<>(EnumActionResult.SUCCESS, stack);
            }
            player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.travel_anchor_unavailable"), true);
        }

        doBlink(player, world, stack);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addTooltip(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.blink_dist",
                TextFormatting.YELLOW + String.format("%.1f", getBlinkDistance(stack))));
        if (isWallPhaseEnabled(stack)) {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.wall_phase.on"));
        } else {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.wall_phase.off"));
        }
    }

    // ==================== Travel Anchor Binding ====================

    public static boolean isTravelAnchorBound(ItemStack stack) {
        return OmniToolUpgrades.isTravelAnchorBound(stack);
    }

    public static void setBoundTravelAnchor(ItemStack stack, BlockPos pos, int dim) {
        OmniToolUpgrades.setBoundTravelAnchor(stack, pos, dim);
    }

    public static BlockPos getBoundTravelAnchorPos(ItemStack stack) {
        return OmniToolUpgrades.getBoundTravelAnchorPos(stack);
    }

    public static int getBoundTravelAnchorDim(ItemStack stack) {
        return OmniToolUpgrades.getBoundTravelAnchorDim(stack);
    }

    public static void clearBoundTravelAnchor(ItemStack stack) {
        OmniToolUpgrades.clearBoundTravelAnchor(stack);
    }

    // ==================== Travel Mode ====================

    private EnumActionResult doTravel(EntityPlayer player, World world, BlockPos pos, EnumHand hand, ItemStack stack) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        player.fallDistance = 0.0f;

        // 安装旅行手杖且 Shift+右键 Travel Anchor 时将其绑定为传送目标
        if (ItemAdvancedMEOmniTool.hasTravelStaff(stack) && TravelAnchorHelper.isTravelAnchor(world, pos)) {
            if (player.isSneaking()) {
                setBoundTravelAnchor(stack, pos, world.provider.getDimension());
                player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.travel_anchor_bound",
                        pos.getX(), pos.getY(), pos.getZ()), true);
                player.swingArm(hand);
                return EnumActionResult.SUCCESS;
            }
            // 非 Shift 右键不直接传送，允许锚点方块自身交互
            return EnumActionResult.PASS;
        }

        return doBlink(player, world, stack);
    }

    private EnumActionResult doBlink(EntityPlayer player, World world, ItemStack stack) {
        long now = world.getTotalWorldTime();
        long lastBlink = getLastBlink(stack);
        if (now - lastBlink < BLINK_COOLDOWN_TICKS) return EnumActionResult.PASS;

        double distance = getBlinkDistance(stack);
        Vec3d look = player.getLookVec();
        Vec3d start = player.getPositionEyes(1.0f);
        Vec3d end = start.add(look.x * distance, look.y * distance, look.z * distance);

        RayTraceResult ray = world.rayTraceBlocks(start, end, false, true, false);
        Vec3d target;
        if (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK) {
            if (isWallPhaseEnabled(stack)) {
                // 尝试穿墙：穿过阻挡方块后继续搜索安全落点
                Vec3d through = ray.hitVec.add(look.scale(0.5));
                Vec3d safe = findSafePos(world, through, end, look, player);
                if (safe != null) {
                    target = safe;
                } else {
                    target = ray.hitVec.subtract(look.scale(0.5));
                }
            } else {
                // 不穿墙：在阻挡点前留出更大安全距离，减少卡在方块内
                target = ray.hitVec.subtract(look.scale(0.5));
            }
        } else {
            target = end;
        }

        // 防卡墙：根据视线方向增加偏移，并确保落点安全
        target = adjustLandingPosition(world, target, look, player);

        player.setPositionAndUpdate(target.x, target.y - player.getEyeHeight(), target.z);
        player.fallDistance = 0.0f;
        setLastBlink(stack, now);
        return EnumActionResult.SUCCESS;
    }

    /**
     * 调整落点以避免卡墙。向下看时额外抬高，并在不安全时向上搜索，
     * 同时尝试在落点周围小范围寻找可站立位置。
     */
    private Vec3d adjustLandingPosition(World world, Vec3d target, Vec3d look, EntityPlayer player) {
        // 向下看时额外抬高落点，避免卡在台阶/斜面/不完整方块内
        if (look.y < -0.1) {
            target = target.add(0, 0.25, 0);
        }

        // 优先尝试原落点，再尝试向上搜索，最后尝试水平微调
        double feetY = target.y - player.getEyeHeight();
        BlockPos basePos = new BlockPos(target.x, feetY, target.z);

        // 垂直搜索
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos feetPos = basePos.up(dy);
            if (isSafeStandingPos(world, feetPos, player)) {
                return new Vec3d(target.x, feetPos.getY() + player.getEyeHeight(), target.z);
            }
        }

        // 水平微调（用于落点紧贴方块边缘的情况）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos feetPos = basePos.add(dx, 0, dz);
                if (isSafeStandingPos(world, feetPos, player)) {
                    return new Vec3d(feetPos.getX() + 0.5, feetPos.getY() + player.getEyeHeight(), feetPos.getZ() + 0.5);
                }
            }
        }

        return target;
    }

    /**
     * 在穿过阻挡点后向前搜索第一个安全的站立位置。
     */
    private Vec3d findSafePos(World world, Vec3d through, Vec3d maxEnd, Vec3d look, EntityPlayer player) {
        double remainingDist = through.distanceTo(maxEnd);
        double step = 0.5;
        int steps = (int) Math.ceil(remainingDist / step);

        for (int i = 0; i <= steps; i++) {
            Vec3d check = through.add(look.scale(i * step));
            if (check.distanceTo(through) > remainingDist + 0.01) break;

            BlockPos feetPos = new BlockPos(check);
            if (isSafeStandingPos(world, feetPos, player)) {
                return new Vec3d(check.x, feetPos.getY(), check.z);
            }
        }
        return null;
    }

    /**
     * 检查指定坐标是否为安全位置（使用实体碰撞箱检测，不要求脚下有地面）。
     */
    private boolean isSafeStandingPos(World world, BlockPos pos, EntityPlayer player) {
        IBlockState feet = world.getBlockState(pos);
        IBlockState head = world.getBlockState(pos.up());
        // 完整方块碰撞箱直接判定为不安全
        if (feet.getBlock().getCollisionBoundingBox(feet, world, pos) != null) return false;
        if (head.getBlock().getCollisionBoundingBox(head, world, pos.up()) != null) return false;
        // 使用玩家碰撞箱进一步确认
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        AxisAlignedBB box = player.getEntityBoundingBox()
                .offset(x - player.posX, y - player.posY, z - player.posZ);
        return world.getCollisionBoxes(player, box).isEmpty();
    }

    // ==================== Blink Distance / Cooldown ====================

    public static double getBlinkDistance(ItemStack stack) {
        return OmniToolUpgrades.getBlinkDistance(stack);
    }

    public static void setBlinkDistance(ItemStack stack, double dist) {
        OmniToolUpgrades.setBlinkDistance(stack, dist);
    }

    private static long getLastBlink(ItemStack stack) {
        return OmniToolUpgrades.getLastBlinkTick(stack);
    }

    private static void setLastBlink(ItemStack stack, long tick) {
        OmniToolUpgrades.setLastBlinkTick(stack, tick);
    }

    // ==================== Wall Phase ====================

    public static boolean isWallPhaseEnabled(ItemStack stack) {
        return OmniToolUpgrades.isWallPhaseEnabled(stack);
    }

    public static void setWallPhaseEnabled(ItemStack stack, boolean enabled) {
        OmniToolUpgrades.setWallPhaseEnabled(stack, enabled);
    }
}
