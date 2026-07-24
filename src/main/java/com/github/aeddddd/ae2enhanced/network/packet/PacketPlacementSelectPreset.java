package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.client.gui.PlacementRadialMenuScreen;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;

/**
 * 客户端请求选择放置预设槽（C→S）.
 *
 * <p>槽位索引语义：</p>
 * <ul>
 * <li>0~8：选择对应预设槽。</li>
 * <li>9（即 PlacementConfig.MAX_PRESETS）：选取当前准星目标（中键）。</li>
 * <li>-2：清空当前选择（径向菜单空选项）。</li>
 * </ul>
 */
public class PacketPlacementSelectPreset implements ServerboundPacket {

    private final int slot;

    public PacketPlacementSelectPreset(int slot) {
        this.slot = slot;
    }

    public static PacketPlacementSelectPreset decode(FriendlyByteBuf buffer) {
        return new PacketPlacementSelectPreset(buffer.readByte());
    }

    public static void encode(PacketPlacementSelectPreset packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.slot);
    }

    public static void handle(PacketPlacementSelectPreset packet, Supplier<NetworkEvent.Context> contextSupplier) {
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
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)) {
            stack = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
            if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)) {
                return;
            }
        }

        PlacementConfig config = new PlacementConfig(stack);

        if (slot >= 0 && slot < PlacementConfig.MAX_PRESETS) {
            config.setSelectedSlot(slot);
        } else if (slot == PlacementRadialMenuScreen.SLOT_EMPTY) {
            config.setSelectedSlot(-1);
        } else if (slot == PlacementConfig.MAX_PRESETS) {
            // 中键选取当前准星目标
            HitResult ray = player.pick(5.0, 1.0f, false);
            if (!(ray instanceof BlockHitResult blockHit) || ray.getType() != HitResult.Type.BLOCK) return;

            ItemStack pick = PlacementTargetResolver.pickRepresentativeStack(player.level(), blockHit.getBlockPos());
            if (pick.isEmpty()) return;

            // 合并同种选取：普通物品精确比较；线缆按类型比较（忽略颜色）
            int existing = -1;
            for (int i = 0; i < PlacementConfig.MAX_PRESETS; i++) {
                ItemStack p = config.getStackInSlot(i);
                if (p.isEmpty()) continue;
                if (PlacementTargetResolver.isSameCableType(p, pick)
                        || (!PlacementTargetResolver.isCable(p)
                                && ItemStack.isSameItemSameTags(p, pick))) {
                    existing = i;
                    break;
                }
            }

            if (existing >= 0) {
                config.setSelectedSlot(existing);
            } else {
                int targetSlot = config.getSelectedSlot();
                if (targetSlot < 0 || targetSlot >= PlacementConfig.MAX_PRESETS
                        || !config.getStackInSlot(targetSlot).isEmpty()) {
                    targetSlot = config.getFirstEmptySlot();
                }
                if (targetSlot < 0) targetSlot = 0; // 满了则覆盖第 0 槽
                config.setStackInSlot(targetSlot, pick);
                config.setSelectedSlot(targetSlot);
            }
        }

        // 强制同步 NBT 到客户端，防止数据丢失
        player.setItemInHand(hand, stack);
    }

    public int getSlot() {
        return slot;
    }
}
