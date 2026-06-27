package com.github.aeddddd.ae2enhanced.util.memorycard.handler.techreborn;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.IUpgradeProvider;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemStackArrayUpgradeAdapter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * TechReborn 机器的配置复制粘贴 Handler.
 * <p>支持所有继承 {@code reborncore.common.tile.RebornMachineTile} 的机器,包括：</p>
 * <ul>
 *   <li>加工机器(压缩机、研磨机、离心机等)</li>
 *   <li>发电机(固体燃料、太阳能等)</li>
 *   <li>储能设备(MFSU、AESU、BatBox 等)</li>
 *   <li>变压器、量子箱/罐等</li>
 * </ul>
 *
 * <p>复制/粘贴的内容包括：朝向、物品侧面配置(SlotConfiguration)、
 * 流体侧面配置(FluidConfiguration)、红石模式(储能设备)、
 * 输出电量(AESU)、升级槽.</p>
 *
 * <p>使用反射访问 TechReborn/RebornCore API,避免硬依赖.</p>
 */
public class TechRebornMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    // ---------- 类 ----------
    private static Class<?> REBORN_MACHINE_TILE_CLASS;
    private static Class<?> SLOT_CONFIGURATION_CLASS;
    private static Class<?> FLUID_CONFIGURATION_CLASS;
    private static Class<?> TILE_ENERGY_STORAGE_CLASS;
    private static Class<?> TILE_ADJUSTABLE_SU_CLASS;

    // ---------- RebornMachineTile 字段 ----------
    private static Field SLOT_CONFIGURATION_FIELD;
    private static Field FLUID_CONFIGURATION_FIELD;
    private static Field UPGRADE_INVENTORY_FIELD;

    // ---------- TileEnergyStorage 字段 ----------
    private static Field REDSTONE_MODE_FIELD;

    // ---------- TileAdjustableSU 字段 ----------
    private static Field OUTPUT_FIELD;

    // ---------- 方法 ----------
    private static Method GET_FACING_METHOD;
    private static Method SET_FACING_METHOD;
    private static Method CAN_BE_UPGRADED_METHOD;
    private static Method GET_UPGRADE_SLOT_COUNT_METHOD;
    private static Method SLOT_CONFIGURATION_SERIALIZE_METHOD;
    private static Method SLOT_CONFIGURATION_DESERIALIZE_METHOD;
    private static Method FLUID_CONFIGURATION_SERIALIZE_METHOD;
    private static Method FLUID_CONFIGURATION_DESERIALIZE_METHOD;
    private static Method INVENTORY_GET_STACKS_METHOD;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("techreborn")) {
                REBORN_MACHINE_TILE_CLASS = Class.forName("reborncore.common.tile.RebornMachineTile");
                SLOT_CONFIGURATION_CLASS = Class.forName("reborncore.common.tile.SlotConfiguration");
                FLUID_CONFIGURATION_CLASS = Class.forName("reborncore.common.tile.FluidConfiguration");
                TILE_ENERGY_STORAGE_CLASS = Class.forName("techreborn.tiles.storage.TileEnergyStorage");
                try {
                    TILE_ADJUSTABLE_SU_CLASS = Class.forName("techreborn.tiles.storage.TileAdjustableSU");
                } catch (ClassNotFoundException e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] TileAdjustableSU not found, AESU output support disabled");
                }

                SLOT_CONFIGURATION_FIELD = REBORN_MACHINE_TILE_CLASS.getField("slotConfiguration");
                FLUID_CONFIGURATION_FIELD = REBORN_MACHINE_TILE_CLASS.getField("fluidConfiguration");
                UPGRADE_INVENTORY_FIELD = REBORN_MACHINE_TILE_CLASS.getField("upgradeInventory");

                GET_FACING_METHOD = REBORN_MACHINE_TILE_CLASS.getMethod("getFacing");
                SET_FACING_METHOD = REBORN_MACHINE_TILE_CLASS.getMethod("setFacing", EnumFacing.class);
                CAN_BE_UPGRADED_METHOD = REBORN_MACHINE_TILE_CLASS.getMethod("canBeUpgraded");
                GET_UPGRADE_SLOT_COUNT_METHOD = Class.forName("reborncore.api.tile.IUpgradeable").getMethod("getUpgradeSlotCount");

                SLOT_CONFIGURATION_SERIALIZE_METHOD = SLOT_CONFIGURATION_CLASS.getMethod("serializeNBT");
                SLOT_CONFIGURATION_DESERIALIZE_METHOD = SLOT_CONFIGURATION_CLASS.getMethod("deserializeNBT", NBTTagCompound.class);
                FLUID_CONFIGURATION_SERIALIZE_METHOD = FLUID_CONFIGURATION_CLASS.getMethod("serializeNBT");
                FLUID_CONFIGURATION_DESERIALIZE_METHOD = FLUID_CONFIGURATION_CLASS.getMethod("deserializeNBT", NBTTagCompound.class);

                Class<?> inventoryClass = Class.forName("reborncore.common.util.Inventory");
                INVENTORY_GET_STACKS_METHOD = inventoryClass.getMethod("getStacks");

                // 储能设备红石模式（可选，失败不影响核心功能）
                try {
                    REDSTONE_MODE_FIELD = TILE_ENERGY_STORAGE_CLASS.getField("redstoneMode");
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] TechReborn redstoneMode support not available");
                }

                // AESU 输出电量字段为 private，需使用 getDeclaredField + setAccessible
                if (TILE_ADJUSTABLE_SU_CLASS != null) {
                    try {
                        OUTPUT_FIELD = TILE_ADJUSTABLE_SU_CLASS.getDeclaredField("OUTPUT");
                        OUTPUT_FIELD.setAccessible(true);
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.debug("[AE2E] TechReborn AESU OUTPUT support not available");
                    }
                }

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize TechReborn reflection for UMC", e);
        }
        AVAILABLE = available;
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) {
            return false;
        }
        // 精确匹配 TechReborn 包名下的机器,避免处理其他使用 RebornCore 的 mod
        String className = target.getClass().getName();
        return className.startsWith("techreborn.");
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            output.setString("dataType", tile.getClass().getName());

            // 朝向
            EnumFacing facing = (EnumFacing) GET_FACING_METHOD.invoke(tile);
            if (facing != null) {
                output.setInteger("facing", facing.ordinal());
            }

            // 物品侧面配置
            Object slotConfig = SLOT_CONFIGURATION_FIELD.get(tile);
            if (slotConfig != null) {
                NBTTagCompound nbt = (NBTTagCompound) SLOT_CONFIGURATION_SERIALIZE_METHOD.invoke(slotConfig);
                if (nbt != null && !nbt.isEmpty()) {
                    output.setTag("slotConfig", nbt);
                }
            }

            // 流体侧面配置
            Object fluidConfig = FLUID_CONFIGURATION_FIELD.get(tile);
            if (fluidConfig != null) {
                NBTTagCompound nbt = (NBTTagCompound) FLUID_CONFIGURATION_SERIALIZE_METHOD.invoke(fluidConfig);
                if (nbt != null && !nbt.isEmpty()) {
                    output.setTag("fluidConfig", nbt);
                }
            }

            // 红石模式(仅储能设备)
            if (TILE_ENERGY_STORAGE_CLASS.isInstance(tile)) {
                output.setByte("redstoneMode", (Byte) REDSTONE_MODE_FIELD.get(tile));
            }

            // 输出电量(仅 AESU)
            if (TILE_ADJUSTABLE_SU_CLASS != null && TILE_ADJUSTABLE_SU_CLASS.isInstance(tile)) {
                output.setInteger("output", (Integer) OUTPUT_FIELD.get(tile));
            }

            // 升级
            copyUpgrades(tile, output);

        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy TechReborn machine config", e);
        }

        return output;
    }

    /** 复制升级槽内容 */
    private void copyUpgrades(TileEntity tile, NBTTagCompound output) throws Exception {
        if (!(Boolean) CAN_BE_UPGRADED_METHOD.invoke(tile)) {
            return;
        }
        Object upgradeInventory = UPGRADE_INVENTORY_FIELD.get(tile);
        if (upgradeInventory == null) {
            return;
        }
        ItemStack[] stacks = (ItemStack[]) INVENTORY_GET_STACKS_METHOD.invoke(upgradeInventory);
        if (stacks == null || stacks.length == 0) {
            return;
        }
        NBTTagList list = MemoryCardUpgradeHelper.serializeUpgrades(new ItemStackArrayUpgradeAdapter(stacks));
        if (!list.isEmpty()) {
            output.setTag("ae2e:upgrades", list);
        }
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            // 严格匹配机器类名(不同机器的槽位/罐位数量可能不同)
            String sourceType = data.getString("dataType");
            String targetType = target.getClass().getName();
            if (!sourceType.equals(targetType)) {
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

            // 朝向
            if (data.hasKey("facing")) {
                int facingOrdinal = data.getInteger("facing");
                if (facingOrdinal >= 0 && facingOrdinal < EnumFacing.VALUES.length) {
                    SET_FACING_METHOD.invoke(tile, EnumFacing.VALUES[facingOrdinal]);
                }
            }

            // 物品侧面配置
            if (data.hasKey("slotConfig")) {
                Object slotConfig = SLOT_CONFIGURATION_FIELD.get(tile);
                if (slotConfig != null) {
                    SLOT_CONFIGURATION_DESERIALIZE_METHOD.invoke(slotConfig, data.getCompoundTag("slotConfig"));
                }
            }

            // 流体侧面配置
            if (data.hasKey("fluidConfig")) {
                Object fluidConfig = FLUID_CONFIGURATION_FIELD.get(tile);
                if (fluidConfig != null) {
                    FLUID_CONFIGURATION_DESERIALIZE_METHOD.invoke(fluidConfig, data.getCompoundTag("fluidConfig"));
                }
            }

            // 红石模式
            if (data.hasKey("redstoneMode") && TILE_ENERGY_STORAGE_CLASS.isInstance(tile)) {
                REDSTONE_MODE_FIELD.set(tile, data.getByte("redstoneMode"));
            }

            // 输出电量
            if (data.hasKey("output") && TILE_ADJUSTABLE_SU_CLASS != null && TILE_ADJUSTABLE_SU_CLASS.isInstance(tile)) {
                OUTPUT_FIELD.set(tile, data.getInteger("output"));
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
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste TechReborn machine config", e);
            return PasteResult.FAILED;
        }
    }

    /** 从 NBT 解析需要的升级物品,按目标机器的升级槽数量截断 */
    private List<ItemStack> parseUpgrades(TileEntity tile, NBTTagCompound data) throws Exception {
        List<ItemStack> list = new ArrayList<>();
        if (!data.hasKey("ae2e:upgrades")) {
            return list;
        }
        if (!(Boolean) CAN_BE_UPGRADED_METHOD.invoke(tile)) {
            return list;
        }

        int slotCount = (Integer) GET_UPGRADE_SLOT_COUNT_METHOD.invoke(tile);
        NBTTagList upgrades = data.getTagList("ae2e:upgrades", 10);
        for (int i = 0; i < upgrades.tagCount() && i < slotCount; i++) {
            ItemStack stack = new ItemStack(upgrades.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    /** 为目标机器创建 IUpgradeProvider */
    private IUpgradeProvider createUpgradeProvider(TileEntity tile) throws Exception {
        Object upgradeInventory = UPGRADE_INVENTORY_FIELD.get(tile);
        if (upgradeInventory == null) {
            return null;
        }
        ItemStack[] stacks = (ItemStack[]) INVENTORY_GET_STACKS_METHOD.invoke(upgradeInventory);
        if (stacks == null || stacks.length == 0) {
            return null;
        }
        return new ItemStackArrayUpgradeAdapter(stacks);
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
