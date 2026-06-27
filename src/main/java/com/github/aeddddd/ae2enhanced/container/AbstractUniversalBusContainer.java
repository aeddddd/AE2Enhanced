package com.github.aeddddd.ae2enhanced.container;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFakeTypeOnly;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeTypeOnly;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.part.PartUniversalBusBase;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentiaSafe;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;

/**
 * 通用总线(输入/输出)Container 基类.
 *
 * 统一封装 7×9 过滤槽布局、5 升级槽、容量卡解锁、fake 物品转换等逻辑,
 * 消除 ContainerUniversalExportBus / ContainerUniversalImportBus 中
 * 各自重复的 ~130 行代码.
 */
public abstract class AbstractUniversalBusContainer extends ContainerUpgradeable implements IOptionalSlotHost {

    protected final PartUniversalBusBase part;

    @GuiSync(value = 10)
    public int busModeOrdinal = 0;

    public AbstractUniversalBusContainer(InventoryPlayer ip, PartUniversalBusBase te) {
        super(ip, te);
        this.part = te;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        IItemHandler config = this.getUpgradeable().getInventoryByName("config");
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                if (y < 2) {
                    this.addSlotToContainer(new SlotFakeTypeOnly(config, y * 9 + x, 8 + x * 18, 29 + y * 18));
                } else {
                    this.addSlotToContainer(new OptionalSlotFakeTypeOnly(config, this, y * 9 + x, 8, 29, x, y, y - 2));
                }
            }
        }

        IItemHandler upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 0, 187, 8, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 1, 187, 26, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 2, 187, 44, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 3, 187, 62, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 4, 187, 80, this.getInventoryPlayer()).setNotDraggable());
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        int capacityUpgrades = this.part.getInstalledUpgrades(Upgrades.CAPACITY);
        return idx < capacityUpgrades;
    }

    @Override
    public void func_75142_b() {
        this.verifyPermissions(appeng.api.config.SecurityPermissions.BUILD, false);
        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setCraftingMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.CRAFT_ONLY));
            this.busModeOrdinal = this.part.getBusMode().ordinal();
        }
        this.standardDetectAndSendChanges();
    }

    public PartUniversalBusBase getPart() {
        return this.part;
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (slot >= 0 && slot < this.inventorySlots.size()) {
            Slot s = this.inventorySlots.get(slot);
            if (s instanceof SlotFake) {
                ItemStack held = player.inventory.getItemStack();
                if (!held.isEmpty()) {
                    ItemStack fake = tryConvertHeldToFake(held);
                    if (fake != null && !fake.isEmpty()) {
                        if (action == InventoryAction.PICKUP_OR_SET_DOWN) {
                            s.putStack(fake);
                            return;
                        }
                    }
                }
            }
        }
        super.doAction(player, action, slot, id);
    }

    private static ItemStack tryConvertHeldToFake(ItemStack held) {
        if (held.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem fh = held.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (fh != null) {
                FluidStack drained = fh.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return ItemFluidDrop.createStack(drained);
                }
            }
        }

        ItemStack gasFake = tryConvertGasToFake(held);
        if (gasFake != null) return gasFake;

        ItemStack essentiaFake = FakeEssentiaSafe.tryConvertContainerToFake(held);
        if (essentiaFake != null) return essentiaFake;

        return null;
    }

    private static ItemStack tryConvertGasToFake(ItemStack held) {
        try {
            Class<?> gasItemClass = Class.forName("mekanism.api.gas.IGasItem");
            if (!gasItemClass.isInstance(held.getItem())) return null;
            Object gasItem = held.getItem();
            Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
            java.lang.reflect.Field amountField = gasStackClass.getField("amount");
            Object gasStack = gasItemClass.getMethod("getGas", ItemStack.class).invoke(gasItem, held);
            if (gasStack == null) return null;
            int amount = amountField.getInt(gasStack);
            Object gasType = gasStackClass.getMethod("getGas").invoke(gasStack);
            Object newGasStack = gasStackClass.getConstructor(gasType.getClass(), int.class)
                    .newInstance(gasType, Math.min(amount, 1000));
            Class<?> itemGasDropClass = Class.forName("com.github.aeddddd.ae2enhanced.item.ItemGasDrop");
            return (ItemStack) itemGasDropClass.getMethod("createStack", gasStackClass).invoke(null, newGasStack);
        } catch (Exception e) {
            return null;
        }
    }
}
