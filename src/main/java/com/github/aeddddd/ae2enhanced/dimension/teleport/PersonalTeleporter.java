package com.github.aeddddd.ae2enhanced.dimension.teleport;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/**
 * 个人维度定点传送器，避免生成传送门。
 *
 * <p>继承原版 Teleporter 以获得 Forge 最稳定的跨维度实体放置支持，
 * 在 {@link #placeInPortal} 中直接设定目标坐标与朝向，不生成任何传送门结构。</p>
 */
public class PersonalTeleporter extends Teleporter {

    private final double x, y, z;
    private final float yaw, pitch;

    public PersonalTeleporter(WorldServer world, double x, double y, double z, float yaw, float pitch) {
        super(world);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void placeInPortal(Entity entity, float rotationYaw) {
        entity.setLocationAndAngles(x, y, z, this.yaw, this.pitch);
        entity.motionX = 0;
        entity.motionY = 0;
        entity.motionZ = 0;
        // 不再调用 setPositionAndUpdate，避免额外触发 chunk 加载/同步；
        // PlayerList.transferPlayerToDimension 后续会调用 setPlayerLocation 完成最终定位。
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, float rotationYaw) {
        return false;
    }

    @Override
    public boolean makePortal(Entity entity) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long worldTime) {
    }

    /**
     * 构造一个以 BlockPos 为中心的传送器（Y 轴向下偏移 0.1 防止卡在方块边缘）。
     */
    public static PersonalTeleporter at(WorldServer world, BlockPos pos, float yaw, float pitch) {
        return new PersonalTeleporter(world, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, yaw, pitch);
    }
}
