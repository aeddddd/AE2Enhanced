package com.github.aeddddd.ae2enhanced.command;

import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex;
import com.github.aeddddd.ae2enhanced.chamber.LongItemStore;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.*;

final class ChamberDebugCommand implements CommandAdapter {
    private static String ids(List<ChamberRecipe> recipes) {
        if (recipes.isEmpty()) return "[]";
        List<String> ids = new ArrayList<>();
        for (ChamberRecipe recipe : recipes) ids.add(recipe.getId());
        return ids.toString();
    }

    public String name() { return "chamberdebug"; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        List<ChamberRecipe> all = ChamberRecipeIndex.allRecipes();
        Map<String, Integer> counts = new TreeMap<>();
        for (ChamberRecipe recipe : all) {
            String id = recipe.getId();
            counts.merge(id.contains(":") ? id.substring(0, id.indexOf(':')) : id, 1, Integer::sum);
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "[ChamberDebug] index total=" + all.size() + " byType=" + counts));
        if (!(sender instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) sender;
        ItemStack held = player.getHeldItemMainhand();
        if (!held.isEmpty()) {
            String key = LongItemStore.keyOf(held);
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[ChamberDebug] held=" + held.getDisplayName()
                    + " key=" + key + " matched=" + ids(ChamberRecipeIndex.recipesForInput(key, held))));
        }
        RayTraceResult trace = player.rayTrace(6.0, 1.0f);
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) return;
        TileEntity tile = player.world.getTileEntity(trace.getBlockPos());
        if (!(tile instanceof TileSingularityChamber)) {
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "[ChamberDebug] not looking at a singularity chamber")); return;
        }
        TileSingularityChamber chamber = (TileSingularityChamber) tile;
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "[ChamberDebug] chamber energy=" + chamber.getEnergy()
                + " jobs=" + chamber.getActiveJobCount() + " channels=" + chamber.getUsedChannels() + "/" + chamber.getParallelChannels()));
        Map<String, Long> available = new HashMap<>();
        for (LongItemStore.Entry entry : chamber.getInputStore().getEntries()) available.put(LongItemStore.keyOf(entry.getTemplate()), entry.getCount());
        for (LongItemStore.Entry entry : chamber.getInputStore().getEntries()) {
            String key = LongItemStore.keyOf(entry.getTemplate());
            List<ChamberRecipe> recipes = ChamberRecipeIndex.recipesForInput(key, entry.getTemplate());
            sender.sendMessage(new TextComponentString("  store " + key + " x" + entry.getCount() + " -> " + ids(recipes)));
            for (ChamberRecipe recipe : recipes) sender.sendMessage(new TextComponentString("    " + recipe.getId() + " maxBatches=" + recipe.maxBatches(available)));
        }
    }
}
