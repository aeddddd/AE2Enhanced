package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.container.slot.AppEngSlot;
import appeng.util.Platform;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.helpers.InventoryAction;
import appeng.util.InventoryAdaptor;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.WrapperCursorItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.WrapperRangeItemHandler;
import appeng.util.inv.filter.IAEItemFilter;
import com.github.aeddddd.ae2enhanced.integration.terminal.AssemblyInterfaceTracker;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 扩展接口终端,使其显示装配中枢的样板存储.
 *
 * <p>装配中枢不是 AE2 原版的 {@code IInterfaceHost},因此无法被原版扫描逻辑覆盖.
 * 本 Mixin 在 {@code ContainerInterfaceTerminal} 中维护独立的装配中枢跟踪数据,
 * 在 {@code regenList} 时把装配中枢数据合并进同一个 NBT 包,在检测到变化时单独补发刷新包.</p>
 */
@Mixin(value = ContainerInterfaceTerminal.class, remap = false)
public class MixinContainerInterfaceTerminal {

    @Shadow
    private IGrid grid;

    @Shadow
    private NBTTagCompound data;

    @Shadow
    private void regenList(NBTTagCompound data) {
    }

    private final Map<TileAssemblyController, AssemblyInterfaceTracker> ae2enhanced$assemblyDiList = new HashMap<>();
    private final Map<Long, AssemblyInterfaceTracker> ae2enhanced$assemblyById = new HashMap<>();

    /**
     * 在 regenList 末尾追加装配中枢数据.
     * 这样当终端因接口列表变化而进行全量刷新时,装配中枢也会一起显示.
     */
    @Inject(method = "regenList", at = @At("TAIL"))
    private void ae2enhanced$onRegenList(NBTTagCompound data, CallbackInfo ci) {
        ae2enhanced$regenAssemblyList(data);
    }

    /**
     * 在 detectAndSendChanges 末尾检测装配中枢变化.
     * 若发现装配中枢增删或样板槽内容变化,调用 regenList 发送一次带 clear 的全量刷新.
     */
    @Inject(method = "func_75142_b", at = @At("TAIL"))
    private void ae2enhanced$onDetectAndSendChanges(CallbackInfo ci) {
        if (Platform.isClient()) {
            return;
        }
        if (this.grid == null) {
            return;
        }
        if (!ae2enhanced$checkAssemblyChanges()) {
            return;
        }
        this.regenList(this.data);
        if (this.data.getSize() != 0) {
            try {
                NetworkHandler.instance().sendTo(new PacketCompressedNBT(this.data),
                        (EntityPlayerMP) ((AEBaseContainer) (Object) this).getPlayerInv().player);
            } catch (IOException ignored) {
            }
            this.data = new NBTTagCompound();
        }
    }

    /**
     * 拦截 doAction,处理装配中枢相关的终端操作.
     */
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onDoAction(EntityPlayerMP player, InventoryAction action, int slot, long id,
                                        CallbackInfo ci) {
        AssemblyInterfaceTracker tracker = this.ae2enhanced$assemblyById.get(id);
        if (tracker == null) {
            return;
        }
        ae2enhanced$doAssemblyAction(player, action, slot, tracker);
        ci.cancel();
    }

    private boolean ae2enhanced$checkAssemblyChanges() {
        boolean changed = false;
        Map<TileAssemblyController, AssemblyInterfaceTracker> current = new HashMap<>();

        for (IGridNode gn : this.grid.getMachines(TileAssemblyController.class)) {
            if (!gn.isActive()) {
                continue;
            }
            Object machine = gn.getMachine();
            if (!(machine instanceof TileAssemblyController)) {
                continue;
            }
            TileAssemblyController controller = (TileAssemblyController) machine;
            if (!controller.isFormed()) {
                continue;
            }
            current.put(controller, null);

            AssemblyInterfaceTracker old = this.ae2enhanced$assemblyDiList.get(controller);
            if (old == null) {
                changed = true;
                continue;
            }
            current.put(controller, old);

            if (!old.getUnlocalizedName().equals(controller.getBlockType().getTranslationKey())) {
                changed = true;
                continue;
            }

            IItemHandler server = old.getServer();
            IItemHandler client = old.getClient();
            for (int i = 0; i < server.getSlots(); i++) {
                if (ae2enhanced$isDifferent(server.getStackInSlot(i), client.getStackInSlot(i))) {
                    changed = true;
                    break;
                }
            }
        }

        if (!changed && this.ae2enhanced$assemblyDiList.size() != current.size()) {
            changed = true;
        }

        return changed;
    }

    private void ae2enhanced$regenAssemblyList(NBTTagCompound data) {
        this.ae2enhanced$assemblyDiList.clear();
        this.ae2enhanced$assemblyById.clear();

        if (this.grid == null) {
            return;
        }

        for (IGridNode gn : this.grid.getMachines(TileAssemblyController.class)) {
            if (!gn.isActive()) {
                continue;
            }
            Object machine = gn.getMachine();
            if (!(machine instanceof TileAssemblyController)) {
                continue;
            }
            TileAssemblyController controller = (TileAssemblyController) machine;
            if (!controller.isFormed()) {
                continue;
            }

            AssemblyInterfaceTracker tracker = new AssemblyInterfaceTracker(controller);
            this.ae2enhanced$assemblyDiList.put(controller, tracker);
            this.ae2enhanced$assemblyById.put(tracker.getWhich(), tracker);
            tracker.writeToNBT(data, 0, tracker.getServer().getSlots());
        }
    }

    private boolean ae2enhanced$isDifferent(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) {
            return false;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return true;
        }
        return !ItemStack.areItemStacksEqual(a, b);
    }

    private void ae2enhanced$doAssemblyAction(EntityPlayerMP player, InventoryAction action, int slot,
                                              AssemblyInterfaceTracker tracker) {
        IItemHandler server = tracker.getServer();

        if (action == InventoryAction.PLACE_SINGLE) {
            Slot playerSlot;
            try {
                playerSlot = ((AEBaseContainer) (Object) this).inventorySlots.get(slot);
            } catch (IndexOutOfBoundsException ignored) {
                return;
            }
            if (!(playerSlot instanceof AppEngSlot)
                    || !((AppEngSlot) playerSlot).isPlayerSide()
                    || !playerSlot.getHasStack()) {
                return;
            }
            ItemStack itemStack = playerSlot.getStack();
            if (!itemStack.isEmpty()) {
                WrapperFilteredItemHandler handler = new WrapperFilteredItemHandler(
                        new WrapperRangeItemHandler(server, 0, server.getSlots()),
                        new AssemblyPatternSlotFilter());
                playerSlot.putStack(ItemHandlerHelper.insertItem(handler, itemStack, false));
                ((AEBaseContainer) (Object) this).detectAndSendChanges();
            }
            return;
        }

        ItemStack is = server.getStackInSlot(slot);
        boolean hasItemInHand = !player.inventory.getItemStack().isEmpty();
        AdaptorItemHandler playerHand = new AdaptorItemHandler(new WrapperCursorItemHandler(player.inventory));
        WrapperFilteredItemHandler theSlot = new WrapperFilteredItemHandler(
                new WrapperRangeItemHandler(server, slot, slot + 1),
                new AssemblyPatternSlotFilter());
        AdaptorItemHandler interfaceSlot = new AdaptorItemHandler(theSlot);

        switch (action) {
            case PICKUP_OR_SET_DOWN: {
                if (hasItemInHand) {
                    boolean canInsert = true;
                    for (int s = 0; s < server.getSlots(); s++) {
                        if (appeng.util.Platform.itemComparisons().isSameItem(server.getStackInSlot(s),
                                player.inventory.getItemStack())) {
                            canInsert = false;
                            break;
                        }
                    }
                    if (!canInsert) {
                        break;
                    }
                    ItemStack inSlot = theSlot.getStackInSlot(0);
                    if (inSlot.isEmpty()) {
                        player.inventory.setItemStack(interfaceSlot.addItems(player.inventory.getItemStack()));
                        break;
                    }
                    inSlot = inSlot.copy();
                    ItemStack inHand = player.inventory.getItemStack().copy();
                    ItemHandlerUtil.setStackInSlot(theSlot, 0, ItemStack.EMPTY);
                    player.inventory.setItemStack(ItemStack.EMPTY);
                    player.inventory.setItemStack(interfaceSlot.addItems(inHand.copy()));
                    if (player.inventory.getItemStack().isEmpty()) {
                        player.inventory.setItemStack(inSlot);
                        break;
                    }
                    player.inventory.setItemStack(inHand);
                    ItemHandlerUtil.setStackInSlot(theSlot, 0, inSlot);
                } else {
                    ItemHandlerUtil.setStackInSlot(theSlot, 0,
                            playerHand.addItems(theSlot.getStackInSlot(0)));
                }
                break;
            }
            case SPLIT_OR_PLACE_SINGLE: {
                if (hasItemInHand) {
                    boolean canInsert = true;
                    for (int s = 0; s < server.getSlots(); s++) {
                        if (appeng.util.Platform.itemComparisons().isSameItem(server.getStackInSlot(s),
                                player.inventory.getItemStack())) {
                            canInsert = false;
                            break;
                        }
                    }
                    if (!canInsert) {
                        break;
                    }
                    ItemStack extra = playerHand.removeItems(1, ItemStack.EMPTY, null);
                    if (!extra.isEmpty() && !interfaceSlot.containsItems()) {
                        extra = interfaceSlot.addItems(extra);
                    }
                    if (!extra.isEmpty()) {
                        playerHand.addItems(extra);
                    }
                } else {
                    if (is.isEmpty()) {
                        break;
                    }
                    ItemStack extra = interfaceSlot.removeItems((is.getCount() + 1) / 2, ItemStack.EMPTY, null);
                    if (!extra.isEmpty()) {
                        extra = playerHand.addItems(extra);
                    }
                    if (!extra.isEmpty()) {
                        interfaceSlot.addItems(extra);
                    }
                }
                break;
            }
            case SHIFT_CLICK: {
                InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor((EntityPlayer) player);
                ItemHandlerUtil.setStackInSlot(theSlot, 0, playerInv.addItems(theSlot.getStackInSlot(0)));
                break;
            }
            case MOVE_REGION: {
                InventoryAdaptor playerInvAd = InventoryAdaptor.getAdaptor((EntityPlayer) player);
                for (int x = 0; x < server.getSlots(); x++) {
                    ItemHandlerUtil.setStackInSlot(server, x, playerInvAd.addItems(server.getStackInSlot(x)));
                }
                break;
            }
            case CREATIVE_DUPLICATE: {
                if (player.capabilities.isCreativeMode && !hasItemInHand) {
                    player.inventory.setItemStack(is.isEmpty() ? ItemStack.EMPTY : is.copy());
                }
                break;
            }
        }
        ae2enhanced$updateHeld(player);
    }

    private void ae2enhanced$updateHeld(EntityPlayerMP player) {
        if (Platform.isServer()) {
            try {
                appeng.api.storage.data.IAEItemStack held = appeng.util.item.AEItemStack.fromItemStack(player.inventory.getItemStack());
                NetworkHandler.instance().sendTo(
                        new appeng.core.sync.packets.PacketInventoryAction(
                                InventoryAction.UPDATE_HAND, 0, held), player);
            } catch (IOException ignored) {
            }
        }
    }

    private static class AssemblyPatternSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ICraftingPatternItem;
        }
    }
}
