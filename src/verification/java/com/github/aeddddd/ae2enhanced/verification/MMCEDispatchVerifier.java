package com.github.aeddddd.ae2enhanced.verification;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.IStorageChannel;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.PlayerSource;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.storage.TileDrive;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.UUID;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
/** 真实 AE2 网格提交与 MMCE 供应器收料验证，不宣称模块化机器已产出。 */
@Mod.EventBusSubscriber(modid = ServerSmokeMod.MOD_ID)
public final class MMCEDispatchVerifier {
    private static WorldServer world;
    private static Object provider;
    private static Class<?> providerClass;
    private static IGrid grid;
    private static ICraftingGrid crafting;
    private static IMEMonitorAccessor inventory;
    private static ICraftingJob job;
    private static ICraftingLink link;
    private static java.util.concurrent.Future<ICraftingJob> future;
    private static PlayerSource source;
    private static int stage;
    private static int ticks;

    private MMCEDispatchVerifier() {
    }

    static void start(WorldServer world) throws Exception {
        MMCEDispatchVerifier.world = world;
        stage = 0;
        ticks = 0;
        job = null;
        link = null;
        future = null;
        grid = null;
        crafting = null;
        inventory = null;
        BlockPos origin = world.getSpawnPoint().add(0, 12, 0);
        place(origin, "creative_energy_cell");
        place(origin.add(1, 0, 0), "drive");
        place(origin.add(2, 0, 0), "crafting_storage_1k");
        place(origin.add(3, 0, 0), "crafting_monitor");
        Class<?> blocks = Class.forName("hellfirepvp.modularmachinery.common.lib.BlocksMM");
        Block providerBlock = (Block) blocks.getField("mePatternProvider").get(null);
        world.setBlockState(origin.add(4, 0, 0), providerBlock.getDefaultState(), 3);
        provider = world.getTileEntity(origin.add(4, 0, 0));
        providerClass = provider.getClass();
        java.lang.reflect.Field workModeField = providerClass.getDeclaredField("workMode");
        workModeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Class<Enum> workModeType = (Class<Enum>) workModeField.getType();
        workModeField.set(provider, Enum.valueOf(workModeType, "ENHANCED_BLOCKING_MODE"));
        TileDrive drive = (TileDrive) world.getTileEntity(origin.add(1, 0, 0));
        drive.getInternalInventory().insertItem(0,
                AEApi.instance().definitions().items().cell1k().maybeStack(1).orElseThrow(
                        () -> new IllegalStateException("1k cell unavailable")), false);

        UUID id = UUID.randomUUID();
        SmartPatternRepository repository = SmartPatternRepository.get(world);
        repository.migrate(new SmartPatternData(id, "minecraft:furnace", java.util.Arrays.asList(
                recipe(Items.APPLE, Items.BREAD), recipe(Items.BONE, Items.DIAMOND))), 0L);
        ItemStack pattern = ItemSmartPattern.createPattern(id, new java.util.BitSet(), 2, "minecraft:furnace", 1L);
        java.lang.reflect.Field patternsField = providerClass.getDeclaredField("patterns");
        patternsField.setAccessible(true);
        ((AppEngInternalInventory) patternsField.get(provider)).setStackInSlot(0, pattern);
        source = new PlayerSource(net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world),
                (IActionHost) provider);
    }

    static boolean awaiting() {
        return world != null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (world == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ticks++;
            if (ticks > 2400) {
                throw new IllegalStateException("MMCE dispatch timed out in stage " + stage);
            }
            if (stage == 0) {
                IActionHost host = (IActionHost) provider;
                if (host.getActionableNode() == null || !host.getActionableNode().isActive()) {
                    return;
                }
                grid = host.getActionableNode().getGrid();
                crafting = grid.getCache(ICraftingGrid.class);
                IStorageGrid storage = grid.getCache(IStorageGrid.class);
                inventory = new IMEMonitorAccessor(storage.getInventory(channel()));
                require(inventory.inject(item(Items.BONE), 8) == 0, "MMCE bone stock insertion failed");
                require(!crafting.getCpus().isEmpty(), "MMCE crafting CPU did not form");
                IAEItemStack output = item(Items.DIAMOND);
                future = crafting.beginCraftingJob(world, grid, source, output, null);
                stage = 1;
                return;
            }
            if (stage == 1) {
                if (!future.isDone()) {
                    return;
                }
                job = future.get();
                require(job != null && !job.isSimulation(), "MMCE dispatch plan is missing or simulated");
                link = crafting.submitJob(job, (ICraftingRequester) provider, null, false, source);
                require(link != null, "MMCE submitJob returned null");
                stage = 2;
                return;
            }
            if (stage == 2) {
                java.lang.reflect.Field currentField = providerClass.getDeclaredField("currentPattern");
                currentField.setAccessible(true);
                Object current = currentField.get(provider);
                IItemHandler handler = (IItemHandler) providerClass.getMethod("getInfHandler").invoke(provider);
                boolean boneAccepted = false;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty() && stack.getItem() == Items.BONE) {
                        boneAccepted = true;
                        break;
                    }
                }
                if (ticks % 200 == 0) {
                    org.apache.logging.log4j.LogManager.getLogger("AE2E-MMCE-VERIFY").info(
                            "MMCE_DISPATCH_WAIT stage=2 current={} boneAccepted={} slots={} done={} canceled={}",
                            current, boneAccepted, handler.getSlots(), link.isDone(), link.isCanceled());
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        org.apache.logging.log4j.LogManager.getLogger("AE2E-MMCE-VERIFY").info(
                                "MMCE_DISPATCH_SLOT slot={} stack={}", slot, handler.getStackInSlot(slot));
                    }
                }
                if (current == null || !boneAccepted) {
                    return;
                }
                appeng.api.networking.crafting.ICraftingPatternDetails details =
                        (appeng.api.networking.crafting.ICraftingPatternDetails) current;
                require(details.getOutputs()[0].createItemStack().getItem() == Items.DIAMOND,
                        "MMCE dispatched pattern is not the expanded diamond recipe");
                require(!link.isCanceled(), "MMCE dispatch link was cancelled");
                String version = Loader.instance().getIndexedModList().get("modularmachinery").getVersion();
                org.apache.logging.log4j.LogManager.getLogger("AE2E-MMCE-VERIFY").info(
                        "MMCE_DISPATCH_PASS version={} accepted=true output=diamond boneAccepted=true", version);
                link.cancel();
                world = null;
                ServerSmokeMod.requestShutdown();
            }
        } catch (Throwable throwable) {
            ServerSmokeMod.fail("MMCE dispatch failed: " + throwable, throwable);
            world = null;
        }
    }

    private static SmartRecipe recipe(Item input, Item output) {
        return new SmartRecipe(new IAEItemStack[]{AEItemStack.fromItemStack(new ItemStack(input))},
                new IAEItemStack[]{AEItemStack.fromItemStack(new ItemStack(output))}, false);
    }

    private static IAEItemStack item(Item item) {
        return AEItemStack.fromItemStack(new ItemStack(item));
    }

    private static IStorageChannel<IAEItemStack> channel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    private static void place(BlockPos pos, String name) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("appliedenergistics2", name));
        if (block == null) {
            throw new IllegalStateException("AE2 block missing " + name);
        }
        world.setBlockState(pos, block.getDefaultState(), 3);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static final class IMEMonitorAccessor {
        private final appeng.api.storage.IMEMonitor<IAEItemStack> monitor;

        private IMEMonitorAccessor(appeng.api.storage.IMEMonitor<IAEItemStack> monitor) {
            this.monitor = monitor;
        }

        private long inject(IAEItemStack stack, long amount) {
            IAEItemStack copy = stack.copy();
            copy.setStackSize(amount);
            IAEItemStack remainder = monitor.injectItems(copy, appeng.api.config.Actionable.MODULATE, source);
            return remainder == null ? 0L : remainder.getStackSize();
        }
    }
}
