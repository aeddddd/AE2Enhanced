package com.github.aeddddd.ae2enhanced.util.memorycard.handler.extrautils2;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
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
import java.util.Collections;
import java.util.List;

/**
 * Extra Utilities 2 设备的配置复制粘贴 Handler.
 *
 * <p>ExU2 设备通过 XUTile 的 registerNBT 机制序列化.支持的设备:</p>
 * <ul>
 *   <li>{@code TileMachineReceiver/TileMachineProvider} - 全部机器
 *       (机器类型由 NBT {@code Type} 字符串决定,如 extrautils2:generator,与方块 meta 无关);
 *       配置: {@code redstone}(short 枚举) + {@code upgrades}(单槽速度升级)</li>
 *   <li>{@code TileUse} - 机械用户; 配置: {@code mode}/{@code button}/{@code select}/
 *       {@code sneak}/{@code redstone} + {@code upgrades}</li>
 *   <li>{@code TileTrashCan} - 垃圾桶; 配置: {@code filter}(单槽过滤器物品)</li>
 *   <li>{@code TileResonator} - 谐振腔; 配置: {@code upgrades}</li>
 *   <li>{@code TilePlayerChest} - 玩家箱子; 配置: {@code InsertSlots}/{@code ExtractSlots}</li>
 * </ul>
 *
 * <p>升级/过滤器槽为 SingleStackHandler 格式(物品 NBT 平铺 + 可选 ExtendedCount),
 * 粘贴时按升级物品流程消耗.粘贴后通过反射重置速度缓存(speed/recalc)、
 * 刷新 GP 功耗(PowerManager.markDirty)并触发方块更新(markForUpdate).</p>
 *
 * <p>实现仅操作 NBT + 反射,不直接引用 ExU2 类,满足反射隔离约定.</p>
 */
public class ExU2MachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    private static final String[] SUPPORTED_CLASSES = {
            "com.rwtema.extrautils2.machine.TileMachine",
            "com.rwtema.extrautils2.tile.TileUse",
            "com.rwtema.extrautils2.tile.TileTrashCan",
            "com.rwtema.extrautils2.tile.TileResonator",
            "com.rwtema.extrautils2.tile.TilePlayerChest"
    };

    /** 通用配置键(存在才复制,均为标量,与物品内容无关). */
    private static final String[] CONFIG_KEYS = {
            "redstone", "mode", "button", "select", "sneak",
            "InsertSlots", "ExtractSlots"
    };

    static {
        AVAILABLE = Loader.isModLoaded("extrautils2");
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) return false;
        String className = target.getClass().getName();
        for (String prefix : SUPPORTED_CLASSES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
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

            // 升级(单槽 SingleStackHandler) → 标准 ae2e:upgrades 列表
            NBTTagCompound upgrade = readSingleStack(fullNbt, "upgrades");
            if (upgrade != null) {
                NBTTagCompound entry = upgrade.copy();
                entry.setInteger("Slot", 0);
                NBTTagList list = new NBTTagList();
                list.appendTag(entry);
                output.setTag("ae2e:upgrades", list);
            }

            // 过滤器(垃圾桶等,单槽) → 独立键,粘贴时同样按消耗品处理
            NBTTagCompound filter = readSingleStack(fullNbt, "filter");
            if (filter != null) {
                output.setTag("xu2:filter", filter);
            }

            // 机器类型(extrautils2:generator 等),用于粘贴时的类型校验
            if (fullNbt.hasKey("Type")) {
                output.setString("xu2:type", fullNbt.getString("Type"));
            }
            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy ExU2 device config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());

            // 类型校验:机器要求同 Type(同种机器),其余设备要求同类
            if (data.hasKey("xu2:type")) {
                String sourceType = data.getString("xu2:type");
                String targetType = currentNbt.hasKey("Type") ? currentNbt.getString("Type") : "";
                if (!sourceType.equals(targetType)) {
                    return PasteResult.INVALID_MACHINE;
                }
            }
            if (data.hasKey("dataType") && !data.getString("dataType").equals(target.getClass().getName())) {
                return PasteResult.INVALID_MACHINE;
            }

            // ===== 升级(单槽) =====
            if (data.hasKey("ae2e:upgrades")) {
                NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
                List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgradeList);
                if (!needed.isEmpty()) {
                    PasteResult r = applySingleStackSlot(player, currentNbt, "upgrades",
                            upgradeList.getCompoundTagAt(0), needed.get(0));
                    if (r != PasteResult.SUCCESS) return r;
                }
            }

            // ===== 过滤器(单槽) =====
            if (data.hasKey("xu2:filter")) {
                NBTTagCompound filterNbt = data.getCompoundTag("xu2:filter");
                PasteResult r = applySingleStackSlot(player, currentNbt, "filter",
                        filterNbt, new ItemStack(filterNbt));
                if (r != PasteResult.SUCCESS) return r;
            }

            // ===== 通用配置 =====
            for (String key : CONFIG_KEYS) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }

            tile.readFromNBT(currentNbt);
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()), 3);
            }
            postPasteRefresh(tile);

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste ExU2 device config", e);
            return PasteResult.FAILED;
        }
    }

    /**
     * 读取单槽 handler(SingleStackHandler)中的物品,空槽返回 null.
     */
    private static NBTTagCompound readSingleStack(NBTTagCompound fullNbt, String key) {
        if (!fullNbt.hasKey(key)) return null;
        NBTTagCompound stackNbt = fullNbt.getCompoundTag(key);
        if (!stackNbt.hasKey("id")) return null;
        ItemStack stack = new ItemStack(stackNbt);
        return stack.isEmpty() ? null : stackNbt.copy();
    }

    /**
     * 向单槽 handler 写入物品:验证可用性(背包/ME 网络) → 弹出旧物品 → 消耗 → 写入.
     * 保留原始条目中的 ExtendedCount 等字段.
     */
    private PasteResult applySingleStackSlot(EntityPlayer player, NBTTagCompound currentNbt,
                                             String slotKey, NBTTagCompound stackNbt, ItemStack required) {
        if (required.isEmpty()) return PasteResult.SUCCESS;
        if (!MemoryCardUpgradeHelper.ensureAvailable(player, Collections.singletonList(required))) {
            return PasteResult.MISSING_UPGRADES;
        }
        // 弹出旧物品
        if (currentNbt.hasKey(slotKey)) {
            ItemStack old = new ItemStack(currentNbt.getCompoundTag(slotKey));
            if (!old.isEmpty() && !player.addItemStackToInventory(old)) {
                player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, old));
            }
        }
        MemoryCardUpgradeHelper.consumeFromInventory(player, required);
        NBTTagCompound entry = stackNbt.copy();
        entry.removeTag("Slot");
        currentNbt.setTag(slotKey, entry);
        return PasteResult.SUCCESS;
    }

    /**
     * 粘贴后刷新:重置速度缓存、刷新 GP 功耗、触发同步与配方重算.
     * 全部为尽力而为的反射调用,失败不影响主流程.
     */
    private void postPasteRefresh(TileEntity tile) {
        // TileMachine: speed=-1 / recalc=true 使下一 tick 重算升级效果
        try {
            Field speed = ReflectionHelper.findFieldInHierarchy(tile.getClass(), "speed");
            if (speed != null && speed.getType() == float.class) {
                speed.setFloat(tile, -1.0F);
            }
            Field recalc = ReflectionHelper.findFieldInHierarchy(tile.getClass(), "recalc");
            if (recalc != null && recalc.getType() == boolean.class) {
                recalc.setBoolean(tile, true);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not reset ExU2 speed cache for {}", tile.getClass().getName());
        }

        // XUTile.markForUpdate(): 客户端同步
        try {
            Method markForUpdate = ReflectionHelper.findMethodInHierarchy(tile.getClass(), "markForUpdate");
            if (markForUpdate != null) {
                markForUpdate.invoke(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not invoke markForUpdate on {}", tile.getClass().getName());
        }

        // TileResonator.onInputChanged(): 配方缓存刷新
        try {
            Method onInputChanged = ReflectionHelper.findMethodInHierarchy(tile.getClass(), "onInputChanged");
            if (onInputChanged != null) {
                onInputChanged.invoke(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not invoke onInputChanged on {}", tile.getClass().getName());
        }

        // PowerManager.instance.markDirty(tile): GP 功耗重算(SingleStackHandler 不触发回调)
        try {
            Class<?> pmClass = Class.forName("com.rwtema.extrautils2.power.PowerManager");
            Object instance = pmClass.getDeclaredField("instance").get(null);
            for (Method m : pmClass.getMethods()) {
                if (m.getName().equals("markDirty") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(tile)) {
                    m.invoke(instance, tile);
                    break;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not mark GP power dirty for {}", tile.getClass().getName());
        }
    }

    // ===== 键分类声明(粘贴选项过滤用) =====

    @Override
    public java.util.Set<String> getUpgradeKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("ae2e:upgrades", "xu2:filter", "upgrades"));
    }

    @Override
    public java.util.Set<String> getRedstoneKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("redstone"));
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            TileEntity tile = (TileEntity) target;
            // 机器方块所有类型共用同一个本地化方块名,尝试用 Type 细分
            try {
                NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
                if (nbt.hasKey("Type")) {
                    String type = nbt.getString("Type");
                    String base = tile.getBlockType().getLocalizedName();
                    return base + " (" + type.replace("extrautils2:", "") + ")";
                }
            } catch (Exception ignored) {
            }
            return tile.getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
