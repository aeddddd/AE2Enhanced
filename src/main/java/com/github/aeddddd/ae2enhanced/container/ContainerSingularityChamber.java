package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.chamber.LongItemStore;
import com.github.aeddddd.ae2enhanced.container.slot.SlotLongStore;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 奇点处理仓 Container.
 *
 * <p>槽位布局：0-26 输入缓存虚拟槽、27-35 输出缓冲虚拟槽（{@link SlotLongStore},
 * 点击由 {@link #slotClick} 拦截,走标准 CPacketClickWindow 链路）、
 * 36 并行卡、37-40 加速卡、41+ 玩家物品栏.</p>
 *
 * <p>缓存内容与状态通过 {@link PacketChamberSync} 同步（打开首 tick + 每 2 tick）.</p>
 */
public class ContainerSingularityChamber extends Container {

    public static final int VIRTUAL_INPUT_START = 0;
    public static final int VIRTUAL_OUTPUT_START = TileSingularityChamber.INPUT_TYPES;
    public static final int SLOT_PARALLEL = VIRTUAL_OUTPUT_START + TileSingularityChamber.OUTPUT_TYPES;
    public static final int SLOT_SPEED_START = SLOT_PARALLEL + 1;
    public static final int PLAYER_INV_START = SLOT_SPEED_START + 4;

    private final TileSingularityChamber tile;
    /** 初始化为阈值,使打开 GUI 的首个 tick 立即下发一次同步 */
    private int syncCounter = 2;

    public ContainerSingularityChamber(InventoryPlayer playerInv, TileSingularityChamber tile) {
        this.tile = tile;

        // 输入缓存虚拟槽 9×3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new SlotLongStore(tile.getInputStore(), row * 9 + col,
                        7 + col * 18, 18 + row * 18));
            }
        }
        // 输出缓冲虚拟槽 9×1
        for (int col = 0; col < TileSingularityChamber.OUTPUT_TYPES; col++) {
            addSlotToContainer(new SlotLongStore(tile.getOutputStore(), col, 7 + col * 18, 88));
        }

        // 并行卡槽
        addSlotToContainer(new SlotItemHandler(tile.getCardSlots(), TileSingularityChamber.SLOT_PARALLEL, 7, 148) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemVirtualParallelCard;
            }
        });
        // 加速卡槽 ×4
        for (int i = 0; i < 4; i++) {
            addSlotToContainer(new SlotItemHandler(tile.getCardSlots(), 1 + i, 25 + i * 18, 148) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return stack.getItem() instanceof ItemUpgradeCard
                            && stack.getMetadata() == ItemUpgradeCard.META_SPEED;
                }
            });
        }

        // 玩家物品栏
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 7 + col * 18, 172 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 7 + col * 18, 230));
        }
    }

    public TileSingularityChamber getTile() {
        return tile;
    }

    // ---- 虚拟槽位交互（标准 slotClick 链路） ----

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (slotId >= 0 && slotId < SLOT_PARALLEL) {
            handleVirtualClick(slotId, dragType, clickType, player);
            detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    private void handleVirtualClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        boolean fromOutput = slotId >= VIRTUAL_OUTPUT_START;
        LongItemStore store = fromOutput ? tile.getOutputStore() : tile.getInputStore();
        int index = fromOutput ? slotId - VIRTUAL_OUTPUT_START : slotId - VIRTUAL_INPUT_START;
        LongItemStore.Entry entry = store.entryAt(index);
        ItemStack held = player.inventory.getItemStack();

        if (clickType == ClickType.PICKUP) {
            if (!held.isEmpty()) {
                // 光标有物 → 倒入输入缓存（左键全部 / 右键一个）,输出缓冲只出不进
                if (!fromOutput) {
                    int amount = dragType == 1 ? 1 : held.getCount();
                    long rem = tile.insertInput(held, amount);
                    int used = amount - (int) rem;
                    if (used > 0) {
                        held.shrink(used);
                        if (held.isEmpty()) {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                    }
                }
                return;
            }
            // 光标为空 → 取回到光标（左键一组 / 右键一个）
            if (entry == null) {
                return;
            }
            String key = LongItemStore.keyOf(entry.getTemplate());
            int amount = dragType == 1 ? 1 : 64;
            ItemStack stack = fromOutput
                    ? tile.withdrawOutput(key, amount)
                    : tile.withdrawInput(key, amount);
            if (!stack.isEmpty()) {
                player.inventory.setItemStack(stack);
            }
        } else if (clickType == ClickType.QUICK_MOVE && entry != null) {
            // Shift 点击 → 全部取回入背包
            String key = LongItemStore.keyOf(entry.getTemplate());
            for (int i = 0; i < 4096; i++) {
                ItemStack template = fromOutput
                        ? tile.getOutputStore().getTemplate(key)
                        : tile.getInputTemplate(key);
                if (template.isEmpty()) {
                    break;
                }
                ItemStack stack = fromOutput
                        ? tile.withdrawOutput(key, template.getMaxStackSize())
                        : tile.withdrawInput(key, template.getMaxStackSize());
                if (stack.isEmpty()) {
                    break;
                }
                if (!player.inventory.addItemStackToInventory(stack)) {
                    player.dropItem(stack, false);
                    break;
                }
            }
        }
    }

    // ---- shift-click（真实槽位） ----

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index >= PLAYER_INV_START) {
            boolean isCard = stack.getItem() instanceof ItemVirtualParallelCard
                    || (stack.getItem() instanceof ItemUpgradeCard
                    && stack.getMetadata() == ItemUpgradeCard.META_SPEED);
            if (isCard) {
                if (!mergeItemStack(stack, SLOT_PARALLEL, SLOT_SPEED_START + 4, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 倒入原料缓存
                long remaining = tile.insertInput(stack, stack.getCount());
                int inserted = stack.getCount() - (int) remaining;
                if (inserted <= 0) {
                    return ItemStack.EMPTY;
                }
                stack.shrink(inserted);
            }
        } else {
            if (!mergeItemStack(stack, PLAYER_INV_START, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return original;
    }

    // ---- 同步 ----

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (tile.getWorld() == null || tile.getWorld().isRemote) {
            return;
        }
        if (++syncCounter < 2) {
            return;
        }
        syncCounter = 0;
        for (IContainerListener listener : listeners) {
            if (listener instanceof EntityPlayerMP) {
                AE2Enhanced.network.sendTo(buildSyncPacket(), (EntityPlayerMP) listener);
            }
        }
    }

    private PacketChamberSync buildSyncPacket() {
        PacketChamberSync packet = new PacketChamberSync(
                tile.getPos(), tile.getEnergy(),
                tile.getParallelChannels(), tile.getUsedChannels(), tile.getActiveJobCount(),
                tile.getRedstoneMode().ordinal());
        for (LongItemStore.Entry entry : tile.getInputStore().getEntries()) {
            packet.addInput(entry.getTemplate(), entry.getCount());
        }
        for (LongItemStore.Entry entry : tile.getOutputStore().getEntries()) {
            packet.addOutput(entry.getTemplate(), entry.getCount());
        }
        for (PacketChamberSync.JobView job : tile.getJobViews()) {
            packet.addJob(job);
        }
        return packet;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.getWorld() != null
                && tile.getWorld().getTileEntity(tile.getPos()) == tile
                && player.getDistanceSq(tile.getPos().add(0.5, 0.5, 0.5)) <= 64.0;
    }
}
