package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;

public final class TerminalPacketBridge {
    private TerminalPacketBridge() {
    }

    public static boolean postUpdate(NBTTagCompound data) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof IOmniTerminalGui) {
            ((IOmniTerminalGui) screen).postUpdate(data);
            return true;
        }
        return false;
    }
}
