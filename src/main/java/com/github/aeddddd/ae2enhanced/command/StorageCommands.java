package com.github.aeddddd.ae2enhanced.command;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.hooks.TickHandler;
import appeng.me.Grid;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.block.BlockRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.compat.Ae2fcFluidCompat;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;
import java.util.*;

final class StorageCommandSupport {
    private StorageCommandSupport() {
    }

    static List<String> collectUuids(MinecraftServer server) {
        List<String> result = new ArrayList<>();
        WorldServer world = server.getWorld(0);
        if (world == null) return result;
        File storageDir = new File(world.getSaveHandler().getWorldDirectory(), "ae2enhanced/storage");
        File[] entries = storageDir.listFiles((dir, name) -> {
            if (name.startsWith("smartpattern_")) return false;
            File file = new File(dir, name);
            return file.isDirectory() || name.endsWith(".dat");
        });
        if (entries == null) return result;
        for (File entry : entries) {
            String name = entry.getName();
            result.add(entry.isDirectory() ? name : name.substring(0, name.length() - 4));
        }
        return result;
    }
}

final class RecoverHdCommand implements CommandAdapter {
    private static void list(ICommandSender sender, MinecraftServer server) {
        WorldServer world = server.getWorld(0);
        if (world == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Cannot access the overworld."));
            return;
        }
        File storageDir = new File(world.getSaveHandler().getWorldDirectory(), "ae2enhanced/storage");
        File[] entries = storageDir.listFiles((dir, name) -> {
            if (name.startsWith("smartpattern_")) return false;
            File file = new File(dir, name);
            return file.isDirectory() || name.endsWith(".dat");
        });
        if (entries == null || entries.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No hyperdimensional storage data found."));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Found " + entries.length
                + " hyperdimensional storage entry(s) (click UUID to copy):"));
        Arrays.sort(entries, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File entry : entries) {
            String name = entry.getName();
            String uuid = entry.isDirectory() ? name : name.substring(0, name.length() - 4);
            TextComponentString uuidText = new TextComponentString(uuid);
            uuidText.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid));
            uuidText.getStyle().setColor(TextFormatting.AQUA);
            uuidText.getStyle().setUnderlined(true);
            TextComponentString suffix = new TextComponentString("  (" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                    .format(new Date(entry.lastModified())) + ")");
            suffix.getStyle().setColor(TextFormatting.GRAY);
            TextComponentString line = new TextComponentString("  - ");
            line.getStyle().setColor(TextFormatting.GRAY);
            line.appendSibling(uuidText);
            line.appendSibling(suffix);
            sender.sendMessage(line);
        }
    }

    private static void give(MinecraftServer server, ICommandSender sender, String uuidText) {
        UUID uuid;
        try { uuid = UUID.fromString(uuidText); } catch (IllegalArgumentException exception) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Invalid UUID: " + uuidText));
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        ItemStack stack = new ItemStack(BlockRegistry.HYPERDIMENSIONAL_CONTROLLER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("nexusId", uuid);
        stack.setTagCompound(tag);
        if (!player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN
                + "[AE2E] Given Hyperdimensional Controller block carrying UUID " + uuidText + "."));
    }

    public String name() { return "recoverhd"; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) {
            CommandSupport.usage(sender, "Usage: /ae2e recoverhd list  or  /ae2e recoverhd <uuid>");
            return;
        }
        if ("list".equalsIgnoreCase(args[1])) list(sender, server); else give(server, sender, args[1].toLowerCase());
    }

    public List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        if (args.length != 2) return Collections.emptyList();
        List<String> options = new ArrayList<>();
        options.add("list");
        options.addAll(StorageCommandSupport.collectUuids(server));
        return CommandSupport.matching(args, options);
    }
}

final class TestHdCommand implements CommandAdapter {
    private static final List<Item> GEAR_CACHE = new ArrayList<>();

    private static ItemStack generateRandomGear(Random random) {
        ItemStack stack = new ItemStack(GEAR_CACHE.get(random.nextInt(GEAR_CACHE.size())));
        int enchantCount = random.nextInt(6);
        if (enchantCount > 0) {
            List<Enchantment> possible = new ArrayList<>();
            for (Enchantment enchantment : Enchantment.REGISTRY) {
                if (enchantment != null && enchantment.canApply(stack)) possible.add(enchantment);
            }
            if (!possible.isEmpty()) {
                Collections.shuffle(possible, random);
                for (int i = 0; i < Math.min(enchantCount, possible.size()); i++) {
                    Enchantment enchantment = possible.get(i);
                    stack.addEnchantment(enchantment, 1 + random.nextInt(enchantment.getMaxLevel()));
                }
            }
        }
        return stack;
    }

    public String name() { return "testhd"; }

    public List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        return args.length == 2 ? CommandSupport.matching(args, StorageCommandSupport.collectUuids(server)) : Collections.emptyList();
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 3) { CommandSupport.usage(sender, "Usage: /ae2e testhd <uuid> <count>"); return; }
        UUID uuid;
        try { uuid = UUID.fromString(args[1]); } catch (IllegalArgumentException exception) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Invalid UUID: " + args[1])); return;
        }
        int count;
        try {
            count = Integer.parseInt(args[2]);
            if (count <= 0 || count > 100_000) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Count must be between 1 and 100000.")); return;
            }
        } catch (NumberFormatException exception) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Invalid count: " + args[2])); return;
        }
        TileHyperdimensionalController controller = null;
        for (WorldServer world : server.worlds) {
            if (world == null) continue;
            for (net.minecraft.tileentity.TileEntity tile : world.loadedTileEntityList) {
                if (tile instanceof TileHyperdimensionalController) {
                    TileHyperdimensionalController candidate = (TileHyperdimensionalController) tile;
                    if (candidate.isFormed() && uuid.equals(candidate.getNexusId())) { controller = candidate; break; }
                }
            }
            if (controller != null) break;
        }
        if (controller == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] No formed hyperdimensional controller found with UUID " + uuid + ".")); return;
        }
        ItemStorageAdapter adapter = controller.getItemAdapter();
        if (adapter == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Controller found but item adapter is not initialized.")); return;
        }
        synchronized (GEAR_CACHE) {
            if (GEAR_CACHE.isEmpty()) {
                for (Item item : Item.REGISTRY) {
                    if (item instanceof ItemSword || item instanceof ItemTool || item instanceof ItemArmor || item instanceof ItemBow) GEAR_CACHE.add(item);
                }
            }
        }
        if (GEAR_CACHE.isEmpty()) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] No gear items found in registry.")); return;
        }
        IActionSource source = sender instanceof EntityPlayerMP ? new PlayerSource((EntityPlayerMP) sender, null) : new MachineSource(controller);
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            ItemStack stack = generateRandomGear(random);
            if (stack.isEmpty()) continue;
            IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(stack);
            if (aeStack == null) continue;
            long amount = Math.abs(random.nextLong());
            if (amount <= 0) amount = 1;
            IAEItemStack injected = aeStack.copy();
            injected.setStackSize(amount);
            adapter.injectItems(injected, Actionable.MODULATE, source);
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Injected " + count
                + " random enchanted gear type(s) into controller " + uuid + "."));
    }
}

final class MigrateFluidsCommand implements CommandAdapter {
    public String name() { return "migratefluids"; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (!Ae2fcCompat.AE2FC_LOADED) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] ae2fc is not loaded, no migration needed.")); return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command must be executed by a player.")); return;
        }
        IActionSource source = new PlayerSource((EntityPlayerMP) sender, null);
        int convertedStacks = 0;
        long convertedAmount = 0;
        try {
            IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            for (Grid grid : TickHandler.INSTANCE.getGridList()) {
                GridStorageCache cache = grid.getCache(GridStorageCache.class);
                if (cache == null) continue;
                appeng.api.storage.IMEMonitor<IAEItemStack> monitor = cache.getInventory(channel);
                if (monitor == null) continue;
                appeng.api.storage.data.IItemList<IAEItemStack> list = channel.createList();
                monitor.getAvailableItems(list);
                for (IAEItemStack stack : list) {
                    if (stack == null || stack.getStackSize() <= 0) continue;
                    ItemStack mcStack = stack.createItemStack();
                    if (!ItemFluidDrop.isFluidDrop(mcStack)) continue;
                    FluidStack fluid = ItemFluidDrop.getFluidStack(mcStack);
                    if (fluid == null || fluid.getFluid() == null) continue;
                    IAEItemStack notExtracted = monitor.extractItems(stack.copy(), Actionable.MODULATE, source);
                    long extracted = stack.getStackSize() - (notExtracted != null ? notExtracted.getStackSize() : 0);
                    long remaining = extracted;
                    while (remaining > 0) {
                        int batch = (int) Math.min(remaining, Integer.MAX_VALUE);
                        FluidStack convert = fluid.copy();
                        convert.amount = batch;
                        ItemStack converted = Ae2fcFluidCompat.createFluidDrop(convert);
                        if (converted.isEmpty()) {
                            ItemStack returned = mcStack.copy(); returned.setCount(batch);
                            monitor.injectItems(AEItemStack.fromItemStack(returned), Actionable.MODULATE, source);
                            remaining -= batch;
                            continue;
                        }
                        IAEItemStack notInserted = monitor.injectItems(AEItemStack.fromItemStack(converted), Actionable.MODULATE, source);
                        long rejected = notInserted != null ? notInserted.getStackSize() : 0;
                        if (rejected > 0) {
                            ItemStack returned = mcStack.copy(); returned.setCount((int) rejected);
                            monitor.injectItems(AEItemStack.fromItemStack(returned), Actionable.MODULATE, source);
                        }
                        convertedAmount += batch - rejected;
                        convertedStacks++;
                        remaining -= batch;
                    }
                }
            }
        } catch (Exception exception) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to migrate fluid drops", exception);
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Migration failed: " + exception.getMessage())); return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Migrated " + convertedStacks
                + " AE2E fluid drop stacks (" + convertedAmount + " mB) to ae2fc format."));
    }
}
