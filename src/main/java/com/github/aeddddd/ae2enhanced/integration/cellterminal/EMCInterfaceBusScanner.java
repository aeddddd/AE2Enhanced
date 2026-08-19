package com.github.aeddddd.ae2enhanced.integration.cellterminal;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.cells.api.ResourcePreviewEntry;
import com.cellterminal.client.StorageType;
import com.cellterminal.container.handler.StorageBusDataHandler;
import com.cellterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.cellterminal.integration.storagebus.IStorageBusScanner;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 将网络中的 EMC 接口作为"存储总线式"条目上报给元件终端(Cell Terminal).
 *
 * <p>条目承载白名单(已用槽 + 额外一行空槽,上限 63)、内容预览(可提取物品)、
 * 绑定玩家与 EMC 余额等信息;编辑经 {@link EMCInterfaceFilterHost} 路由回
 * {@link TileEMCInterface} 的白名单 API.</p>
 *
 * <p>本类引用 cellterminal 类,仅允许在 cellterminal 存在时加载.</p>
 */
public class EMCInterfaceBusScanner implements IStorageBusScanner {

    /** 固定朝向: EMC 接口为全方块设备,无侧面语义,仅用于条目 id 与客户端显示 */
    private static final EnumFacing FACING = EnumFacing.NORTH;

    /** 条目名前缀的本地化键(客户端 I18n 解析,见 lang 文件) */
    private static final String NAME_PREFIX_KEY = "gui.ae2enhanced.cellterminal.emc_interface";

    @Override
    public String getId() {
        return "ae2enhanced";
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded("projecte") && BlockRegistry.EMC_INTERFACE != null;
    }

    @Override
    public void scanStorageBuses(IGrid grid, NBTTagList out, Map<Long, StorageBusTracker> trackerMap) {
        if (grid == null) return;

        for (IGridNode node : grid.getMachines(TileEMCInterface.class)) {
            if (!node.isActive()) continue;
            Object machine = node.getMachine();
            if (!(machine instanceof TileEMCInterface)) continue;

            TileEMCInterface tile = (TileEMCInterface) machine;
            if (tile.getWorld() == null) continue;

            EMCInterfaceFilterHost filterHost = new EMCInterfaceFilterHost(tile);
            long busId = StorageBusDataHandler.createBusId(tile, FACING.ordinal(), StorageType.ITEM.ordinal());

            NBTTagCompound busData = new NBTTagCompound();
            busData.setLong("id", busId);
            busData.setLong("pos", tile.getPos().toLong());
            busData.setInteger("dim", tile.getWorld().provider.getDimension());
            busData.setInteger("side", FACING.ordinal());
            busData.setInteger("priority", tile.getPriority());
            // 只读源: AccessRestriction.READ = 1, 且不提供 IO 模式切换
            busData.setInteger("access", 1);
            StorageType.ITEM.writeToNBT(busData);

            // 能力标志: 不支持优先级编辑 / IO 模式切换 / 升级卡
            busData.setBoolean("supportsPriority", false);
            busData.setBoolean("supportsIOMode", false);
            busData.setInteger("upgradeSlotCount", 0);

            // 槽位参数: 无容量卡概念,可用槽数 = 暴露槽数
            int exposedSlots = EMCInterfaceFilterHost.getExposedSlots(tile);
            busData.setInteger("baseConfigSlots", exposedSlots);
            busData.setInteger("slotsPerUpgrade", 0);
            busData.setInteger("maxConfigSlots", EMCInterfaceFilterHost.MAX_EXPOSED_SLOTS);

            // 名称: 前缀(本地化"EMC 接口") + 连接名(绑定玩家与 EMC 余额)
            busData.setString("namePrefixKey", NAME_PREFIX_KEY);
            busData.setString("connectedName", buildInfoLine(tile));

            ItemStack icon = new ItemStack(BlockRegistry.EMC_INTERFACE);
            NBTTagCompound iconNbt = new NBTTagCompound();
            icon.writeToNBT(iconNbt);
            busData.setTag("connectedIcon", iconNbt);

            busData.setTag("partition", buildPartitionNbt(tile, exposedSlots));
            busData.setTag("contents", buildContentsNbt(filterHost));

            out.appendTag(busData);
            trackerMap.put(busId, new StorageBusTracker(
                    busId, tile, tile, FACING.ordinal(), StorageType.ITEM, filterHost));
        }
    }

    /**
     * 条目信息行: 绑定玩家名与 EMC 余额(紧凑格式);未绑定时提示.
     * 服务端无法按客户端语言本地化,与 Cell Terminal 自身连接名行为一致使用英文.
     */
    private static String buildInfoLine(TileEMCInterface tile) {
        if (!tile.isBound()) return "Unbound";
        String owner = tile.getOwnerName();
        StringBuilder sb = new StringBuilder();
        sb.append(owner == null || owner.isEmpty() ? "Unknown" : owner);
        BigInteger balance = getEmcBalance(tile);
        if (balance != null) {
            sb.append(" | EMC: ").append(formatEmc(balance));
        }
        return sb.toString();
    }

    private static BigInteger getEmcBalance(TileEMCInterface tile) {
        try {
            Object provider = tile.getKnowledgeProvider();
            if (provider == null) return null;
            return ProjectEHelper.getEmcBig(provider);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 白名单分区 NBT: 与 Cell Terminal populateConfigInventory 相同的
     * 槽位索引格式,仅写出暴露范围内的槽位.
     */
    private static NBTTagList buildPartitionNbt(TileEMCInterface tile, int exposedSlots) {
        NBTTagList partitionList = new NBTTagList();
        for (int i = 0; i < exposedSlots; i++) {
            ItemStack filter = tile.getWhitelistSlot(i);
            NBTTagCompound slotNbt = new NBTTagCompound();
            slotNbt.setInteger("slot", i);
            if (!filter.isEmpty()) filter.writeToNBT(slotNbt);
            partitionList.appendTag(slotNbt);
        }
        return partitionList;
    }

    /**
     * 内容预览 NBT: 与 CellsIntegration.createPreviewNBT 相同的格式
     * (物品 + "Cnt" 数量),数量直接取网络视图中的可提取量.
     */
    private static NBTTagList buildContentsNbt(EMCInterfaceFilterHost filterHost) {
        NBTTagList contentsList = new NBTTagList();
        List<ResourcePreviewEntry> entries = filterHost.getPreviewEntries(0);
        for (ResourcePreviewEntry entry : entries) {
            ItemStack display = entry.getDisplayStack();
            if (display.isEmpty()) continue;
            NBTTagCompound stackNbt = new NBTTagCompound();
            display.writeToNBT(stackNbt);
            stackNbt.setLong("Cnt", entry.getAmount());
            contentsList.appendTag(stackNbt);
        }
        return contentsList;
    }

    /**
     * 紧凑格式化 EMC 数值(K/M/B/T 递进),供条目信息行显示.
     */
    private static String formatEmc(BigInteger value) {
        final String[] suffixes = {"", "K", "M", "B", "T", "Q"};
        BigInteger thousand = BigInteger.valueOf(1000);
        int tier = 0;
        BigInteger scaled = value;
        while (scaled.compareTo(thousand) >= 0 && tier < suffixes.length - 1) {
            scaled = scaled.divide(thousand);
            tier++;
        }
        return scaled + suffixes[tier];
    }
}
