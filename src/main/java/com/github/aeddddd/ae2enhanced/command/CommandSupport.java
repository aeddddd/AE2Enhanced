package com.github.aeddddd.ae2enhanced.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Locale;

final class CommandSupport {
    static final String[] TOGGLE_OPTIONS = {"enable", "disable", "status"};

    private CommandSupport() {
    }

    static ITextComponent msg(TextFormatting color, String key, Object... args) {
        TextComponentTranslation component = new TextComponentTranslation(key, args);
        component.getStyle().setColor(color);
        return component;
    }

    static List<String> matching(String[] args, String... options) {
        return CommandBase.getListOfStringsMatchingLastWord(args, options);
    }

    static List<String> matching(String[] args, java.util.Collection<String> options) {
        return CommandBase.getListOfStringsMatchingLastWord(args, options);
    }

    static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static double parsePositiveDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static void usage(ICommandSender sender, String usage) {
        sender.sendMessage(new TextComponentString(TextFormatting.RED + usage));
    }
}
