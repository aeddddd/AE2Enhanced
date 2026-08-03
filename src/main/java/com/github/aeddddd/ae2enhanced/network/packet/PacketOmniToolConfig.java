package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.network.NetworkEvent;

import appeng.api.util.AEColor;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementRestriction;

/**
 * 客户端发送先进 ME 全能工具配置更新到服务端（C→S）.
 * 服务端全量写入工具 NBT,附魔按 source 等级钳制,并强制同步到客户端.
 */
public class PacketOmniToolConfig implements ServerboundPacket {

    /** 参数启用掩码仅应用低 12 位（对应基础参数 PID 0~11）. */
    private static final int PARAM_MASK_BITS = 12;

    private final int mode;
    private final int dropMode;
    private final boolean silkTouch;
    private final int fortune;
    private final double blinkDistance;
    private final int breakCooldown;
    private final int paramEnabled;
    private final boolean chaosForceKill;
    private final boolean conformalEnabled;
    private final boolean advancedSilkTouch;
    private final boolean wallPhase;
    private final int cableColor;
    private final float reachDistance;
    private final int placementRestriction;
    private final ListTag enchantments;

    public PacketOmniToolConfig(int mode, int dropMode, boolean silkTouch,
            int fortune, double blinkDistance, int breakCooldown, int paramEnabled,
            boolean chaosForceKill, boolean conformalEnabled, boolean advancedSilkTouch, boolean wallPhase,
            int cableColor, float reachDistance, int placementRestriction, ListTag enchantments) {
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

    public static PacketOmniToolConfig decode(FriendlyByteBuf buffer) {
        int mode = buffer.readByte();
        int dropMode = buffer.readByte();
        boolean silkTouch = buffer.readBoolean();
        int fortune = buffer.readVarInt();
        double blinkDistance = buffer.readDouble();
        int breakCooldown = buffer.readByte();
        int paramEnabled = buffer.readShort();
        boolean chaosForceKill = buffer.readBoolean();
        boolean conformalEnabled = buffer.readBoolean();
        boolean advancedSilkTouch = buffer.readBoolean();
        boolean wallPhase = buffer.readBoolean();
        int cableColor = buffer.readByte();
        float reachDistance = buffer.readFloat();
        int placementRestriction = buffer.readByte();
        ListTag enchantments = new ListTag();
        CompoundTag wrapper = buffer.readNbt();
        if (wrapper != null && wrapper.contains("ench", Tag.TAG_LIST)) {
            enchantments = wrapper.getList("ench", Tag.TAG_COMPOUND);
        }
        return new PacketOmniToolConfig(mode, dropMode, silkTouch, fortune, blinkDistance, breakCooldown, paramEnabled,
                chaosForceKill, conformalEnabled, advancedSilkTouch, wallPhase, cableColor, reachDistance,
                placementRestriction, enchantments);
    }

    public static void encode(PacketOmniToolConfig packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.mode);
        buffer.writeByte(packet.dropMode);
        buffer.writeBoolean(packet.silkTouch);
        buffer.writeVarInt(packet.fortune);
        buffer.writeDouble(packet.blinkDistance);
        buffer.writeByte(packet.breakCooldown);
        buffer.writeShort(packet.paramEnabled & 0xFFF); // 只写低 12 位
        buffer.writeBoolean(packet.chaosForceKill);
        buffer.writeBoolean(packet.conformalEnabled);
        buffer.writeBoolean(packet.advancedSilkTouch);
        buffer.writeBoolean(packet.wallPhase);
        buffer.writeByte(packet.cableColor);
        buffer.writeFloat(packet.reachDistance);
        buffer.writeByte(packet.placementRestriction);
        CompoundTag wrapper = new CompoundTag();
        if (packet.enchantments != null && !packet.enchantments.isEmpty()) {
            wrapper.put("ench", packet.enchantments);
        }
        buffer.writeNbt(wrapper);
    }

    public static void handle(PacketOmniToolConfig packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                packet.handleOnServer(player);
            }
        });
        context.setPacketHandled(true);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)) {
                continue;
            }
            OmniToolUpgrades.setMode(stack, mode);
            OmniToolUpgrades.setDropMode(stack, dropMode);
            OmniToolUpgrades.setSilkTouchEnabled(stack, silkTouch);
            OmniToolUpgrades.setBlinkDistance(stack, blinkDistance);
            OmniToolUpgrades.setBreakCooldown(stack, Math.max(0, breakCooldown));
            for (int i = 0; i < PARAM_MASK_BITS; i++) {
                OmniToolUpgrades.setParamEnabled(stack, i, (paramEnabled & (1 << i)) != 0);
            }
            OmniToolUpgrades.setChaosForceKillEnabled(stack, chaosForceKill);
            OmniToolUpgrades.setConformalCharge(stack, conformalEnabled);
            OmniToolUpgrades.setAdvancedSilkTouchEnabled(stack, advancedSilkTouch);
            OmniToolUpgrades.setWallPhaseEnabled(stack, wallPhase);

            // 应用放置工具配置：线缆颜色、触及距离、方向锁
            PlacementConfig placementConfig = new PlacementConfig(stack);
            if (cableColor >= 0 && cableColor < AEColor.values().length) {
                placementConfig.setCableColor(AEColor.values()[cableColor]);
            }
            placementConfig.setReachDistance(reachDistance);
            placementConfig.setPlacementRestriction(PlacementRestriction.fromOrdinal(placementRestriction));

            // 时运滑条：先记录当前 source 上限（附魔列表应用后会丢失时运条目）
            ResourceLocation fortuneId = BuiltInRegistries.ENCHANTMENT.getKey(Enchantments.BLOCK_FORTUNE);
            int fortuneSource = OmniToolEnchantments.getEnchantmentSourceLevel(stack, fortuneId);

            // 同步附魔存储,并按已有 source 等级上限进行钳制
            ListTag ench = enchantments != null ? enchantments : new ListTag();
            for (int i = 0; i < ench.size(); i++) {
                CompoundTag tag = ench.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
                if (id == null) {
                    continue;
                }
                int source = OmniToolEnchantments.getEnchantmentSourceLevel(stack, id);
                if (source > 0) {
                    tag.putShort("lvl", (short) Math.min(tag.getShort("lvl"), source));
                }
            }
            OmniToolEnchantments.setStoredEnchantments(stack, ench);

            // 应用时运滑条（受合成时附魔书的 source 等级钳制）
            OmniToolUpgrades.setFortuneLevel(stack, Mth.clamp(fortune, 0, fortuneSource));

            // 强制同步 NBT 到客户端
            player.setItemInHand(hand, stack);
            break;
        }
    }
}
