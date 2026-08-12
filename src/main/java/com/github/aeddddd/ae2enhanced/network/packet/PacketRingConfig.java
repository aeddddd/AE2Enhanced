package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.ring.RingLocator;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端发送指环配置更新到服务端.
 * 服务端对所有字段做白名单校验与钳制,防止客户端注入非法值.
 */
public class PacketRingConfig implements IMessage {

    private NBTTagCompound config = new NBTTagCompound();

    public PacketRingConfig() {}

    public PacketRingConfig(NBTTagCompound config) {
        this.config = config != null ? config : new NBTTagCompound();
    }

    public NBTTagCompound getConfig() {
        return config;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        config = tag != null ? tag : new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, config);
    }

    public static class Handler implements IMessageHandler<PacketRingConfig, IMessage> {
        @Override
        public IMessage onMessage(PacketRingConfig message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            NBTTagCompound cfg = message.getConfig();
            player.getServerWorld().addScheduledTask(() -> apply(player, cfg));
            return null;
        }

        private static void apply(EntityPlayerMP player, NBTTagCompound cfg) {
            ItemStack ring = RingLocator.findRing(player);
            if (ring.isEmpty()) return;
            NBTTagCompound tag = ring.hasTagCompound() ? ring.getTagCompound() : new NBTTagCompound();
            ring.setTagCompound(tag);

            int maxPct = AE2EnhancedConfig.ring.maxSpeedPercent;
            int maxJump = AE2EnhancedConfig.ring.maxJumpPercent;
            float maxReach = (float) AE2EnhancedConfig.ring.maxReachDistance;
            boolean tier1 = RingNBT.tierAtLeast(ring, 1);
            boolean tier2 = RingNBT.tierAtLeast(ring, 2);

            // ---- 阶段 I 功能(始终允许) ----
            copyBool(cfg, tag, RingNBT.MINING_FIX);
            copyBool(cfg, tag, RingNBT.NIGHT_VISION);
            copyBool(cfg, tag, RingNBT.WALK_TWEAK);
            copyBool(cfg, tag, RingNBT.FEED);
            if (cfg.hasKey(RingNBT.REACH)) {
                tag.setFloat(RingNBT.REACH, clamp(cfg.getFloat(RingNBT.REACH), 5.0f, maxReach));
            }
            if (cfg.hasKey(RingNBT.WALK_SPEED)) {
                tag.setFloat(RingNBT.WALK_SPEED,
                        clamp(cfg.getFloat(RingNBT.WALK_SPEED), 0.05f, 0.1f * maxPct / 100f));
            }
            if (cfg.hasKey(RingNBT.FEED_MODE)) {
                tag.setInteger(RingNBT.FEED_MODE, clampInt(cfg.getInteger(RingNBT.FEED_MODE), 0, 1));
            }
            if (cfg.hasKey(RingNBT.POTION_MODE)) {
                tag.setInteger(RingNBT.POTION_MODE, clampInt(cfg.getInteger(RingNBT.POTION_MODE), 0, 2));
            }

            // ---- 阶段 II 功能(阶段不足时强制关闭,防客户端注入) ----
            if (tier1) {
                copyBool(cfg, tag, RingNBT.FLIGHT);
                copyBool(cfg, tag, RingNBT.NO_INERTIA);
                copyBool(cfg, tag, RingNBT.HEAL_AUTO);
                if (cfg.hasKey(RingNBT.FLY_SPEED)) {
                    tag.setFloat(RingNBT.FLY_SPEED,
                            clamp(cfg.getFloat(RingNBT.FLY_SPEED), 0.05f, 0.05f * maxPct / 100f));
                }
                if (cfg.hasKey(RingNBT.JUMP_PCT)) {
                    tag.setInteger(RingNBT.JUMP_PCT,
                            clampInt(cfg.getInteger(RingNBT.JUMP_PCT), 100, maxJump));
                }
                if (cfg.hasKey(RingNBT.HEAL_PCT)) {
                    tag.setInteger(RingNBT.HEAL_PCT, clampInt(cfg.getInteger(RingNBT.HEAL_PCT), 1, 100));
                }
            } else {
                tag.setBoolean(RingNBT.FLIGHT, false);
                tag.setBoolean(RingNBT.NO_INERTIA, false);
                tag.setBoolean(RingNBT.HEAL_AUTO, false);
            }

            // ---- 阶段 III 功能 ----
            if (tier2) {
                copyBool(cfg, tag, RingNBT.WALL_PHASE);
                copyBool(cfg, tag, RingNBT.DMG_BLOCK);
            } else {
                tag.setBoolean(RingNBT.WALL_PHASE, false);
                tag.setBoolean(RingNBT.DMG_BLOCK, true);
            }

            // ---- 阶段 IV(飞升): 强制飞行仅限飞升指环 ----
            if (RingNBT.isAscended(ring)) {
                copyBool(cfg, tag, RingNBT.FORCE_FLIGHT);
            } else {
                tag.setBoolean(RingNBT.FORCE_FLIGHT, false);
            }

            // 强制同步到客户端(覆盖手上/背包/饰品中的同一堆叠)
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        private static void copyBool(NBTTagCompound from, NBTTagCompound to, String key) {
            if (from.hasKey(key)) {
                to.setBoolean(key, from.getBoolean(key));
            }
        }

        private static float clamp(float v, float min, float max) {
            return v < min ? min : (v > max ? max : v);
        }

        private static int clampInt(int v, int min, int max) {
            return v < min ? min : (v > max ? max : v);
        }
    }
}
