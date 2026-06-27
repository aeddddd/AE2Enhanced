package com.github.aeddddd.ae2enhanced.part;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidStack;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Loader;

/**
 * E1a：通用输入总线.
 * 同时支持物品、流体、气体(Mekanism)、源质(Thaumcraft)的导入.
 */
public class PartUniversalImportBus extends PartUniversalBusBase {

    @PartModels
    public static final ResourceLocation[] MODELS = new ResourceLocation[]{
            new ResourceLocation(AE2Enhanced.MOD_ID, "part/universal_import_bus_base"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_off"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_on"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_has_channel")
    };

    public static final IPartModel MODELS_OFF = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[1]});
    public static final IPartModel MODELS_ON = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[2]});
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[3]});

    public PartUniversalImportBus(ItemStack is) {
        super(is, 63, MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5 + com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.wirelessChannel.extraUpgradeSlots;
    }

    @Override
    protected TickRates getTickRates() {
        return TickRates.ImportBus;
    }

    @Override
    protected int getGuiId() {
        return GuiHandler.GUI_UNIVERSAL_IMPORT_BUS;
    }

    // region Item Import

    @Override
    protected boolean processItemSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception {
        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
        if (adaptor == null) return false;

        IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage().getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IEnergyGrid energy = this.getProxy().getEnergy();
        FuzzyMode fzMode = (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE);

        long itemsToSend = this.calculateItemsToSend();
        boolean worked = false;

        while (itemsToSend > 0) {
            if (!this.importItemStack(adaptor, filter, inv, energy, fzMode, itemsToSend)) break;
            worked = true;
            itemsToSend--;
        }

        return worked;
    }

    @Override
    protected boolean processItemUnfiltered(TileEntity target, EnumFacing opposite) throws Exception {
        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
        if (adaptor == null) return false;

        IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage().getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IEnergyGrid energy = this.getProxy().getEnergy();
        FuzzyMode fzMode = (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE);

        long itemsToSend = this.calculateItemsToSend();
        boolean worked = false;

        while (itemsToSend > 0) {
            if (!this.importItemStack(adaptor, null, inv, energy, fzMode, itemsToSend)) break;
            worked = true;
            itemsToSend--;
        }

        return worked;
    }

    private boolean importItemStack(InventoryAdaptor adaptor, IAEItemStack filter,
                                     IMEMonitor<IAEItemStack> inv, IEnergyGrid energy,
                                     FuzzyMode fzMode, long max) {
        ItemStack filterStack = filter == null ? ItemStack.EMPTY : filter.getDefinition();
        ItemStack stack = adaptor.removeItems(1, filterStack.isEmpty() ? ItemStack.EMPTY : filterStack, null);
        if (stack.isEmpty()) return false;

        IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
        if (aeStack == null) {
            adaptor.addItems(stack); // 退回
            return false;
        }
        aeStack.setStackSize(1);

        IAEItemStack notInserted = Platform.poweredInsert(energy, inv, aeStack, this.source);
        if (notInserted != null && notInserted.getStackSize() > 0) {
            // 退回未成功部分
            ItemStack spill = notInserted.createItemStack();
            adaptor.addItems(spill);
            if (notInserted.getStackSize() >= aeStack.getStackSize()) {
                return false; // 完全失败
            }
        }
        return true;
    }

    // endregion

    // region Fluid Import

    @Override
    protected boolean processFluidSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception {
        IFluidHandler fh = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        if (fh == null) return false;

        IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage().getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        IAEFluidStack wanted = FakeFluids.unpackFluid(filter);
        if (wanted == null || wanted.getFluid() == null) return false;

        FluidStack drained = fh.drain(new FluidStack(wanted.getFluid(), 1000), false);
        if (drained == null || drained.amount <= 0) return false;
        drained = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(drained);

        IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(drained);
        if (aeFluid == null) return false;

        IAEFluidStack notInserted = inv.injectItems(aeFluid, appeng.api.config.Actionable.SIMULATE, this.source);
        long canInsert = aeFluid.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
        if (canInsert <= 0) return false;

        FluidStack actual = fh.drain(new FluidStack(wanted.getFluid(), (int) canInsert), true);
        if (actual != null && actual.amount > 0) {
            actual = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(actual);
            IAEFluidStack toInsert = AEFluidStack.fromFluidStack(actual);
            inv.injectItems(toInsert, appeng.api.config.Actionable.MODULATE, this.source);
            return true;
        }
        return false;
    }

    @Override
    protected boolean processFluidUnfiltered(TileEntity target, EnumFacing opposite) throws Exception {
        IFluidHandler fh = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        if (fh == null) return false;

        IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage().getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        FluidStack drained = fh.drain(1000, false);
        if (drained != null && drained.amount > 0) {
            drained = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(drained);
            IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(drained);
            if (aeFluid != null) {
                IAEFluidStack notInserted = inv.injectItems(aeFluid, appeng.api.config.Actionable.SIMULATE, this.source);
                long canInsert = aeFluid.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
                if (canInsert > 0) {
                    FluidStack actual = fh.drain((int) canInsert, true);
                    if (actual != null && actual.amount > 0) {
                        actual = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(actual);
                        inv.injectItems(AEFluidStack.fromFluidStack(actual), appeng.api.config.Actionable.MODULATE, this.source);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // endregion

    // region Gas Import

    @Override
    protected boolean processGasSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception {
        if (!GasReflectionHelper.isAvailable()) return false;

        Object gasHandler = GasReflectionHelper.getGasHandler(target, opposite);
        if (gasHandler == null) return false;

        try {
            IMEMonitor<?> rawInv = GasReflectionHelper.getGasInventory(this.getProxy().getGrid());
            if (rawInv == null) return false;
            @SuppressWarnings("unchecked")
            IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv =
                    (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) rawInv;

            com.mekeng.github.common.me.data.IAEGasStack wanted = FakeGases.unpackGas(filter);
            if (wanted == null || wanted.getGas() == null) return false;

            Object drained = GasReflectionHelper.drawGas(gasHandler, opposite, 1000, false);
            if (drained == null) return false;

            int amount = GasReflectionHelper.getGasAmount(drained);
            if (amount <= 0) return false;

            com.mekeng.github.common.me.data.impl.AEGasStack aeGas =
                    com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) drained);
            if (aeGas == null) return false;

            com.mekeng.github.common.me.data.IAEGasStack notInserted = inv.injectItems(aeGas, appeng.api.config.Actionable.SIMULATE, this.source);
            long canInsert = aeGas.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
            if (canInsert <= 0) return false;

            Object actual = GasReflectionHelper.drawGas(gasHandler, opposite, (int) canInsert, true);
            if (actual != null) {
                int actualAmount = GasReflectionHelper.getGasAmount(actual);
                if (actualAmount > 0) {
                    com.mekeng.github.common.me.data.impl.AEGasStack toInsert =
                            com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) actual);
                    inv.injectItems(toInsert, appeng.api.config.Actionable.MODULATE, this.source);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Gas import slot failed", e);
            return false;
        }
    }

    @Override
    protected boolean processGasUnfiltered(TileEntity target, EnumFacing opposite) throws Exception {
        if (!GasReflectionHelper.isAvailable()) return false;

        Object gasHandler = GasReflectionHelper.getGasHandler(target, opposite);
        if (gasHandler == null) return false;

        try {
            IMEMonitor<?> rawInv = GasReflectionHelper.getGasInventory(this.getProxy().getGrid());
            if (rawInv == null) return false;
            @SuppressWarnings("unchecked")
            IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv =
                    (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) rawInv;

            Object drained = GasReflectionHelper.drawGas(gasHandler, opposite, 1000, false);
            if (drained != null) {
                int amount = GasReflectionHelper.getGasAmount(drained);
                if (amount > 0) {
                    com.mekeng.github.common.me.data.impl.AEGasStack aeGas =
                            com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) drained);
                    if (aeGas != null) {
                        com.mekeng.github.common.me.data.IAEGasStack notInserted = inv.injectItems(aeGas, appeng.api.config.Actionable.SIMULATE, this.source);
                        long canInsert = aeGas.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
                        if (canInsert > 0) {
                            Object actual = GasReflectionHelper.drawGas(gasHandler, opposite, (int) canInsert, true);
                            if (actual != null) {
                                int actualAmount = GasReflectionHelper.getGasAmount(actual);
                                if (actualAmount > 0) {
                                    com.mekeng.github.common.me.data.impl.AEGasStack toInsert =
                                            com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) actual);
                                    inv.injectItems(toInsert, appeng.api.config.Actionable.MODULATE, this.source);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Gas import unfiltered failed", e);
            return false;
        }
    }

    // endregion

    // region Essentia Import

    @Override
    protected boolean processEssentiaSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception {
        if (!Loader.isModLoaded("thaumcraft") || !Loader.isModLoaded("thaumicenergistics")) return false;
        try {
            Class<?> ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
            if (!ieTransportClass.isInstance(target)) return false;
        } catch (ClassNotFoundException e) {
            return false;
        }

        try {
            Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.util.reflection.EssentiaBusHelper");
            java.lang.reflect.Method method = helperClass.getMethod("importEssentiaSlot",
                    appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                    IAEItemStack.class, appeng.api.networking.security.IActionSource.class);
            return (Boolean) method.invoke(null, this.getProxy().getGrid(), target, opposite, filter, this.source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Essentia import slot failed", e);
            return false;
        }
    }

    @Override
    protected boolean processEssentiaUnfiltered(TileEntity target, EnumFacing opposite) throws Exception {
        if (!Loader.isModLoaded("thaumcraft") || !Loader.isModLoaded("thaumicenergistics")) return false;
        try {
            Class<?> ieTransportClass = Class.forName("thaumcraft.api.aspects.IEssentiaTransport");
            if (!ieTransportClass.isInstance(target)) return false;
        } catch (ClassNotFoundException e) {
            return false;
        }

        try {
            Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.util.reflection.EssentiaBusHelper");
            java.lang.reflect.Method method = helperClass.getMethod("importEssentias",
                    appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                    appeng.tile.inventory.AppEngInternalAEInventory.class, appeng.api.networking.security.IActionSource.class);
            return (Boolean) method.invoke(null, this.getProxy().getGrid(), target, opposite, this.config, this.source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Essentia import unfiltered failed", e);
            return false;
        }
    }

    // endregion
}
