package com.github.aeddddd.ae2enhanced.client.render;

import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

/**
 * 多方块控制器 BlockEntityRenderer 抽象基类。
 * <p>统一处理：</p>
 * <ul>
 *   <li>成形状态检查</li>
 *   <li>基于配置的距离裁剪</li>
 *   <li>渲染包围盒计算</li>
 *   <li>控制器朝向获取</li>
 *   <li>结构几何中心/半径推导</li>
 * </ul>
 */
public abstract class AbstractMultiblockRenderer<T extends BlockEntity & IMultiblockController>
        implements BlockEntityRenderer<T> {

    protected final BlockEntityRendererProvider.Context context;

    protected AbstractMultiblockRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public final void render(T be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.isFormed()) {
            return;
        }
        if (!isRendererEnabled()) {
            return;
        }
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        // 距离裁剪
        double maxDist = AE2EnhancedConfig.CLIENT.renderDistance.get();
        if (maxDist > 0) {
            Vec3 center = getEffectCenterWorld(be);
            Vec3 cameraPos = context.getBlockEntityRenderDispatcher().camera.getPosition();
            if (center.distanceToSqr(cameraPos) > maxDist * maxDist) {
                return;
            }
        }

        poseStack.pushPose();
        renderEffect(be, partialTicks, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * 返回用于方块实体 {@link net.minecraft.world.level.block.entity.BlockEntity#getRenderBoundingBox()}
     * 的渲染包围盒。子类或对应方块实体可调用此方法。
     */
    public AABB getRenderBounds(T be) {
        Vec3 center = getEffectCenterWorld(be);
        double radius = getRenderRadius();
        return new AABB(center, center).inflate(radius);
    }

    /**
     * 子类实现具体特效渲染。
     */
    protected abstract void renderEffect(T be, float partialTicks, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay);

    /**
     * 子类声明是否启用本渲染器。
     */
    protected abstract boolean isRendererEnabled();

    /**
     * 子类返回特效中心相对于控制器原点的偏移（已考虑朝向）。
     * <p>默认返回结构几何中心。</p>
     */
    protected abstract Vec3 getEffectCenterOffset(T be);

    /**
     * 子类返回渲染半径，用于距离裁剪与包围盒。
     */
    protected abstract double getRenderRadius();

    /**
     * 获取世界空间中的特效中心位置。
     */
    protected Vec3 getEffectCenterWorld(T be) {
        BlockPos pos = be.getBlockPos();
        Vec3 offset = getEffectCenterOffset(be);
        return new Vec3(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
    }

    /**
     * 从方块状态读取控制器水平朝向。
     */
    protected Direction getFacing(T be) {
        Level level = be.getLevel();
        if (level == null) {
            return Direction.NORTH;
        }
        BlockState state = level.getBlockState(be.getBlockPos());
        if (state.hasProperty(MultiblockControllerBlock.FACING)) {
            return state.getValue(MultiblockControllerBlock.FACING);
        }
        return Direction.NORTH;
    }

    /**
     * 计算给定相对坐标集合在指定朝向下的包围盒。
     *
     * @param relPositions 结构相对坐标集合
     * @param facing 控制器朝向
     * @return float[6] {minX, minY, minZ, maxX, maxY, maxZ}
     */
    public static float[] computeBounds(Set<BlockPos> relPositions, Direction facing) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos rel : relPositions) {
            BlockPos rot = StructureUtils.rotate(rel, facing);
            minX = Math.min(minX, rot.getX());
            minY = Math.min(minY, rot.getY());
            minZ = Math.min(minZ, rot.getZ());
            maxX = Math.max(maxX, rot.getX());
            maxY = Math.max(maxY, rot.getY());
            maxZ = Math.max(maxZ, rot.getZ());
        }

        return new float[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /**
     * 由结构包围盒计算中心偏移（相对于控制器方块中心 0.5, 0.5, 0.5）。
     */
    public static Vec3 computeCenterOffset(float[] bounds) {
        return new Vec3(
                (bounds[0] + bounds[3]) * 0.5,
                (bounds[1] + bounds[4]) * 0.5,
                (bounds[2] + bounds[5]) * 0.5);
    }

    /**
     * 由结构包围盒计算包围球半径。
     */
    public static double computeRadius(float[] bounds) {
        double dx = bounds[3] - bounds[0];
        double dy = bounds[4] - bounds[1];
        double dz = bounds[5] - bounds[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5;
    }

    /**
     * 根据与相机的距离计算 LOD 分段数。
     *
     * @param distance 到特效中心的距离
     * @param base 近距离分段数
     * @param min 最远距离分段数下限
     * @return 当前 LOD 分段数
     */
    protected static int lodSegments(double distance, int base, int min) {
        if (!AE2EnhancedConfig.CLIENT.useLOD.get()) {
            return base;
        }
        if (distance < 32.0) {
            return base;
        } else if (distance < 64.0) {
            return Math.max(min, base / 2);
        } else if (distance < 128.0) {
            return Math.max(min, base / 4);
        } else {
            return Math.max(min, base / 8);
        }
    }

    /**
     * 获取基于总刻数的动画时间。
     */
    protected float getTime(T be, float partialTicks) {
        Level level = be.getLevel();
        return level == null ? 0 : (level.getGameTime() + partialTicks);
    }

    /**
     * 辅助：将 0.0~1.0 浮点颜色分量转换为 0~255 整数。
     */
    protected static int f2i(float v) {
        return Mth.clamp((int) (v * 255.0f), 0, 255);
    }
}
