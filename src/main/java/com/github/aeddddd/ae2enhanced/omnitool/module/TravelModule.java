package com.github.aeddddd.ae2enhanced.omnitool.module;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;

/**
 * 旅行模式：闪烁传送（blink）。
 * <p>注：1.12 中的 Ender IO Travel Anchor 绑定部分未移植（1.20.1 无 Ender IO，
 * 且 1.12 中旅行手杖升级无安装途径属死代码）。</p>
 */
public class TravelModule implements IOmniToolModule {

    private static final int BLINK_COOLDOWN_TICKS = 1;

    @Override
    public int getMode() {
        return AdvancedMEOmniToolItem.MODE_TRAVEL;
    }

    @Override
    public InteractionResult onItemUse(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return doBlink(player, context.getLevel(), player.getItemInHand(context.getHand()));
    }

    @Override
    public InteractionResultHolder<ItemStack> onItemRightClick(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.fallDistance = 0.0f; // 每次尝试位移都重置摔落伤害

        doBlink(player, level, stack);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void addTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2enhanced.me_omni_tool.blink_dist",
                String.format("%.1f", getBlinkDistance(stack))));
        if (isWallPhaseEnabled(stack)) {
            tooltip.add(Component.translatable("item.ae2enhanced.me_omni_tool.wall_phase.on"));
        } else {
            tooltip.add(Component.translatable("item.ae2enhanced.me_omni_tool.wall_phase.off"));
        }
    }

    // ==================== Travel Mode ====================

    private InteractionResult doBlink(Player player, Level level, ItemStack stack) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.fallDistance = 0.0f;

        long now = level.getGameTime();
        long lastBlink = getLastBlink(stack);
        if (now - lastBlink < BLINK_COOLDOWN_TICKS) return InteractionResult.PASS;

        double distance = getBlinkDistance(stack);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(look.x * distance, look.y * distance, look.z * distance);

        BlockHitResult ray = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 target;
        if (ray.getType() == HitResult.Type.BLOCK) {
            if (isWallPhaseEnabled(stack)) {
                // 尝试穿墙：穿过阻挡方块后继续搜索安全落点
                Vec3 through = ray.getLocation().add(look.scale(0.5));
                Vec3 safe = findSafePos(level, through, end, look, player);
                if (safe != null) {
                    target = safe;
                } else {
                    target = ray.getLocation().subtract(look.scale(0.5));
                }
            } else {
                // 不穿墙：在阻挡点前留出更大安全距离，减少卡在方块内
                target = ray.getLocation().subtract(look.scale(0.5));
            }
        } else {
            target = end;
        }

        // 防卡墙：根据视线方向增加偏移，并确保落点安全
        target = adjustLandingPosition(level, target, look, player);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.teleport(target.x, target.y - player.getEyeHeight(), target.z,
                    player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(target.x, target.y - player.getEyeHeight(), target.z);
        }
        player.fallDistance = 0.0f;
        setLastBlink(stack, now);
        return InteractionResult.SUCCESS;
    }

    /**
     * 调整落点以避免卡墙。向下看时额外抬高，并在不安全时向上搜索，
     * 同时尝试在落点周围小范围寻找可站立位置。
     */
    private Vec3 adjustLandingPosition(Level level, Vec3 target, Vec3 look, Player player) {
        // 向下看时额外抬高落点，避免卡在台阶/斜面/不完整方块内
        if (look.y < -0.1) {
            target = target.add(0, 0.25, 0);
        }

        // 优先尝试原落点，再尝试向上搜索，最后尝试水平微调
        double feetY = target.y - player.getEyeHeight();
        BlockPos basePos = BlockPos.containing(target.x, feetY, target.z);

        // 垂直搜索
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos feetPos = basePos.above(dy);
            if (isSafeStandingPos(level, feetPos, player)) {
                return new Vec3(feetPos.getX() + 0.5, feetPos.getY() + player.getEyeHeight(), feetPos.getZ() + 0.5);
            }
        }

        // 水平微调（用于落点紧贴方块边缘的情况）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos feetPos = basePos.offset(dx, 0, dz);
                if (isSafeStandingPos(level, feetPos, player)) {
                    return new Vec3(feetPos.getX() + 0.5, feetPos.getY() + player.getEyeHeight(),
                            feetPos.getZ() + 0.5);
                }
            }
        }

        return target;
    }

    /**
     * 在穿过阻挡点后向前搜索第一个安全的站立位置。
     */
    @Nullable
    private Vec3 findSafePos(Level level, Vec3 through, Vec3 maxEnd, Vec3 look, Player player) {
        double remainingDist = through.distanceTo(maxEnd);
        double step = 0.5;
        int steps = (int) Math.ceil(remainingDist / step);

        for (int i = 0; i <= steps; i++) {
            Vec3 check = through.add(look.scale(i * step));
            if (check.distanceTo(through) > remainingDist + 0.01) break;

            BlockPos feetPos = BlockPos.containing(check);
            if (isSafeStandingPos(level, feetPos, player)) {
                return new Vec3(feetPos.getX() + 0.5, feetPos.getY() + player.getEyeHeight(), feetPos.getZ() + 0.5);
            }
        }
        return null;
    }

    /**
     * 检查指定坐标是否为安全位置（使用实体碰撞箱检测，不要求脚下有地面）。
     */
    private boolean isSafeStandingPos(Level level, BlockPos pos, Player player) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        // 有碰撞箱的方块直接判定为不安全
        if (!feet.getCollisionShape(level, pos).isEmpty()) return false;
        if (!head.getCollisionShape(level, pos.above()).isEmpty()) return false;
        // 使用玩家碰撞箱进一步确认
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        AABB box = player.getBoundingBox()
                .move(x - player.getX(), y - player.getY(), z - player.getZ());
        return level.noCollision(player, box);
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
