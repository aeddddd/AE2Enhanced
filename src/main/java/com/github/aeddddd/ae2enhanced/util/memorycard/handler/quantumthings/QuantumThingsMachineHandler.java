package com.github.aeddddd.ae2enhanced.util.memorycard.handler.quantumthings;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Quantum Things(Random Things 非官方续作,modid 沿用 randomthings)设备的
 * 配置复制粘贴 Handler.
 *
 * <p>QT 设备(lumien.randomthings.tileentity.*)通过 writeDataToNBT 将配置键
 * 直接写在 NBT 根节点.本 handler 按白名单复制各设备的可调参数:</p>
 * <ul>
 *   <li>{@code rangeX/rangeY/rangeZ} - 工作范围(收集器/检测器等)</li>
 *   <li>{@code invert} - 红石输出反转(实体检测器)</li>
 *   <li>{@code filter} - 过滤类型枚举 ordinal(实体检测器)</li>
 *   <li>{@code powerMode} - 红石输出模式(实体检测器)</li>
 *   <li>{@code emitLevel} - 输出强度(模拟红石发射器)</li>
 *   <li>{@code mode} - 点火模式(点火器 Igniter: TOGGLE/IGNITE/KEEP_IGNITED)</li>
 * </ul>
 *
 * <p>过滤器(物品/实体过滤器)存放在设备的 {@code inventory.slot0} 槽位中,
 * 过滤配置位于过滤器物品自身的 NBT 上.粘贴规则:目标槽已有同类过滤器时
 * 直接改写其 NBT(不消耗物品);否则按升级物品流程消耗一个配置相同的过滤器.</p>
 *
 * <p>点火器粘贴 KEEP_IGNITED 后需要一次邻接更新才会点火,paste 末尾统一触发.
 * 实现全程仅操作 NBT,不引用任何 QT 类,满足反射隔离约定.</p>
 */
public class QuantumThingsMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    /** 可复制的配置键白名单(运行时状态键如 powered/mining/canMine 不复制). */
    private static final Set<String> CONFIG_KEYS = new HashSet<>(Arrays.asList(
            "rangeX", "rangeY", "rangeZ",
            "invert", "filter", "powerMode", "powerLevel", "emitLevel",
            "mode"
    ));

    static {
        AVAILABLE = Loader.isModLoaded("randomthings");
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) return false;
        return target.getClass().getName().startsWith("lumien.randomthings.tileentity.");
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

            // 过滤器:inventory 内 slot0 的过滤器物品(含其配置 NBT)
            NBTTagCompound filterSlot = readFilterSlot(fullNbt);
            if (filterSlot != null) {
                output.setTag("qt:filter", filterSlot);
            }

            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy QuantumThings device config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            // QT 各设备配置键含义不同,要求完全同类
            if (data.hasKey("dataType") && !data.getString("dataType").equals(target.getClass().getName())) {
                return PasteResult.INVALID_MACHINE;
            }

            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());
            boolean applied = false;
            for (String key : CONFIG_KEYS) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                    applied = true;
                }
            }

            // ===== 过滤器 =====
            if (data.hasKey("qt:filter")) {
                PasteResult filterResult = applyFilter(tile, currentNbt, data.getCompoundTag("qt:filter"), player);
                if (filterResult != PasteResult.SUCCESS) {
                    return filterResult;
                }
                applied = true;
            }

            if (!applied) {
                return PasteResult.INVALID_MACHINE;
            }

            tile.readFromNBT(currentNbt);
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()), 3);
                // 点火器 KEEP_IGNITED 等模式依赖邻接更新触发
                tile.getWorld().notifyNeighborsOfStateChange(tile.getPos(), tile.getBlockType(), false);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste QuantumThings device config", e);
            return PasteResult.FAILED;
        }
    }

    /**
     * 读取 inventory.slot0 中的过滤器物品 NBT,无过滤器返回 null.
     */
    private static NBTTagCompound readFilterSlot(NBTTagCompound fullNbt) {
        if (!fullNbt.hasKey("inventory")) return null;
        NBTTagCompound inv = fullNbt.getCompoundTag("inventory");
        if (!inv.hasKey("slot0")) return null;
        NBTTagCompound slot0 = inv.getCompoundTag("slot0");
        // InventoryUtil 格式:空槽标记 empty=true
        if (!slot0.hasKey("id")) return null;
        ItemStack stack = new ItemStack(slot0);
        if (stack.isEmpty()) return null;
        return slot0.copy();
    }

    /**
     * 将过滤器配置应用到目标设备的 slot0.
     * 目标槽已有同类过滤器 → 直接改写其 NBT(不消耗);
     * 否则消耗一个配置相同的过滤器物品(背包或 ME 网络).
     */
    private PasteResult applyFilter(TileEntity tile, NBTTagCompound currentNbt,
                                    NBTTagCompound filterNbt, EntityPlayer player) {
        ItemStack filterStack = new ItemStack(filterNbt);
        if (filterStack.isEmpty()) return PasteResult.SUCCESS;

        NBTTagCompound inv = currentNbt.hasKey("inventory")
                ? currentNbt.getCompoundTag("inventory") : new NBTTagCompound();
        NBTTagCompound slot0 = inv.getCompoundTag("slot0");
        ItemStack current = slot0.hasKey("id") ? new ItemStack(slot0) : ItemStack.EMPTY;

        if (!current.isEmpty() && current.getItem() == filterStack.getItem()) {
            // 同类过滤器:仅改写配置 NBT
            ItemStack rewritten = current.copy();
            rewritten.setTagCompound(filterStack.hasTagCompound() ? filterStack.getTagCompound().copy() : null);
            NBTTagCompound newSlot = new NBTTagCompound();
            rewritten.writeToNBT(newSlot);
            inv.setTag("slot0", newSlot);
            currentNbt.setTag("inventory", inv);
            return PasteResult.SUCCESS;
        }

        // 需要消耗一个配置相同的过滤器(NBT 精确匹配)
        if (!MemoryCardUpgradeHelper.ensureAvailable(player, Collections.singletonList(filterStack))) {
            return PasteResult.MISSING_UPGRADES;
        }
        // 弹出旧过滤器/占用物品
        if (!current.isEmpty() && !player.addItemStackToInventory(current)) {
            player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, current));
        }
        MemoryCardUpgradeHelper.consumeFromInventory(player, filterStack);
        NBTTagCompound newSlot = new NBTTagCompound();
        filterStack.writeToNBT(newSlot);
        inv.setTag("slot0", newSlot);
        currentNbt.setTag("inventory", inv);
        return PasteResult.SUCCESS;
    }

    // ===== 键分类声明(粘贴选项过滤用) =====

    @Override
    public java.util.Set<String> getUpgradeKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("qt:filter"));
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
