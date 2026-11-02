package com.github.aeddddd.ae2enhanced.integration.terminal;

import appeng.helpers.ItemStackHelper;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.helpers.ItemHandlerUtil;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * 装配中枢在接口终端中的跟踪数据。
 *
 * <p>每个实例现在代表装配中枢样板库存的一行（9 槽），而不是整个控制器。
 * 这样接口终端的滚动条可以按实际行数计算，解决只能显示单页的问题。</p>
 */
public class AssemblyInterfaceTracker {

    private static long nextId = 1;

    private final long which;
    private final long sortBy;
    private final String unlocalizedName;
    private final IItemHandler server;
    private final IItemHandler client;
    private final BlockPos pos;
    private final int dim;
    private final int numUpgrades;

    /**
     * 为装配中枢的指定行创建跟踪器。
     *
     * @param controller 装配中枢控制器
     * @param rowIndex   行索引（从 0 开始）
     * @param totalRows  总行数
     * @param rowSize    本行实际槽位数（最后一行可能不足 9）
     */
    public AssemblyInterfaceTracker(TileAssemblyController controller, int rowIndex, int totalRows, int rowSize) {
        this.which = nextId++;
        this.server = new AssemblyPatternRowWrapper(new AssemblyPatternInventoryWrapper(controller), rowIndex * 9, rowSize);
        this.client = new AppEngInternalInventory(null, 9);
        this.pos = controller.getPos();
        this.dim = controller.getWorld().provider.getDimension();
        this.unlocalizedName = controller.getBlockType().getTranslationKey();
        // 同一控制器的行按 pos 聚类，rowIndex 保证顺序
        this.sortBy = pos.toLong() + rowIndex;
        this.numUpgrades = 0; // 每行只显示一行，不需要额外展开
    }

    public long getWhich() {
        return which;
    }

    public long getSortBy() {
        return sortBy;
    }

    public String getUnlocalizedName() {
        return unlocalizedName;
    }

    public IItemHandler getServer() {
        return server;
    }

    public IItemHandler getClient() {
        return client;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getDim() {
        return dim;
    }

    public int getNumUpgrades() {
        return numUpgrades;
    }

    /**
     * 将当前跟踪数据写入 NBT，格式与原版 InvTracker.addItems 一致。
     */
    public void writeToNBT(@Nonnull NBTTagCompound data, int offset, int length) {
        String name = '=' + Long.toString(which, 36);
        NBTTagCompound tag = data.getCompoundTag(name);
        if (tag.getSize() == 0) {
            tag.setLong("sortBy", sortBy);
            tag.setString("un", unlocalizedName);
            tag.setTag("pos", (NBTBase) NBTUtil.createPosTag(pos));
            tag.setInteger("dim", dim);
            tag.setInteger("numUpgrades", numUpgrades);
        }
        for (int x = 0; x < length; x++) {
            NBTTagCompound itemNBT = new NBTTagCompound();
            ItemStack is = server.getStackInSlot(x + offset);
            ItemHandlerUtil.setStackInSlot(client, x + offset, is.isEmpty() ? ItemStack.EMPTY : is.copy());
            if (!is.isEmpty()) {
                ItemStackHelper.stackWriteToNBT(is, itemNBT);
            }
            tag.setTag(Integer.toString(x + offset), (NBTBase) itemNBT);
        }
        data.setTag(name, (NBTBase) tag);
    }
}
