package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.google.common.collect.ImmutableList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.github.aeddddd.ae2enhanced.craftingplan.dag.DagCompiler;
import com.github.aeddddd.ae2enhanced.craftingplan.dag.DagFallback;
import com.github.aeddddd.ae2enhanced.craftingplan.dag.DagGraph;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

/**
 * {@link DagCompiler} 的直接单元测试:不经过完整计划流程,
 * 用 mock 合成服务编译小样版图,断言节点类型、合并、拓扑序与回落原因.
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class DagCompilerTest {

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    /** 以样板表构造合成服务 mock(无发射台). */
    private static ICraftingService service(Map<AEKey, List<IPatternDetails>> patterns) {
        return service(patterns, Set.of());
    }

    /** 以样板表与发射台集合构造合成服务 mock. */
    private static ICraftingService service(Map<AEKey, List<IPatternDetails>> patterns, Set<AEKey> emitters) {
        ICraftingService service = mock(ICraftingService.class);
        when(service.getCraftingFor(any(AEKey.class))).thenAnswer(inv -> {
            var list = patterns.get(inv.getArgument(0));
            return list == null ? ImmutableList.of() : ImmutableList.copyOf(list);
        });
        when(service.canEmitFor(any(AEKey.class))).thenAnswer(inv -> emitters.contains(inv.getArgument(0)));
        return service;
    }

    /** 拓扑序不变量:每条边上父节点(需求方)下标小于子节点(原料方). */
    private static void assertTopoOrder(DagGraph graph) {
        var index = new IdentityHashMap<DagGraph.DagNode, Integer>();
        for (int i = 0; i < graph.topoOrder.size(); i++) {
            index.put(graph.topoOrder.get(i), i);
        }
        assertThat(index).hasSize(graph.topoOrder.size()); // 无重复节点
        for (var node : graph.topoOrder) {
            for (var edge : node.edges) {
                assertThat(index.get(node)).as("父节点应先于子节点").isLessThan(index.get(edge.child()));
            }
        }
    }

    /** 简单两步链:根 NORMAL,终端叶子 TERMINAL,拓扑序父先子后. */
    @Test
    public void testSimpleChainStructure() throws DagFallback {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(cobble.what(),
                List.of(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build()));

        var graph = DagCompiler.compile(service(patterns), cobble.what());

        assertThat(graph.root.kind).isEqualTo(DagGraph.Kind.NORMAL);
        assertThat(graph.root.key).isEqualTo(cobble.what());
        assertThat(graph.root.outputPerCraft).isEqualTo(1);
        assertThat(graph.root.pattern).isNotNull();
        assertThat(graph.root.edges).hasSize(1);
        var edge = graph.root.edges.get(0);
        assertThat(edge.perCraft()).isEqualTo(1);
        assertThat(edge.child().kind).isEqualTo(DagGraph.Kind.TERMINAL); // 无样板且不可发射
        assertThat(edge.child().edges).isEmpty();
        assertThat(graph.topoOrder).hasSize(2);
        assertThat(graph.topoOrder.get(0)).isSameAs(graph.root);
        assertTopoOrder(graph);
    }

    /** 节点按 key 合并:D 需 B+C,B、C 各自需 A,A 节点全局唯一共享. */
    @Test
    public void testSharedSubtreeNodeMerged() throws DagFallback {
        var a = item(Items.STONE);
        var b = item(Items.COBBLESTONE);
        var c = item(Items.SAND);
        var d = item(Items.DIRT);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(b.what(), List.of(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build()));
        patterns.put(c.what(), List.of(new ProcessingPatternBuilder(c).addPreciseInput(1, a).build()));
        patterns.put(d.what(), List.of(new ProcessingPatternBuilder(d)
                .addPreciseInput(1, b)
                .addPreciseInput(1, c)
                .build()));

        var graph = DagCompiler.compile(service(patterns), d.what());

        assertThat(graph.topoOrder).hasSize(4); // A 只编译一次
        var bNode = graph.root.edges.get(0).child();
        var cNode = graph.root.edges.get(1).child();
        assertThat(bNode.edges.get(0).child()).isSameAs(cNode.edges.get(0).child());
        assertTopoOrder(graph);
    }

    /** 发射台叶子:无样板但 canEmitFor → EMITTER,零成本无输入边. */
    @Test
    public void testEmitterLeafKind() throws DagFallback {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(cobble.what(),
                List.of(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build()));

        var graph = DagCompiler.compile(service(patterns, Set.of(stone.what())), cobble.what());

        var stoneNode = graph.root.edges.get(0).child();
        assertThat(stoneNode.kind).isEqualTo(DagGraph.Kind.EMITTER);
        assertThat(stoneNode.pattern).isNull();
        assertThat(stoneNode.edges).isEmpty();
    }

    /** 根请求本身可发射:整图只有一个 EMITTER 根. */
    @Test
    public void testEmitterRoot() throws DagFallback {
        var stone = item(Items.STONE);

        var graph = DagCompiler.compile(service(Map.of(), Set.of(stone.what())), stone.what());

        assertThat(graph.root.kind).isEqualTo(DagGraph.Kind.EMITTER);
        assertThat(graph.topoOrder).containsExactly(graph.root);
    }

    /** 根请求无任何样板且不可发射:整图只有一个 TERMINAL 根. */
    @Test
    public void testTerminalRoot() throws DagFallback {
        var stone = item(Items.STONE);

        var graph = DagCompiler.compile(service(Map.of()), stone.what());

        assertThat(graph.root.kind).isEqualTo(DagGraph.Kind.TERMINAL);
        assertThat(graph.topoOrder).containsExactly(graph.root);
    }

    /** 边上数量 = 输入堆叠数 × 输入倍率;单次产出 = 主输出数量. */
    @Test
    public void testOutputAndEdgeAmounts() throws DagFallback {
        var planks = item(Items.OAK_PLANKS);
        var sticks = item(Items.STICK);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        // 2 木板 → 4 木棍
        patterns.put(sticks.what(), List.of(new ProcessingPatternBuilder(mult(sticks, 4))
                .addPreciseInput(2, planks)
                .build()));

        var graph = DagCompiler.compile(service(patterns), sticks.what());

        assertThat(graph.root.outputPerCraft).isEqualTo(4);
        assertThat(graph.root.edges.get(0).perCraft()).isEqualTo(2);
    }

    /** 同一 key 的多个输出条目应累加为 outputPerCraft. */
    @Test
    public void testMultipleOutputsSameKeySummed() throws DagFallback {
        var planks = item(Items.OAK_PLANKS);
        var sticks = item(Items.STICK);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(sticks.what(), List.of(new ProcessingPatternBuilder(mult(sticks, 2), mult(sticks, 3))
                .addPreciseInput(1, planks)
                .build()));

        var graph = DagCompiler.compile(service(patterns), sticks.what());

        assertThat(graph.root.outputPerCraft).isEqualTo(5);
    }

    /** 副产物(不同 key)不计入本 key 的 outputPerCraft. */
    @Test
    public void testByproductNotCountedInOutputPerCraft() throws DagFallback {
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        // 1 石头 → 1 圆石 + 2 沙子(副产物)
        patterns.put(cobble.what(), List.of(new ProcessingPatternBuilder(cobble, mult(sand, 2))
                .addPreciseInput(1, stone)
                .build()));

        var graph = DagCompiler.compile(service(patterns), cobble.what());

        assertThat(graph.root.outputPerCraft).isEqualTo(1);
    }

    /** 多候选(tag/替代)输入的样板不干净 → 整单回落,原因带 key. */
    @Test
    public void testUncleanPatternFallsBack() {
        var stone = item(Items.STONE);
        var dirt = item(Items.DIRT);
        var cobble = item(Items.COBBLESTONE);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(cobble.what(),
                List.of(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone, dirt).build()));

        assertThatThrownBy(() -> DagCompiler.compile(service(patterns), cobble.what()))
                .isInstanceOf(DagFallback.class)
                .hasMessageStartingWith("unclean_inputs:");
    }

    /** 首个候选不干净时跳过、选后续干净候选. */
    @Test
    public void testCleanCandidateChosenOverUnclean() throws DagFallback {
        var stone = item(Items.STONE);
        var dirt = item(Items.DIRT);
        var cobble = item(Items.COBBLESTONE);
        var unclean = new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone, dirt).build();
        var clean = new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build();
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(cobble.what(), List.of(unclean, clean));

        var graph = DagCompiler.compile(service(patterns), cobble.what());

        assertThat(graph.root.kind).isEqualTo(DagGraph.Kind.NORMAL);
        assertThat(graph.root.pattern).isSameAs(clean);
    }

    /** 合成服务返回的样板不产出请求 key → 防御性回落. */
    @Test
    public void testPatternWithoutMatchingOutputFallsBack() {
        var stone = item(Items.STONE);
        var dirt = item(Items.DIRT);
        var cobble = item(Items.COBBLESTONE);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        // 为 stone 注册的样板只产出 cobble(异常网络状态)
        patterns.put(stone.what(),
                List.of(new ProcessingPatternBuilder(cobble).addPreciseInput(1, dirt).build()));

        assertThatThrownBy(() -> DagCompiler.compile(service(patterns), stone.what()))
                .isInstanceOf(DagFallback.class)
                .hasMessageStartingWith("pattern_without_output:");
    }

    /** 深层自增殖(根无环、中间节点在环上)→ 中间节点收缩为 CYCLE 叶子. */
    @Test
    public void testDeepCycleCompilesToBoundaryLeaf() throws DagFallback {
        var c = item(Items.COBBLESTONE);
        var d = item(Items.DIRT);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(d.what(), List.of(new ProcessingPatternBuilder(d).addPreciseInput(1, c).build()));
        patterns.put(c.what(),
                List.of(new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, c).build()));

        var graph = DagCompiler.compile(service(patterns), d.what());

        assertThat(graph.root.kind).isEqualTo(DagGraph.Kind.NORMAL);
        var cNode = graph.root.edges.get(0).child();
        assertThat(cNode.kind).isEqualTo(DagGraph.Kind.CYCLE);
        assertThat(cNode.edges).isEmpty(); // 边界为叶子,输入遍历委托 CycleBoundarySolver
        assertThat(graph.topoOrder).hasSize(2);
        assertTopoOrder(graph);
    }

    /** 根本身在环上:根直接编译为 CYCLE 节点,不回落. */
    @Test
    public void testRootCycleCompilesToCycleNode() throws DagFallback {
        var c = item(Items.COBBLESTONE);
        var patterns = new HashMap<AEKey, List<IPatternDetails>>();
        patterns.put(c.what(),
                List.of(new ProcessingPatternBuilder(mult(c, 2)).addPreciseInput(1, c).build()));

        var graph = DagCompiler.compile(service(patterns), c.what());

        assertThat(graph.root.kind).isEqualTo(DagGraph.Kind.CYCLE);
        assertThat(graph.topoOrder).containsExactly(graph.root);
    }

    /** describe 辅助:null → null,否则透传 reason. */
    @Test
    public void testDescribe() {
        assertThat(DagCompiler.describe(null)).isNull();
        assertThat(DagCompiler.describe(new DagFallback("budget_nodes_exceeded")))
                .isEqualTo("budget_nodes_exceeded");
    }

    /** 节点数预算常量(病态网络保护)固化. */
    @Test
    public void testNodeBudgetConstant() {
        assertThat(DagCompiler.MAX_NODES).isEqualTo(100_000);
    }
}
