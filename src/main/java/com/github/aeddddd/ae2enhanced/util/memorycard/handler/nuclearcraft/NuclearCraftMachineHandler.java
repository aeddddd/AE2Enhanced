package com.github.aeddddd.ae2enhanced.util.memorycard.handler.nuclearcraft;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.IUpgradeProvider;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * NuclearCraft 机器的配置复制粘贴 Handler.
 * <p>同时支持 NuclearCraft Overhauled (重制版) 和 NuclearCraft 2.x (非重制版).</p>
 * <p>复制/粘贴的内容包括：物品侧面配置、流体侧面配置、红石控制、比较器模式、
 * 物品输出设置、流体设置(分离/废弃/输出模式)、升级槽(速度/能量).</p>
 *
 * <p>使用反射访问 NuclearCraft API,避免硬依赖.</p>
 */
public class NuclearCraftMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final boolean IS_OVERHAULED;

    // ---------- 重制版类 ----------
    private static Class<?> TILE_ENERGY_PROCESSOR_CLASS;
    private static Class<?> TILE_CONTAINER_INFO_CLASS;
    private static Class<?> UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS;
    private static Field INFO_FIELD;
    private static Field NAME_FIELD;
    private static Field SPEED_UPGRADE_SLOT_FIELD;
    private static Field ENERGY_UPGRADE_SLOT_FIELD;

    // ---------- 非重制版类 ----------
    private static Class<?> TILE_ITEM_PROCESSOR_CLASS;
    private static Class<?> TILE_FLUID_PROCESSOR_CLASS;
    private static Class<?> TILE_ITEM_FLUID_PROCESSOR_CLASS;
    private static Class<?> ITILE_CLASS;
    private static Class<?> IUPGRADABLE_CLASS;
    private static Method GET_SPEED_UPGRADE_SLOT_METHOD;
    private static Method GET_ENERGY_UPGRADE_SLOT_METHOD;
    private static Method HAS_UPGRADES_METHOD;

    // ---------- 共享方法 ----------
    private static Method GET_REDSTONE_CONTROL_METHOD;
    private static Method SET_REDSTONE_CONTROL_METHOD;
    private static Method GET_ALTERNATE_COMPARATOR_METHOD;
    private static Method SET_ALTERNATE_COMPARATOR_METHOD;

    private static Method HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD;
    private static Method WRITE_INVENTORY_CONNECTIONS_METHOD;
    private static Method READ_INVENTORY_CONNECTIONS_METHOD;
    private static Method WRITE_SLOT_SETTINGS_METHOD;
    private static Method READ_SLOT_SETTINGS_METHOD;
    private static Method GET_INVENTORY_STACKS_METHOD;

    private static Method HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD;
    private static Method WRITE_FLUID_CONNECTIONS_METHOD;
    private static Method READ_FLUID_CONNECTIONS_METHOD;
    private static Method WRITE_TANK_SETTINGS_METHOD;
    private static Method READ_TANK_SETTINGS_METHOD;

    private static Method REFRESH_ENERGY_CAPACITY_METHOD;
    private static Method REFRESH_UPGRADES_METHOD;

    static {
        boolean available = false;
        boolean overhauled = false;
        try {
            if (Loader.isModLoaded("nuclearcraft")) {
                // 尝试检测重制版
                try {
                    TILE_ENERGY_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileEnergyProcessor");
                    overhauled = true;
                } catch (ClassNotFoundException e) {
                    overhauled = false;
                }

                if (overhauled) {
                    // ===== 重制版初始化 =====
                    TILE_CONTAINER_INFO_CLASS = Class.forName("nc.tile.TileContainerInfo");
                    try {
                        UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS = Class.forName("nc.tile.processor.info.UpgradableProcessorContainerInfo");
                    } catch (ClassNotFoundException e) {
                        AE2Enhanced.LOGGER.warn("[AE2E] UpgradableProcessorContainerInfo not found, NC upgrade support disabled");
                    }

                    INFO_FIELD = TILE_ENERGY_PROCESSOR_CLASS.getDeclaredField("info");
                    INFO_FIELD.setAccessible(true);
                    NAME_FIELD = TILE_CONTAINER_INFO_CLASS.getField("name");

                    if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS != null) {
                        SPEED_UPGRADE_SLOT_FIELD = UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.getField("speedUpgradeSlot");
                        ENERGY_UPGRADE_SLOT_FIELD = UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.getField("energyUpgradeSlot");
                    }

                    Class<?> ncTileClass = Class.forName("nc.tile.NCTile");
                    GET_REDSTONE_CONTROL_METHOD = ncTileClass.getMethod("getRedstoneControl");
                    SET_REDSTONE_CONTROL_METHOD = ncTileClass.getMethod("setRedstoneControl", boolean.class);
                    GET_ALTERNATE_COMPARATOR_METHOD = ncTileClass.getMethod("getAlternateComparator");
                    SET_ALTERNATE_COMPARATOR_METHOD = ncTileClass.getMethod("setAlternateComparator", boolean.class);

                    Class<?> iTileInventoryClass = Class.forName("nc.tile.inventory.ITileInventory");
                    HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("hasConfigurableInventoryConnections");
                    WRITE_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("writeInventoryConnections", NBTTagCompound.class);
                    READ_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("readInventoryConnections", NBTTagCompound.class);
                    WRITE_SLOT_SETTINGS_METHOD = iTileInventoryClass.getMethod("writeSlotSettings", NBTTagCompound.class);
                    READ_SLOT_SETTINGS_METHOD = iTileInventoryClass.getMethod("readSlotSettings", NBTTagCompound.class);
                    GET_INVENTORY_STACKS_METHOD = iTileInventoryClass.getMethod("getInventoryStacks");

                    Class<?> iTileFluidClass = Class.forName("nc.tile.fluid.ITileFluid");
                    HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("hasConfigurableFluidConnections");
                    WRITE_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("writeFluidConnections", NBTTagCompound.class);
                    READ_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("readFluidConnections", NBTTagCompound.class);
                    WRITE_TANK_SETTINGS_METHOD = iTileFluidClass.getMethod("writeTankSettings", NBTTagCompound.class);
                    READ_TANK_SETTINGS_METHOD = iTileFluidClass.getMethod("readTankSettings", NBTTagCompound.class);

                    REFRESH_ENERGY_CAPACITY_METHOD = TILE_ENERGY_PROCESSOR_CLASS.getMethod("refreshEnergyCapacity");

                    available = true;
                } else {
                    // ===== 非重制版初始化 =====
                    TILE_ITEM_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemProcessor");
                    TILE_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileFluidProcessor");
                    TILE_ITEM_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemFluidProcessor");
                    ITILE_CLASS = Class.forName("nc.tile.ITile");
                    IUPGRADABLE_CLASS = Class.forName("nc.tile.processor.IUpgradable");

                    GET_REDSTONE_CONTROL_METHOD = ITILE_CLASS.getMethod("getRedstoneControl");
                    SET_REDSTONE_CONTROL_METHOD = ITILE_CLASS.getMethod("setRedstoneControl", boolean.class);
                    GET_ALTERNATE_COMPARATOR_METHOD = ITILE_CLASS.getMethod("getAlternateComparator");
                    SET_ALTERNATE_COMPARATOR_METHOD = ITILE_CLASS.getMethod("setAlternateComparator", boolean.class);

                    if (IUPGRADABLE_CLASS != null) {
                        GET_SPEED_UPGRADE_SLOT_METHOD = IUPGRADABLE_CLASS.getMethod("getSpeedUpgradeSlot");
                        GET_ENERGY_UPGRADE_SLOT_METHOD = IUPGRADABLE_CLASS.getMethod("getEnergyUpgradeSlot");
                        HAS_UPGRADES_METHOD = IUPGRADABLE_CLASS.getMethod("hasUpgrades");
                        REFRESH_UPGRADES_METHOD = IUPGRADABLE_CLASS.getMethod("refreshUpgrades");
                    }

                    Class<?> iTileInventoryClass = Class.forName("nc.tile.inventory.ITileInventory");
                    HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("hasConfigurableInventoryConnections");
                    WRITE_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("writeInventoryConnections", NBTTagCompound.class);
                    READ_INVENTORY_CONNECTIONS_METHOD = iTileInventoryClass.getMethod("readInventoryConnections", NBTTagCompound.class);
                    WRITE_SLOT_SETTINGS_METHOD = iTileInventoryClass.getMethod("writeSlotSettings", NBTTagCompound.class);
                    READ_SLOT_SETTINGS_METHOD = iTileInventoryClass.getMethod("readSlotSettings", NBTTagCompound.class);
                    GET_INVENTORY_STACKS_METHOD = iTileInventoryClass.getMethod("getInventoryStacks");

                    Class<?> iTileFluidClass = Class.forName("nc.tile.fluid.ITileFluid");
                    HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("hasConfigurableFluidConnections");
                    WRITE_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("writeFluidConnections", NBTTagCompound.class);
                    READ_FLUID_CONNECTIONS_METHOD = iTileFluidClass.getMethod("readFluidConnections", NBTTagCompound.class);
                    WRITE_TANK_SETTINGS_METHOD = iTileFluidClass.getMethod("writeTankSettings", NBTTagCompound.class);
                    READ_TANK_SETTINGS_METHOD = iTileFluidClass.getMethod("readTankSettings", NBTTagCompound.class);

                    available = true;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NuclearCraft reflection for UMC", e);
        }
        AVAILABLE = available;
        IS_OVERHAULED = overhauled;
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) {
            return false;
        }
        if (IS_OVERHAULED) {
            return TILE_ENERGY_PROCESSOR_CLASS != null && TILE_ENERGY_PROCESSOR_CLASS.isInstance(target);
        } else {
            return (TILE_ITEM_PROCESSOR_CLASS != null && TILE_ITEM_PROCESSOR_CLASS.isInstance(target))
                || (TILE_FLUID_PROCESSOR_CLASS != null && TILE_FLUID_PROCESSOR_CLASS.isInstance(target))
                || (TILE_ITEM_FLUID_PROCESSOR_CLASS != null && TILE_ITEM_FLUID_PROCESSOR_CLASS.isInstance(target));
        }
    }

    /** 获取机器标识名称(用于严格匹配) */
    private String getInfoName(TileEntity tile) throws Exception {
        if (IS_OVERHAULED) {
            Object info = INFO_FIELD.get(tile);
            if (info == null) {
                return tile.getClass().getName();
            }
            return (String) NAME_FIELD.get(info);
        } else {
            // 非重制版：使用类名作为标识
            return tile.getClass().getName();
        }
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            output.setString("infoName", getInfoName(tile));

            // 物品侧面配置
            if ((Boolean) HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD.invoke(tile)) {
                NBTTagCompound nbt = (NBTTagCompound) WRITE_INVENTORY_CONNECTIONS_METHOD.invoke(tile, new NBTTagCompound());
                if (nbt != null && !nbt.isEmpty()) {
                    output.setTag("inventoryConnections", nbt);
                }
            }

            // 流体侧面配置
            if ((Boolean) HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD.invoke(tile)) {
                NBTTagCompound nbt = (NBTTagCompound) WRITE_FLUID_CONNECTIONS_METHOD.invoke(tile, new NBTTagCompound());
                if (nbt != null && !nbt.isEmpty()) {
                    output.setTag("fluidConnections", nbt);
                }
            }

            // 物品输出设置
            NBTTagCompound slotNbt = (NBTTagCompound) WRITE_SLOT_SETTINGS_METHOD.invoke(tile, new NBTTagCompound());
            if (slotNbt != null && !slotNbt.isEmpty()) {
                output.setTag("slotSettings", slotNbt);
            }

            // 流体设置
            NBTTagCompound tankNbt = (NBTTagCompound) WRITE_TANK_SETTINGS_METHOD.invoke(tile, new NBTTagCompound());
            if (tankNbt != null && !tankNbt.isEmpty()) {
                output.setTag("tankSettings", tankNbt);
            }

            // 红石控制
            output.setBoolean("redstoneControl", (Boolean) GET_REDSTONE_CONTROL_METHOD.invoke(tile));
            // 比较器模式
            output.setBoolean("alternateComparator", (Boolean) GET_ALTERNATE_COMPARATOR_METHOD.invoke(tile));

            // 升级
            copyUpgrades(tile, output);

        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy NuclearCraft machine config", e);
        }

        return output;
    }

    /** 复制速度/能量升级槽 */
    @SuppressWarnings("unchecked")
    private void copyUpgrades(TileEntity tile, NBTTagCompound output) throws Exception {
        int speedSlot = -1;
        int energySlot = -1;

        if (IS_OVERHAULED) {
            if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS == null) {
                return;
            }
            Object info = INFO_FIELD.get(tile);
            if (info == null || !UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.isInstance(info)) {
                return;
            }
            speedSlot = SPEED_UPGRADE_SLOT_FIELD.getInt(info);
            energySlot = ENERGY_UPGRADE_SLOT_FIELD.getInt(info);
        } else {
            if (IUPGRADABLE_CLASS == null || !IUPGRADABLE_CLASS.isInstance(tile)
                    || HAS_UPGRADES_METHOD == null || !(Boolean) HAS_UPGRADES_METHOD.invoke(tile)) {
                return;
            }
            speedSlot = (Integer) GET_SPEED_UPGRADE_SLOT_METHOD.invoke(tile);
            energySlot = (Integer) GET_ENERGY_UPGRADE_SLOT_METHOD.invoke(tile);
        }

        if (speedSlot < 0 || energySlot < 0) {
            return;
        }

        NonNullList<ItemStack> stacks = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(tile);

        NBTTagList list = new NBTTagList();
        ItemStack speed = stacks.get(speedSlot);
        if (!speed.isEmpty()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", 0);
            speed.writeToNBT(tag);
            list.appendTag(tag);
        }
        ItemStack energy = stacks.get(energySlot);
        if (!energy.isEmpty()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", 1);
            energy.writeToNBT(tag);
            list.appendTag(tag);
        }

        if (!list.isEmpty()) {
            output.setTag("ae2e:upgrades", list);
        }
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            // 严格匹配 infoName
            String sourceInfoName = data.getString("infoName");
            String targetInfoName = getInfoName(tile);
            if (!sourceInfoName.equals(targetInfoName)) {
                return PasteResult.INVALID_MACHINE;
            }

            // ===== 阶段 1：升级处理 =====
            List<ItemStack> neededUpgrades = parseUpgrades(tile, data);
            if (!neededUpgrades.isEmpty()) {
                IUpgradeProvider provider = createUpgradeProvider(tile);
                if (provider != null) {
                    PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(provider, neededUpgrades, player);
                    if (result != PasteResult.SUCCESS) {
                        return result;
                    }
                }
            }

            // ===== 阶段 2：应用配置 =====
            if (data.hasKey("inventoryConnections")
                    && (Boolean) HAS_CONFIGURABLE_INVENTORY_CONNECTIONS_METHOD.invoke(tile)) {
                READ_INVENTORY_CONNECTIONS_METHOD.invoke(tile, data.getCompoundTag("inventoryConnections"));
            }

            if (data.hasKey("fluidConnections")
                    && (Boolean) HAS_CONFIGURABLE_FLUID_CONNECTIONS_METHOD.invoke(tile)) {
                READ_FLUID_CONNECTIONS_METHOD.invoke(tile, data.getCompoundTag("fluidConnections"));
            }

            if (data.hasKey("slotSettings")) {
                READ_SLOT_SETTINGS_METHOD.invoke(tile, data.getCompoundTag("slotSettings"));
            }

            if (data.hasKey("tankSettings")) {
                READ_TANK_SETTINGS_METHOD.invoke(tile, data.getCompoundTag("tankSettings"));
            }

            if (data.hasKey("redstoneControl")) {
                SET_REDSTONE_CONTROL_METHOD.invoke(tile, data.getBoolean("redstoneControl"));
            }

            if (data.hasKey("alternateComparator")) {
                SET_ALTERNATE_COMPARATOR_METHOD.invoke(tile, data.getBoolean("alternateComparator"));
            }

            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(
                        tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()),
                        3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste NuclearCraft machine config", e);
            return PasteResult.FAILED;
        }
    }

    /** 从 NBT 解析需要的升级物品 */
    @SuppressWarnings("unchecked")
    private List<ItemStack> parseUpgrades(TileEntity tile, NBTTagCompound data) throws Exception {
        List<ItemStack> list = new ArrayList<>();
        if (!data.hasKey("ae2e:upgrades")) {
            return list;
        }

        if (IS_OVERHAULED) {
            if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS == null) {
                return list;
            }
            Object info = INFO_FIELD.get(tile);
            if (info == null || !UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.isInstance(info)) {
                return list;
            }
        } else {
            if (IUPGRADABLE_CLASS == null || !IUPGRADABLE_CLASS.isInstance(tile)
                    || HAS_UPGRADES_METHOD == null || !(Boolean) HAS_UPGRADES_METHOD.invoke(tile)) {
                return list;
            }
        }

        NBTTagList upgrades = data.getTagList("ae2e:upgrades", 10);
        for (int i = 0; i < upgrades.tagCount(); i++) {
            ItemStack stack = new ItemStack(upgrades.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    /** 为目标机器创建 IUpgradeProvider */
    @SuppressWarnings("unchecked")
    private IUpgradeProvider createUpgradeProvider(TileEntity tile) throws Exception {
        int speedSlot = -1;
        int energySlot = -1;

        if (IS_OVERHAULED) {
            if (UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS == null) {
                return null;
            }
            Object info = INFO_FIELD.get(tile);
            if (info == null || !UPGRADABLE_PROCESSOR_CONTAINER_INFO_CLASS.isInstance(info)) {
                return null;
            }
            speedSlot = SPEED_UPGRADE_SLOT_FIELD.getInt(info);
            energySlot = ENERGY_UPGRADE_SLOT_FIELD.getInt(info);
        } else {
            if (IUPGRADABLE_CLASS == null || !IUPGRADABLE_CLASS.isInstance(tile)
                    || HAS_UPGRADES_METHOD == null || !(Boolean) HAS_UPGRADES_METHOD.invoke(tile)) {
                return null;
            }
            speedSlot = (Integer) GET_SPEED_UPGRADE_SLOT_METHOD.invoke(tile);
            energySlot = (Integer) GET_ENERGY_UPGRADE_SLOT_METHOD.invoke(tile);
        }

        if (speedSlot < 0 || energySlot < 0) {
            return null;
        }

        NonNullList<ItemStack> stacks = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(tile);
        return new NuclearCraftUpgradeProvider(tile, stacks, speedSlot, energySlot);
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }

    /**
     * NuclearCraft 升级槽的 IUpgradeProvider 实现.
     */
    private static class NuclearCraftUpgradeProvider implements IUpgradeProvider {

        private final TileEntity tile;
        private final NonNullList<ItemStack> stacks;
        private final int speedSlot;
        private final int energySlot;

        NuclearCraftUpgradeProvider(TileEntity tile, NonNullList<ItemStack> stacks, int speedSlot, int energySlot) {
            this.tile = tile;
            this.stacks = stacks;
            this.speedSlot = speedSlot;
            this.energySlot = energySlot;
        }

        @Override
        public int getSlotCount() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stacks.get(slot == 0 ? speedSlot : energySlot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            stacks.set(slot == 0 ? speedSlot : energySlot, stack);
            refresh();
        }

        @Override
        public void clearSlots() {
            stacks.set(speedSlot, ItemStack.EMPTY);
            stacks.set(energySlot, ItemStack.EMPTY);
            refresh();
        }

        private void refresh() {
            try {
                if (IS_OVERHAULED) {
                    if (REFRESH_ENERGY_CAPACITY_METHOD != null) {
                        REFRESH_ENERGY_CAPACITY_METHOD.invoke(tile);
                    }
                } else {
                    if (REFRESH_UPGRADES_METHOD != null) {
                        REFRESH_UPGRADES_METHOD.invoke(tile);
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Could not refresh NuclearCraft upgrades", e);
            }
        }
    }
}
