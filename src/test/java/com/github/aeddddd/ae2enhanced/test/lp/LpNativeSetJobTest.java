package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.MECraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.ReusePatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/** LP 物化结果必须经原生 CraftingTreeNode.setJob 形成真实 CPU 义务. */
public class LpNativeSetJobTest {

    private static CraftingCPUCluster setJob(SimulationEnv env, CraftingJob job) throws Exception {
        CraftingCPUCluster cpu = mock(CraftingCPUCluster.class);
        job.getTree().setJob(new MECraftingInventory(env.networkStorage()), cpu,
                Ae2CraftingReflect.getActionSrc(job));
        return cpu;
    }

    @Test
    public void rootEmitterAddsFullIgnoredRequestToCpu() throws Exception {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack emitted = SimulationEnv.item(Items.DIAMOND);
        env.addEmitable(emitted);
        env.addStoredItem(mult(emitted, 3));

        CraftingJob job = env.runLp(mult(emitted, 10));
        CraftingCPUCluster cpu = setJob(env, job);

        ArgumentCaptor<IAEItemStack> captor = ArgumentCaptor.forClass(IAEItemStack.class);
        verify(cpu).addEmitable(captor.capture());
        assertTrue(captor.getValue().isSameType(emitted));
        assertEquals(10L, captor.getValue().getStackSize(), "根请求库存被 ignore,三份库存不得抵扣发射义务");
    }

    @Test
    public void intermediateEmitterAddsOnlyRequestMinusUsedStock() throws Exception {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack emitted = SimulationEnv.item(Items.DIAMOND);
        IAEItemStack output = block(Blocks.EMERALD_BLOCK);
        env.addPattern(new ProcessingPatternBuilder(output).addPreciseInput(2, emitted).build());
        env.addEmitable(emitted);
        env.addStoredItem(mult(emitted, 3));

        CraftingJob job = env.runLp(mult(output, 5));
        CraftingCPUCluster cpu = setJob(env, job);

        ArgumentCaptor<IAEItemStack> captor = ArgumentCaptor.forClass(IAEItemStack.class);
        verify(cpu).addEmitable(captor.capture());
        assertTrue(captor.getValue().isSameType(emitted));
        assertEquals(7L, captor.getValue().getStackSize(), "需求 10 扣除 used 库存 3,发射 7");
    }

    @Test
    public void selectedVariantProducerIsReachableAndAddedToCpu() throws Exception {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack encoded = block(Blocks.STONE);
        IAEItemStack alternative = block(Blocks.DIRT);
        IAEItemStack raw = block(Blocks.SAND);
        IAEItemStack output = block(Blocks.COBBLESTONE);
        ICraftingPatternDetails alternativeProducer = env.addPattern(
                new ProcessingPatternBuilder(alternative).addPreciseInput(1, raw).build());
        ICraftingPatternDetails rootPattern = env.addPattern(new ReusePatternBuilder(output)
                .addPreciseInput(1, encoded)
                .nativeStyleSubstituteList()
                .substituteAlternatives(encoded, alternative)
                .canSubstitute(true)
                .build());
        env.addStoredItem(mult(raw, 100));

        CraftingJob job = env.runLp(mult(output, 5));
        CraftingCPUCluster cpu = setJob(env, job);

        ArgumentCaptor<Long> alternativeCrafts = ArgumentCaptor.forClass(Long.class);
        verify(cpu).addCrafting(eq(alternativeProducer), alternativeCrafts.capture());
        assertEquals(5L, alternativeCrafts.getValue(), "LP 选中的候选生产者必须进入原生树并交给 CPU");
        verify(cpu).addCrafting(rootPattern, 5L);
    }
}
