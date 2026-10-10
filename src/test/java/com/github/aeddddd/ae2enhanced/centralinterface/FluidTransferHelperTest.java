package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link FluidTransferHelper} 的流体生命周期测试：收集判据、{@code canDrain} 闸门、
 * 阶段 1/2 的匹配逻辑，以及发配前清理的探察（{@code peekExtractableFluids}）与真实回收同判据。
 *
 * <p>目标机器用 Mockito 的 {@link TileEntity} 替身按<b>面</b>注入
 * {@link FakeFluidHandler}（其余面没有流体能力），因此"只在特定面暴露流体"、
 * "属性说 canDrain 但真实 drain 按侧面配置拒绝"这类实机形态都能在这里复现。</p>
 *
 * <h3>环境耦合（重要，改动本类前必读）</h3>
 * <ul>
 *   <li><b>公共前置</b>：{@link AE2TestBootstrap#boot()}（MC 注册表 + AE2 配置/API 引导）
 *       与 {@code ItemRegistry.FLUID_DROP} 的注入。缺任何一个，{@code ItemStack} / AE2 单例
 *       会以 ExceptionInInitializerError 之类的形式在任意位置挂掉。</li>
 *   <li><b>依赖 {@code ItemFluidDrop}（本 mod 自己的假物品）</b>：这类断言在无头环境可达——
 *       {@code ItemFluidDrop}/{@code AbstractNbtDrop} 不触发任何第三方兼容层。</li>
 *   <li><b>依赖 {@code Ae2fcFluidCompat}（第三方兼容层），但恒走"ae2fc 未安装"分支</b>：
 *       该兼容层已加固为"未装载 FML {@code Loader} 也不抛异常"（安装检测用类加载器资源探测，
 *       见 {@code Ae2fcFluidCompat} 类 javadoc），无头测试 JVM 的 classpath 里没有 ae2fc，
 *       因此 {@code isAe2fcFluidFakeItem} 恒 false、{@code getFluidStack} 只认本 mod 的
 *       Fluid Drop。由此：
 *       <ul>
 *         <li>{@code collectFluidProducts} 的<b>阶段 1 公共入口</b>（先经
 *             {@code extractFluidFromItemStack} 把样板产物 ItemStack 解析成 FluidStack）
 *             在无头环境只覆盖"样板产物是本 mod Fluid Drop"这一形态，ae2fc 假物品形态的
 *             真机行为<b>需实机验证</b>；本类的阶段 1 用例直接驱动包可见的
 *             {@link FluidTransferHelper#tryCollectExpectedFluid}（判据本体，不碰兼容层）。</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public class FluidTransferHelperTest {

    private static final BlockPos POS = new BlockPos(9, 70, 9);

    private World world;
    private TileEntity target;

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
        // 流体假物品需要本 mod 的 Fluid Drop 实例；测试环境没有正常的注册流程，这里直接注入。
        if (ItemRegistry.FLUID_DROP == null) {
            ItemRegistry.FLUID_DROP = new ItemFluidDrop();
        }
    }

    @BeforeEach
    public void setUp() {
        this.world = mock(World.class);
        this.target = mock(TileEntity.class);
        when(this.world.getTileEntity(POS)).thenReturn(this.target);
    }

    // ------------------------------------------------------------------
    // 收集：阶段 1 / 阶段 2
    // ------------------------------------------------------------------

    /** 阶段 2 兜底：机器声明可抽取的输出罐会被抽干，且是真的抽（不是只读）。 */
    @Test
    public void testPhase2CollectsDrainableTank() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 1);
        face.setTank(0, new FluidStack(FluidRegistry.LAVA, 500));

        List<FluidStack> collected = FluidTransferHelper.collectFluidProducts(world, POS, session(null));

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getFluid()).isEqualTo(FluidRegistry.LAVA);
        assertThat(collected.get(0).amount).isEqualTo(500);
        assertThat(face.contents(0)).isNull();
        assertThat(face.realDrained()).hasSize(1);
    }

    /** {@code canDrain == false} 的罐（机器声明的输入罐）一个字节都不许动。 */
    @Test
    public void testPhase2SkipsTankDeclaredNotDrainable() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 1);
        face.setTankNotDrainable(0, new FluidStack(FluidRegistry.WATER, 1000));

        List<FluidStack> collected = FluidTransferHelper.collectFluidProducts(world, POS, session(null));

        assertThat(collected).isEmpty();
        assertThat(face.contents(0).amount).isEqualTo(1000);
        assertThat(face.realDrained()).isEmpty();
    }

    /**
     * 属性说 canDrain、真实 {@code drain()} 却按侧面配置拒绝的机器（TE 的形态）：
     * 收集为空且流体原封不动——绝不能因为属性"看起来可抽"就把它当产物销毁。
     */
    @Test
    public void testPhase2NeverLosesFluidWhenDrainIsRefused() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 1);
        face.setTank(0, new FluidStack(FluidRegistry.LAVA, 500));
        face.refuseDrain();

        List<FluidStack> collected = FluidTransferHelper.collectFluidProducts(world, POS, session(null));

        assertThat(collected).isEmpty();
        assertThat(face.contents(0).amount).isEqualTo(500);
    }

    /**
     * 同一面上"输入罐压住输出罐"：本批推入的流体必须跳过，同面其它罐的产物照收。
     */
    @Test
    public void testPhase2SkipsBatchInputFluidButKeepsSiblingTanks() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 2);
        face.setTank(0, new FluidStack(FluidRegistry.WATER, 800));
        face.setTank(1, new FluidStack(FluidRegistry.LAVA, 300));

        TargetSession session = session(Collections.singletonList(new FluidStack(FluidRegistry.WATER, 800)));
        List<FluidStack> collected = FluidTransferHelper.collectFluidProducts(world, POS, session);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getFluid()).isEqualTo(FluidRegistry.LAVA);
        assertThat(face.contents(0).amount).isEqualTo(800);
        assertThat(face.contents(1)).isNull();
    }

    /**
     * 阶段 1 判据：样板声明的流体产物足量时按预期精确收取.
     *
     * <p>直接驱动 {@link FluidTransferHelper#tryCollectExpectedFluid}（包可见）而不是走
     * {@code collectFluidProducts}：后者的阶段 1 入口要先解析样板产物 ItemStack，本方法只覆盖
     * 判定本体，用例无需构造流体假物品产物（见类注释"环境耦合"）。</p>
     */
    @Test
    public void testPhase1CollectsWhenTankHoldsExpectedAmount() {
        FakeFluidHandler face = installFace(EnumFacing.UP, 1);
        face.setTank(0, new FluidStack(FluidRegistry.LAVA, 500));
        List<FluidStack> collected = new ArrayList<>();

        boolean matched = FluidTransferHelper.tryCollectExpectedFluid(target, EnumFacing.UP,
                new FluidStack(FluidRegistry.LAVA, 500), Collections.emptyList(), collected);

        assertThat(matched).isTrue();
        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getFluid()).isEqualTo(FluidRegistry.LAVA);
        assertThat(collected.get(0).amount).isEqualTo(500);
        assertThat(face.contents(0)).isNull();
    }

    /** 阶段 1 对没有流体能力的面直接放弃：不抛异常、不误抽。 */
    @Test
    public void testPhase1SkipsFaceWithoutCapability() {
        List<FluidStack> collected = new ArrayList<>();

        assertThat(FluidTransferHelper.tryCollectExpectedFluid(target, EnumFacing.UP,
                new FluidStack(FluidRegistry.LAVA, 100), Collections.emptyList(), collected)).isFalse();
        assertThat(collected).isEmpty();
    }

    /**
     * 阶段 1 只认"足量"：罐里不足预期量时整条拒绝（模拟抽取判定失败）、
     * **一滴都不抽**，留给阶段 2 兜底收走。
     */
    @Test
    public void testPhase1RejectsPartialAmountAndPhase2TakesIt() {
        FakeFluidHandler face = installFace(EnumFacing.UP, 1);
        face.setTank(0, new FluidStack(FluidRegistry.LAVA, 300));
        List<FluidStack> collected = new ArrayList<>();

        boolean matched = FluidTransferHelper.tryCollectExpectedFluid(target, EnumFacing.UP,
                new FluidStack(FluidRegistry.LAVA, 1000), Collections.emptyList(), collected);

        assertThat(matched).isFalse();
        assertThat(collected).isEmpty();
        assertThat(face.contents(0).amount).isEqualTo(300);

        // 阶段 2（无头可达）把现有的 300 收走，不丢
        List<FluidStack> viaPhase2 = FluidTransferHelper.collectFluidProducts(world, POS, session(null));
        assertThat(viaPhase2).hasSize(1);
        assertThat(viaPhase2.get(0).amount).isEqualTo(300);
        assertThat(face.contents(0)).isNull();
    }

    /** 阶段 1 同样不得把"与预期产物同种的本批输入流体"当成产物。 */
    @Test
    public void testPhase1SkipsFluidThatIsBatchInput() {
        FakeFluidHandler face = installFace(EnumFacing.UP, 1);
        face.setTank(0, new FluidStack(FluidRegistry.WATER, 1000));
        List<FluidStack> collected = new ArrayList<>();

        boolean matched = FluidTransferHelper.tryCollectExpectedFluid(target, EnumFacing.UP,
                new FluidStack(FluidRegistry.WATER, 1000),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000)), collected);

        assertThat(matched).isFalse();
        assertThat(collected).isEmpty();
        assertThat(face.contents(0).amount).isEqualTo(1000);
    }

    /** 没有流体能力的面被跳过，其它面的罐照样收集。 */
    @Test
    public void testCollectIgnoresFacesWithoutCapability() {
        FakeFluidHandler top = installFace(EnumFacing.UP, 1);
        top.setTank(0, new FluidStack(FluidRegistry.LAVA, 100));

        List<FluidStack> collected = FluidTransferHelper.collectFluidProducts(world, POS, session(null));

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).amount).isEqualTo(100);
    }

    /** 目标不存在/未加载时返回空列表而不是抛异常。 */
    @Test
    public void testCollectWithoutTargetReturnsEmpty() {
        when(this.world.getTileEntity(POS)).thenReturn(null);

        assertThat(FluidTransferHelper.collectFluidProducts(world, POS, session(null))).isEmpty();
        assertThat(FluidTransferHelper.drainExtractableFluids(world, POS, null)).isEmpty();
        assertThat(FluidTransferHelper.pushFluidsToTarget(world, POS,
                Collections.singletonList(new FluidStack(FluidRegistry.LAVA, 10))))
                .hasSize(1);
    }

    /** 一台机器都没有流体能力时收集为空（不抛异常、不误抽）。 */
    @Test
    public void testCollectWithoutAnyFluidFaceReturnsEmpty() {
        assertThat(FluidTransferHelper.collectFluidProducts(world, POS, session(null))).isEmpty();
    }

    // ------------------------------------------------------------------
    // 发配前清理输出罐
    // ------------------------------------------------------------------

    /** 清理只抽可抽取的罐：输入罐（canDrain == false）不动。 */
    @Test
    public void testDrainExtractableFluidsRespectsCanDrainGate() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 2);
        face.setTankNotDrainable(0, new FluidStack(FluidRegistry.WATER, 1000));
        face.setTank(1, new FluidStack(FluidRegistry.LAVA, 250));

        List<FluidStack> drained = FluidTransferHelper.drainExtractableFluids(world, POS, null);

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).getFluid()).isEqualTo(FluidRegistry.LAVA);
        assertThat(face.contents(0).amount).isEqualTo(1000);
        assertThat(face.contents(1)).isNull();
    }

    /** 本批将要推送的流体不得被当成上一轮残留回收（否则新配方还没开始就丢了输入）。 */
    @Test
    public void testDrainExtractableFluidsSkipsBatchFluids() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 1);
        face.setTank(0, new FluidStack(FluidRegistry.WATER, 1000));

        List<FluidStack> drained = FluidTransferHelper.drainExtractableFluids(
                world, POS, Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000)));

        assertThat(drained).isEmpty();
        assertThat(face.contents(0).amount).isEqualTo(1000);
    }

    /** 同一个罐被多个面暴露时只会被抽一次（抽空后其它面读到 null）。 */
    @Test
    public void testDrainExtractableFluidsCollectsEachTankOnce() {
        FakeFluidHandler shared = new FakeFluidHandler(1);
        shared.setTank(0, new FluidStack(FluidRegistry.LAVA, 400));
        installFace(EnumFacing.UP, shared);
        installFace(EnumFacing.DOWN, shared);

        List<FluidStack> drained = FluidTransferHelper.drainExtractableFluids(world, POS, null);

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).amount).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // 发配前清理的探察：先确认送得走，机器才被动
    // ------------------------------------------------------------------

    /** 探察只读：列出会被回收的流体，机器内容一个字节都不动。 */
    @Test
    public void testPeekExtractableFluidsDoesNotTouchTheMachine() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 2);
        face.setTank(0, new FluidStack(FluidRegistry.WATER, 1000));
        face.setTank(1, new FluidStack(FluidRegistry.LAVA, 250));

        List<FluidStack> peeked = FluidTransferHelper.peekExtractableFluids(world, POS, null);

        assertThat(peeked).hasSize(2);
        assertThat(peeked.get(0).getFluid()).isEqualTo(FluidRegistry.WATER);
        assertThat(peeked.get(0).amount).isEqualTo(1000);
        assertThat(peeked.get(1).getFluid()).isEqualTo(FluidRegistry.LAVA);
        assertThat(peeked.get(1).amount).isEqualTo(250);
        assertThat(face.contents(0).amount).isEqualTo(1000);
        assertThat(face.contents(1).amount).isEqualTo(250);
        assertThat(face.realDrained()).isEmpty();
    }

    /** 探察与真实回收同一判据：canDrain 闸门与本批输入过滤都不放行。 */
    @Test
    public void testPeekExtractableFluidsUsesSameGatesAsDrain() {
        FakeFluidHandler face = installFace(EnumFacing.UP, 2);
        face.setTankNotDrainable(0, new FluidStack(FluidRegistry.WATER, 1000));
        face.setTank(1, new FluidStack(FluidRegistry.LAVA, 300));

        List<FluidStack> peeked = FluidTransferHelper.peekExtractableFluids(
                world, POS, Collections.singletonList(new FluidStack(FluidRegistry.LAVA, 300)));

        assertThat(peeked).isEmpty();
        assertThat(face.contents(0).amount).isEqualTo(1000);
        assertThat(face.contents(1).amount).isEqualTo(300);

        List<FluidStack> drained = FluidTransferHelper.drainExtractableFluids(world, POS, Collections.emptyList());
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).getFluid()).isEqualTo(FluidRegistry.LAVA);
        assertThat(drained.get(0).amount).isEqualTo(300);
    }

    /** 探察量就是真实回收量：模拟注入的判据据此决定"送得走才抽"。 */
    @Test
    public void testPeekExtractableFluidsReportsWhatDrainTakes() {
        FakeFluidHandler face = installFace(EnumFacing.DOWN, 1);
        face.setTank(0, new FluidStack(FluidRegistry.LAVA, 400));

        List<FluidStack> peeked = FluidTransferHelper.peekExtractableFluids(world, POS, null);
        List<FluidStack> drained = FluidTransferHelper.drainExtractableFluids(world, POS, null);

        assertThat(peeked).hasSize(1);
        assertThat(drained).hasSize(1);
        assertThat(peeked.get(0).getFluid()).isEqualTo(FluidRegistry.LAVA);
        assertThat(drained.get(0).amount).isEqualTo(peeked.get(0).amount);
        assertThat(face.contents(0)).isNull();
    }

    /**
     * 属性说 canDrain、真实 {@code drain()} 拒绝的机器（TE 的形态）：探察也报空——
     * 探察走机器真实的 {@code drain(..., false)} 判定，不是只看罐属性。
     */
    @Test
    public void testPeekExtractableFluidsHonoursRealDrainRefusal() {
        FakeFluidHandler face = installFace(EnumFacing.NORTH, 1);
        face.setTank(0, new FluidStack(FluidRegistry.LAVA, 500));
        face.refuseDrain();

        assertThat(FluidTransferHelper.peekExtractableFluids(world, POS, null)).isEmpty();
        assertThat(face.contents(0).amount).isEqualTo(500);
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /**
     * 会话替身：本类不通过 {@code collectFluidProducts} 驱动阶段 1（阶段 1 用例直调判据本体，
     * 见类注释），因此预期产物恒为 null，只需要按需给出"本批输入流体"。
     */
    private TargetSession session(List<FluidStack> inputFluids) {
        TargetSession session = mock(TargetSession.class);
        when(session.getExpectedOutputs()).thenReturn(null);
        when(session.getInputFluids()).thenReturn(inputFluids != null ? inputFluids : Collections.emptyList());
        return session;
    }

    private FakeFluidHandler installFace(EnumFacing face, int tanks) {
        return installFace(face, new FakeFluidHandler(tanks));
    }

    /** 只在指定面提供流体能力（其余面没有 → 与"机器只在部分面暴露流体"一致）。 */
    private FakeFluidHandler installFace(EnumFacing face, FakeFluidHandler handler) {
        when(this.target.hasCapability(eq(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY), eq(face))).thenReturn(true);
        when(this.target.getCapability(eq(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY), eq(face))).thenReturn(handler);
        return handler;
    }

    /**
     * 单个面的流体能力替身.
     *
     * <p>三个开关分别对应实机里三种不同的"能不能抽"：</p>
     * <ul>
     *   <li>{@code drainable} —— {@link IFluidTankProperties#canDrain()}；</li>
     *   <li>{@code drainPermitted} —— 真实 {@code drain()} 是否放行（TE 那类按侧面配置拒绝的机器，
     *       属性与真实行为可以相反）；</li>
     *   <li>罐内容 —— 被抽走后置 null，用来断言"流体到底动没动"。</li>
     * </ul>
     */
    private static final class FakeFluidHandler implements IFluidHandler {

        private final FluidStack[] contents;
        private final boolean[] drainable;
        private final boolean[] drainPermitted;
        private final List<FluidStack> realDrained = new ArrayList<>();

        FakeFluidHandler(int tanks) {
            this.contents = new FluidStack[tanks];
            this.drainable = new boolean[tanks];
            this.drainPermitted = new boolean[tanks];
        }

        void setTank(int index, FluidStack fluid) {
            this.contents[index] = fluid;
            this.drainable[index] = true;
            this.drainPermitted[index] = true;
        }

        void setTankNotDrainable(int index, FluidStack fluid) {
            this.contents[index] = fluid;
            this.drainable[index] = false;
            this.drainPermitted[index] = true;
        }

        /** 所有罐的真实抽取一律拒绝（模拟侧仍报 canDrain）。 */
        void refuseDrain() {
            Arrays.fill(this.drainPermitted, false);
        }

        FluidStack contents(int index) {
            return this.contents[index];
        }

        List<FluidStack> realDrained() {
            return this.realDrained;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            IFluidTankProperties[] properties = new IFluidTankProperties[this.contents.length];
            for (int i = 0; i < this.contents.length; i++) {
                properties[i] = new FluidTankProperties(this.contents[i], 1000, false, this.drainable[i]);
            }
            return properties;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.getFluid() == null) {
                return null;
            }
            for (int i = 0; i < this.contents.length; i++) {
                FluidStack content = this.contents[i];
                if (!this.drainable[i] || !this.drainPermitted[i] || content == null
                        || !content.isFluidEqual(resource)) {
                    continue;
                }
                return drainAmount(i, resource.amount, doDrain);
            }
            return null;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            for (int i = 0; i < this.contents.length; i++) {
                if (!this.drainable[i] || !this.drainPermitted[i] || this.contents[i] == null) {
                    continue;
                }
                return drainAmount(i, maxDrain, doDrain);
            }
            return null;
        }

        private FluidStack drainAmount(int index, int maxDrain, boolean doDrain) {
            FluidStack content = this.contents[index];
            int amount = Math.min(maxDrain, content.amount);
            FluidStack out = new FluidStack(content.getFluid(), amount);
            if (amount <= 0) {
                return null;
            }
            if (doDrain) {
                content.amount -= amount;
                if (content.amount <= 0) {
                    this.contents[index] = null;
                }
                this.realDrained.add(out.copy());
            }
            return out;
        }
    }
}
