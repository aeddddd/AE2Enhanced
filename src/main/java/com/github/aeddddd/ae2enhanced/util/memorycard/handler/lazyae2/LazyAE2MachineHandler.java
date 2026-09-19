package com.github.aeddddd.ae2enhanced.util.memorycard.handler.lazyae2;

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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Lazy AE2(modid {@code threng}, libnine 框架)设备的配置复制粘贴 Handler.
 *
 * <p>libnine 通过 {@code @AutoSerialize} 自动序列化,NBT 键名 = 字段名首字母大写
 * (如 {@code FrontFace}),大小写敏感.支持的设备:</p>
 * <ul>
 *   <li>{@code TileAggregator}/{@code TileCentrifuge}/{@code TileEtcher}/{@code TileEnergizer}
 *       - 加工机; 配置: {@code FrontFace}(朝向,short 枚举 ordinal) + {@code Sides}(面 IO 配置)
 *       + {@code AutoExporting}(自动导出) + {@code SlotUpgrade}(AE2 加速卡,真实物品)</li>
 *   <li>{@code TileFastCraftingBus} - 快速合成总线; 配置: {@code FrontFace} + {@code Sides}
 *       (样板栏 {@code PatternInventory} 属内容物,不复制)</li>
 *   <li>{@code TileLevelMaintainer} - 电平维持器; 配置: {@code FrontFace} + {@code Requests}
 *       (维持物品/目标数量/批量,核心配置)</li>
 *   <li>{@code TileBigAssembler*} - 大型组装机; 无可复制配置(仅作类型校验)</li>
 * </ul>
 *
 * <p>必须排除: {@code aeproxy}(AE2 节点)、{@code Energy}、{@code Work}、{@code Active}、
 * {@code MultiBlock}、{@code Crafter}(进行中的合成链接)、{@code Results}、{@code JobQueue}
 * 及各库存键.该 mod 机器无红石模式配置.</p>
 *
 * <p>电平维持器粘贴 {@code Requests} 后需反射调用私有 {@code resetWatcher()}
 * 重建存量监控,否则 watcher 仍按旧配置推送.</p>
 *
 * <p>实现仅操作 NBT + 反射,不直接引用 Lazy AE2 类,满足反射隔离约定.</p>
 */
public class LazyAE2MachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    private static final String CLASS_PREFIX = "io.github.phantamanta44.threng.tile.";

    /** 配置键(存在才复制). */
    private static final String[] CONFIG_KEYS = {
            "FrontFace", "Sides", "AutoExporting", "Requests"
    };

    static {
        AVAILABLE = Loader.isModLoaded("threng");
    }

    @Override
    public boolean canHandle(Object target) {
        return AVAILABLE && target instanceof TileEntity
                && target.getClass().getName().startsWith(CLASS_PREFIX);
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

            // 加速卡(单槽 {Item: {...}}) → 标准 ae2e:upgrades 列表
            NBTTagCompound upgrade = readSlotItem(fullNbt, "SlotUpgrade");
            if (upgrade != null) {
                NBTTagCompound entry = upgrade.copy();
                entry.setInteger("Slot", 0);
                NBTTagList list = new NBTTagList();
                list.appendTag(entry);
                output.setTag("ae2e:upgrades", list);
            }

            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy Lazy AE2 device config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());

            // 类型校验:要求完全同类(机器类型由 tile 类决定,与方块 meta 对应)
            if (data.hasKey("dataType") && !data.getString("dataType").equals(target.getClass().getName())) {
                return PasteResult.INVALID_MACHINE;
            }

            // ===== 加速卡(单槽 SlotUpgrade) =====
            if (data.hasKey("ae2e:upgrades")) {
                NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
                List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgradeList);
                if (!needed.isEmpty()) {
                    PasteResult r = applyUpgradeSlot(player, currentNbt,
                            upgradeList.getCompoundTagAt(0), needed.get(0));
                    if (r != PasteResult.SUCCESS) return r;
                }
            }

            // ===== 通用配置 =====
            boolean requestsApplied = false;
            for (String key : CONFIG_KEYS) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                    if ("Requests".equals(key)) requestsApplied = true;
                }
            }

            tile.readFromNBT(currentNbt);
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            // 电平维持器:Requests 变更后需重建存量监控(deserNBT 不会自行重置 watcher)
            if (requestsApplied && "TileLevelMaintainer".equals(tile.getClass().getSimpleName())) {
                resetWatcher(tile);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste Lazy AE2 device config", e);
            return PasteResult.FAILED;
        }
    }

    /**
     * 读取 libnine 单槽(L9AspectSlot)中的物品,空槽返回 null.
     * 格式: {@code slotKey: {Item: {...}}} 或 {@code slotKey: {Empty: 1b}}.
     */
    private static NBTTagCompound readSlotItem(NBTTagCompound fullNbt, String slotKey) {
        if (!fullNbt.hasKey(slotKey)) return null;
        NBTTagCompound slot = fullNbt.getCompoundTag(slotKey);
        if (!slot.hasKey("Item")) return null;
        NBTTagCompound stackNbt = slot.getCompoundTag("Item");
        ItemStack stack = new ItemStack(stackNbt);
        return stack.isEmpty() ? null : stackNbt.copy();
    }

    /**
     * 向 SlotUpgrade 单槽写入加速卡:验证可用性(背包/绑定网络) → 弹出旧卡 → 消耗 → 写入.
     */
    private PasteResult applyUpgradeSlot(EntityPlayer player, NBTTagCompound currentNbt,
                                         NBTTagCompound stackNbt, ItemStack required) {
        if (required.isEmpty()) return PasteResult.SUCCESS;
        if (!MemoryCardUpgradeHelper.ensureAvailable(player, Collections.singletonList(required))) {
            return PasteResult.MISSING_UPGRADES;
        }
        // 弹出旧加速卡
        NBTTagCompound old = readSlotItem(currentNbt, "SlotUpgrade");
        if (old != null) {
            ItemStack oldStack = new ItemStack(old);
            if (!oldStack.isEmpty() && !player.addItemStackToInventory(oldStack)) {
                player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, oldStack));
            }
        }
        MemoryCardUpgradeHelper.consumeFromInventory(player, required);
        NBTTagCompound entry = stackNbt.copy();
        entry.removeTag("Slot");
        NBTTagCompound slot = new NBTTagCompound();
        slot.setTag("Item", entry);
        currentNbt.setTag("SlotUpgrade", slot);
        return PasteResult.SUCCESS;
    }

    /**
     * 反射调用电平维持器的私有 resetWatcher(),强制按新 Requests 重挂 IStackWatcher.
     */
    private void resetWatcher(TileEntity tile) {
        try {
            Method m = ReflectionHelper.findMethodInHierarchy(tile.getClass(), "resetWatcher");
            if (m != null) {
                m.invoke(tile);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not reset level maintainer watcher for {}", tile.getClass().getName());
        }
    }

    // ===== 键分类声明(粘贴选项过滤用) =====

    @Override
    public java.util.Set<String> getUpgradeKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("ae2e:upgrades"));
    }

    @Override
    public java.util.Set<String> getSideKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("Sides"));
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
