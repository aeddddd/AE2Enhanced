package com.github.aeddddd.ae2enhanced.util.memorycard.handler.enderio;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.IUpgradeProvider;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemStackArrayUpgradeAdapter;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Ender IO 机器的配置复制粘贴 Handler.
 * 配置与升级分离：升级通过 IUpgradeProvider 处理,配置直接反射应用.
 */
public class EnderIOMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Class<?> ABSTRACT_MACHINE_ENTITY_CLASS;
    private static final Class<?> ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS;
    private static final Class<?> SLOT_DEFINITION_CLASS;

    private static final Field REDSTONE_CONTROL_MODE_FIELD;
    private static final Field FACING_FIELD;
    private static final Field FACE_MODES_FIELD;
    private static final Field INVENTORY_FIELD;
    private static final Field SLOT_DEFINITION_FIELD;

    private static final Method GET_REDSTONE_CONTROL_MODE;
    private static final Method SET_REDSTONE_CONTROL_MODE;
    private static final Method GET_FACING;
    private static final Method SET_FACING;
    private static final Method GET_IO_MODE;
    private static final Method SET_IO_MODE;
    private static final Method GET_SLOT_DEFINITION;
    private static final Method GET_MACHINE_NAME;
    private static final Method SET_INVENTORY_SLOT_CONTENTS;

    private static final Class<?> REDSTONE_CONTROL_MODE_CLASS;
    private static final Object[] REDSTONE_CONTROL_MODE_VALUES;

    private static final Class<?> IO_MODE_CLASS;
    private static final Object[] IO_MODE_VALUES;

    static {
        boolean available = false;
        Class<?> abstractMachineEntityClass = null;
        Class<?> abstractInventoryMachineEntityClass = null;
        Class<?> slotDefinitionClass = null;

        Field redstoneControlModeField = null;
        Field facingField = null;
        Field faceModesField = null;
        Field inventoryField = null;
        Field slotDefinitionField = null;

        Method getRedstoneControlMode = null;
        Method setRedstoneControlMode = null;
        Method getFacing = null;
        Method setFacing = null;
        Method getIoMode = null;
        Method setIoMode = null;
        Method getSlotDefinition = null;
        Method getMachineName = null;
        Method setInventorySlotContents = null;

        Class<?> redstoneControlModeClass = null;
        Object[] redstoneControlModeValues = null;

        Class<?> ioModeClass = null;
        Object[] ioModeValues = null;

        try {
            if (Loader.isModLoaded("enderio")) {
                abstractMachineEntityClass = Class.forName("crazypants.enderio.base.machine.base.te.AbstractMachineEntity");
                abstractInventoryMachineEntityClass = Class.forName("crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity");
                slotDefinitionClass = Class.forName("crazypants.enderio.base.machine.baselegacy.SlotDefinition");

                redstoneControlModeClass = Class.forName("crazypants.enderio.base.machine.modes.RedstoneControlMode");
                redstoneControlModeValues = redstoneControlModeClass.getEnumConstants();

                ioModeClass = Class.forName("crazypants.enderio.base.machine.modes.IoMode");
                ioModeValues = ioModeClass.getEnumConstants();

                redstoneControlModeField = abstractMachineEntityClass.getDeclaredField("redstoneControlMode");
                redstoneControlModeField.setAccessible(true);
                facingField = abstractMachineEntityClass.getDeclaredField("facing");
                facingField.setAccessible(true);
                faceModesField = abstractMachineEntityClass.getDeclaredField("faceModes");
                faceModesField.setAccessible(true);

                inventoryField = abstractInventoryMachineEntityClass.getDeclaredField("inventory");
                inventoryField.setAccessible(true);
                slotDefinitionField = abstractInventoryMachineEntityClass.getDeclaredField("slotDefinition");
                slotDefinitionField.setAccessible(true);

                getRedstoneControlMode = abstractMachineEntityClass.getMethod("getRedstoneControlMode");
                setRedstoneControlMode = abstractMachineEntityClass.getMethod("setRedstoneControlMode", redstoneControlModeClass);
                getFacing = abstractMachineEntityClass.getMethod("getFacing");
                setFacing = abstractMachineEntityClass.getMethod("setFacing", EnumFacing.class);
                getIoMode = abstractMachineEntityClass.getMethod("getIoMode", EnumFacing.class);
                setIoMode = abstractMachineEntityClass.getMethod("setIoMode", EnumFacing.class, ioModeClass);
                getSlotDefinition = abstractInventoryMachineEntityClass.getMethod("getSlotDefinition");
                getMachineName = abstractMachineEntityClass.getMethod("getMachineName");
                setInventorySlotContents = abstractInventoryMachineEntityClass.getMethod("setInventorySlotContents", int.class, ItemStack.class);

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Ender IO reflection for UMC", e);
        }

        AVAILABLE = available;
        ABSTRACT_MACHINE_ENTITY_CLASS = abstractMachineEntityClass;
        ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS = abstractInventoryMachineEntityClass;
        SLOT_DEFINITION_CLASS = slotDefinitionClass;

        REDSTONE_CONTROL_MODE_FIELD = redstoneControlModeField;
        FACING_FIELD = facingField;
        FACE_MODES_FIELD = faceModesField;
        INVENTORY_FIELD = inventoryField;
        SLOT_DEFINITION_FIELD = slotDefinitionField;

        GET_REDSTONE_CONTROL_MODE = getRedstoneControlMode;
        SET_REDSTONE_CONTROL_MODE = setRedstoneControlMode;
        GET_FACING = getFacing;
        SET_FACING = setFacing;
        GET_IO_MODE = getIoMode;
        SET_IO_MODE = setIoMode;
        GET_SLOT_DEFINITION = getSlotDefinition;
        GET_MACHINE_NAME = getMachineName;
        SET_INVENTORY_SLOT_CONTENTS = setInventorySlotContents;

        REDSTONE_CONTROL_MODE_CLASS = redstoneControlModeClass;
        REDSTONE_CONTROL_MODE_VALUES = redstoneControlModeValues;

        IO_MODE_CLASS = ioModeClass;
        IO_MODE_VALUES = ioModeValues;
    }

    @Override
    public boolean canHandle(Object target) {
        return AVAILABLE && target != null && ABSTRACT_MACHINE_ENTITY_CLASS.isInstance(target);
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            Object redstoneMode = GET_REDSTONE_CONTROL_MODE.invoke(tile);
            output.setInteger("redstoneMode", ((Enum<?>) redstoneMode).ordinal());

            Object facing = GET_FACING.invoke(tile);
            output.setInteger("facing", ((EnumFacing) facing).getIndex());

            @SuppressWarnings("unchecked")
            Map<EnumFacing, ?> faceModes = (Map<EnumFacing, ?>) FACE_MODES_FIELD.get(tile);
            if (faceModes != null) {
                NBTTagCompound faceModesNbt = new NBTTagCompound();
                for (Map.Entry<EnumFacing, ?> entry : faceModes.entrySet()) {
                    faceModesNbt.setInteger(entry.getKey().getName(), ((Enum<?>) entry.getValue()).ordinal());
                }
                output.setTag("faceModes", faceModesNbt);
            }

            if (ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(tile)) {
                Object slotDefinition = GET_SLOT_DEFINITION.invoke(tile);
                int minUpgradeSlot = (int) SLOT_DEFINITION_CLASS.getMethod("getMinUpgradeSlot").invoke(slotDefinition);
                int maxUpgradeSlot = (int) SLOT_DEFINITION_CLASS.getMethod("getMaxUpgradeSlot").invoke(slotDefinition);

                if (minUpgradeSlot >= 0) {
                    ItemStack[] inventory = (ItemStack[]) INVENTORY_FIELD.get(tile);
                    int count = maxUpgradeSlot - minUpgradeSlot + 1;
                    NBTTagList upgrades = MemoryCardUpgradeHelper.serializeUpgrades(
                            new ItemStackArrayUpgradeAdapter(inventory, minUpgradeSlot, count));
                    if (!upgrades.isEmpty()) {
                        output.setTag("eio:upgrades", upgrades);
                    }
                }

                String machineName = (String) GET_MACHINE_NAME.invoke(tile);
                output.setString("dataType", machineName);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy EIO machine config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            if (data.hasKey("dataType")) {
                String sourceName = data.getString("dataType");
                String targetName = (String) GET_MACHINE_NAME.invoke(tile);
                if (!sourceName.equals(targetName)) {
                    return PasteResult.INVALID_MACHINE;
                }
            }

            // 1. 先处理升级
            if (data.hasKey("eio:upgrades") && ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(tile)) {
                Object slotDefinition = GET_SLOT_DEFINITION.invoke(tile);
                int minUpgradeSlot = (int) SLOT_DEFINITION_CLASS.getMethod("getMinUpgradeSlot").invoke(slotDefinition);
                int maxUpgradeSlot = (int) SLOT_DEFINITION_CLASS.getMethod("getMaxUpgradeSlot").invoke(slotDefinition);

                if (minUpgradeSlot >= 0) {
                    ItemStack[] inventory = (ItemStack[]) INVENTORY_FIELD.get(tile);
                    int count = maxUpgradeSlot - minUpgradeSlot + 1;
                    NBTTagList upgrades = data.getTagList("eio:upgrades", 10);
                    List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgrades);
                    IUpgradeProvider provider = new ItemStackArrayUpgradeAdapter(inventory, minUpgradeSlot, count);
                    PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(provider, needed, player);
                    if (result != PasteResult.SUCCESS) return result;

                    // 通过 setInventorySlotContents 触发电容刷新
                    // AbstractPoweredMachineEntity.setInventorySlotContents 会在升级槽位变更时
                    // 调用 updateCapacitorFromSlot(),直接操作 inventory[] 数组会绕过此逻辑
                    if (SET_INVENTORY_SLOT_CONTENTS != null) {
                        for (int i = 0; i < count; i++) {
                            int slot = minUpgradeSlot + i;
                            ItemStack stack = inventory[slot];
                            SET_INVENTORY_SLOT_CONTENTS.invoke(tile, slot, stack != null ? stack : ItemStack.EMPTY);
                        }
                    }
                }
            }

            // 2. 红石控制模式
            if (data.hasKey("redstoneMode")) {
                int ordinal = data.getInteger("redstoneMode");
                if (ordinal >= 0 && ordinal < REDSTONE_CONTROL_MODE_VALUES.length) {
                    SET_REDSTONE_CONTROL_MODE.invoke(tile, REDSTONE_CONTROL_MODE_VALUES[ordinal]);
                }
            }

            // 3. 朝向
            if (data.hasKey("facing")) {
                int facingIndex = data.getInteger("facing");
                EnumFacing facing = EnumFacing.byIndex(facingIndex);
                if (facing != null) {
                    SET_FACING.invoke(tile, facing);
                }
            }

            // 4. 面 IO 模式
            if (data.hasKey("faceModes")) {
                NBTTagCompound faceModesNbt = data.getCompoundTag("faceModes");
                Method clearAllIoModes = ABSTRACT_MACHINE_ENTITY_CLASS.getMethod("clearAllIoModes");
                clearAllIoModes.invoke(tile);
                for (String key : faceModesNbt.getKeySet()) {
                    EnumFacing face = EnumFacing.byName(key);
                    if (face != null) {
                        int ordinal = faceModesNbt.getInteger(key);
                        if (ordinal >= 0 && ordinal < IO_MODE_VALUES.length) {
                            SET_IO_MODE.invoke(tile, face, IO_MODE_VALUES[ordinal]);
                        }
                    }
                }
            }

            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(), tile.getWorld().getBlockState(tile.getPos()), tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste EIO machine config", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (AVAILABLE && target != null) {
            try {
                return (String) GET_MACHINE_NAME.invoke(target);
            } catch (Exception e) {
                // fallback
            }
        }
        return target.getClass().getSimpleName();
    }
}
