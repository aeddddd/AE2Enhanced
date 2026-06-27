package com.github.aeddddd.ae2enhanced.part;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.config.FuzzyMode;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.PartUpgradeable;
import appeng.util.SettingsFrom;
import appeng.tile.inventory.AppEngInternalAEInventory;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.util.CapabilityProbe;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentiaSafe;
import com.github.aeddddd.ae2enhanced.util.fakeitem.GasFakeItemChecks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 通用总线(Export / Import)的抽象基类.
 *
 * 提取了两个总线之间重复的外壳逻辑：
 * - BusMode / 槽位排序
 * - Capability 探测
 * - doBusWork 骨架
 * - NBT / Model / Collision
 *
 * 子类只需实现具体的 processXxxSlot / processXxxUnfiltered 方法.
 */
public abstract class PartUniversalBusBase extends PartUpgradeable implements IGridTickable {

    public enum BusMode {
        SEQUENTIAL,
        ROUND_ROBIN,
        RANDOM
    }

    private static final Random RAND = new Random();

    protected BusMode busMode = BusMode.SEQUENTIAL;
    protected int roundRobinIndex = 0;

    protected final AppEngInternalAEInventory config;
    protected final IActionSource source;

    protected boolean hasItemCap = false;
    protected boolean hasFluidCap = false;
    protected boolean hasGasCap = false;
    protected boolean hasEssentiaCap = false;

    private final IPartModel modelsOff;
    private final IPartModel modelsOn;
    private final IPartModel modelsHasChannel;

    protected PartUniversalBusBase(ItemStack is, int configSize,
                                    IPartModel modelsOff, IPartModel modelsOn, IPartModel modelsHasChannel) {
        super(is);
        this.modelsOff = modelsOff;
        this.modelsOn = modelsOn;
        this.modelsHasChannel = modelsHasChannel;
        this.config = new AppEngInternalAEInventory(this, configSize);
        this.source = new MachineSource(this);
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
    }

    // region Abstract hooks for subclasses

    protected abstract TickRates getTickRates();

    protected abstract boolean processItemSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception;
    protected abstract boolean processFluidSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception;
    protected abstract boolean processGasSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception;
    protected abstract boolean processEssentiaSlot(TileEntity target, EnumFacing opposite, IAEItemStack filter) throws Exception;

    protected abstract boolean processItemUnfiltered(TileEntity target, EnumFacing opposite) throws Exception;
    protected abstract boolean processFluidUnfiltered(TileEntity target, EnumFacing opposite) throws Exception;
    protected abstract boolean processGasUnfiltered(TileEntity target, EnumFacing opposite) throws Exception;
    protected abstract boolean processEssentiaUnfiltered(TileEntity target, EnumFacing opposite) throws Exception;

    protected abstract int getGuiId();

    // endregion

    // region IGridTickable

    @Override
    public TickingRequest getTickingRequest(@Nonnull IGridNode node) {
        TickRates rates = getTickRates();
        return new TickingRequest(rates.getMin(), rates.getMax(), this.isSleeping(), false);
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
    protected boolean isSleeping() {
        try {
            if (this.getHost() == null || this.getSide() == null) {
                return false;
            }
            return super.isSleeping();
        } catch (NullPointerException e) {
            return false;
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

        CapabilityProbe probe = new CapabilityProbe(target, opposite);
        this.hasItemCap = probe.hasItem;
        this.hasFluidCap = probe.hasFluid;
        this.hasGasCap = probe.hasGas;
        this.hasEssentiaCap = probe.hasEssentia;

        if (!probe.hasAny()) {
            return TickRateModulation.SLOWER;
        }

        List<Integer> activeSlots = new ArrayList<>();
        int maxSlot = this.availableSlots();
        for (int i = 0; i < maxSlot; i++) {
            if (this.config.getAEStackInSlot(i) != null) {
                activeSlots.add(i);
            }
        }

        boolean worked = false;

        try {
            if (activeSlots.isEmpty()) {
                if (this.hasItemCap) worked |= this.processItemUnfiltered(target, opposite);
                if (this.hasFluidCap) worked |= this.processFluidUnfiltered(target, opposite);
                if (this.hasGasCap) worked |= this.processGasUnfiltered(target, opposite);
                if (this.hasEssentiaCap) worked |= this.processEssentiaUnfiltered(target, opposite);
            } else {
                List<Integer> order = this.getSlotOrder(activeSlots);
                for (int slot : order) {
                    IAEItemStack filter = this.config.getAEStackInSlot(slot);
                    if (filter == null) continue;

                    ResourceType type = this.getSlotType(filter);
                    if (type == null) continue;

                    switch (type) {
                        case ITEM:
                            if (this.hasItemCap) worked |= this.processItemSlot(target, opposite, filter);
                            break;
                        case FLUID:
                            if (this.hasFluidCap) worked |= this.processFluidSlot(target, opposite, filter);
                            break;
                        case GAS:
                            if (this.hasGasCap) worked |= this.processGasSlot(target, opposite, filter);
                            break;
                        case ESSENTIA:
                            if (this.hasEssentiaCap) worked |= this.processEssentiaSlot(target, opposite, filter);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            return TickRateModulation.SLOWER;
        }

        return worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    // endregion

    // region Slot Ordering

    protected enum ResourceType { ITEM, FLUID, GAS, ESSENTIA }

    protected ResourceType getSlotType(IAEItemStack filter) {
        if (filter == null) return null;
        ItemStack stack = filter.createItemStack();
        if (stack.isEmpty()) return null;
        if (ItemFluidDrop.isFluidDrop(stack)) return ResourceType.FLUID;
        if (GasFakeItemChecks.isGasFakeItem(stack)) return ResourceType.GAS;
        if (FakeEssentiaSafe.isEssentiaFakeItem(stack)) return ResourceType.ESSENTIA;
        return ResourceType.ITEM;
    }

    protected List<Integer> getSlotOrder(List<Integer> slots) {
        switch (this.busMode) {
            case SEQUENTIAL:
                return slots;
            case ROUND_ROBIN:
                List<Integer> rotated = new ArrayList<>(slots);
                if (rotated.size() > 1) {
                    int idx = this.roundRobinIndex % rotated.size();
                    Collections.rotate(rotated, -idx);
                    this.roundRobinIndex = (this.roundRobinIndex + 1) % rotated.size();
                }
                return rotated;
            case RANDOM:
                List<Integer> shuffled = new ArrayList<>(slots);
                Collections.shuffle(shuffled, RAND);
                return shuffled;
            default:
                return slots;
        }
    }

    // endregion

    // region Helpers

    protected long calculateItemsToSend() {
        int speedUpgrades = this.getInstalledUpgrades(Upgrades.SPEED);
        return Math.min((long) Math.pow(2, speedUpgrades), 64);
    }

    protected int availableSlots() {
        int capacityUpgrades = this.getInstalledUpgrades(Upgrades.CAPACITY);
        return Math.min(18 + capacityUpgrades * 9, 63);
    }

    protected boolean isFakeItemFilter(IAEItemStack filter) {
        if (filter == null) return false;
        ItemStack stack = filter.createItemStack();
        if (ItemFluidDrop.isFluidDrop(stack)) return true;
        if (isAeFluidDummy(stack)) return true;
        if (isAe2fcFluidDrop(stack)) return true;
        if (GasFakeItemChecks.isGasFakeItem(stack)) return true;
        if (isAe2fcGasDrop(stack)) return true;
        if (com.github.aeddddd.ae2enhanced.util.fakeitem.EssentiaFakeItemChecks.isEssentiaFakeItem(stack)) return true;
        if (isTheDummyAspect(stack)) return true;
        return false;
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

    // endregion

    // region NBT

    @Override
    protected NBTTagCompound downloadSettings(SettingsFrom from, NBTTagCompound output) {
        super.downloadSettings(from, output);
        output.setInteger("busMode", this.busMode.ordinal());
        output.setInteger("roundRobinIndex", this.roundRobinIndex);
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        if (compound.hasKey("busMode")) {
            int mode = compound.getInteger("busMode");
            if (mode >= 0 && mode < BusMode.values().length) {
                this.busMode = BusMode.values()[mode];
            }
        }
        if (compound.hasKey("roundRobinIndex")) {
            this.roundRobinIndex = compound.getInteger("roundRobinIndex");
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("busMode")) {
            int mode = data.getInteger("busMode");
            if (mode >= 0 && mode < BusMode.values().length) {
                this.busMode = BusMode.values()[mode];
            }
        }
        if (data.hasKey("roundRobinIndex")) {
            this.roundRobinIndex = data.getInteger("roundRobinIndex");
        }
        this.config.readFromNBT(data, "config");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("busMode", this.busMode.ordinal());
        data.setInteger("roundRobinIndex", this.roundRobinIndex);
        this.config.writeToNBT(data, "config");
    }

    // endregion

    // region GUI

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!player.world.isRemote) {
            TileEntity te = this.getHost().getTile();
            int guiId = getGuiId() | (this.getSide().ordinal() << 8);
            player.openGui(com.github.aeddddd.ae2enhanced.AE2Enhanced.instance, guiId,
                    te.getWorld(), te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
        }
        return true;
    }

    // endregion

    // region Model & Collision

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
            return this.modelsHasChannel;
        }
        if (this.isPowered()) {
            return this.modelsOn;
        }
        return this.modelsOff;
    }

    // endregion

    // region Getters / Setters

    public BusMode getBusMode() {
        return this.busMode;
    }

    public int getRoundRobinIndex() {
        return this.roundRobinIndex;
    }

    public void setBusMode(BusMode mode) {
        this.busMode = mode;
        this.saveChanges();
    }

    public AppEngInternalAEInventory getConfig() {
        return this.config;
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        return super.getInventoryByName(name);
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);
    }

    // endregion
}
