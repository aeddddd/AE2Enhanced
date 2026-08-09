package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.util.AEColor;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOmniToolConfigHandler implements IMessageHandler<PacketOmniToolConfig, IMessage> {
    @Override
    public IMessage onMessage(PacketOmniToolConfig message, MessageContext ctx) {
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            for (EnumHand hand : EnumHand.values()) {
                ItemStack stack = player.getHeldItem(hand);
                if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                    ItemAdvancedMEOmniTool.setMode(stack, message.getMode());
                    ItemAdvancedMEOmniTool.setDropMode(stack, message.getDropMode());
                    ItemAdvancedMEOmniTool.setSilkTouchEnabled(stack, message.isSilkTouch());
                    TravelModule.setBlinkDistance(stack, message.getBlinkDistance());
                    MiningModule.setBreakCooldown(stack, Math.max(0, message.getBreakCooldown()));
                    int mask = message.getParamEnabled();
                    for (int i = 0; i < 12; i++) {
                        ItemAdvancedMEOmniTool.setParamEnabled(stack, i, (mask & (1 << i)) != 0);
                    }
                    // 混沌强杀与共形不变荷需对应升级已安装,未安装则忽略客户端字段,防止免费获取升级
                    if (ItemAdvancedMEOmniTool.hasChaosCore(stack)) {
                        ItemAdvancedMEOmniTool.setChaosForceKillEnabled(stack, message.isChaosForceKill());
                    }
                    if (ItemAdvancedMEOmniTool.hasConformalCharge(stack)) {
                        ItemAdvancedMEOmniTool.setConformalCharge(stack, message.isConformalEnabled());
                    }
                    ItemAdvancedMEOmniTool.setAdvancedSilkTouchEnabled(stack, message.isAdvancedSilkTouch());
                    TravelModule.setWallPhaseEnabled(stack, message.isWallPhase());

                    // 应用放置工具配置：线缆颜色、触及距离
                    PlacementConfig placementConfig = new PlacementConfig(stack);
                    int colorIdx = message.getCableColor();
                    if (colorIdx >= 0 && colorIdx < AEColor.values().length) {
                        placementConfig.setCableColor(AEColor.values()[colorIdx]);
                    }
                    placementConfig.setReachDistance(message.getReachDistance());
                    placementConfig.setPlacementRestriction(com.github.aeddddd.ae2enhanced.util.placement.PlacementRestriction.fromOrdinal(message.getPlacementRestriction()));

                    // 同步附魔存储：source<=0 的条目为非法注入直接移除,
                    // 其余条目等级按 source level 与配置上限双重钳制
                    NBTTagList ench = message.getEnchantments();
                    if (ench != null) {
                        NBTTagList filtered = new NBTTagList();
                        int maxLevel = AE2EnhancedConfig.omniTool.maxEnchantmentLevel;
                        for (int i = 0; i < ench.tagCount(); i++) {
                            net.minecraft.nbt.NBTTagCompound tag = ench.getCompoundTagAt(i);
                            short id = tag.getShort("id");
                            int source = ItemAdvancedMEOmniTool.getEnchantmentSourceLevel(stack, id);
                            if (source <= 0) {
                                continue;
                            }
                            int lvl = Math.min(tag.getShort("lvl"), Math.min(source, maxLevel));
                            tag.setShort("lvl", (short) lvl);
                            filtered.appendTag(tag);
                        }
                        ench = filtered;
                    }
                    ItemAdvancedMEOmniTool.setStoredEnchantments(stack, ench != null ? ench : new NBTTagList());

                    // 强制同步NBT到客户端
                    player.setHeldItem(hand, stack);
                    break;
                }
            }
        });
        return null;
    }
}
