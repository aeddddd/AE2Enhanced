package com.github.aeddddd.ae2enhanced.memorycard.handler.enderio;

import java.lang.reflect.Method;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;

/**
 * Ender IO 机器的配置复制粘贴 Handler(目标版本: Ender IO 1.20.1, 6.x 重写版).
 *
 * <p>复制内容(对齐 Ender IO 自身的 saveAdditional NBT 键):
 * <ul>
 * <li>IoConfig — 六面 IO 模式(IIOConfig#serializeNBT)</li>
 * <li>RedstoneControl — 红石控制模式(MachineBlockEntity#getRedstoneControl)</li>
 * <li>Range / RangeVisible — 工作范围(仅 getMaxRange() &gt; 0 的机器,如各类真空/农场)</li>
 * <li>Mode — 合金炉模式(仅 AlloySmelterBlockEntity)</li>
 * </ul>
 * Ender IO 1.20.1 机器没有升级卡槽(1.12 的电容升级已移除),故无升级适配.</p>
 *
 * <p>反射访问点(基于 Team-EnderIO/EnderIO 1.20.1 分支源码核对):
 * com.enderio.machines.common.blockentity.base.MachineBlockEntity 及其
 * getIOConfig/getRedstoneControl/setRedstoneControl/supportsRedstoneControl/
 * getMaxRange/getRange/setRange/isRangeVisible/setIsRangeVisible,
 * com.enderio.api.io.IIOConfig(INBTSerializable),
 * com.enderio.api.misc.RedstoneControl,
 * com.enderio.machines.common.blockentity.AlloySmelterBlockEntity#getMode/setMode
 * + com.enderio.machines.common.blockentity.AlloySmelterMode.</p>
 *
 * <p>反射失效时行为: canHandle 返回 false,复制/粘贴按"不支持该设备"处理,不影响游戏运行.</p>
 */
public class EnderIOMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Class<?> MACHINE_BE_CLASS;
    private static final Method GET_IO_CONFIG;
    private static final Method IO_CONFIG_SERIALIZE;
    private static final Method IO_CONFIG_DESERIALIZE;
    private static final Method SUPPORTS_REDSTONE_CONTROL;
    private static final Method GET_REDSTONE_CONTROL;
    private static final Method SET_REDSTONE_CONTROL;
    private static final Object[] REDSTONE_CONTROL_VALUES;
    private static final Method GET_MAX_RANGE;
    private static final Method GET_RANGE;
    private static final Method SET_RANGE;
    private static final Method IS_RANGE_VISIBLE;
    private static final Method SET_RANGE_VISIBLE;
    private static final Class<?> ALLOY_SMELTER_CLASS;
    private static final Method GET_MODE;
    private static final Method SET_MODE;
    private static final Object[] MODE_VALUES;

    static {
        boolean available = false;
        Class<?> machineBeClass = null;
        Method getIoConfig = null;
        Method ioConfigSerialize = null;
        Method ioConfigDeserialize = null;
        Method supportsRedstoneControl = null;
        Method getRedstoneControl = null;
        Method setRedstoneControl = null;
        Object[] redstoneControlValues = null;
        Method getMaxRange = null;
        Method getRange = null;
        Method setRange = null;
        Method isRangeVisible = null;
        Method setRangeVisible = null;
        Class<?> alloySmelterClass = null;
        Method getMode = null;
        Method setMode = null;
        Object[] modeValues = null;

        try {
            machineBeClass = Class.forName("com.enderio.machines.common.blockentity.base.MachineBlockEntity");
            getIoConfig = machineBeClass.getMethod("getIOConfig");
            supportsRedstoneControl = machineBeClass.getMethod("supportsRedstoneControl");
            getRedstoneControl = machineBeClass.getMethod("getRedstoneControl");
            Class<?> redstoneControlClass = Class.forName("com.enderio.api.misc.RedstoneControl");
            setRedstoneControl = machineBeClass.getMethod("setRedstoneControl", redstoneControlClass);
            redstoneControlValues = redstoneControlClass.getEnumConstants();
            getMaxRange = machineBeClass.getMethod("getMaxRange");
            getRange = machineBeClass.getMethod("getRange");
            setRange = machineBeClass.getMethod("setRange", int.class);
            isRangeVisible = machineBeClass.getMethod("isRangeVisible");
            setRangeVisible = machineBeClass.getMethod("setIsRangeVisible", boolean.class);

            Class<?> ioConfigClass = Class.forName("com.enderio.api.io.IIOConfig");
            ioConfigSerialize = ioConfigClass.getMethod("serializeNBT");
            // INBTSerializable<CompoundTag> 擦除后接口上只有 deserializeNBT(Tag)
            ioConfigDeserialize = ioConfigClass.getMethod("deserializeNBT", Tag.class);

            // 合金炉模式为可选段,单独尝试,失败不影响主功能
            try {
                alloySmelterClass = Class.forName("com.enderio.machines.common.blockentity.AlloySmelterBlockEntity");
                getMode = alloySmelterClass.getMethod("getMode");
                Class<?> modeClass = Class.forName("com.enderio.machines.common.blockentity.AlloySmelterMode");
                setMode = alloySmelterClass.getMethod("setMode", modeClass);
                modeValues = modeClass.getEnumConstants();
            } catch (Throwable t) {
                alloySmelterClass = null;
                AE2Enhanced.LOGGER.debug("[AE2E] EnderIO alloy smelter mode reflection unavailable", t);
            }

            available = true;
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize EnderIO reflection for UMC", t);
        }

        AVAILABLE = available;
        MACHINE_BE_CLASS = machineBeClass;
        GET_IO_CONFIG = getIoConfig;
        IO_CONFIG_SERIALIZE = ioConfigSerialize;
        IO_CONFIG_DESERIALIZE = ioConfigDeserialize;
        SUPPORTS_REDSTONE_CONTROL = supportsRedstoneControl;
        GET_REDSTONE_CONTROL = getRedstoneControl;
        SET_REDSTONE_CONTROL = setRedstoneControl;
        REDSTONE_CONTROL_VALUES = redstoneControlValues;
        GET_MAX_RANGE = getMaxRange;
        GET_RANGE = getRange;
        SET_RANGE = setRange;
        IS_RANGE_VISIBLE = isRangeVisible;
        SET_RANGE_VISIBLE = setRangeVisible;
        ALLOY_SMELTER_CLASS = alloySmelterClass;
        GET_MODE = getMode;
        SET_MODE = setMode;
        MODE_VALUES = modeValues;
    }

    @Override
    public boolean canHandle(Object target) {
        return AVAILABLE && MACHINE_BE_CLASS.isInstance(target);
    }

    @Override
    public CompoundTag copy(Object target) {
        BlockEntity blockEntity = (BlockEntity) target;
        CompoundTag output = new CompoundTag();
        try {
            // 1. 六面 IO 配置
            Object ioConfig = GET_IO_CONFIG.invoke(blockEntity);
            output.put("IoConfig", (CompoundTag) IO_CONFIG_SERIALIZE.invoke(ioConfig));

            // 2. 红石控制
            if ((Boolean) SUPPORTS_REDSTONE_CONTROL.invoke(blockEntity)) {
                output.putInt("RedstoneControl", ((Enum<?>) GET_REDSTONE_CONTROL.invoke(blockEntity)).ordinal());
            }

            // 3. 工作范围(仅支持范围的机器)
            if ((Integer) GET_MAX_RANGE.invoke(blockEntity) > 0) {
                output.putInt("Range", (Integer) GET_RANGE.invoke(blockEntity));
                output.putBoolean("RangeVisible", (Boolean) IS_RANGE_VISIBLE.invoke(blockEntity));
            }

            // 4. 合金炉模式
            if (ALLOY_SMELTER_CLASS != null && ALLOY_SMELTER_CLASS.isInstance(blockEntity)) {
                output.putInt("Mode", ((Enum<?>) GET_MODE.invoke(blockEntity)).ordinal());
            }

            // 5. 机型校验键: 方块注册名
            output.putString("dataType", ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).toString());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy EnderIO machine config", e);
        }
        return output;
    }

    @Override
    public PasteResult paste(Object target, CompoundTag data, Player player) {
        BlockEntity blockEntity = (BlockEntity) target;
        try {
            // 1. 机型校验(同种方块才允许粘贴)
            String blockKey = ForgeRegistries.BLOCKS.getKey(blockEntity.getBlockState().getBlock()).toString();
            if (!blockKey.equals(data.getString("dataType"))) {
                return PasteResult.INVALID_MACHINE;
            }

            // 2. 六面 IO 配置
            if (data.contains("IoConfig", Tag.TAG_COMPOUND)) {
                Object ioConfig = GET_IO_CONFIG.invoke(blockEntity);
                IO_CONFIG_DESERIALIZE.invoke(ioConfig, data.getCompound("IoConfig"));
            }

            // 3. 红石控制
            if (data.contains("RedstoneControl") && (Boolean) SUPPORTS_REDSTONE_CONTROL.invoke(blockEntity)) {
                int ordinal = data.getInt("RedstoneControl");
                if (ordinal >= 0 && ordinal < REDSTONE_CONTROL_VALUES.length) {
                    SET_REDSTONE_CONTROL.invoke(blockEntity, REDSTONE_CONTROL_VALUES[ordinal]);
                }
            }

            // 4. 工作范围
            if (data.contains("Range") && (Integer) GET_MAX_RANGE.invoke(blockEntity) > 0) {
                SET_RANGE.invoke(blockEntity, Math.min(data.getInt("Range"), (Integer) GET_MAX_RANGE.invoke(blockEntity)));
                SET_RANGE_VISIBLE.invoke(blockEntity, data.getBoolean("RangeVisible"));
            }

            // 5. 合金炉模式
            if (data.contains("Mode") && ALLOY_SMELTER_CLASS != null && ALLOY_SMELTER_CLASS.isInstance(blockEntity)) {
                int ordinal = data.getInt("Mode");
                if (ordinal >= 0 && ordinal < MODE_VALUES.length) {
                    SET_MODE.invoke(blockEntity, MODE_VALUES[ordinal]);
                }
            }

            // 6. 落盘并同步(对齐原生 load/onIOConfigChanged 的更新方式)
            blockEntity.setChanged();
            Level level = blockEntity.getLevel();
            if (level != null) {
                level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(),
                        blockEntity.getBlockState(), Block.UPDATE_ALL);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste EnderIO machine config", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockState().getBlock().getName().getString();
        }
        return target.getClass().getSimpleName();
    }
}
