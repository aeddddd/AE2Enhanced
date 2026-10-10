package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端命令 {@code /ae2edockdemo}：打开 {@link GuiDockDemo} 停靠演示屏。
 *
 * <p>纯开发入口，供手工验证停靠行为与持久化；见 ClientProxy 里的移除 TODO。</p>
 */
@SideOnly(Side.CLIENT)
public final class DockDemoCommand implements ICommand {
    @Override
    public String getName() {
        return "ae2edockdemo";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ae2edockdemo";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                Minecraft.getMinecraft().displayGuiScreen(new GuiDockDemo());
            }
        });
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            BlockPos targetPos) {
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand other) {
        return getName().compareTo(other.getName());
    }
}
