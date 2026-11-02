package com.github.aeddddd.ae2enhanced.test.lp;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.specialcrafting.*;
import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.ReusePatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;
import net.minecraft.init.Blocks;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResolvedPlanContractTest {

    @Test
    public void modelIsDetachedAndImmutable() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack input = block(Blocks.STONE);
        IAEItemStack output = block(Blocks.DIRT);
        env.addPattern(new ProcessingPatternBuilder(output).addPreciseInput(1, input).build());
        env.addStoredItem(mult(input, 20));
        LpCraftingJob job = (LpCraftingJob) env.runLp(mult(output, 10));
        ResolvedPlan plan = job.resolvedPlan();

        assertThat(plan).isNotNull();
        assertThat(plan.executions()).hasSize(1);
        assertThat(plan.executions().get(0).count()).isEqualTo(10L);
        assertThatThrownBy(() -> plan.executions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.initialExtractions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.missing().clear()).isInstanceOf(UnsupportedOperationException.class);
        Map<IAEItemStack, Long> detached = plan.initialExtractions();
        detached.keySet().iterator().next().setStackSize(999);
        assertThat(plan.initialExtractions().get(detached.keySet().iterator().next())).isEqualTo(10L);
        IAEItemStack root = plan.rootDemand();
        root.setStackSize(1);
        assertThat(plan.rootDemand().getStackSize()).isEqualTo(10L);
    }

    @Test
    public void missingDuplicateAndExtractionDamageAreRejected() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack input = block(Blocks.STONE);
        IAEItemStack output = block(Blocks.DIRT);
        env.addPattern(new ProcessingPatternBuilder(output).addPreciseInput(1, input).build());
        env.addStoredItem(mult(input, 20));
        LpCraftingJob missing = (LpCraftingJob) env.runLp(mult(output, 10));
        LpPlanMaterializer.Report report = missing.materializationReport();
        assertThat(report.committable()).isTrue();
        Ae2CraftingReflect.getNodeProcesses(missing.getTree()).clear();
        assertThat(report.revalidate(missing.getTree()).reasons())
                .anyMatch(reason -> reason.contains("活跃执行未物化"));

        LpCraftingJob duplicate = (LpCraftingJob) env.runLp(mult(output, 10));
        CraftingTreeProcess process = Ae2CraftingReflect.getNodeProcesses(duplicate.getTree()).get(0);
        Ae2CraftingReflect.getNodeProcesses(duplicate.getTree()).add(process);
        assertThat(duplicate.materializationReport().revalidate(duplicate.getTree()).reasons())
                .anyMatch(reason -> reason.contains("重复"));

        LpCraftingJob extraction = (LpCraftingJob) env.runLp(mult(output, 10));
        Ae2CraftingReflect.getNodeUsed(extraction.getTree()).resetStatus();
        assertThat(extraction.materializationReport().revalidate(extraction.getTree()).reasons())
                .anyMatch(reason -> reason.contains("初始提取不一致"));
    }

    @Test
    public void planTrackerChecksMaterializationNotOnlyMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack input = block(Blocks.STONE);
        IAEItemStack output = block(Blocks.DIRT);
        env.addPattern(new ProcessingPatternBuilder(output).addPreciseInput(1, input).build());
        env.addStoredItem(mult(input, 20));
        LpCraftingJob job = (LpCraftingJob) env.runLp(mult(output, 10));
        Ae2CraftingReflect.getNodeProcesses(job.getTree()).clear();
        long before = MetricsRegistry.counter("plan.verify.violation").get();
        PlanObservationFactory.onJobComputed(job, 0L);
        assertThat(MetricsRegistry.counter("plan.verify.violation").get()).isEqualTo(before + 1);
    }

    @Test
    public void emitterVariantAndSharedOutputsRemainMaterializable() {
        SimulationEnv emitterEnv = new SimulationEnv();
        IAEItemStack emitted = block(Blocks.STONE);
        IAEItemStack emitterOutput = block(Blocks.DIRT);
        emitterEnv.addPattern(new ProcessingPatternBuilder(emitterOutput).addPreciseInput(2, emitted).build());
        emitterEnv.addEmitable(emitted);
        emitterEnv.addStoredItem(mult(emitted, 3));
        LpCraftingJob emitter = (LpCraftingJob) emitterEnv.runLp(mult(emitterOutput, 5));
        assertThat(emitter.materializationReport().committable()).isTrue();
        assertThat(emitter.resolvedPlan().emissionObligations().values()).containsExactly(7L);
        Ae2CraftingReflect.setNodeEmitted(
                Ae2CraftingReflect.sortedProcessNodes(
                        Ae2CraftingReflect.getNodeProcesses(emitter.getTree()).get(0)).get(0), 0L);
        assertThat(emitter.materializationReport().revalidate(emitter.getTree()).committable()).isFalse();

        SimulationEnv variantEnv = new SimulationEnv();
        IAEItemStack encoded = block(Blocks.STONE);
        IAEItemStack alternate = block(Blocks.DIRT);
        IAEItemStack variantOutput = block(Blocks.COBBLESTONE);
        variantEnv.addPattern(new ReusePatternBuilder(variantOutput).addPreciseInput(1, encoded)
                .nativeStyleSubstituteList().substituteAlternatives(encoded, alternate)
                .canSubstitute(true).build());
        variantEnv.addStoredItem(mult(alternate, 10));
        LpCraftingJob variant = (LpCraftingJob) variantEnv.runLp(mult(variantOutput, 5));
        assertThat(variant.materializationReport().committable()).isTrue();
        assertThat(variant.resolvedPlan().executions().get(0).variantInputs()).isNotEmpty();
        assertThat(PlanView.of(variant).usedItems().get(alternate)).isEqualTo(5L);

        SimulationEnv sharedEnv = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack x = block(Blocks.COBBLESTONE);
        IAEItemStack b = block(Blocks.DIRT);
        sharedEnv.addPattern(new ProcessingPatternBuilder(x, b).addPreciseInput(1, a).build());
        sharedEnv.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, b).build());
        sharedEnv.addStoredItem(a);
        LpCraftingJob shared = (LpCraftingJob) sharedEnv.runLp(mult(x, 10));
        assertThat(shared.materializationReport().committable()).isTrue();
        assertThat(shared.resolvedPlan().executions()).hasSize(2);
        assertThat(shared.resolvedPlan().hasCycleBoundary()).isTrue();
    }

    @Test
    public void interruptionDoesNotInvokeNativeFallback() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack input = block(Blocks.STONE);
        IAEItemStack output = block(Blocks.DIRT);
        env.addPattern(new ProcessingPatternBuilder(output).addPreciseInput(1, input).build());
        env.addStoredItem(mult(input, 20));
        LpCraftingJob job = new LpCraftingJob(env.world(), env.grid(), env.actionSource(), mult(output, 10), null) {
            @Override
            protected CraftingTreeNode computeLpPlan(
                    com.github.aeddddd.ae2enhanced.specialcrafting.RequestConfigSnapshot config)
                    throws InterruptedException {
                throw new InterruptedException("test cancel");
            }
        };
        env.runJobTimed(job, 10, TimeUnit.SECONDS);
        assertThat(PlanView.of(job).patternTimes()).isEmpty();
        assertThat(job.isSimulation()).isFalse();
        assertThat(job.materializationReport()).isNull();
    }

    @Test
    public void exceptionFallbackDiscardsPartialTree() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack input = block(Blocks.STONE);
        IAEItemStack output = block(Blocks.DIRT);
        env.addPattern(new ProcessingPatternBuilder(output).addPreciseInput(1, input).build());
        env.addStoredItem(mult(input, 20));
        LpCraftingJob job = new LpCraftingJob(env.world(), env.grid(), env.actionSource(), mult(output, 10), null) {
            @Override
            protected CraftingTreeNode computeLpPlan(
                    com.github.aeddddd.ae2enhanced.specialcrafting.RequestConfigSnapshot config) {
                CraftingTreeNode partial = new CraftingTreeNode(Ae2CraftingReflect.getCc(this), this,
                        this.getOutput().copy(), null, -1, 0);
                IAEItemStack contaminated = input.copy();
                contaminated.setStackSize(999);
                Ae2CraftingReflect.getNodeUsed(partial).add(contaminated);
                Ae2CraftingReflect.setTree(this, partial);
                throw new IllegalStateException("test failure");
            }
        };
        env.runJobTimed(job, 10, TimeUnit.SECONDS);
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).isFalse();
        assertThat(view.patternTimes().values()).containsExactly(10L);
        assertThat(view.usedItems().values()).containsExactly(10L);
        assertThat(job.resolvedPlan()).isNull();
    }
}
