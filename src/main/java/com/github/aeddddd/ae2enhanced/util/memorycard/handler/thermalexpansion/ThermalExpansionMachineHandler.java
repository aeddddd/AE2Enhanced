package com.github.aeddddd.ae2enhanced.util.memorycard.handler.thermalexpansion;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemStackArrayUpgradeAdapter;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.IUpgradeProvider;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.reflection.ReflectionHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thermal Expansion 机器的配置复制粘贴 Handler.
 * 通过 NBT 读写关键配置字段(Facing, SideCache, Level, Augments 等).
 * Level 升级会消耗对应的 Upgrade Kit / Conversion Kit.
 *
 * 架构约定：
 * 1. paste() 采用"先统一验证(含 ME 网络回退),再原子执行"的流程.
 * 2. 配置与升级完全分离：升级通过 IUpgradeProvider 处理,配置通过 readFromNBT 处理.
 * 3. 任何消耗操作前必须确认所有物品(kit + augment)都已可用.
 */
public class ThermalExpansionMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;
    private static final Set<String> CONFIG_KEYS = new HashSet<>(Arrays.asList(
            "Facing", "SideCache", "Level", "RSControl",
            "EnableAutoInput", "EnableAutoOutput", "Mode"
    ));

    // Thermal Foundation 升级套件缓存(从静态字段直接引用,使用时必须 copy())
    private static ItemStack[] UPGRADE_INCREMENTAL = null;
    private static ItemStack[] UPGRADE_FULL = null;

    static {
        AVAILABLE = Loader.isModLoaded("thermalexpansion");
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) return false;
        String className = target.getClass().getName();
        return className.startsWith("cofh.thermalexpansion.block.machine.")
                || className.startsWith("cofh.thermalexpansion.block.dynamo.")
                || className.startsWith("cofh.thermalexpansion.block.device.")
                || className.startsWith("cofh.thermalexpansion.block.apparatus.");
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            NBTTagCompound fullNbt = tile.writeToNBT(new NBTTagCompound());

            for (String key : CONFIG_KEYS) {
                if (fullNbt.hasKey(key)) {
                    output.setTag(key, fullNbt.getTag(key));
                }
            }

            // 升级：通过 IUpgradeProvider 序列化
            ItemStack[] augments = getAugmentsArray(tile);
            if (augments != null && augments.length > 0) {
                NBTTagList list = MemoryCardUpgradeHelper.serializeUpgrades(
                        new ItemStackArrayUpgradeAdapter(augments));
                if (!list.isEmpty()) {
                    output.setTag("Augments", list);
                }
            }

            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy TE machine config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            if (data.hasKey("dataType")) {
                String sourceType = data.getString("dataType");
                String targetType = target.getClass().getName();
                if (!isCompatible(sourceType, targetType)) {
                    return PasteResult.INVALID_MACHINE;
                }
            }

            int originalLevel = getCurrentLevel(tile);
            int targetLevel = data.hasKey("Level") ? data.getByte("Level") : originalLevel;

            // ===== 阶段 1：统一计算需要的所有物品 =====
            List<ItemStack> allNeeded = new ArrayList<>();

            // 1a. Level upgrade kit(如有)
            List<ItemStack> levelKits = getLevelKits(originalLevel, targetLevel);
            for (ItemStack kit : levelKits) {
                if (kit.isEmpty()) {
                    return PasteResult.MISSING_UPGRADES;
                }
                allNeeded.add(kit);
            }

            // 1b. Augments(按目标 level 的 slot 数量截断)
            List<ItemStack> neededAugments = new ArrayList<>();
            if (data.hasKey("Augments")) {
                neededAugments = parseAugmentList(data.getTagList("Augments", 10));
                int augmentSlots = getNumAugmentSlots(tile, targetLevel);
                if (augmentSlots > 0 && neededAugments.size() > augmentSlots) {
                    neededAugments = neededAugments.subList(0, augmentSlots);
                }
                for (ItemStack aug : neededAugments) {
                    if (!aug.isEmpty()) {
                        allNeeded.add(aug.copy());
                    }
                }
            }

            // ===== 阶段 2：统一验证(含 ME 网络回退) =====
            if (!allNeeded.isEmpty() && !MemoryCardUpgradeHelper.ensureAvailable(player, allNeeded)) {
                return PasteResult.MISSING_UPGRADES;
            }

            // ===== 阶段 3：执行 Level upgrade =====
            if (!levelKits.isEmpty()) {
                for (ItemStack kit : levelKits) {
                    MemoryCardUpgradeHelper.consumeFromInventory(player, kit);
                }
                boolean ok = setLevel(tile, targetLevel);
                if (!ok) {
                    for (ItemStack kit : levelKits) {
                        if (!player.addItemStackToInventory(kit.copy())) {
                            player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, kit.copy()));
                        }
                    }
                    return PasteResult.FAILED;
                }
                closeOpenGUIs(tile);
            }

            // ===== 阶段 4：执行 Augment(通过 IUpgradeProvider) =====
            if (!neededAugments.isEmpty()) {
                ItemStack[] augments = getAugmentsArray(tile);
                if (augments != null && augments.length > 0) {
                    IUpgradeProvider provider = new ItemStackArrayUpgradeAdapter(augments);
                    PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(provider, neededAugments, player);
                    if (result != PasteResult.SUCCESS) {
                        if (!levelKits.isEmpty()) {
                            setLevel(tile, originalLevel);
                            closeOpenGUIs(tile);
                        }
                        return result;
                    }
                } else {
                    // fallback：augments 数组不可直接访问,通过 readAugmentsFromNBT
                    PasteResult result = applyAugmentsViaNBT(tile, neededAugments, player);
                    if (result != PasteResult.SUCCESS) {
                        if (!levelKits.isEmpty()) {
                            setLevel(tile, originalLevel);
                            closeOpenGUIs(tile);
                        }
                        return result;
                    }
                }
            }

            // ===== 阶段 5：readFromNBT 恢复其余配置(不含 Augments/Level) =====
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());
            for (String key : CONFIG_KEYS) {
                if (!key.equals("Level") && data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }
            // 移除 Augments,因为已通过 IUpgradeProvider 处理,避免 readFromNBT 双写
            currentNbt.removeTag("Augments");
            tile.readFromNBT(currentNbt);

            invokeIfExists(tile, "updateAugmentStatus");

            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(), tile.getWorld().getBlockState(tile.getPos()), tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste TE machine config", e);
            return PasteResult.FAILED;
        }
    }

    /**
     * 当 augments[] 字段不可直接访问时,通过构造 NBT 调用 readAugmentsFromNBT.
     */
    private PasteResult applyAugmentsViaNBT(TileEntity tile, List<ItemStack> neededAugments, EntityPlayer player) throws Exception {
        // 先消耗
        for (ItemStack needed : neededAugments) {
            MemoryCardUpgradeHelper.consumeFromInventory(player, needed);
        }
        // 构造 NBT
        NBTTagList augmentList = new NBTTagList();
        for (int i = 0; i < neededAugments.size(); i++) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", i);
            neededAugments.get(i).writeToNBT(tag);
            augmentList.appendTag(tag);
        }
        NBTTagCompound augmentNbt = new NBTTagCompound();
        augmentNbt.setTag("Augments", augmentList);
        Method readAugments = ReflectionHelper.findMethodInHierarchy(tile.getClass(), "readAugmentsFromNBT", NBTTagCompound.class);
        if (readAugments != null) {
            readAugments.invoke(tile, augmentNbt);
        }
        return PasteResult.SUCCESS;
    }

    private List<ItemStack> getLevelKits(int currentLevel, int targetLevel) {
        List<ItemStack> result = new ArrayList<>();
        if (targetLevel <= currentLevel) return result;
        initUpgradeKits();

        int fullKitIndex = targetLevel - 1;
        if (UPGRADE_FULL != null && fullKitIndex >= 0 && fullKitIndex < UPGRADE_FULL.length) {
            ItemStack fullKit = UPGRADE_FULL[fullKitIndex];
            if (fullKit != null && !fullKit.isEmpty()) {
                result.add(fullKit.copy());
                return result;
            }
        }

        if (UPGRADE_INCREMENTAL != null) {
            for (int i = currentLevel; i < targetLevel; i++) {
                if (i < 0 || i >= UPGRADE_INCREMENTAL.length) continue;
                ItemStack incKit = UPGRADE_INCREMENTAL[i];
                if (incKit != null && !incKit.isEmpty()) {
                    result.add(incKit.copy());
                } else {
                    result.add(ItemStack.EMPTY);
                }
            }
        }
        return result;
    }

    private void initUpgradeKits() {
        if (UPGRADE_INCREMENTAL != null) return;
        try {
            Class<?> itemUpgradeClass = Class.forName("cofh.thermalfoundation.item.ItemUpgrade");
            java.lang.reflect.Field incField = itemUpgradeClass.getDeclaredField("upgradeIncremental");
            incField.setAccessible(true);
            java.lang.reflect.Field fullField = itemUpgradeClass.getDeclaredField("upgradeFull");
            fullField.setAccessible(true);
            UPGRADE_INCREMENTAL = (ItemStack[]) incField.get(null);
            UPGRADE_FULL = (ItemStack[]) fullField.get(null);
            AE2Enhanced.LOGGER.debug("[AE2E] Loaded TE upgrade kits: incremental={}, full={}",
                    UPGRADE_INCREMENTAL != null ? UPGRADE_INCREMENTAL.length : "null",
                    UPGRADE_FULL != null ? UPGRADE_FULL.length : "null");
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Could not load Thermal Foundation upgrade kits", e);
        }
    }

    private int getCurrentLevel(TileEntity tile) {
        try {
            Field levelField = ReflectionHelper.findFieldInHierarchy(tile.getClass(), "level");
            if (levelField != null) {
                return levelField.getInt(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not get TE level", e);
        }
        return 0;
    }

    private boolean setLevel(TileEntity tile, int level) {
        try {
            Method setLevel = ReflectionHelper.findMethodInHierarchy(tile.getClass(), "setLevel", int.class);
            if (setLevel != null) {
                Object result = setLevel.invoke(tile, level);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
                return true;
            }
            Field levelField = ReflectionHelper.findFieldInHierarchy(tile.getClass(), "level");
            if (levelField != null) {
                levelField.setInt(tile, level);
                return true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Could not set TE level", e);
        }
        return false;
    }

    private List<ItemStack> parseAugmentList(NBTTagList sourceAugments) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < sourceAugments.tagCount(); i++) {
            NBTTagCompound tag = sourceAugments.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    private ItemStack[] getAugmentsArray(TileEntity tile) {
        Class<?> current = tile.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals("augments") && field.getType().isArray()
                        && field.getType().getComponentType() == ItemStack.class) {
                    try {
                        field.setAccessible(true);
                        return (ItemStack[]) field.get(tile);
                    } catch (IllegalAccessException e) {
                        return null;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void invokeIfExists(TileEntity tile, String methodName) {
        try {
            Method method = ReflectionHelper.findMethodInHierarchy(tile.getClass(), methodName);
            if (method != null) {
                method.invoke(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not invoke {} on {}", methodName, tile.getClass().getName());
        }
    }

    private int getNumAugmentSlots(TileEntity tile, int level) {
        try {
            Method getNumAugmentSlots = ReflectionHelper.findMethodInHierarchy(tile.getClass(), "getNumAugmentSlots", int.class);
            if (getNumAugmentSlots != null) {
                return (int) getNumAugmentSlots.invoke(tile, level);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not get TE augment slot count", e);
        }
        return 0;
    }

    private void closeOpenGUIs(TileEntity tile) {
        if (tile.getWorld() == null) return;
        for (EntityPlayer p : tile.getWorld().playerEntities) {
            if (p.openContainer == null || p.openContainer == p.inventoryContainer) continue;
            for (Object slotObj : p.openContainer.inventorySlots) {
                if (slotObj instanceof net.minecraft.inventory.Slot) {
                    net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) slotObj;
                    if (slot.inventory == tile) {
                        p.closeScreen();
                        break;
                    }
                    try {
                        java.lang.reflect.Method m = slot.getClass().getMethod("getItemHandler");
                        Object handler = m.invoke(slot);
                        if (handler == tile) {
                            p.closeScreen();
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private boolean isCompatible(String sourceType, String targetType) {
        if (sourceType.equals(targetType)) return true;
        String sourceCategory = extractCategory(sourceType);
        String targetCategory = extractCategory(targetType);
        return sourceCategory != null && sourceCategory.equals(targetCategory);
    }

    private String extractCategory(String className) {
        if (className.contains(".block.machine.")) return "machine";
        if (className.contains(".block.dynamo.")) return "dynamo";
        if (className.contains(".block.device.")) return "device";
        if (className.contains(".block.apparatus.")) return "apparatus";
        return null;
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
