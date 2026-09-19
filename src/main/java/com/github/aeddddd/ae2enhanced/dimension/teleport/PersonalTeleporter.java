package com.github.aeddddd.ae2enhanced.dimension.teleport;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/**
 * 个人维度定点传送器, 不生成传送门.
 * 继承原版 Teleporter 以获得稳定的跨维度实体放置支持, 在 {@link #placeInPortal} 中直接设定目标坐标与朝向.
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

    @Override
    public void placeInPortal(Entity entity, float rotationYaw) {
        entity.setLocationAndAngles(x, y, z, this.yaw, this.pitch);
        entity.motionX = 0;
        entity.motionY = 0;
        entity.motionZ = 0;
        // 不再调用 setPositionAndUpdate, 避免额外触发 chunk 加载/同步;
        // PlayerList.transferPlayerToDimension 后续会调用 setPlayerLocation 完成最终定位.
    }
}
