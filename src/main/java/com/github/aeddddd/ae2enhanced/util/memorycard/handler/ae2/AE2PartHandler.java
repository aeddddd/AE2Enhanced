package com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemHandlerUpgradeAdapter;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;

import appeng.helpers.IPriorityHost;
import appeng.api.parts.PartItemStack;
import appeng.me.cache.P2PCache;
import appeng.parts.AEBasePart;
import appeng.parts.misc.PartOreDicStorageBus;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.parts.reporting.AbstractPartEncoder;
import appeng.parts.reporting.AbstractPartMonitor;
import appeng.parts.reporting.AbstractPartReporting;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalBusBase;
import com.github.aeddddd.ae2enhanced.util.reflection.ReflectionHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 处理 AE2 Part 的配置复制粘贴.
 * 不依赖反射调用 AEBasePart.downloadSettings,直接复制其基础逻辑并扩展本 mod Part 的自定义状态.
 *
 * <p>通用路径(configManager/priority/config 库存/升级槽)之外,以下面板有独立持久化配置,
 * 经 writeToNBT 白名单提取并在粘贴时走各自的专用写入路径:</p>
 * <ul>
 *   <li>{@code PartP2PTunnel}: freq/output(频率绑定必须经 P2PCache 注册,不能直写字段)</li>
 *   <li>{@code AbstractPartMonitor}(存储/转换显示器): configuredItem/configuredFluid/isLocked</li>
 *   <li>{@code PartOreDicStorageBus}: oreMatch(须经 saveOreMatch 重建过滤缓存)</li>
 *   <li>{@code AbstractPartEncoder}(样板终端): craftingMode/substitute</li>
 *   <li>{@code AbstractPartReporting}: spin(扳手旋转的显示朝向)</li>
 * </ul>
 */
public class AE2PartHandler implements IMemoryCardHandler {

    /** 通用路径不覆盖的面板自定义配置键(经 writeToNBT 提取). */
    private static final String[] EXTRA_CONFIG_KEYS = {
            "configuredItem", "configuredFluid", "isLocked",
            "freq", "output",
            "oreMatch",
            "craftingMode", "substitute",
            "spin"
    };

    @Override
    public boolean canHandle(Object target) {
        return target instanceof AEBasePart;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        AEBasePart part = (AEBasePart) target;
        NBTTagCompound output = new NBTTagCompound();

        // 1. 基础配置：IConfigManager
        if (part.getConfigManager() != null) {
            part.getConfigManager().writeToNBT(output);
        }

        // 2. 基础配置：IPriorityHost
        if (part instanceof IPriorityHost) {
            output.setInteger("priority", ((IPriorityHost) part).getPriority());
        }

        // 3. 基础配置：config inventory
        IItemHandler configInv = part.getInventoryByName("config");
        if (configInv instanceof AppEngInternalAEInventory) {
            ((AppEngInternalAEInventory) configInv).writeToNBT(output, "config");
        }

        // 4. 本 mod Part 的自定义状态
        if (part instanceof PartStockingBus) {
            PartStockingBus stocking = (PartStockingBus) part;
            output.setInteger("stockingMode", stocking.getMode().ordinal());
            for (int i = 0; i < 9; i++) {
                output.setLong("targetAmount_" + i, stocking.getTargetAmount(i));
            }
        }
        if (part instanceof PartUniversalBusBase) {
            PartUniversalBusBase bus = (PartUniversalBusBase) part;
            output.setInteger("busMode", bus.getBusMode().ordinal());
            output.setInteger("roundRobinIndex", bus.getRoundRobinIndex());
        }

        // 5. 额外复制升级槽
        try {
            IItemHandler upgrades = part.getInventoryByName("upgrades");
            if (upgrades != null && upgrades.getSlots() > 0) {
                NBTTagList upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(
                        new ItemHandlerUpgradeAdapter(upgrades));
                if (!upgradeList.isEmpty()) {
                    output.setTag("ae2e:upgrades", upgradeList);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy upgrades for {}", part.getClass().getName(), e);
        }

        // 6. 通用路径不覆盖的面板自定义配置(经 writeToNBT 提取白名单键)
        try {
            NBTTagCompound partNbt = new NBTTagCompound();
            part.writeToNBT(partNbt);
            for (String key : EXTRA_CONFIG_KEYS) {
                if (partNbt.hasKey(key)) {
                    output.setTag(key, partNbt.getTag(key));
                }
            }
            // 类型标识(P2P 粘贴时要求同类型)
            output.setString("dataType", part.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy extra config for {}", part.getClass().getName(), e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        AEBasePart part = (AEBasePart) target;

        // 1. 先处理升级
        if (data.hasKey("ae2e:upgrades")) {
            NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
            IItemHandler upgrades = part.getInventoryByName("upgrades");
            if (upgrades != null) {
                List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgradeList);
                PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(
                        new ItemHandlerUpgradeAdapter(upgrades), needed, player);
                if (result != PasteResult.SUCCESS) return result;
            }
        }

        // 2. 用不含升级的 NBT 应用配置
        NBTTagCompound settings = data.copy();
        settings.removeTag("ae2e:upgrades");
        part.uploadSettings(SettingsFrom.MEMORY_CARD, settings, player);

        // 3. 通用路径之外的面板自定义配置
        // P2P 隧道:频率绑定必须经 P2PCache 注册,且要求同类型隧道
        if (part instanceof PartP2PTunnel && data.hasKey("freq")) {
            return pasteP2PTunnel((PartP2PTunnel) part, data);
        }
        if (part instanceof AbstractPartMonitor) {
            pasteMonitor(part, data);
        }
        if (part instanceof PartOreDicStorageBus && data.hasKey("oreMatch")) {
            // saveOreMatch 内部重新解析表达式并重建过滤缓存
            ((PartOreDicStorageBus) part).saveOreMatch(data.getString("oreMatch"));
        }
        if (part instanceof AbstractPartEncoder) {
            AbstractPartEncoder encoder = (AbstractPartEncoder) part;
            if (data.hasKey("craftingMode")) encoder.setCraftingRecipe(data.getBoolean("craftingMode"));
            if (data.hasKey("substitute")) encoder.setSubstitution(data.getBoolean("substitute"));
        }
        if (part instanceof AbstractPartReporting && data.hasKey("spin")) {
            pasteSpin(part, data.getByte("spin"));
        }

        return PasteResult.SUCCESS;
    }

    /**
     * P2P 隧道粘贴:注销旧频率 → 切换输入/输出端 → 注册新频率 → 刷新网络与邻居.
     * 频率经 {@link P2PCache#updateFreq} 注册进网格缓存,直写字段不生效.
     */
    private PasteResult pasteP2PTunnel(PartP2PTunnel tunnel, NBTTagCompound data) {
        // 跨类型粘贴(如物品 P2P → 流体 P2P)会错注册频率,要求同类型
        if (data.hasKey("dataType") && !data.getString("dataType").equals(tunnel.getClass().getName())) {
            return PasteResult.INVALID_MACHINE;
        }
        try {
            short oldFreq = tunnel.getFrequency();
            short newFreq = data.getShort("freq");
            boolean newOutput = data.hasKey("output") && data.getBoolean("output");

            P2PCache p2p = tunnel.getProxy().getP2P();
            p2p.removeTunnel(tunnel, oldFreq);
            // setOutput 为包私有,反射调用
            Method setOutput = PartP2PTunnel.class.getDeclaredMethod("setOutput", boolean.class);
            setOutput.setAccessible(true);
            setOutput.invoke(tunnel, newOutput);
            p2p.updateFreq(tunnel, newFreq);
            tunnel.onTunnelNetworkChange();
            tunnel.onTunnelConfigChange();
            if (tunnel.getHost() != null && tunnel.getHost().getTile() != null) {
                World world = tunnel.getHost().getTile().getWorld();
                if (world != null) {
                    // 红石/光 P2P 依赖邻居刷新
                    Platform.notifyBlocksOfNeighbors(world, tunnel.getHost().getTile().getPos());
                }
            }
            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste P2P tunnel config", e);
            return PasteResult.FAILED;
        }
    }

    /**
     * 显示器粘贴:写入锁定物品/流体与锁定状态,并重建存量 watcher.
     * 网格未就绪时 configureWatchers 失败无害(连网时 updateWatcher 会重新配置).
     */
    private void pasteMonitor(AEBasePart part, NBTTagCompound data) {
        try {
            Class<?> clazz = part.getClass();
            if (data.hasKey("configuredItem")) {
                NBTTagCompound tag = data.getCompoundTag("configuredItem");
                appeng.api.storage.data.IAEItemStack stack = tag.getKeySet().isEmpty() ? null : AEItemStack.fromNBT(tag);
                if (stack != null) stack.setStackSize(0); // 数量是运行时从网络刷新的
                Field f = ReflectionHelper.findFieldInHierarchy(clazz, "configuredItem");
                if (f != null) f.set(part, stack);
            }
            if (data.hasKey("configuredFluid")) {
                NBTTagCompound tag = data.getCompoundTag("configuredFluid");
                appeng.api.storage.data.IAEFluidStack stack = tag.getKeySet().isEmpty() ? null : AEFluidStack.fromNBT(tag);
                if (stack != null) stack.setStackSize(0);
                Field f = ReflectionHelper.findFieldInHierarchy(clazz, "configuredFluid");
                if (f != null) f.set(part, stack);
            }
            if (data.hasKey("isLocked")) {
                Field f = ReflectionHelper.findFieldInHierarchy(clazz, "isLocked");
                if (f != null) f.setBoolean(part, data.getBoolean("isLocked"));
            }
            // configureWatchers 为私有方法
            Method configureWatchers = ReflectionHelper.findMethodInHierarchy(clazz, "configureWatchers");
            if (configureWatchers != null) configureWatchers.invoke(part);
            if (part.getHost() != null) {
                part.getHost().markForUpdate();
                part.getHost().markForSave();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not fully apply monitor config for {}", part.getClass().getName());
        }
    }

    /**
     * 面板显示朝向粘贴:spin 为私有字段,写入后刷新渲染.
     */
    private void pasteSpin(AEBasePart part, byte spin) {
        try {
            Field f = ReflectionHelper.findFieldInHierarchy(part.getClass(), "spin");
            if (f != null) {
                f.setByte(part, spin);
                if (part.getHost() != null) part.getHost().markForUpdate();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Could not apply panel spin for {}", part.getClass().getName());
        }
    }

    // ===== 键分类声明(粘贴选项过滤用) =====

    @Override
    public java.util.Set<String> getUpgradeKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("ae2e:upgrades"));
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof AEBasePart) {
            return ((AEBasePart) target).getItemStack(PartItemStack.NETWORK).getDisplayName();
        }
        return target.getClass().getSimpleName();
    }
}
