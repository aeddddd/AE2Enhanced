package com.github.aeddddd.ae2enhanced.integration.terminal;

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
 * 装配中枢在接口终端中的跟踪数据.
 *
 * <p>与 AE2 原版 {@code ContainerInterfaceTerminal.InvTracker} 字段对齐,
 * 使用相同的 NBT 输出格式,使客户端 {@code GuiInterfaceTerminal} 无需修改即可显示.</p>
 */
public class AssemblyInterfaceTracker {

    private static long nextId = 0;

    private final long which;
    private final long sortBy;
    private final String unlocalizedName;
    private final IItemHandler server;
    private final IItemHandler client;
    private final BlockPos pos;
    private final int dim;
    private final int numUpgrades;

    public AssemblyInterfaceTracker(TileAssemblyController controller) {
        this.which = nextId++;
        this.server = new AssemblyPatternInventoryWrapper(controller);
        this.client = new AppEngInternalInventory(null, this.server.getSlots());
        this.pos = controller.getPos();
        this.dim = controller.getWorld().provider.getDimension();
        this.unlocalizedName = controller.getBlockType().getTranslationKey();
        this.sortBy = pos.toLong();

        int patternSlots = this.server.getSlots();
        this.numUpgrades = Math.max(0, (int) Math.ceil(patternSlots / 9.0) - 1);
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
     * 将当前跟踪数据写入 NBT,格式与原版 InvTracker.addItems 一致.
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
                is.writeToNBT(itemNBT);
            }
            tag.setTag(Integer.toString(x + offset), (NBTBase) itemNBT);
        }
        data.setTag(name, (NBTBase) tag);
    }
}
