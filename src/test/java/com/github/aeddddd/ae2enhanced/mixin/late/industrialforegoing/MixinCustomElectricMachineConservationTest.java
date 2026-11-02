package com.github.aeddddd.ae2enhanced.mixin.late.industrialforegoing;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerBindingRegistry;
import com.github.aeddddd.ae2enhanced.recycler.RecyclerNetworkHandler;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MixinCustomElectricMachineConservationTest {

    private final BlockPos pos = BlockPos.ORIGIN;
    private final TargetManager.TargetRef ref = new TargetManager.TargetRef(0, pos, EnumFacing.UP);
    private World world;
    private RecyclerNetworkHandler recycler;
    private boolean oldRedirect;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static void setFinal(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = net.minecraft.world.World.class.getDeclaredField(name);
            field.setAccessible(true);
            java.lang.reflect.Field modifiers = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @BeforeEach
    void setup() {
        RuntimeScopes.currentServer().ifPresent(scope -> {
            scope.close();
            RuntimeScopes.releaseServer();
        });
        RuntimeScopes.startServer();
        world = mock(World.class);
        WorldProvider provider = mock(WorldProvider.class);
        when(provider.getDimension()).thenReturn(0);
        setFinal(world, "provider", provider);
        setFinal(world, "rand", new Random(0));
        setFinal(world, "isRemote", false);
        recycler = mock(RecyclerNetworkHandler.class);
        RecyclerBindingRegistry.getInstance().register(ref, recycler, null);
        oldRedirect = AE2EnhancedConfig.recycler.machineOutputRedirect;
        AE2EnhancedConfig.recycler.machineOutputRedirect = true;
    }

    @AfterEach
    void cleanup() {
        RecyclerBindingRegistry.getInstance().unregister(ref);
        AE2EnhancedConfig.recycler.machineOutputRedirect = oldRedirect;
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();
    }

    @Test
    void redirectsOnlyActuallyExtractedItemsAndRestoresRemainder() throws Exception {
        FakeItemHandler handler = new FakeItemHandler(1);
        handler.setStack(0, new ItemStack(Items.APPLE, 10));
        when(recycler.tryInjectMachineOutput(any())).thenAnswer(invocation -> {
            ItemStack input = invocation.getArgument(0);
            ItemStack remainder = input.copy();
            remainder.setCount(4);
            return remainder;
        });

        redirect(handler);

        ArgumentCaptor<ItemStack> redirected = ArgumentCaptor.forClass(ItemStack.class);
        verify(recycler).tryInjectMachineOutput(redirected.capture());
        assertThat(redirected.getValue().getCount()).isEqualTo(10);
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(4);
    }

    @Test
    void shortRealExtractionLimitsInjectionInsteadOfUsingOriginalStackSize() throws Exception {
        FakeItemHandler delegate = new FakeItemHandler(1);
        delegate.setStack(0, new ItemStack(Items.APPLE, 10));
        IItemHandler handler = new LimitedExtractionHandler(delegate, 6);
        when(recycler.tryInjectMachineOutput(any())).thenAnswer(invocation -> {
            ItemStack input = invocation.getArgument(0);
            ItemStack remainder = input.copy();
            remainder.setCount(2);
            return remainder;
        });

        redirect(handler);

        ArgumentCaptor<ItemStack> redirected = ArgumentCaptor.forClass(ItemStack.class);
        verify(recycler).tryInjectMachineOutput(redirected.capture());
        assertThat(redirected.getValue().getCount()).isEqualTo(6);
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(6);
    }

    @Test
    void rejectedReinsertionDropsExactRemainder() throws Exception {
        IItemHandler handler = new RejectingInsertHandler(new ItemStack(Items.APPLE, 10));
        when(recycler.tryInjectMachineOutput(any())).thenAnswer(invocation -> {
            ItemStack input = invocation.getArgument(0);
            ItemStack remainder = input.copy();
            remainder.setCount(4);
            return remainder;
        });

        redirect(handler);

        ArgumentCaptor<net.minecraft.entity.Entity> spawned = ArgumentCaptor.forClass(net.minecraft.entity.Entity.class);
        verify(world).spawnEntity(spawned.capture());
        assertThat(spawned.getValue()).isInstanceOf(EntityItem.class);
        assertThat(((EntityItem) spawned.getValue()).getItem().getCount()).isEqualTo(4);
    }

    private void redirect(IItemHandler handler) throws Exception {
        MixinCustomElectricMachine mixin = new MixinCustomElectricMachine();
        Method method = MixinCustomElectricMachine.class.getDeclaredMethod("redirectHandler",
                IItemHandler.class, World.class, BlockPos.class);
        method.setAccessible(true);
        method.invoke(mixin, handler, world, pos);
    }

    private static final class LimitedExtractionHandler implements IItemHandler {
        private final FakeItemHandler delegate;
        private final int limit;

        private LimitedExtractionHandler(FakeItemHandler delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, Math.min(amount, limit), simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }
    }

    private static final class RejectingInsertHandler implements IItemHandler {
        private ItemStack stack;

        private RejectingInsertHandler(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack result = stack.copy();
            if (!simulate) {
                stack = ItemStack.EMPTY;
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    }
}
