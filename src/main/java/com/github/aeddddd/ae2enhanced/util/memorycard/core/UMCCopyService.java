package com.github.aeddddd.ae2enhanced.util.memorycard.core;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * UMC 复制逻辑服务.
 */
public class UMCCopyService {

    public static void handleCopy(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        Object target = findTarget(world, pos, face);
        if (target == null) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_invalid"));
            return;
        }

        IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(target);
        if (handler == null) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_unsupported"));
            return;
        }

        NBTTagCompound data = handler.copy(target);
        if (data == null || data.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_empty", handler.getDisplayName(target)));
            return;
        }

        String handlerId;
        if (target instanceof appeng.parts.AEBasePart) handlerId = "ae2_part";
        else if (target instanceof appeng.tile.AEBaseTile) handlerId = "ae2_tile";
        else handlerId = "ae2e_custom";

        ItemUniversalMemoryCard.setConfig(stack, handlerId, handler.getDisplayName(target), data);
        // 集中打标：渲染/信息展示所需的元数据由复制服务统一记录,
        // 任何 handler 都无需(也不能)遗漏——新增 handler 自动获得该能力
        stampMeta(stack, world, pos, target, handler);
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_success", handler.getDisplayName(target)));
    }

    /** AE 网络连接状态(复制时刻快照). */
    public enum NetStatus {
        NOT_AE,   // 非 AE 设备(第三方机器)
        OFFLINE,  // AE 设备但复制时未接入在线网络
        ONLINE    // 复制时已接入在线网络
    }

    /**
     * 将渲染与展示元数据写入配置：
     * <ul>
     *   <li>Tile 目标：方块注册名 + meta(GUI 据此渲染 3D 模型)</li>
     *   <li>blockItem：方块的 pickBlock 物品形态(GUI 据此显示机型变体——
     *       部分 mod(如热力机器)的机型存于物品 NBT,blockMeta 只有等级,
     *       光有 meta 无法区分"红石炉/锯木机"与等级后缀)</li>
     *   <li>Part 目标：Part 物品堆(GUI 据此渲染物品模型)</li>
     *   <li>net：复制时刻的 AE 网络连接状态快照</li>
     *   <li>handlerId：来源 handler 的稳定 ID(供粘贴过滤器按键分类查询)</li>
     * </ul>
     */
    private static void stampMeta(ItemStack stack, World world, BlockPos pos, Object target, IMemoryCardHandler handler) {
        NBTTagCompound config = ItemUniversalMemoryCard.getConfig(stack);
        if (config == null) return;
        NBTTagCompound meta = new NBTTagCompound();
        try {
            if (target instanceof IPart) {
                ItemStack partStack = ((IPart) target).getItemStack(appeng.api.parts.PartItemStack.NETWORK);
                if (partStack != null && !partStack.isEmpty()) {
                    meta.setTag("partItem", partStack.writeToNBT(new NBTTagCompound()));
                }
            }
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            net.minecraft.block.Block block = state.getBlock();
            if (block.getRegistryName() != null) {
                meta.setString("block", block.getRegistryName().toString());
                meta.setInteger("blockMeta", block.getMetaFromState(state));
            }
            ItemStack pick = block.getPickBlock(state,
                    new net.minecraft.util.math.RayTraceResult(net.minecraft.util.math.RayTraceResult.Type.BLOCK,
                            new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                            net.minecraft.util.EnumFacing.UP, pos),
                    world, pos, null);
            if (pick != null && !pick.isEmpty()) {
                meta.setTag("blockItem", pick.writeToNBT(new NBTTagCompound()));
            }
        } catch (Exception e) {
            // 元数据仅用于展示,失败不影响复制本体
        }
        meta.setInteger("net", detectNetStatus(target).ordinal());
        meta.setString("handlerId", handler.getId());
        config.setTag("meta", meta);
    }

    private static NetStatus detectNetStatus(Object target) {
        if (!(target instanceof appeng.me.helpers.IGridProxyable)) return NetStatus.NOT_AE;
        try {
            appeng.me.helpers.AENetworkProxy proxy = ((appeng.me.helpers.IGridProxyable) target).getProxy();
            if (proxy == null) return NetStatus.OFFLINE;
            proxy.getGrid();
            return proxy.isActive() ? NetStatus.ONLINE : NetStatus.OFFLINE;
        } catch (appeng.me.GridAccessException e) {
            return NetStatus.OFFLINE;
        } catch (Exception e) {
            return NetStatus.NOT_AE;
        }
    }

    private static Object findTarget(World world, BlockPos pos, EnumFacing face) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;
            IPart part = host.getPart(AEPartLocation.fromFacing(face));
            if (part != null) return part;
        }
        if (te != null) return te;
        return null;
    }
}
