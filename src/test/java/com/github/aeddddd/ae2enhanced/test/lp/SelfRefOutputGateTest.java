package com.github.aeddddd.ae2enhanced.test.lp;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuExecutionContext;
import com.github.aeddddd.ae2enhanced.crafting.ISpecialCpuAccess;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuAccess;
import com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuState;
import com.github.aeddddd.ae2enhanced.specialcrafting.SelfRefOutputGate;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Blocks;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SelfRefOutputGateTest {

    @Test
    public void zeroHeldKeepsFinalOutputAndJobOpen() throws Exception {
        Fixture fixture = fixture(10, 0);

        fixture.settle();

        assertEquals(10L, fixture.finalOutput.getStackSize());
        verify(fixture.access, never()).ae2e$completeJob();
        verify(fixture.access, never()).ae2e$updateCPU();
    }

    @Test
    public void partialHeldIsNotTreatedAsFullDelivery() throws Exception {
        Fixture fixture = fixture(10, 4);

        fixture.settle();

        assertEquals(10L, fixture.finalOutput.getStackSize(), "部分库存不得清零最终产出");
        verify(fixture.access, never()).ae2e$completeJob();
        verify(fixture.access, never()).ae2e$updateCPU();
        verify(fixture.inventory, never()).extractItems(any(IAEItemStack.class), eq(Actionable.MODULATE), any());
    }

    @Test
    public void fullHeldCompletesOnceAtFullRemainingAmount() throws Exception {
        Fixture fixture = fixture(10, 10);

        fixture.settle();

        assertEquals(0L, fixture.finalOutput.getStackSize());
        verify(fixture.access).ae2e$completeJob();
        verify(fixture.access).ae2e$updateCPU();
    }

    private Fixture fixture(long remaining, long held) {
        AE2TestBootstrap.boot();
        CraftingCPUCluster cpu = mock(CraftingCPUCluster.class);
        ISpecialCpuAccess access = mock(ISpecialCpuAccess.class);
        IMEInventory<IAEItemStack> inventory = mock(IMEInventory.class);
        IActionSource source = mock(IActionSource.class);
        IAEItemStack finalOutput = block(Blocks.STONE);
        finalOutput.setStackSize(remaining);
        IItemList<IAEItemStack> waitingFor = appeng.api.AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        when(access.ae2e$waitingFor()).thenReturn(waitingFor);
        when(access.ae2e$tasks()).thenReturn(Collections.emptyMap());
        when(access.ae2e$myLastLink()).thenReturn(null);
        when(access.ae2e$inventory()).thenReturn(inventory);
        when(access.ae2e$context()).thenReturn(new CraftingCpuExecutionContext());
        when(inventory.extractItems(any(IAEItemStack.class), eq(Actionable.SIMULATE), any())).thenAnswer(call -> {
            if (held == 0) {
                return null;
            }
            IAEItemStack result = ((IAEItemStack) call.getArgument(0)).copy();
            result.setStackSize(held);
            return result;
        });
        return new Fixture(cpu, access, inventory, finalOutput, source);
    }

    private static final class Fixture {
        private final CraftingCPUCluster cpu;
        private final ISpecialCpuAccess access;
        private final IMEInventory<IAEItemStack> inventory;
        private final IAEItemStack finalOutput;
        private final IActionSource source;

        private Fixture(CraftingCPUCluster cpu, ISpecialCpuAccess access, IMEInventory<IAEItemStack> inventory,
                IAEItemStack finalOutput, IActionSource source) {
            this.cpu = cpu;
            this.access = access;
            this.inventory = inventory;
            this.finalOutput = finalOutput;
            this.source = source;
        }

        private void settle() throws Exception {
            java.lang.reflect.Method method = SelfRefOutputGate.class.getDeclaredMethod("trySettle",
                    CraftingCpuAccess.class, CraftingCpuState.class, IAEItemStack.class, IActionSource.class);
            method.setAccessible(true);
            method.invoke(null, access, access.ae2e$context(), finalOutput, source);
        }
    }
}
