package com.github.aeddddd.ae2enhanced.part;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.SettingsFrom;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.essentia.EssentiaChannelAccessor;
import com.github.aeddddd.ae2enhanced.util.fakeitem.EssentiaFakeItemChecks;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import com.github.aeddddd.ae2enhanced.util.fakeitem.GasFakeItemChecks;
import com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper;
import com.google.common.collect.ImmutableList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.List;

public class PartStockingBus extends PartUpgradeable implements IGridTickable {

    @PartModels
    public static final ResourceLocation[] MODELS = new ResourceLocation[]{
            new ResourceLocation(AE2Enhanced.MOD_ID, "part/stocking_bus_base"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_off"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_on"),
            new ResourceLocation("appliedenergistics2", "part/import_bus_has_channel")
    };

    public static final IPartModel MODELS_OFF = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[1]});
    public static final IPartModel MODELS_ON = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[2]});
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(new ResourceLocation[]{MODELS[0], MODELS[3]});

    public enum StockingMode {
        BIDIRECTIONAL,
        SUPPLY_ONLY,
        RECOVER_ONLY
    }

    private enum ResourceType {
        ITEM, FLUID, GAS, ESSENTIA, UNKNOWN
    }

    private interface StockingHandler {
        long handle(TileEntity target, EnumFacing opposite, IAEItemStack filter,
                    long targetAmount, long maxWork) throws GridAccessException;
    }

    @FunctionalInterface
    private interface StockingOperation {
        long apply(long amount) throws Exception;
    }

    private static final int CONFIG_SIZE = 9;

    private final AppEngInternalAEInventory config;
    private final long[] targetAmounts = new long[CONFIG_SIZE];
    private final IActionSource source;

    private StockingMode mode = StockingMode.BIDIRECTIONAL;

    // 用于避免 onChangeInventory 在滚轮清除 config slot 时循环重置 targetAmount
    private transient boolean ignoreConfigChange = false;

    private final StockingHandler itemHandler = new ItemStockingHandler();
    private final StockingHandler fluidHandler = new FluidStockingHandler();
    private final StockingHandler gasHandler = new GasStockingHandler();
    private final StockingHandler essentiaHandler = new EssentiaStockingHandler();

    public PartStockingBus(ItemStack is) {
        super(is);
        this.config = new AppEngInternalAEInventory(this, CONFIG_SIZE, Integer.MAX_VALUE);
        this.source = new MachineSource(this);
        for (int i = 0; i < CONFIG_SIZE; i++) {
            this.targetAmounts[i] = 1;
        }
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
        this.getConfigManager().registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
    }

    @Override
    public TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        return new TickingRequest(TickRates.ExportBus.getMin(), TickRates.ExportBus.getMax(), this.isSleeping(), false);
    }

    protected boolean canDoBusWork() {
        TileEntity self = this.getHost().getTile();
        BlockPos targetPos = self.getPos().offset(this.getSide().getFacing());
        net.minecraft.world.World world = self.getWorld();
        return world != null && world.getChunkProvider().getLoadedChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4) != null;
    }

    @Override
    public void upgradesChanged() {
        this.updateState();
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        this.updateState();
    }

    private void updateState() {
        try {
            if (!this.isSleeping()) {
                this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
            } else {
                this.getProxy().getTick().sleepDevice(this.getProxy().getNode());
            }
        } catch (GridAccessException ignored) {
        }
    }

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
        return this.doBusWork();
    }

    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }
        if (this.isSleeping()) {
            return TickRateModulation.IDLE;
        }

        TileEntity self = this.getHost().getTile();
        TileEntity target = self.getWorld().getTileEntity(self.getPos().offset(this.getSide().getFacing()));
        if (target == null) {
            return TickRateModulation.SLOWER;
        }

        EnumFacing opposite = this.getSide().getFacing().getOpposite();

        boolean hasItemCap = target.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, opposite);
        boolean hasFluidCap = target.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite)
                || target instanceof IFluidHandler;
        boolean hasGasCap = GasReflectionHelper.isAvailable() && GasReflectionHelper.getGasHandler(target, opposite) != null;
        boolean hasEssentiaCap = EssentiaChannelAccessor.isAvailable() && EssentiaChannelAccessor.isEssentiaTransport(target);

        if (!hasItemCap && !hasFluidCap && !hasGasCap && !hasEssentiaCap) {
            return TickRateModulation.SLOWER;
        }

        boolean worked = false;
        long remainingWork = this.calculateItemsToSend();

        try {
            for (int slot = 0; slot < CONFIG_SIZE && remainingWork > 0; slot++) {
                IAEItemStack filter = this.config.getAEStackInSlot(slot);
                if (filter == null) continue;

                long targetAmount = this.targetAmounts[slot];
                if (targetAmount <= 0) continue;

                ItemStack filterStack = filter.createItemStack();
                if (filterStack.isEmpty()) continue;

                long consumed = 0;
                switch (getSlotType(filter)) {
                    case FLUID:
                        if (!hasFluidCap) continue;
                        consumed = this.fluidHandler.handle(target, opposite, filter, targetAmount, remainingWork);
                        break;
                    case GAS:
                        if (!hasGasCap) continue;
                        consumed = this.gasHandler.handle(target, opposite, filter, targetAmount, remainingWork);
                        break;
                    case ESSENTIA:
                        if (!hasEssentiaCap) continue;
                        consumed = this.essentiaHandler.handle(target, opposite, filter, targetAmount, remainingWork);
                        break;
                    case ITEM:
                        if (!hasItemCap) continue;
                        consumed = this.itemHandler.handle(target, opposite, filter, targetAmount, remainingWork);
                        break;
                    case UNKNOWN:
                    default:
                        continue;
                }
                if (consumed > 0) {
                    worked = true;
                    remainingWork -= Math.min(consumed, remainingWork);
                }
            }
        } catch (GridAccessException e) {
            return TickRateModulation.SLOWER;
        }

        return worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    private long calculateItemsToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        int capacityUpgrades = this.getInstalledUpgrades(Upgrades.CAPACITY);
        long base = Math.min((long) Math.pow(2, speedUpgrades), 64);
        long multiplier = Math.min((long) Math.pow(2, capacityUpgrades), 16);
        return base * multiplier;
    }

    private ResourceType getSlotType(IAEItemStack filter) {
        ItemStack stack = filter.createItemStack();
        if (ItemFluidDrop.isFluidDrop(stack)) return ResourceType.FLUID;
        if (isAeFluidDummy(stack)) return ResourceType.FLUID;
        if (isAe2fcFluidDrop(stack)) return ResourceType.FLUID;
        if (GasFakeItemChecks.isGasFakeItem(stack)) return ResourceType.GAS;
        if (isAe2fcGasDrop(stack)) return ResourceType.GAS;
        if (EssentiaFakeItemChecks.isEssentiaFakeItem(stack)) return ResourceType.ESSENTIA;
        if (isTheDummyAspect(stack)) return ResourceType.ESSENTIA;
        // 只有通过所有类型检测后才判定为普通物品
        if (!stack.isEmpty()) return ResourceType.ITEM;
        return ResourceType.UNKNOWN;
    }

    private static boolean isAeFluidDummy(ItemStack stack) {
        return !stack.isEmpty() && "appeng.fluids.items.FluidDummyItem".equals(stack.getItem().getClass().getName());
    }

    private static boolean isAe2fcFluidDrop(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().getClass().getName();
        return "com.glodblock.github.common.item.ItemFluidDrop".equals(name)
                || "com.glodblock.github.common.item.ItemFluidPacket".equals(name);
    }

    private static boolean isAe2fcGasDrop(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().getClass().getName();
        return "com.glodblock.github.common.item.ItemGasDrop".equals(name)
                || "com.glodblock.github.common.item.ItemGasPacket".equals(name);
    }

    private static boolean isTheDummyAspect(ItemStack stack) {
        return !stack.isEmpty() && "thaumicenergistics.item.ItemDummyAspect".equals(stack.getItem().getClass().getName());
    }

    private long runStocking(long actual, long targetAmount, long maxWork,
                             StockingOperation supply, StockingOperation recover) throws Exception {
        long delta = targetAmount - actual;
        long consumed = 0;
        if (delta > 0 && this.mode != StockingMode.RECOVER_ONLY) {
            long toSupply = Math.min(delta, maxWork);
            if (toSupply > 0) {
                consumed = supply.apply(toSupply);
            }
        }
        if (delta < 0 && this.mode != StockingMode.SUPPLY_ONLY) {
            long toRecover = Math.min(-delta, maxWork);
            if (toRecover > 0) {
                consumed = recover.apply(toRecover);
            }
        }
        return consumed;
    }

    // === Item Stocking ===

    private class ItemStockingHandler implements StockingHandler {
        @Override
        public long handle(TileEntity target, EnumFacing opposite, IAEItemStack filter,
                           long targetAmount, long maxWork) throws GridAccessException {
            InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(target, opposite);
            if (adaptor == null) return 0;

            IMEMonitor<IAEItemStack> inv = PartStockingBus.this.getProxy().getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            IEnergyGrid energy = PartStockingBus.this.getProxy().getEnergy();
            FuzzyMode fzMode = (FuzzyMode) PartStockingBus.this.getConfigManager().getSetting(Settings.FUZZY_MODE);
            boolean fuzzy = PartStockingBus.this.getInstalledUpgrades(Upgrades.FUZZY) > 0;

            long actual = countItems(target, opposite, filter, fuzzy, fzMode);
            try {
                return runStocking(actual, targetAmount, maxWork,
                        toSupply -> supplyItems(adaptor, inv, energy, filter, toSupply, fuzzy, fzMode),
                        toRecover -> recoverItems(adaptor, inv, energy, filter, toRecover, fuzzy, fzMode));
            } catch (GridAccessException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private long countItems(TileEntity target, EnumFacing opposite, IAEItemStack filter, boolean fuzzy, FuzzyMode fzMode) {
        long count = 0;
        ItemStack filterStack = filter == null ? ItemStack.EMPTY : filter.createItemStack();
        IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, opposite);
        if (handler == null) return 0;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (fuzzy) {
                if (filterStack.isEmpty()) continue;
                IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                IAEItemStack aeFilter = AEItemStack.fromItemStack(filterStack);
                if (aeStack != null && aeFilter != null && aeStack.fuzzyComparison(aeFilter, fzMode)) {
                    count += stack.getCount();
                }
            } else {
                if (ItemStack.areItemsEqual(stack, filterStack) && ItemStack.areItemStackTagsEqual(stack, filterStack)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    private long supplyItems(InventoryAdaptor adaptor, IMEMonitor<IAEItemStack> inv, IEnergyGrid energy,
                              IAEItemStack filter, long amount, boolean fuzzy, FuzzyMode fzMode) {
        if (amount <= 0) return 0;
        long totalSent = 0;
        long remaining = amount;

        if (fuzzy) {
            for (IAEItemStack candidate : ImmutableList.copyOf(inv.getStorageList().findFuzzy(filter, fzMode))) {
                if (candidate.getStackSize() <= 0) continue;
                long sent = com.github.aeddddd.ae2enhanced.util.ItemPushHelper.pushItemIntoTarget(adaptor, energy, inv, candidate, remaining, this.source);
                if (sent > 0) {
                    remaining -= sent;
                    totalSent += sent;
                }
                if (remaining <= 0) break;
            }
        } else {
            IAEItemStack precise = inv.getStorageList().findPrecise(filter);
            if (precise != null && precise.getStackSize() > 0) {
                long sent = com.github.aeddddd.ae2enhanced.util.ItemPushHelper.pushItemIntoTarget(adaptor, energy, inv, precise, remaining, this.source);
                if (sent > 0) totalSent += sent;
            }
        }
        return totalSent;
    }

    private long recoverItems(InventoryAdaptor adaptor, IMEMonitor<IAEItemStack> inv, IEnergyGrid energy,
                               IAEItemStack filter, long amount, boolean fuzzy, FuzzyMode fzMode) {
        if (amount <= 0) return 0;
        long totalRecovered = 0;
        ItemStack filterStack = filter.createItemStack();
        long remaining = amount;

        while (remaining > 0) {
            int toRemove = (int) Math.min(remaining, 64);
            ItemStack removed = adaptor.removeItems(toRemove, filterStack, null);
            if (removed.isEmpty()) break;

            IAEItemStack aeRemoved = AEItemStack.fromItemStack(removed);
            if (aeRemoved == null) {
                adaptor.addItems(removed);
                break;
            }

            IAEItemStack notInserted = inv.injectItems(aeRemoved, Actionable.SIMULATE, this.source);
            long canInsert = aeRemoved.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
            if (canInsert <= 0) {
                adaptor.addItems(removed);
                break;
            }

            if (canInsert < removed.getCount()) {
                ItemStack returnStack = removed.copy();
                returnStack.setCount((int) (removed.getCount() - canInsert));
                adaptor.addItems(returnStack);
                removed.setCount((int) canInsert);
                aeRemoved.setStackSize(canInsert);
            }

            inv.injectItems(aeRemoved, Actionable.MODULATE, this.source);
            totalRecovered += canInsert;
            remaining -= canInsert;
        }

        return totalRecovered;
    }

    // === Fluid Stocking ===

    private class FluidStockingHandler implements StockingHandler {
        @Override
        public long handle(TileEntity target, EnumFacing opposite, IAEItemStack filter,
                           long targetAmount, long maxWork) throws GridAccessException {
            IFluidHandler fhRaw = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
            final IFluidHandler fh = (fhRaw != null) ? fhRaw
                    : (target instanceof IFluidHandler) ? (IFluidHandler) target : null;
            if (fh == null) return 0;

            IMEMonitor<IAEFluidStack> inv = PartStockingBus.this.getProxy().getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

            IAEFluidStack fluidFilter = FakeFluids.unpackFluid(filter);
            if (fluidFilter == null || fluidFilter.getFluid() == null) {
                AE2Enhanced.LOGGER.warn("[AE2E] FluidStockingHandler: unpackFluid failed for filter {} (item={})",
                        filter, filter != null ? filter.getItem() : "null");
                return 0;
            }
            Fluid targetFluid = fluidFilter.getFluid();

            try {
                long actual = countFluids(fh, targetFluid);
                long scaledMaxWork = maxWork * 1000;
                long consumedMb = runStocking(actual, targetAmount, scaledMaxWork,
                        toSupply -> supplyFluid(fh, inv, fluidFilter, toSupply),
                        toRecover -> recoverFluid(fh, inv, fluidFilter, toRecover));
                return consumedMb > 0 ? Math.max(1, consumedMb / 1000) : 0;
            } catch (GridAccessException e) {
                throw e;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Fluid stocking failed for {}", targetFluid.getName(), e);
                return 0;
            }
        }
    }

    private long countFluids(IFluidHandler fh, Fluid targetFluid) {
        long count = 0;
        IFluidTankProperties[] tanks = fh.getTankProperties();
        if (tanks != null) {
            String targetName = targetFluid.getName();
            for (IFluidTankProperties tank : tanks) {
                FluidStack content = tank.getContents();
                if (content != null && content.getFluid() != null
                        && targetName.equals(content.getFluid().getName())) {
                    count += content.amount;
                }
            }
        }
        if (count == 0) {
            // 尝试用不同 amount 进行 drain 探测,某些容器的 drain 可能不支持 MAX_VALUE
            int[] probeAmounts = {Integer.MAX_VALUE, 1000, 1};
            for (int amount : probeAmounts) {
                FluidStack probe = new FluidStack(targetFluid, amount);
                FluidStack drained = fh.drain(probe, false);
                if (drained != null && drained.getFluid() != null
                        && targetFluid.getName().equals(drained.getFluid().getName())) {
                    count = drained.amount;
                    break;
                }
            }
        }
        return count;
    }

    private long supplyFluid(IFluidHandler fh, IMEMonitor<IAEFluidStack> inv, IAEFluidStack fluidFilter, long amount) {
        if (amount <= 0) return 0;
        
        FluidStack filterStack = fluidFilter.getFluidStack();
        if (filterStack == null) return 0;
        filterStack = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(filterStack);
        
        IAEFluidStack aeWanted = AEFluidStack.fromFluidStack(filterStack);
        if (aeWanted == null) return 0;
        aeWanted.setStackSize(amount);

        IAEFluidStack extracted = inv.extractItems(aeWanted, Actionable.SIMULATE, this.source);
        long canExtract = extracted != null ? extracted.getStackSize() : 0;
        if (canExtract <= 0) return 0;

        FluidStack toFill = filterStack.copy();
        toFill.amount = (int) canExtract;
        int filled = fh.fill(toFill, false);
        if (filled <= 0) return 0;

        toFill.amount = filled;
        IAEFluidStack aeExtract = AEFluidStack.fromFluidStack(toFill);
        inv.extractItems(aeExtract, Actionable.MODULATE, this.source);
        fh.fill(toFill, true);
        return filled;
    }

    private long recoverFluid(IFluidHandler fh, IMEMonitor<IAEFluidStack> inv, IAEFluidStack fluidFilter, long amount) {
        if (amount <= 0) return 0;
        FluidStack toDrain = fluidFilter.getFluidStack();
        if (toDrain == null) return 0;
        toDrain = toDrain.copy();
        toDrain.amount = (int) Math.min(amount, Integer.MAX_VALUE);
        toDrain = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(toDrain);

        FluidStack drained = fh.drain(toDrain, false);
        if (drained == null || drained.amount <= 0) {
            return 0;
        }
        drained = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(drained);

        IAEFluidStack aeDrained = AEFluidStack.fromFluidStack(drained);
        if (aeDrained == null) {
            return 0;
        }

        IAEFluidStack notInserted = inv.injectItems(aeDrained, Actionable.SIMULATE, this.source);
        long canInsert = aeDrained.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
        if (canInsert <= 0) return 0;

        toDrain.amount = (int) canInsert;
        FluidStack actualDrain = fh.drain(toDrain, true);
        if (actualDrain != null && actualDrain.amount > 0) {
            actualDrain = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.canonicalizeFluidStack(actualDrain);
            inv.injectItems(AEFluidStack.fromFluidStack(actualDrain), Actionable.MODULATE, this.source);
            return actualDrain.amount;
        }
        return 0;
    }

    // === Gas Stocking ===

    @SuppressWarnings("unchecked")
    private class GasStockingHandler implements StockingHandler {
        @Override
        public long handle(TileEntity target, EnumFacing opposite, IAEItemStack filter,
                           long targetAmount, long maxWork) throws GridAccessException {
            if (!GasReflectionHelper.isAvailable()) return 0;
            Object gasHandler = GasReflectionHelper.getGasHandler(target, opposite);
            if (gasHandler == null) return 0;

            IMEMonitor<?> rawInv = null;
            try {
                rawInv = GasReflectionHelper.getGasInventory(PartStockingBus.this.getProxy().getGrid());
            } catch (Exception e) {
                return 0;
            }
            if (rawInv == null) return 0;
            IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv = 
                    (IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack>) rawInv;

            com.mekeng.github.common.me.data.IAEGasStack wanted = FakeGases.unpackGas(filter);
            if (wanted == null || wanted.getGas() == null) {
                AE2Enhanced.LOGGER.warn("[AE2E] GasStockingHandler: unpackGas failed for filter {} (item={})",
                        filter, filter != null ? filter.getItem() : "null");
                return 0;
            }

            try {
                long actual = countGas(gasHandler, opposite, wanted);
                long scaledMaxWork = maxWork * 1000;
                long consumedMb = runStocking(actual, targetAmount, scaledMaxWork,
                        toSupply -> supplyGas(gasHandler, opposite, inv, wanted, toSupply),
                        toRecover -> recoverGas(gasHandler, opposite, inv, wanted, toRecover));
                return consumedMb > 0 ? Math.max(1, consumedMb / 1000) : 0;
            } catch (GridAccessException e) {
                throw e;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Gas stocking failed", e);
                return 0;
            }
        }
    }

    private long countGas(Object gasHandler, EnumFacing opposite,
                          com.mekeng.github.common.me.data.IAEGasStack wanted) throws Exception {
        int[] probeAmounts = {Integer.MAX_VALUE, 1000, 1};
        for (int amount : probeAmounts) {
            Object drained = GasReflectionHelper.drawGas(gasHandler, opposite, amount, false);
            if (drained != null) {
                int drainedAmount = GasReflectionHelper.getGasAmount(drained);
                Object gasType = GasReflectionHelper.getGasType(drained);
                Object wantedGasType = wanted.getGas();
                if (gasType != null && gasType.equals(wantedGasType)) {
                    return drainedAmount;
                }
            }
        }
        return 0;
    }

    private long supplyGas(Object gasHandler, EnumFacing opposite,
                           IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv,
                           com.mekeng.github.common.me.data.IAEGasStack wanted, long amount) throws Exception {
        if (amount <= 0) return 0;

        int supplyAmount = (int) Math.min(amount, Integer.MAX_VALUE);
        Object gasStackObj = GasReflectionHelper.createGasStack(wanted.getGas(), supplyAmount);
        if (gasStackObj == null) return 0;
        com.mekeng.github.common.me.data.impl.AEGasStack aeSupply =
                com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) gasStackObj);
        if (aeSupply == null) return 0;

        com.mekeng.github.common.me.data.IAEGasStack extracted = inv.extractItems(aeSupply, Actionable.SIMULATE, this.source);
        long canExtract = extracted != null ? extracted.getStackSize() : 0;
        if (canExtract <= 0) return 0;

        Object toReceive = GasReflectionHelper.createGasStack(wanted.getGas(), (int) canExtract);
        int received = GasReflectionHelper.receiveGas(gasHandler, opposite, toReceive, false);
        if (received <= 0) return 0;

        Object actualExtract = GasReflectionHelper.createGasStack(wanted.getGas(), received);
        com.mekeng.github.common.me.data.impl.AEGasStack aeExtract =
                com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) actualExtract);
        inv.extractItems(aeExtract, Actionable.MODULATE, this.source);
        GasReflectionHelper.receiveGas(gasHandler, opposite, actualExtract, true);
        return received;
    }

    private long recoverGas(Object gasHandler, EnumFacing opposite,
                            IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> inv,
                            com.mekeng.github.common.me.data.IAEGasStack wanted, long amount) throws Exception {
        if (amount <= 0) return 0;

        Object drained = GasReflectionHelper.drawGas(gasHandler, opposite, (int) Math.min(amount, Integer.MAX_VALUE), false);
        if (drained == null) return 0;
        int drainedAmount = GasReflectionHelper.getGasAmount(drained);
        if (drainedAmount <= 0) return 0;

        Object gasStackObj = GasReflectionHelper.createGasStack(wanted.getGas(), drainedAmount);
        com.mekeng.github.common.me.data.impl.AEGasStack aeDraw =
                com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) gasStackObj);
        if (aeDraw == null) return 0;

        com.mekeng.github.common.me.data.IAEGasStack notInserted = inv.injectItems(aeDraw, Actionable.SIMULATE, this.source);
        long canInsert = aeDraw.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
        if (canInsert <= 0) return 0;

        Object actual = GasReflectionHelper.drawGas(gasHandler, opposite, (int) canInsert, true);
        if (actual != null) {
            int actualAmount = GasReflectionHelper.getGasAmount(actual);
            actualAmount = (int) Math.min(actualAmount, canInsert);
            if (actualAmount > 0) {
                Object toInsert = GasReflectionHelper.createGasStack(wanted.getGas(), actualAmount);
                com.mekeng.github.common.me.data.impl.AEGasStack aeInsert =
                        com.mekeng.github.common.me.data.impl.AEGasStack.of((mekanism.api.gas.GasStack) toInsert);
                inv.injectItems(aeInsert, Actionable.MODULATE, this.source);
                return actualAmount;
            }
        }
        return 0;
    }

    // === Essentia Stocking ===

    private class EssentiaStockingHandler implements StockingHandler {
        @Override
        public long handle(TileEntity target, EnumFacing opposite, IAEItemStack filter,
                           long targetAmount, long maxWork) throws GridAccessException {
            try {
                Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.util.reflection.EssentiaBusHelper");
                java.lang.reflect.Method method = helperClass.getMethod("stockEssentias",
                        appeng.api.networking.IGrid.class, TileEntity.class, EnumFacing.class,
                        IAEItemStack.class, long.class, long.class, int.class, IActionSource.class);
                Object result = method.invoke(null, PartStockingBus.this.getProxy().getGrid(), target, opposite,
                        filter, targetAmount, maxWork, PartStockingBus.this.mode.ordinal(), PartStockingBus.this.source);
                return result instanceof Number ? ((Number) result).longValue() : 0;
            } catch (NoSuchMethodException e) {
                return 0;
            } catch (GridAccessException e) {
                throw e;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Essentia stocking failed", e);
                return 0;
            }
        }
    }

    // === NBT ===

    private void syncTargetAmountsToConfig() {
        for (int i = 0; i < CONFIG_SIZE; i++) {
            ItemStack stack = this.config.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int newCount = (int) Math.min(this.targetAmounts[i], Integer.MAX_VALUE);
                if (newCount > 0 && stack.getCount() != newCount) {
                    this.ignoreConfigChange = true;
                    try {
                        ItemStack newStack = stack.copy();
                        newStack.setCount(newCount);
                        this.config.setStackInSlot(i, newStack);
                    } finally {
                        this.ignoreConfigChange = false;
                    }
                } else if (newCount <= 0) {
                    this.ignoreConfigChange = true;
                    try {
                        this.config.setStackInSlot(i, ItemStack.EMPTY);
                    } finally {
                        this.ignoreConfigChange = false;
                    }
                }
            } else if (this.targetAmounts[i] > 0) {
                this.targetAmounts[i] = 0;
            }
        }
    }

    @Override
    protected NBTTagCompound downloadSettings(SettingsFrom from, NBTTagCompound output) {
        super.downloadSettings(from, output);
        output.setInteger("stockingMode", this.mode.ordinal());
        for (int i = 0; i < CONFIG_SIZE; i++) {
            output.setLong("targetAmount_" + i, this.targetAmounts[i]);
        }
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        if (compound.hasKey("stockingMode")) {
            int modeOrd = compound.getInteger("stockingMode");
            if (modeOrd >= 0 && modeOrd < StockingMode.values().length) {
                this.mode = StockingMode.values()[modeOrd];
            }
        }
        for (int i = 0; i < CONFIG_SIZE; i++) {
            String key = "targetAmount_" + i;
            if (compound.hasKey(key)) {
                this.targetAmounts[i] = compound.getLong(key);
            }
        }
        syncTargetAmountsToConfig();
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("stockingMode")) {
            int modeOrd = data.getInteger("stockingMode");
            if (modeOrd >= 0 && modeOrd < StockingMode.values().length) {
                this.mode = StockingMode.values()[modeOrd];
            }
        }
        for (int i = 0; i < CONFIG_SIZE; i++) {
            this.targetAmounts[i] = data.getLong("targetAmount_" + i);
        }
        this.config.readFromNBT(data, "config");
        syncTargetAmountsToConfig();
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("stockingMode", this.mode.ordinal());
        for (int i = 0; i < CONFIG_SIZE; i++) {
            data.setLong("targetAmount_" + i, this.targetAmounts[i]);
        }
        this.config.writeToNBT(data, "config");
    }

    // === GUI ===

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!player.world.isRemote) {
            TileEntity te = this.getHost().getTile();
            int guiId = GuiHandler.GUI_STOCKING_BUS | (this.getSide().ordinal() << 8);
            player.openGui(AE2Enhanced.instance, guiId, te.getWorld(), te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
        }
        return true;
    }

    // === Model & Collision ===

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6.0, 6.0, 11.0, 10.0, 10.0, 13.0);
        bch.addBox(5.0, 5.0, 13.0, 11.0, 11.0, 14.0);
        bch.addBox(4.0, 4.0, 14.0, 12.0, 12.0, 16.0);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5.0f;
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        }
        if (this.isPowered()) {
            return MODELS_ON;
        }
        return MODELS_OFF;
    }

    // === Getters / Setters ===

    public StockingMode getMode() {
        return this.mode;
    }

    public void setMode(StockingMode mode) {
        this.mode = mode;
        this.saveChanges();
    }

    public long getTargetAmount(int slot) {
        if (slot < 0 || slot >= CONFIG_SIZE) return 1;
        return this.targetAmounts[slot];
    }

    public void setTargetAmount(int slot, long amount) {
        if (slot < 0 || slot >= CONFIG_SIZE) return;
        this.targetAmounts[slot] = Math.max(0, amount);
        ItemStack stack = this.config.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            int newCount = (int) Math.min(this.targetAmounts[slot], Integer.MAX_VALUE);
            if (newCount > 0 && stack.getCount() != newCount) {
                this.ignoreConfigChange = true;
                try {
                    ItemStack newStack = stack.copy();
                    newStack.setCount(newCount);
                    this.config.setStackInSlot(slot, newStack);
                } finally {
                    this.ignoreConfigChange = false;
                }
            } else if (newCount <= 0) {
                this.ignoreConfigChange = true;
                try {
                    this.config.setStackInSlot(slot, ItemStack.EMPTY);
                } finally {
                    this.ignoreConfigChange = false;
                }
            }
        }
        if (this.targetAmounts[slot] == 0 && !stack.isEmpty()) {
            // 已在上方处理,此处仅作保险
        }
        this.saveChanges();
    }

    public AppEngInternalAEInventory getConfig() {
        return this.config;
    }

    @Override
    public net.minecraftforge.items.IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        return super.getInventoryByName(name);
    }

    @Override
    public void onChangeInventory(net.minecraftforge.items.IItemHandler inv, int slot,
                                   appeng.util.inv.InvOperation mc,
                                   net.minecraft.item.ItemStack removedStack,
                                   net.minecraft.item.ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);
        if (this.ignoreConfigChange) return;
        if (inv == this.config && slot >= 0 && slot < CONFIG_SIZE) {
            if (newStack.isEmpty()) {
                // 手动取出物品时重置目标数量
                if (this.targetAmounts[slot] > 0) {
                    this.targetAmounts[slot] = 1;
                    this.saveChanges();
                }
            } else if (this.targetAmounts[slot] <= 0) {
                // 新放入物品且之前已被清除,按类型设置默认值
                long defaultAmount = getDefaultTargetAmount(newStack);
                this.targetAmounts[slot] = defaultAmount;
                this.saveChanges();
                if (defaultAmount > 1) {
                    this.ignoreConfigChange = true;
                    try {
                        ItemStack syncStack = newStack.copy();
                        syncStack.setCount((int) Math.min(defaultAmount, Integer.MAX_VALUE));
                        this.config.setStackInSlot(slot, syncStack);
                    } finally {
                        this.ignoreConfigChange = false;
                    }
                }
            }
            // 放入或替换物品时保留当前 targetAmount,避免覆盖 GUI 滚轮设置的值
        }
    }

    private long getDefaultTargetAmount(ItemStack stack) {
        if (stack.isEmpty()) return 1;
        if (ItemFluidDrop.isFluidDrop(stack)) return 1000;
        if (isAeFluidDummy(stack)) return 1000;
        if (isAe2fcFluidDrop(stack)) return 1000;
        if (GasFakeItemChecks.isGasFakeItem(stack)) return 1000;
        if (isAe2fcGasDrop(stack)) return 1000;
        return 1;
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
    }
}
