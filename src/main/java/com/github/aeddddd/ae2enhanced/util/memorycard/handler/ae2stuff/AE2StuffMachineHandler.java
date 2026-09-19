package com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2stuff;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

/**
 * AE2 Stuff Unofficial(bdew 框架)设备的配置复制粘贴 Handler.
 *
 * <p>支持的设备(包 {@code net.bdew.ae2stuff.machines.}):</p>
 * <ul>
 *   <li>{@code TileInscriber} - 高级压印器; 配置: {@code topLocked}/{@code bottomLocked}(模板锁定)</li>
 *   <li>{@code TileGrower} - 晶体催生器; 无可复制配置(仅用于阻止容器兜底误捕获)</li>
 *   <li>{@code TileWireless}/{@code TileWirelessHub} - 无线连接器/枢纽;
 *       配置: {@code CustomName}/{@code Color}(装饰项). 配对坐标 {@code link} 不复制
 *       (跨坐标粘贴会抢连其他机器的配对端)</li>
 * </ul>
 *
 * <p>bdew 框架机器无红石模式/朝向/侧边配置.排除 {@code ae_node}(网格节点)、
 * {@code power}/{@code _poweruse}、{@code progress}/{@code output}、{@code Items}(库存)、
 * {@code upgrades}(真实物品升级卡,直接覆写会造成刷物品,不予复制).</p>
 *
 * <p>实现仅操作 NBT,不直接引用 AE2 Stuff 类,满足反射隔离约定.</p>
 */
public class AE2StuffMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    private static final String CLASS_PREFIX = "net.bdew.ae2stuff.machines.";

    /** 配置键(存在才复制). */
    private static final String[] CONFIG_KEYS = {
            "topLocked", "bottomLocked", "CustomName", "Color"
    };

    static {
        AVAILABLE = Loader.isModLoaded("ae2stuff");
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
            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy AE2 Stuff device config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());

            // 类型校验:要求完全同类
            if (data.hasKey("dataType") && !data.getString("dataType").equals(target.getClass().getName())) {
                return PasteResult.INVALID_MACHINE;
            }

            for (String key : CONFIG_KEYS) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }

            // 注意:键合并到目标当前 NBT 上,因此 TileInscriber 的 persistLoad
            // 迁移逻辑(缺 topLocked/bottomLocked 时强制为 true)不会误触发
            tile.readFromNBT(currentNbt);
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste AE2 Stuff device config", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
