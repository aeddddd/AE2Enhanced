package com.github.aeddddd.ae2enhanced.gui;

import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyFormed;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyPattern;
import com.github.aeddddd.ae2enhanced.container.ContainerAssemblyUnformed;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_ASSEMBLY_CONTROLLER = 0;
    public static final int GUI_ASSEMBLY_PATTERN = 1;

    /** 编码页码到 GUI ID：低4位为 base ID，高位为页码（8位，支持0~255页） */
    public static int encodePatternId(int page) {
        return GUI_ASSEMBLY_PATTERN | (page << 8);
    }

    /** 从 GUI ID 解码页码 */
    public static int decodePatternPage(int ID) {
        return (ID >> 8) & 0xFF;
    }

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (!(te instanceof TileAssemblyController)) return null;
        TileAssemblyController tile = (TileAssemblyController) te;
        int baseId = ID & 0xF;
        if (baseId == GUI_ASSEMBLY_CONTROLLER) {
            if (tile.isFormed()) {
                return new ContainerAssemblyFormed(player.inventory, tile);
            } else {
                return new ContainerAssemblyUnformed(player.inventory, tile);
            }
        } else if (baseId == GUI_ASSEMBLY_PATTERN) {
            int page = decodePatternPage(ID);
            return new ContainerAssemblyPattern(player.inventory, tile, page);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (!(te instanceof TileAssemblyController)) return null;
        TileAssemblyController tile = (TileAssemblyController) te;
        int baseId = ID & 0xF;
        if (baseId == GUI_ASSEMBLY_CONTROLLER) {
            if (tile.isFormed()) {
                return new GuiAssemblyFormed(player.inventory, tile);
            } else {
                return new GuiAssemblyUnformed(player.inventory, tile);
            }
        } else if (baseId == GUI_ASSEMBLY_PATTERN) {
            int page = decodePatternPage(ID);
            return new GuiAssemblyPattern(player.inventory, tile, page);
        }
        return null;
    }
}
