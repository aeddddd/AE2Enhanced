package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.util.AEColor;
import com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.omnitool.placement.PlacementRestriction;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 客户端发送先进ME工具配置更新到服务端.
 */
public class PacketOmniToolConfig implements IMessage {

    private int mode;
    private int dropMode;
    private boolean silkTouch;
    private int fortune; // 保留兼容旧版工具
    private double blinkDistance;
    private int breakCooldown;
    private int paramEnabled;
    private boolean chaosForceKill;
    private boolean conformalEnabled;
    private boolean advancedSilkTouch;
    private boolean wallPhase;
    private int cableColor;
    private float reachDistance;
    private int placementRestriction;
    private NBTTagList enchantments;

    public PacketOmniToolConfig() {
    }

    public PacketOmniToolConfig(int mode, int dropMode, boolean silkTouch,
                                 int fortune, double blinkDistance, int breakCooldown,
                                 int paramEnabled, boolean chaosForceKill, boolean conformalEnabled,
                                 boolean advancedSilkTouch, boolean wallPhase, int cableColor, float reachDistance,
                                 int placementRestriction, NBTTagList enchantments) {
        this.mode = mode;
        this.dropMode = dropMode;
        this.silkTouch = silkTouch;
        this.fortune = fortune;
        this.blinkDistance = blinkDistance;
        this.breakCooldown = breakCooldown;
        this.paramEnabled = paramEnabled;
        this.chaosForceKill = chaosForceKill;
        this.conformalEnabled = conformalEnabled;
        this.advancedSilkTouch = advancedSilkTouch;
        this.wallPhase = wallPhase;
        this.cableColor = cableColor;
        this.reachDistance = reachDistance;
        this.placementRestriction = placementRestriction;
        this.enchantments = enchantments;
    }

    public int getMode() { return mode; }
    public int getDropMode() { return dropMode; }
    public boolean isSilkTouch() { return silkTouch; }
    public int getFortune() { return fortune; }
    public double getBlinkDistance() { return blinkDistance; }
    public int getBreakCooldown() { return breakCooldown; }
    public int getParamEnabled() { return paramEnabled; }
    public boolean isChaosForceKill() { return chaosForceKill; }
    public boolean isConformalEnabled() { return conformalEnabled; }
    public boolean isAdvancedSilkTouch() { return advancedSilkTouch; }
    public boolean isWallPhase() { return wallPhase; }
    public int getCableColor() { return cableColor; }
    public float getReachDistance() { return reachDistance; }
    public int getPlacementRestriction() { return placementRestriction; }
    public NBTTagList getEnchantments() { return enchantments; }

    public boolean isValid() {
        if (mode < 0 || mode >= ItemAdvancedMEOmniTool.MODE_COUNT
                || dropMode < ItemAdvancedMEOmniTool.DROP_NORMAL
                || dropMode > ItemAdvancedMEOmniTool.DROP_AE
                || fortune < 0 || breakCooldown < 0 || breakCooldown > 10
                || Double.isNaN(blinkDistance) || Double.isInfinite(blinkDistance)
                || blinkDistance < 1.0D || blinkDistance > 256.0D
                || paramEnabled < 0 || (paramEnabled & ~0xFFF) != 0
                || cableColor < 0 || cableColor >= AEColor.values().length
                || Float.isNaN(reachDistance) || Float.isInfinite(reachDistance)
                || reachDistance < PlacementConfig.MIN_REACH_DISTANCE
                || reachDistance > PlacementConfig.MAX_REACH_DISTANCE
                || placementRestriction < 0
                || placementRestriction >= PlacementRestriction.values().length
                || enchantments == null || enchantments.tagCount() > 64) {
            return false;
        }
        for (int i = 0; i < enchantments.tagCount(); i++) {
            NBTTagCompound tag = enchantments.getCompoundTagAt(i);
            if (tag.getShort("id") < 0 || tag.getShort("lvl") < 0) return false;
        }
        return true;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        dropMode = buf.readByte();
        silkTouch = buf.readBoolean();
        fortune = buf.readByte();
        blinkDistance = buf.readDouble();
        breakCooldown = buf.readByte();
        paramEnabled = buf.readShort();
        chaosForceKill = buf.readBoolean();
        conformalEnabled = buf.readBoolean();
        advancedSilkTouch = buf.readBoolean();
        wallPhase = buf.readBoolean();
        cableColor = buf.readByte();
        reachDistance = buf.readFloat();
        placementRestriction = buf.readByte();
        NBTTagCompound wrapper = ByteBufUtils.readTag(buf);
        if (wrapper != null && wrapper.hasKey("ench", 9)) {
            enchantments = wrapper.getTagList("ench", 10);
        } else {
            enchantments = new NBTTagList();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        buf.writeByte(dropMode);
        buf.writeBoolean(silkTouch);
        buf.writeByte(fortune);
        buf.writeDouble(blinkDistance);
        buf.writeByte(breakCooldown);
        buf.writeShort(paramEnabled);
        buf.writeBoolean(chaosForceKill);
        buf.writeBoolean(conformalEnabled);
        buf.writeBoolean(advancedSilkTouch);
        buf.writeBoolean(wallPhase);
        buf.writeByte(cableColor);
        buf.writeFloat(reachDistance);
        buf.writeByte(placementRestriction);
        NBTTagCompound wrapper = new NBTTagCompound();
        if (enchantments != null && enchantments.tagCount() > 0) {
            wrapper.setTag("ench", enchantments);
        }
        ByteBufUtils.writeTag(buf, wrapper);
    }
}
