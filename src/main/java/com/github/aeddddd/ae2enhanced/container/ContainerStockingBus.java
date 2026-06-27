package com.github.aeddddd.ae2enhanced.container;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.slot.IOptionalSlotHost;
import com.github.aeddddd.ae2enhanced.container.slot.OptionalSlotStockingConfig;
import com.github.aeddddd.ae2enhanced.container.slot.SlotStockingConfig;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import com.github.aeddddd.ae2enhanced.util.fakeitem.EssentiaFakeItemChecks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;

public class ContainerStockingBus extends ContainerUpgradeable implements IOptionalSlotHost {

    private final PartStockingBus part;

    @GuiSync(value = 10)
    public int modeOrdinal = 0;

    @GuiSync(value = 20) public int target0 = 1;
    @GuiSync(value = 21) public int target1 = 1;
    @GuiSync(value = 22) public int target2 = 1;
    @GuiSync(value = 23) public int target3 = 1;
    @GuiSync(value = 24) public int target4 = 1;
    @GuiSync(value = 25) public int target5 = 1;
    @GuiSync(value = 26) public int target6 = 1;
    @GuiSync(value = 27) public int target7 = 1;
    @GuiSync(value = 28) public int target8 = 1;

    public ContainerStockingBus(InventoryPlayer ip, PartStockingBus te) {
        super(ip, te);
        this.part = te;
    }

    @Override
    protected int getHeight() {
        return 184;
    }

    @Override
    protected void setupConfig() {
        this.setupUpgrades();
        IItemHandler config = this.getUpgradeable().getInventoryByName("config");
        this.addSlotToContainer(new SlotStockingConfig(config, 0, 80, 40));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 1, 80, 40, -1, 0, 1));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 2, 80, 40, 1, 0, 1));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 3, 80, 40, 0, -1, 1));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 4, 80, 40, 0, 1, 1));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 5, 80, 40, -1, -1, 2));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 6, 80, 40, 1, -1, 2));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 7, 80, 40, -1, 1, 2));
        this.addSlotToContainer(new OptionalSlotStockingConfig(config, this, 8, 80, 40, 1, 1, 2));
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 4;
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        int capacityUpgrades = this.part.getInstalledUpgrades(Upgrades.CAPACITY);
        // idx 实际上是 OptionalSlotStockingConfig 构造函数中传入的 groupNum：
        // slot 1-4 的 groupNum = 1,slot 5-8 的 groupNum = 2
        switch (idx) {
            case 1: return capacityUpgrades >= 1;
            case 2: return capacityUpgrades >= 2;
            default: return true;
        }
    }

    @Override
    public void func_75142_b() {
        this.verifyPermissions(appeng.api.config.SecurityPermissions.BUILD, false);
        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setCraftingMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.CRAFT_ONLY));
            this.modeOrdinal = this.part.getMode().ordinal();
            this.target0 = (int) Math.min(this.part.getTargetAmount(0), Integer.MAX_VALUE);
            this.target1 = (int) Math.min(this.part.getTargetAmount(1), Integer.MAX_VALUE);
            this.target2 = (int) Math.min(this.part.getTargetAmount(2), Integer.MAX_VALUE);
            this.target3 = (int) Math.min(this.part.getTargetAmount(3), Integer.MAX_VALUE);
            this.target4 = (int) Math.min(this.part.getTargetAmount(4), Integer.MAX_VALUE);
            this.target5 = (int) Math.min(this.part.getTargetAmount(5), Integer.MAX_VALUE);
            this.target6 = (int) Math.min(this.part.getTargetAmount(6), Integer.MAX_VALUE);
            this.target7 = (int) Math.min(this.part.getTargetAmount(7), Integer.MAX_VALUE);
            this.target8 = (int) Math.min(this.part.getTargetAmount(8), Integer.MAX_VALUE);
            // 同步目标数量到 config inventory 的 stack size,让 slot 自然显示数量
            for (int i = 0; i < 9; i++) {
                appeng.api.storage.data.IAEItemStack aeStack = this.part.getConfig().getAEStackInSlot(i);
                if (aeStack != null) {
                    aeStack.setStackSize(this.part.getTargetAmount(i));
                }
            }
        }
        this.standardDetectAndSendChanges();
    }

    public PartStockingBus getPart() {
        return this.part;
    }

    public int getTargetAmount(int slot) {
        switch (slot) {
            case 0: return this.target0;
            case 1: return this.target1;
            case 2: return this.target2;
            case 3: return this.target3;
            case 4: return this.target4;
            case 5: return this.target5;
            case 6: return this.target6;
            case 7: return this.target7;
            case 8: return this.target8;
            default: return 1;
        }
    }

    public void setTargetAmount(int slot, int amount) {
        amount = Math.max(0, amount);
        switch (slot) {
            case 0: this.target0 = amount; break;
            case 1: this.target1 = amount; break;
            case 2: this.target2 = amount; break;
            case 3: this.target3 = amount; break;
            case 4: this.target4 = amount; break;
            case 5: this.target5 = amount; break;
            case 6: this.target6 = amount; break;
            case 7: this.target7 = amount; break;
            case 8: this.target8 = amount; break;
        }
        // 同步到 config inventory 的 stack size
        appeng.api.storage.data.IAEItemStack aeStack = this.part.getConfig().getAEStackInSlot(slot);
        if (aeStack != null) {
            aeStack.setStackSize(amount);
        }
    }

    public void syncTargetAmount(int slot, long amount) {
        this.setTargetAmount(slot, (int) Math.min(amount, Integer.MAX_VALUE));
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (slot >= 0 && slot < this.inventorySlots.size()) {
            Slot s = this.inventorySlots.get(slot);
            if (s instanceof appeng.container.slot.SlotFake) {
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

        ItemStack fluidDrop = tryConvertFluidDummyToFake(held);
        if (fluidDrop != null) return fluidDrop;

        ItemStack ae2fcFluid = tryConvertAe2fcFluidToFake(held);
        if (ae2fcFluid != null) return ae2fcFluid;

        ItemStack ae2fcGas = tryConvertAe2fcGasToFake(held);
        if (ae2fcGas != null) return ae2fcGas;

        ItemStack dummyAspect = tryConvertDummyAspectToFake(held);
        if (dummyAspect != null) return dummyAspect;

        ItemStack gasFake = tryConvertGasToFake(held);
        if (gasFake != null) return gasFake;

        ItemStack essentiaFake = EssentiaFakeItemChecks.tryConvertContainerToFake(held);
        if (essentiaFake != null) return essentiaFake;

        return null;
    }

    private static ItemStack tryConvertFluidDummyToFake(ItemStack held) {
        if (held.isEmpty()) return null;
        if (!"appeng.fluids.items.FluidDummyItem".equals(held.getItem().getClass().getName())) return null;
        try {
            Class<?> fluidDummyClass = Class.forName("appeng.fluids.items.FluidDummyItem");
            java.lang.reflect.Method getFluidStack = fluidDummyClass.getMethod("getFluidStack", ItemStack.class);
            FluidStack fluid = (FluidStack) getFluidStack.invoke(held.getItem(), held);
            if (fluid != null && fluid.getFluid() != null) {
                return ItemFluidDrop.createStack(new FluidStack(fluid.getFluid(), 1));
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to convert FluidDummyItem", e);
        }
        return null;
    }

    private static ItemStack tryConvertAe2fcFluidToFake(ItemStack held) {
        if (held.isEmpty()) return null;
        String name = held.getItem().getClass().getName();
        if (!"com.glodblock.github.common.item.ItemFluidDrop".equals(name)
                && !"com.glodblock.github.common.item.ItemFluidPacket".equals(name)) return null;
        try {
            FluidStack fluid = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.unpackAe2fcFluid(held);
            if (fluid != null && fluid.getFluid() != null) {
                return ItemFluidDrop.createStack(new FluidStack(fluid.getFluid(), 1));
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to convert ae2fc fluid item", e);
        }
        return null;
    }

    private static ItemStack tryConvertAe2fcGasToFake(ItemStack held) {
        if (held.isEmpty()) return null;
        String name = held.getItem().getClass().getName();
        if (!"com.glodblock.github.common.item.ItemGasDrop".equals(name)
                && !"com.glodblock.github.common.item.ItemGasPacket".equals(name)) return null;
        try {
            if (held.hasTagCompound()) {
                net.minecraft.nbt.NBTTagCompound tag = held.getTagCompound();
                String gasName = null;
                if (tag.hasKey("Gas", 8)) {
                    gasName = tag.getString("Gas");
                } else if (tag.hasKey("GasStack", 10)) {
                    Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
                    Object gasStack = gasStackClass.getMethod("readFromNBT", net.minecraft.nbt.NBTTagCompound.class)
                            .invoke(null, tag.getCompoundTag("GasStack"));
                    if (gasStack != null) {
                        Object gas = gasStackClass.getMethod("getGas").invoke(gasStack);
                        if (gas != null) {
                            gasName = (String) gas.getClass().getMethod("getName").invoke(gas);
                        }
                    }
                }
                if (gasName != null) {
                    Class<?> gasRegistryClass = Class.forName("mekanism.api.gas.GasRegistry");
                    Object gas = gasRegistryClass.getMethod("getGas", String.class).invoke(null, gasName);
                    if (gas != null) {
                        Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
                        Object gasStack = gasStackClass.getConstructor(gas.getClass(), int.class)
                                .newInstance(gas, 1);
                        Class<?> itemGasDropClass = Class.forName("com.github.aeddddd.ae2enhanced.item.ItemGasDrop");
                        return (ItemStack) itemGasDropClass.getMethod("createStack", gasStackClass)
                                .invoke(null, gasStack);
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to convert ae2fc gas item", e);
        }
        return null;
    }

    private static ItemStack tryConvertDummyAspectToFake(ItemStack held) {
        if (held.isEmpty()) return null;
        if (!"thaumicenergistics.item.ItemDummyAspect".equals(held.getItem().getClass().getName())) return null;
        try {
            if (held.hasTagCompound()) {
                net.minecraft.nbt.NBTTagCompound tag = held.getTagCompound();
                if (tag.hasKey("aspect", 8)) {
                    String aspectTag = tag.getString("aspect");
                    Class<?> essentiaDropClass = Class.forName("com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop");
                    return (ItemStack) essentiaDropClass.getMethod("createStack", String.class, int.class)
                            .invoke(null, aspectTag, 1);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to convert ItemDummyAspect", e);
        }
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
            Class<?> gasClass = Class.forName("mekanism.api.gas.Gas");
            Object newGasStack = gasStackClass.getConstructor(gasClass, int.class)
                    .newInstance(gasType, Math.min(amount, 1000));
            Class<?> itemGasDropClass = Class.forName("com.github.aeddddd.ae2enhanced.item.ItemGasDrop");
            return (ItemStack) itemGasDropClass.getMethod("createStack", gasStackClass).invoke(null, newGasStack);
        } catch (Exception e) {
            return null;
        }
    }
}
