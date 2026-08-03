package com.github.aeddddd.ae2enhanced.item;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import com.github.aeddddd.ae2enhanced.memorycard.core.UMCCopyService;
import com.github.aeddddd.ae2enhanced.memorycard.core.UMCPasteService;
import com.github.aeddddd.ae2enhanced.memorycard.core.UMCSelectionService;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;
import com.github.aeddddd.ae2enhanced.menu.UniversalMemoryCardMenu;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;

/**
 * 通用内存卡:复制/粘贴 AE2 设备配置(含升级卡),选取世界中的目标方块.
 *
 * <p>架构约定:业务逻辑在 memorycard 包的 UMCCopyService / UMCPasteService / UMCSelectionService,
 * 本类只保留 NBT 序列化、服务端动作分发、tooltip 渲染;
 * 客户端交互事件见 {@code client/handler/UMCClientEventHandler}.</p>
 *
 * <p>绑定逻辑相对 1.12 的重构:1.12 绑定中枢 ME 接口/回收节点用于粘贴时向网络请求物品,
 * 1.20 改为绑定 AE2 原生无线访问点(Wireless Access Point),见 {@link UMCNetworkLink}.</p>
 */
public class UniversalMemoryCardItem extends Item {

    private static final String NBT_CONFIG = "ae2e:umc_config";
    private static final String NBT_SELECTIONS = "ae2e:umc_selections";
    private static final String NBT_MODE = "ae2e:umc_mode";

    public enum Mode {
        CONFIG_COPY
    }

    public UniversalMemoryCardItem(Properties properties) {
        super(properties);
    }

    // ============================================================
    // SelectionEntry
    // ============================================================

    public static class SelectionEntry {
        public final BlockPos pos;
        /** 维度 ResourceLocation 字符串(1.12 为 int 维度 id) */
        public final String dim;
        public final String tileId;
        public final int side;

        public SelectionEntry(BlockPos pos, String dim, String tileId, int side) {
            this.pos = pos;
            this.dim = dim;
            this.tileId = tileId;
            this.side = side;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos.asLong());
            tag.putString("dim", dim);
            tag.putString("id", tileId);
            tag.putInt("side", side);
            return tag;
        }

        public static SelectionEntry fromNBT(CompoundTag tag) {
            return new SelectionEntry(
                    BlockPos.of(tag.getLong("pos")),
                    tag.getString("dim"),
                    tag.getString("id"),
                    tag.contains("side") ? tag.getInt("side") : -1);
        }
    }

    public static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    // ============================================================
    // NBT Helpers
    // ============================================================

    public static boolean hasConfig(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(NBT_CONFIG);
    }

    @Nullable
    public static CompoundTag getConfig(ItemStack stack) {
        if (!hasConfig(stack)) {
            return null;
        }
        return stack.getTag().getCompound(NBT_CONFIG);
    }

    public static void setConfig(ItemStack stack, String handlerId, String name, CompoundTag data) {
        CompoundTag config = new CompoundTag();
        config.putString("handler", handlerId);
        config.putString("name", name);
        config.put("data", data);
        stack.getOrCreateTag().put(NBT_CONFIG, config);
    }

    public static void clearConfig(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(NBT_CONFIG);
        }
    }

    public static List<SelectionEntry> getSelections(ItemStack stack) {
        List<SelectionEntry> list = new ArrayList<>();
        if (!stack.hasTag()) {
            return list;
        }
        CompoundTag tag = stack.getTag();
        if (!tag.contains(NBT_SELECTIONS)) {
            return list;
        }
        ListTag selections = tag.getList(NBT_SELECTIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < selections.size(); i++) {
            list.add(SelectionEntry.fromNBT(selections.getCompound(i)));
        }
        return list;
    }

    public static int getSelectionCount(ItemStack stack) {
        return getSelections(stack).size();
    }

    public static void addSelection(ItemStack stack, SelectionEntry entry) {
        ListTag selections;
        CompoundTag root = stack.getOrCreateTag();
        if (root.contains(NBT_SELECTIONS)) {
            selections = root.getList(NBT_SELECTIONS, Tag.TAG_COMPOUND);
        } else {
            selections = new ListTag();
        }
        for (int i = 0; i < selections.size(); i++) {
            CompoundTag tag = selections.getCompound(i);
            if (BlockPos.of(tag.getLong("pos")).equals(entry.pos) && tag.getString("dim").equals(entry.dim)) {
                return;
            }
        }
        selections.add(entry.toNBT());
        root.put(NBT_SELECTIONS, selections);
    }

    public static void removeSelection(ItemStack stack, int index) {
        if (!stack.hasTag()) {
            return;
        }
        ListTag selections = stack.getTag().getList(NBT_SELECTIONS, Tag.TAG_COMPOUND);
        if (index >= 0 && index < selections.size()) {
            selections.remove(index);
        }
        if (selections.isEmpty()) {
            stack.getTag().remove(NBT_SELECTIONS);
        }
    }

    public static void clearSelections(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(NBT_SELECTIONS);
        }
    }

    // ============================================================
    // Mode Helpers
    // ============================================================

    public static Mode getMode(ItemStack stack) {
        if (!stack.hasTag()) {
            return Mode.CONFIG_COPY;
        }
        int ordinal = stack.getTag().getInt(NBT_MODE);
        if (ordinal < 0 || ordinal >= Mode.values().length) {
            return Mode.CONFIG_COPY;
        }
        return Mode.values()[ordinal];
    }

    public static void setMode(ItemStack stack, Mode mode) {
        stack.getOrCreateTag().putInt(NBT_MODE, mode.ordinal());
    }

    public static boolean isUniversalMemoryCard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof UniversalMemoryCardItem;
    }

    // ============================================================
    // Server Action Handler (called by PacketUMCAction)
    // ============================================================

    public static void handleServerAction(ServerPlayer player, PacketUMCAction message) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof UniversalMemoryCardItem)) {
            return;
        }

        switch (message.getType()) {
            case COPY -> UMCCopyService.handleCopy(player, stack, message.getPos(), message.getFace());
            case PASTE -> UMCPasteService.handlePaste(player, stack, message.getPos(), message.getFace());
            case SELECT -> UMCSelectionService.handleSelect(player, stack, message.getPos(), message.getFace());
            case CLEAR_CONFIG -> clearConfig(stack);
            case CLEAR_SELECTIONS -> clearSelections(stack);
            case REMOVE_SELECTION -> removeSelection(stack, message.getIndex());
            case OPEN_GUI -> openGui(player);
            case BIND_ACCESS_POINT -> UMCSelectionService.handleBindAccessPoint(player, stack, message.getPos());
            case CLEAR_BINDING -> UMCSelectionService.handleClearBinding(player, stack);
        }

        // 强制同步主手 NBT 到客户端
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        player.inventoryMenu.broadcastChanges();
    }

    private static void openGui(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inv, p) -> new UniversalMemoryCardMenu(id, inv, 0),
                Component.translatable("gui.ae2enhanced.umc.title")),
                buf -> buf.writeByte(0));
    }

    // ============================================================
    // Tooltip
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.header"));
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.separator"));

        if (hasConfig(stack)) {
            CompoundTag config = getConfig(stack);
            tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.source",
                    config.getString("name")));

            CompoundTag data = config.getCompound("data");
            if (data.contains("ae2e:upgrades")) {
                ListTag upgrades = data.getList("ae2e:upgrades", Tag.TAG_COMPOUND);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < upgrades.size(); i++) {
                    ItemStack upgradeStack = ItemStack.of(upgrades.getCompound(i));
                    if (!upgradeStack.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(upgradeStack.getHoverName().getString());
                        if (upgradeStack.getCount() > 1) {
                            sb.append("×").append(upgradeStack.getCount());
                        }
                    }
                }
                if (sb.length() > 0) {
                    tooltip.add(Component.translatable(
                            "item.ae2enhanced.universal_memory_card.tooltip.upgrades_detail", sb.toString()));
                }
            }
        } else {
            tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.no_config"));
        }

        int count = getSelectionCount(stack);
        if (count > 0) {
            tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.selections", count));
        }

        // 绑定的无线访问点
        var linkedPos = UMCNetworkLink.getLinkedPos(stack);
        if (linkedPos != null) {
            tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.binding",
                    linkedPos.dimension().location() + " " + linkedPos.pos().toShortString()));
        } else {
            tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.no_binding"));
        }

        Mode mode = getMode(stack);
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.mode",
                Component.translatable(
                        "item.ae2enhanced.universal_memory_card.tooltip.mode." + mode.name().toLowerCase())));

        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.separator"));
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.sneak"));
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.use"));
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.ctrl"));
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.bind"));
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.alt"));
        tooltip.add(Component.translatable("item.ae2enhanced.universal_memory_card.tooltip.air"));
    }
}
