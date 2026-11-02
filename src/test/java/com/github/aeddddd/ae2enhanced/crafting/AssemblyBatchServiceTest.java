package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.tile.AssemblyBatchService;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssemblyBatchServiceTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static ICraftingPatternDetails pattern(IAEItemStack input, IAEItemStack output) {
        ICraftingPatternDetails details = mock(ICraftingPatternDetails.class);
        when(details.getCondensedInputs()).thenReturn(input == null
                ? new IAEItemStack[0] : new IAEItemStack[]{input});
        when(details.getCondensedOutputs()).thenReturn(new IAEItemStack[]{output});
        return details;
    }

    private static IAEItemStack stack(Item item, long count) {
        IAEItemStack stack = AEItemStack.fromItemStack(new ItemStack(item));
        stack.setStackSize(count);
        return stack;
    }

    private static long available(IMEInventory<IAEItemStack> inventory, Item item) {
        IAEItemStack result = inventory.extractItems(stack(item, Long.MAX_VALUE), Actionable.SIMULATE, null);
        return result == null ? 0 : result.getStackSize();
    }

    @Test
    void highParallelVirtualBatchUsesCheckedLongEnergyAndNativeWaitingReturnPath() {
        Fixture fixture = new Fixture();
        ICraftingPatternDetails details = pattern(stack(Item.getItemFromBlock(Blocks.STONE), 2), stack(Items.DIAMOND, 1));
        long operations = 1_000_000L;
        fixture.add(details, operations);
        fixture.inventory.injectItems(stack(Item.getItemFromBlock(Blocks.STONE), operations * 2), Actionable.MODULATE, null);
        TileAssemblyController controller = fixture.virtualController(details, operations);

        fixture.execute(controller, details);

        assertThat(fixture.remaining(details)).isZero();
        assertThat(available(fixture.inventory, Item.getItemFromBlock(Blocks.STONE))).isZero();
        assertThat(fixture.injected).hasSize(1);
        assertThat(fixture.injected.get(0).getStackSize()).isEqualTo(operations);
        assertThat(fixture.waiting.isEmpty()).isTrue();
        assertThat(fixture.access.ae2e$remainingOperations()).isEqualTo(37);
        verify(controller).chargeCraftEnergy(operations);
        verify(controller).claimParallel(details, operations);
        verify(fixture.access, never()).ae2e$setRemainingOperations(anyInt());
    }

    @Test
    void shortActualExtractionRollsBackEverythingWithoutOutputOrEnergy() {
        Fixture fixture = new Fixture();
        ICraftingPatternDetails details = mock(ICraftingPatternDetails.class);
        when(details.getCondensedInputs()).thenReturn(new IAEItemStack[]{
                stack(Item.getItemFromBlock(Blocks.STONE), 4), stack(Items.DIAMOND, 6)});
        when(details.getCondensedOutputs()).thenReturn(new IAEItemStack[]{stack(Items.EMERALD, 1)});
        fixture.add(details, 1);
        fixture.inventory.injectItems(stack(Item.getItemFromBlock(Blocks.STONE), 4), Actionable.MODULATE, null);
        fixture.inventory.injectItems(stack(Items.DIAMOND, 6), Actionable.MODULATE, null);
        ShortActualInventory shortInventory = new ShortActualInventory(fixture.inventory);
        when(fixture.access.ae2e$inventory()).thenReturn(shortInventory);
        TileAssemblyController controller = fixture.virtualController(details, 1);

        fixture.execute(controller, details);

        assertThat(fixture.remaining(details)).isEqualTo(1);
        assertThat(available(fixture.inventory, Item.getItemFromBlock(Blocks.STONE))).isEqualTo(4);
        assertThat(available(fixture.inventory, Items.DIAMOND)).isEqualTo(6);
        assertThat(fixture.injected).isEmpty();
        assertThat(fixture.waiting.isEmpty()).isTrue();
        verify(controller, never()).chargeCraftEnergy(anyLong());
        verify(controller, never()).claimParallel(any(), anyLong());
        verify(fixture.access, never()).ae2e$setRemainingOperations(anyInt());
    }

    @Test
    void realBatchQueuesActualRecipeRemaindersAndChargesOnlyCommittedBatch() {
        Fixture fixture = new Fixture();
        ICraftingPatternDetails details = pattern(null, stack(Items.DIAMOND, 1));
        fixture.add(details, 4);
        fixture.inventory.injectItems(stack(Item.getItemFromBlock(Blocks.STONE), 10), Actionable.MODULATE, null);
        TileAssemblyController controller = mock(TileAssemblyController.class);
        when(controller.isFluidPattern(details)).thenReturn(false);
        when(controller.isVirtualPattern(details)).thenReturn(false);
        when(controller.allowanceFor(details)).thenReturn(4L);
        when(controller.canAcceptRealBatch(anyInt())).thenReturn(true);
        when(controller.canAcceptPendingOutputs(any())).thenReturn(true);

        TileAssemblyController.PatternBatchInfo info = new TileAssemblyController.PatternBatchInfo();
        info.slotTemplates = new IAEItemStack[]{stack(Item.getItemFromBlock(Blocks.STONE), 1)};
        info.catalystSlots = new BitSet(1);
        info.transformSlots = new BitSet(1);
        info.recipe = mock(IRecipe.class);
        when(info.recipe.getCraftingResult(any(InventoryCrafting.class))).thenReturn(new ItemStack(Items.DIAMOND));
        NonNullList<ItemStack> remainders = NonNullList.withSize(9, ItemStack.EMPTY);
        remainders.set(0, new ItemStack(Items.STICK));
        when(info.recipe.getRemainingItems(any(InventoryCrafting.class))).thenReturn(remainders);
        when(controller.getPatternBatchInfo(details, fixture.inventory, null)).thenReturn(info);
        List<IAEItemStack> pending = new ArrayList<>();
        when(controller.addPendingOutputs(any())).thenAnswer(invocation -> {
            for (IAEItemStack output : invocation.<Iterable<IAEItemStack>>getArgument(0)) {
                pending.add(output.copy());
            }
            return true;
        });

        fixture.execute(controller, details);

        assertThat(fixture.remaining(details)).isZero();
        assertThat(available(fixture.inventory, Item.getItemFromBlock(Blocks.STONE))).isEqualTo(6);
        assertThat(pending).hasSize(2);
        assertThat(pending).anySatisfy(output -> {
            assertThat(output.getItem()).isEqualTo(Items.DIAMOND);
            assertThat(output.getStackSize()).isEqualTo(4);
        });
        assertThat(pending).anySatisfy(output -> {
            assertThat(output.getItem()).isEqualTo(Items.STICK);
            assertThat(output.getStackSize()).isEqualTo(4);
        });
        assertThat(fixture.waiting.findPrecise(stack(Items.STICK, 1)).getStackSize()).isEqualTo(4);
        assertThat(fixture.injected).isEmpty();
        verify(controller).chargeCraftEnergy(4);
        verify(controller).claimParallel(details, 4);
    }

    private static final class Fixture {
        final ISpecialCpuAccess access = mock(ISpecialCpuAccess.class);
        final CraftingCpuExecutionContext context = new CraftingCpuExecutionContext();
        final CraftingCpuAdapter adapter = new CraftingCpuAdapter(access, context);
        final Map<ICraftingPatternDetails, Object> tasks = new LinkedHashMap<>();
        final MECraftingInventory inventory = new MECraftingInventory();
        final IItemList<IAEItemStack> waiting = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        final List<IAEItemStack> injected = new ArrayList<>();
        final Map<ICraftingPatternDetails, Progress> progress = new IdentityHashMap<>();

        Fixture() {
            when(access.ae2e$context()).thenReturn(context);
            when(access.ae2e$tasks()).thenReturn(tasks);
            when(access.ae2e$inventory()).thenReturn(inventory);
            when(access.ae2e$waitingFor()).thenReturn(waiting);
            when(access.ae2e$remainingOperations()).thenReturn(37);
            when(access.ae2e$taskValue(any())).thenAnswer(invocation ->
                    findProgress(invocation.getArgument(0)).value);
            doAnswer(invocation -> {
                findProgress(invocation.getArgument(0)).value = invocation.getArgument(1);
                return null;
            }).when(access).ae2e$setTaskValue(any(), anyLong());
        }

        Progress findProgress(Object target) {
            for (Progress value : progress.values()) {
                if (value == target) {
                    return value;
                }
            }
            throw new AssertionError("Unknown task progress");
        }

        void add(ICraftingPatternDetails details, long remaining) {
            Progress value = new Progress(remaining);
            tasks.put(details, value);
            progress.put(details, value);
        }

        long remaining(ICraftingPatternDetails details) {
            return progress.get(details).value;
        }

        TileAssemblyController virtualController(ICraftingPatternDetails details, long allowance) {
            TileAssemblyController controller = mock(TileAssemblyController.class);
            when(controller.isFluidPattern(details)).thenReturn(false);
            when(controller.isVirtualPattern(details)).thenReturn(true);
            when(controller.allowanceFor(details)).thenReturn(allowance);
            return controller;
        }

        void execute(TileAssemblyController controller, ICraftingPatternDetails details) {
            TileAssemblyMeInterface medium = mock(TileAssemblyMeInterface.class);
            when(medium.getController()).thenReturn(controller);
            AssemblyBatchService.CpuOperations operations = new AssemblyBatchService.CpuOperations() {
                @Override
                public appeng.api.networking.security.IActionSource actionSource() {
                    return null;
                }

                @Override
                public IAEItemStack inject(IAEItemStack stack, Actionable mode,
                        appeng.api.networking.security.IActionSource source) {
                    injected.add(stack.copy());
                    IAEItemStack expected = waiting.findPrecise(stack);
                    if (expected != null) {
                        expected.decStackSize(stack.getStackSize());
                    }
                    return null;
                }

                @Override
                public IAEItemStack fetchFromNetwork(IAEItemStack request,
                        appeng.api.networking.security.IActionSource source) {
                    return null;
                }
            };
            AssemblyBatchService.execute(adapter, operations, new AssemblyBatchService.MediumAccess() {
                @Override
                public boolean hasAssemblyHub() {
                    return true;
                }

                @Override
                public List<appeng.api.networking.crafting.ICraftingMedium> mediums(
                        ICraftingPatternDetails requested) {
                    return requested == details
                            ? Collections.singletonList(medium) : Collections.emptyList();
                }
            });
        }
    }

    private static final class Progress {
        long value;

        Progress(long value) {
            this.value = value;
        }
    }

    private static final class ShortActualInventory implements IMEInventory<IAEItemStack> {
        private final MECraftingInventory delegate;

        ShortActualInventory(MECraftingInventory delegate) {
            this.delegate = delegate;
        }

        @Override
        public IAEItemStack injectItems(IAEItemStack input, Actionable mode,
                appeng.api.networking.security.IActionSource source) {
            return delegate.injectItems(input, mode, source);
        }

        @Override
        public IAEItemStack extractItems(IAEItemStack request, Actionable mode,
                appeng.api.networking.security.IActionSource source) {
            if (mode == Actionable.MODULATE && request.getItem() == Items.DIAMOND) {
                IAEItemStack shortRequest = request.copy();
                shortRequest.setStackSize(request.getStackSize() / 2);
                return delegate.extractItems(shortRequest, mode, source);
            }
            return delegate.extractItems(request, mode, source);
        }

        @Override
        public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
            return delegate.getAvailableItems(out);
        }

        @Override
        public IStorageChannel<IAEItemStack> getChannel() {
            return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        }
    }
}
