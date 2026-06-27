package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * 通用网络访问节点 TileEntity.
 * 合并原 RF / Mana / Starlight 三种访问节点,根据相邻方块类型自动执行对应 IO.
 */
@Optional.Interface(iface = "vazkii.botania.api.mana.IManaReceiver", modid = "botania")
public class TileNetworkAccessNode extends TileAENetworkBase
        implements IActionHost, ITickable, IEnergyStorage, vazkii.botania.api.mana.IManaReceiver {

    public static final int MODE_INPUT = 0;
    public static final int MODE_OUTPUT = 1;
    private static final String NBT_MODE = "NetworkMode";
    // 旧 ID 的 NBT key,用于在可能的情况下继承模式
    private static final String NBT_MODE_RF = "RFMode";
    private static final String NBT_MODE_MANA = "ManaMode";
    private static final String NBT_MODE_STARLIGHT = "StarlightMode";

    private static final String POOL_CLASS = "vazkii.botania.common.block.tile.mana.TilePool";
    private static Method poolGetCurrentMana;
    private static Method poolReceiveMana;
    private static boolean poolReflectionReady = false;

    private static final String ALTAR_CLASS = "hellfirepvp.astralsorcery.common.tile.TileAltar";
    private static final String ALTAR_CONSTELLATION_CLASS = "hellfirepvp.astralsorcery.common.constellation.IWeakConstellation";
    private static Method altarGetStarlightStored;
    private static Method altarGetMaxStarlightStorage;
    private static Method altarReceiveStarlight;
    private static boolean altarReflectionReady = false;

    private MachineSource machineSource;
    private int creativeBoostCooldown = 0;
    private int mode = MODE_INPUT;

    public TileNetworkAccessNode() {
        initPoolReflection();
        initAltarReflection();
    }

    private static synchronized void initPoolReflection() {
        if (poolReflectionReady || !Loader.isModLoaded("botania")) return;
        try {
            Class<?> poolClass = Class.forName(POOL_CLASS);
            poolGetCurrentMana = poolClass.getMethod("getCurrentMana");
            poolReceiveMana = poolClass.getMethod("recieveMana", int.class);
            poolReflectionReady = true;
        } catch (Throwable t) {
            poolGetCurrentMana = null;
            poolReceiveMana = null;
            poolReflectionReady = false;
        }
    }

    private static synchronized void initAltarReflection() {
        if (altarReflectionReady || !Loader.isModLoaded("astralsorcery")) return;
        try {
            Class<?> altarClass = Class.forName(ALTAR_CLASS);
            altarGetStarlightStored = altarClass.getMethod("getStarlightStored");
            altarGetMaxStarlightStorage = altarClass.getMethod("getMaxStarlightStorage");
            altarReceiveStarlight = altarClass.getMethod("receiveStarlight", Class.forName(ALTAR_CONSTELLATION_CLASS), double.class);
            altarReflectionReady = true;
        } catch (Throwable t) {
            altarGetStarlightStored = null;
            altarGetMaxStarlightStorage = null;
            altarReceiveStarlight = null;
            altarReflectionReady = false;
        }
    }

    // === TileAENetworkBase ===

    @Override
    protected String getProxyName() {
        return "network_access_node";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.NETWORK_ACCESS_NODE);
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void disassemble() {
    }

    @Override
    public void securityBreak() {
        if (this.world != null) {
            this.world.destroyBlock(this.pos, true);
        }
    }

    // === IActionHost ===

    @Override
    public IGridNode getActionableNode() {
        return this.getProxy().getNode();
    }

    // === ITickable ===

    @Override
    public void update() {
        if (!this.world.isRemote && this.needsReady()) {
            this.clearNeedsReady();
            this.getProxy().onReady();
        }
        if (this.world.isRemote) return;

        if (this.mode == MODE_INPUT) {
            doInputTick();
        } else {
            doOutputTick();
        }
    }

    private void doInputTick() {
        doCreativeBoostTick();
        if (Loader.isModLoaded("botania") && poolReflectionReady) {
            doAdjacentPoolDrain();
        }
        if (Loader.isModLoaded("astralsorcery")) {
            doStarlightCollection();
        }
    }

    private void doOutputTick() {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighborPos = this.pos.offset(facing);
            if (!this.world.isBlockLoaded(neighborPos)) continue;
            TileEntity te = this.world.getTileEntity(neighborPos);
            if (te == null || te.isInvalid() || te == this) continue;

            doRfOutputTo(te);
            if (Loader.isModLoaded("botania") && poolReflectionReady && isManaPool(te)) {
                doPoolFill(te);
            }
            if (Loader.isModLoaded("astralsorcery") && altarReflectionReady && isAltar(te)) {
                doAltarFill(te);
            }
        }
    }

    // === RF (Forge Energy) ===

    private void doCreativeBoostTick() {
        if (!AE2EnhancedConfig.energy.creativeRfSourceBoostEnabled) return;
        if (creativeBoostCooldown-- > 0) return;
        creativeBoostCooldown = 20;

        if (!isAdjacentToCreativeRfSource()) return;

        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return;

        long amount = AE2EnhancedConfig.energy.getParsedBoostAmount();
        if (amount <= 0) return;

        IAEEnergyStack rejected = monitor.injectItems(AEEnergyStack.create(amount), Actionable.SIMULATE, getMachineSource());
        long canAccept = amount - (rejected != null ? rejected.getStackSize() : 0);
        if (canAccept <= 0) return;

        monitor.injectItems(AEEnergyStack.create(canAccept), Actionable.MODULATE, getMachineSource());
    }

    private boolean isAdjacentToCreativeRfSource() {
        Block creativeBlock = Block.getBlockFromName("draconicevolution:creative_rf_source");
        if (creativeBlock == null) return false;
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos adjacent = this.pos.offset(facing);
            if (this.world.isBlockLoaded(adjacent) && this.world.getBlockState(adjacent).getBlock() == creativeBlock) {
                return true;
            }
        }
        return false;
    }

    private void doRfOutputTo(TileEntity te) {
        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return;

        IEnergyStorage cap = findReceivableEnergyCap(te);
        if (cap == null) return;

        int demand = cap.receiveEnergy(Integer.MAX_VALUE, true);
        if (demand <= 0) return;
        demand = Math.min(demand, AE2EnhancedConfig.energy.rfAccessNodeMaxTransfer);

        IAEEnergyStack extracted = monitor.extractItems(AEEnergyStack.create(demand), Actionable.MODULATE, getMachineSource());
        if (extracted == null || extracted.getStackSize() <= 0) return;

        int toInject = (int) Math.min(extracted.getStackSize(), Integer.MAX_VALUE);
        int actual = cap.receiveEnergy(toInject, false);

        long leftover = extracted.getStackSize() - actual;
        if (leftover > 0) {
            monitor.injectItems(AEEnergyStack.create(leftover), Actionable.MODULATE, getMachineSource());
        }
    }

    private IEnergyStorage findReceivableEnergyCap(TileEntity te) {
        for (EnumFacing f : EnumFacing.values()) {
            if (te.hasCapability(CapabilityEnergy.ENERGY, f)) {
                IEnergyStorage c = te.getCapability(CapabilityEnergy.ENERGY, f);
                if (c != null && c.canReceive()) {
                    return c;
                }
            }
        }
        return null;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0 || this.mode != MODE_INPUT) return 0;
        int limit = AE2EnhancedConfig.energy.rfAccessNodeMaxTransfer;
        if (maxReceive > limit) maxReceive = limit;
        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return 0;

        Actionable action = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        IAEEnergyStack rejected = monitor.injectItems(AEEnergyStack.create(maxReceive), action, getMachineSource());
        long rejectedSize = rejected != null ? rejected.getStackSize() : 0;
        return (int) (maxReceive - rejectedSize);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0 || this.mode != MODE_OUTPUT) return 0;
        int limit = AE2EnhancedConfig.energy.rfAccessNodeMaxTransfer;
        if (maxExtract > limit) maxExtract = limit;
        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return 0;

        Actionable action = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        IAEEnergyStack extracted = monitor.extractItems(AEEnergyStack.create(maxExtract), action, getMachineSource());
        return extracted != null ? (int) Math.min(extracted.getStackSize(), Integer.MAX_VALUE) : 0;
    }

    @Override
    public int getEnergyStored() {
        IMEMonitor<IAEEnergyStack> monitor = getEnergyMonitor();
        if (monitor == null) return 0;
        IItemList<IAEEnergyStack> list = monitor.getStorageList();
        long total = 0;
        for (IAEEnergyStack stack : list) {
            if (stack != null) total += stack.getStackSize();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canExtract() {
        return this.mode == MODE_OUTPUT;
    }

    @Override
    public boolean canReceive() {
        return this.mode == MODE_INPUT;
    }

    // === Mana (Botania) ===

    private void doAdjacentPoolDrain() {
        TileEntity pool = findAdjacentManaPool();
        if (pool == null) return;

        Integer current = getPoolMana(pool);
        if (current == null || current <= 0) return;

        int maxTransfer = AE2EnhancedConfig.mana.manaAccessNodeMaxTransfer;
        int drain = Math.min(current, maxTransfer);
        if (drain <= 0) return;

        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return;

        IAEManaStack rejected = monitor.injectItems(AEManaStack.create(drain), Actionable.SIMULATE, getMachineSource());
        long canAccept = drain - (rejected != null ? rejected.getStackSize() : 0);
        if (canAccept <= 0) return;

        int actual = (int) Math.min(canAccept, drain);
        if (!changePoolMana(pool, -actual)) return;
        monitor.injectItems(AEManaStack.create(actual), Actionable.MODULATE, getMachineSource());
    }

    private void doPoolFill(TileEntity pool) {
        Integer current = getPoolMana(pool);
        Integer cap = getPoolManaCap(pool);
        if (current == null || cap == null || current >= cap) return;

        int space = Math.min(cap - current, AE2EnhancedConfig.mana.manaAccessNodeMaxTransfer);
        if (space <= 0) return;

        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return;

        IAEManaStack extracted = monitor.extractItems(AEManaStack.create(space), Actionable.MODULATE, getMachineSource());
        if (extracted == null || extracted.getStackSize() <= 0) return;

        int toFill = (int) Math.min(extracted.getStackSize(), space);
        if (!changePoolMana(pool, toFill)) {
            monitor.injectItems(AEManaStack.create(extracted.getStackSize()), Actionable.MODULATE, getMachineSource());
        }
    }

    private TileEntity findAdjacentManaPool() {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighborPos = this.pos.offset(facing);
            if (!this.world.isBlockLoaded(neighborPos)) continue;
            TileEntity te = this.world.getTileEntity(neighborPos);
            if (te != null && !te.isInvalid() && isManaPool(te)) {
                return te;
            }
        }
        return null;
    }

    private boolean isManaPool(TileEntity te) {
        return POOL_CLASS.equals(te.getClass().getName());
    }

    private Integer getPoolMana(TileEntity pool) {
        try {
            return (Integer) poolGetCurrentMana.invoke(pool);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getPoolManaCap(TileEntity pool) {
        try {
            return (Integer) pool.getClass().getField("manaCap").get(pool);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean changePoolMana(TileEntity pool, int delta) {
        try {
            poolReceiveMana.invoke(pool, delta);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isFull() {
        if (this.mode != MODE_INPUT) return true;
        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return true;
        IAEManaStack rejected = monitor.injectItems(AEManaStack.create(1), Actionable.SIMULATE, getMachineSource());
        return rejected != null && rejected.getStackSize() > 0;
    }

    @Override
    public void recieveMana(int mana) {
        if (this.mode != MODE_INPUT || mana <= 0) return;
        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return;
        int limit = AE2EnhancedConfig.mana.manaAccessNodeMaxTransfer;
        if (mana > limit) mana = limit;
        monitor.injectItems(AEManaStack.create(mana), Actionable.MODULATE, getMachineSource());
    }

    @Override
    public boolean canRecieveManaFromBursts() {
        return this.mode == MODE_INPUT;
    }

    @Override
    public int getCurrentMana() {
        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return 0;
        IItemList<IAEManaStack> list = monitor.getStorageList();
        long total = 0;
        for (IAEManaStack stack : list) {
            if (stack != null) total += stack.getStackSize();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    // === Starlight (Astral Sorcery) ===

    private void doStarlightCollection() {
        if (!canCollectStarlight()) return;

        int maxInput = AE2EnhancedConfig.starlight.starlightAccessNodeMaxInput;
        if (maxInput <= 0) return;

        IMEMonitor<IAEStarlightStack> monitor = getStarlightMonitor();
        if (monitor == null) return;

        IAEStarlightStack rejected = monitor.injectItems(AEStarlightStack.create(maxInput), Actionable.SIMULATE, getMachineSource());
        long canAccept = maxInput - (rejected != null ? rejected.getStackSize() : 0);
        if (canAccept <= 0) return;

        monitor.injectItems(AEStarlightStack.create(canAccept), Actionable.MODULATE, getMachineSource());
    }

    private boolean canCollectStarlight() {
        if (this.world == null) return false;
        if (!this.world.canBlockSeeSky(this.pos.up())) return false;
        long time = this.world.getWorldTime() % 24000;
        return time >= 13000 && time <= 23000;
    }

    private void doAltarFill(TileEntity altar) {
        Integer stored = getAltarStarlight(altar);
        Integer max = getAltarMaxStarlight(altar);
        if (stored == null || max == null || stored >= max) return;

        int space = max - stored;
        int maxOutput = AE2EnhancedConfig.starlight.starlightAccessNodeMaxOutput;
        int toTransfer = Math.min(space, maxOutput);
        if (toTransfer <= 0) return;

        IMEMonitor<IAEStarlightStack> monitor = getStarlightMonitor();
        if (monitor == null) return;

        IAEStarlightStack extracted = monitor.extractItems(AEStarlightStack.create(toTransfer), Actionable.MODULATE, getMachineSource());
        if (extracted == null || extracted.getStackSize() <= 0) return;

        int actual = (int) Math.min(extracted.getStackSize(), space);
        if (!giveAltarStarlight(altar, actual)) {
            monitor.injectItems(AEStarlightStack.create(extracted.getStackSize()), Actionable.MODULATE, getMachineSource());
        }
    }

    private boolean isAltar(TileEntity te) {
        return ALTAR_CLASS.equals(te.getClass().getName());
    }

    private Integer getAltarStarlight(TileEntity altar) {
        try {
            return (Integer) altarGetStarlightStored.invoke(altar);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getAltarMaxStarlight(TileEntity altar) {
        try {
            return (Integer) altarGetMaxStarlightStorage.invoke(altar);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean giveAltarStarlight(TileEntity altar, int amount) {
        try {
            altarReceiveStarlight.invoke(altar, null, (double) amount / 200.0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // === Mode ===

    public int getMode() {
        return this.mode;
    }

    public void setMode(int mode) {
        this.mode = mode == MODE_OUTPUT ? MODE_OUTPUT : MODE_INPUT;
        if (this.world != null) {
            this.world.notifyBlockUpdate(this.pos, this.world.getBlockState(this.pos), this.world.getBlockState(this.pos), 2);
        }
    }

    public void cycleMode() {
        setMode(this.mode == MODE_INPUT ? MODE_OUTPUT : MODE_INPUT);
    }

    public boolean isInputMode() {
        return this.mode == MODE_INPUT;
    }

    public boolean isOutputMode() {
        return this.mode == MODE_OUTPUT;
    }

    // === Capability ===

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    // === NBT ===

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(NBT_MODE)) {
            this.mode = compound.getInteger(NBT_MODE);
        } else if (compound.hasKey(NBT_MODE_RF)) {
            this.mode = compound.getInteger(NBT_MODE_RF);
        } else if (compound.hasKey(NBT_MODE_MANA)) {
            this.mode = compound.getInteger(NBT_MODE_MANA);
        } else if (compound.hasKey(NBT_MODE_STARLIGHT)) {
            this.mode = compound.getInteger(NBT_MODE_STARLIGHT);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        compound.setInteger(NBT_MODE, this.mode);
        return compound;
    }

    // === Helpers ===

    private IMEMonitor<IAEEnergyStack> getEnergyMonitor() {
        try {
            IStorageGrid storageGrid = getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return null;
            return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    private IMEMonitor<IAEManaStack> getManaMonitor() {
        try {
            IStorageGrid storageGrid = getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return null;
            return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    private IMEMonitor<IAEStarlightStack> getStarlightMonitor() {
        try {
            IStorageGrid storageGrid = getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return null;
            return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    private MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }

    public void onBreak() {
    }
}
