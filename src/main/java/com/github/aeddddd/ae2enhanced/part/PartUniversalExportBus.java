package com.github.aeddddd.ae2enhanced.part;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.util.InventoryAdaptor;
import com.google.common.collect.ImmutableList;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper;
import com.github.aeddddd.ae2enhanced.util.ItemPushHelper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Loader;

/**
 * E1b：通用输出总线.
 * 同时支持物品、流体、气体(Mekanism)、源质(Thaumcraft)的输出.
 */
public class PartUniversalExportBus extends PartUniversalBusBase {

    @PartModels
    public static final ResourceLocation[] MODELS = new ResourceLocation[]{
            new ResourceLocation(AE2Enhanced.MOD_ID, "part/universal_export_bus_base"),
            new ResourceLocation("appliedenergistics2", "part/export_bus_off"),
            new ResourceLocation("appliedenergistics2", "part/export_bus_on"),
            new ResourceLocation("appliedenergistics2", "part/export_bus_has_channel")
    };

    public static final IPartModel MODELS_OFF = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[1]});
    public static final IPartModel MODELS_ON = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[2]});
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[3]});

    public PartUniversalExportBus(net.minecraft.item.ItemStack is) {
        super(is, 63, MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5 + com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.wirelessChannel.extraUpgradeSlots;
    }

    @Override
    protected TickRates getTickRates() {
        return TickRates.ExportBus;
    }

    @Override
    protected int getGuiId() {
        return GuiHandler.GUI_UNIVERSAL_EXPORT_BUS;
    }

    // region Item Export

    @Override
    protected boolean processItemSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception {
        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
        if (adaptor == null) return false;

        IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IEnergyGrid energy = this.getProxy().getEnergy();
        FuzzyMode fzMode = (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE);

        long itemsToSend = this.calculateItemsToSend();
        boolean worked = false;

        IAEItemStack toExtract = filter.copy();
        toExtract.setStackSize(itemsToSend);

        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            for (IAEItemStack o : ImmutableList.copyOf(inv.getStorageList().findFuzzy(toExtract, fzMode))) {
                if (o.getStackSize() <= 0) continue;
                long sent = ItemPushHelper.pushItemIntoTarget(adaptor, energy, inv, o, itemsToSend, this.source);
                if (sent > 0) {
                    itemsToSend -= sent;
                    worked = true;
                }
                if (itemsToSend <= 0) break;
            }
        } else {
            IAEItemStack o = inv.getStorageList().findPrecise(toExtract);
            if (o != null && o.getStackSize() > 0) {
                long sent = ItemPushHelper.pushItemIntoTarget(adaptor, energy, inv, o, itemsToSend, this.source);
                if (sent > 0) {
                    itemsToSend -= sent;
                    worked = true;
                }
            }
        }

        return worked;
    }

    @Override
    protected boolean processItemUnfiltered(TileEntity target, EnumFacing opposite) throws Exception {
        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
        if (adaptor == null) return false;

        IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IEnergyGrid energy = this.getProxy().getEnergy();

        long itemsToSend = this.calculateItemsToSend();
        boolean worked = false;

        for (IAEItemStack o : ImmutableList.copyOf(inv.getStorageList())) {
            if (o == null || o.getStackSize() <= 0 || isFakeItemFilter(o)) continue;
            if (itemsToSend <= 0) break;
            long sent = ItemPushHelper.pushItemIntoTarget(adaptor, energy, inv, o, itemsToSend, this.source);
            if (sent > 0) {
                itemsToSend -= sent;
                worked = true;
            }
        }

        return worked;
    }

    // endregion

    // region Fluid Export

    @Override
    protected boolean processFluidSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception {
        IFluidHandler fh = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        if (fh == null) return false;

        IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        IAEFluidStack wanted = FakeFluids.unpackFluid(filter);
        if (wanted == null || wanted.getFluid() == null) return false;

        IAEFluidStack toExtract = wanted.copy();
        toExtract.setStackSize(this.calculateFluidToSend());

        IAEFluidStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
        if (out == null || out.getStackSize() <= 0) return false;

        int wasInserted = fh.fill(out.getFluidStack(), false);
        if (wasInserted <= 0) return false;

        toExtract.setStackSize(wasInserted);
        inv.extractItems(toExtract, Actionable.MODULATE, this.source);

        FluidStack toFill = new FluidStack(out.getFluid(), wasInserted);
        fh.fill(toFill, true);
        return true;
    }

    @Override
    protected boolean processFluidUnfiltered(TileEntity target, EnumFacing opposite) throws Exception {
        IFluidHandler fh = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        if (fh == null) return false;

        IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage().getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

        for (IAEFluidStack fluid : ImmutableList.copyOf(inv.getStorageList())) {
            if (fluid == null || fluid.getStackSize() <= 0) continue;
            IAEFluidStack toExtract = fluid.copy();
            toExtract.setStackSize(this.calculateFluidToSend());

            IAEFluidStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
            if (out == null || out.getStackSize() <= 0) continue;

            int wasInserted = fh.fill(out.getFluidStack(), false);
            if (wasInserted <= 0) continue;

            toExtract.setStackSize(wasInserted);
            inv.extractItems(toExtract, Actionable.MODULATE, this.source);

            FluidStack toFill = new FluidStack(out.getFluid(), wasInserted);
            fh.fill(toFill, true);
            return true;
        }

        return false;
    }

    private long calculateFluidToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        return Math.min((long) Math.pow(2, speedUpgrades) * 100L, 8000L);
    }

    // endregion

    // region Gas Export

    @Override
    @SuppressWarnings("unchecked")
    protected boolean processGasSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception {
        if (!GasReflectionHelper.isAvailable()) return false;

        Object gasHandler = GasReflectionHelper.getGasHandler(target, opposite);
        if (gasHandler == null) return false;

        IMEMonitor<?> rawInv = GasReflectionHelper.getGasInventory(this.getProxy().getGrid());
        if (rawInv == null) return false;
        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv =
                (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) rawInv;

        com.mekeng.github.common.me.data.IAEGasStack wanted = FakeGases.unpackGas(filter);
        if (wanted == null || wanted.getGas() == null) return false;

        com.mekeng.github.common.me.data.IAEGasStack toExtract = wanted.copy();
        toExtract.setStackSize(this.calculateGasToSend());

        com.mekeng.github.common.me.data.IAEGasStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
        if (out == null || out.getStackSize() <= 0) return false;

        Object gasStack = out.getGasStack();
        int wasInserted = GasReflectionHelper.receiveGas(gasHandler, opposite, gasStack, false);
        if (wasInserted <= 0) return false;

        toExtract.setStackSize(wasInserted);
        inv.extractItems(toExtract, Actionable.MODULATE, this.source);

        Object actualGas = GasReflectionHelper.createGasStack(
                GasReflectionHelper.getGasType(out.getGasStack()), wasInserted);
        GasReflectionHelper.receiveGas(gasHandler, opposite, actualGas, true);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean processGasUnfiltered(TileEntity target, EnumFacing opposite) throws Exception {
        if (!GasReflectionHelper.isAvailable()) return false;

        Object gasHandler = GasReflectionHelper.getGasHandler(target, opposite);
        if (gasHandler == null) return false;

        IMEMonitor<?> rawInv = GasReflectionHelper.getGasInventory(this.getProxy().getGrid());
        if (rawInv == null) return false;
        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv =
                (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) rawInv;

        for (com.mekeng.github.common.me.data.IAEGasStack gas : ImmutableList.copyOf(inv.getStorageList())) {
            if (gas == null || gas.getStackSize() <= 0) continue;

            com.mekeng.github.common.me.data.IAEGasStack toExtract = gas.copy();
            toExtract.setStackSize(this.calculateGasToSend());

            com.mekeng.github.common.me.data.IAEGasStack out = inv.extractItems(toExtract, Actionable.SIMULATE, this.source);
            if (out == null || out.getStackSize() <= 0) continue;

            Object gasStack = out.getGasStack();
            int wasInserted = GasReflectionHelper.receiveGas(gasHandler, opposite, gasStack, false);
            if (wasInserted <= 0) continue;

            toExtract.setStackSize(wasInserted);
            inv.extractItems(toExtract, Actionable.MODULATE, this.source);

            Object actualGas = GasReflectionHelper.createGasStack(
                    GasReflectionHelper.getGasType(out.getGasStack()), wasInserted);
            GasReflectionHelper.receiveGas(gasHandler, opposite, actualGas, true);
            return true;
        }

        return false;
    }

    private long calculateGasToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        return Math.min((long) Math.pow(2, speedUpgrades) * 100L, 8000L);
    }

    // endregion

    // region Essentia Export

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
            java.lang.reflect.Method method = helperClass.getMethod("exportEssentiaSlot",
                    appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                    IAEItemStack.class, appeng.api.networking.security.IActionSource.class);
            return (Boolean) method.invoke(null, this.getProxy().getGrid(), target, opposite, filter, this.source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Essentia export slot failed", e);
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
            java.lang.reflect.Method method = helperClass.getMethod("exportEssentias",
                    appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                    appeng.tile.inventory.AppEngInternalAEInventory.class, appeng.api.networking.security.IActionSource.class);
            return (Boolean) method.invoke(null, this.getProxy().getGrid(), target, opposite, this.config, this.source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Essentia export unfiltered failed", e);
            return false;
        }
    }

    // endregion
}
