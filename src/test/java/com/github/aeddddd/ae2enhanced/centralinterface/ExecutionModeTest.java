package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.me.helpers.AENetworkProxy;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link ExecutionMode} 推导规则测试（重构计划 P1 · §0.2 / §4）。
 *
 * <p>语义定稿：<b>装卡就纯虚拟，拿走卡自然回归物理</b>，不给额外开关；而"不存在物理交互空间"
 * 的工作台类设备常驻虚拟，与是否装卡无关。</p>
 */
public class ExecutionModeTest {

    private DualityCentralInterface duality;

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        ICentralInterfaceHost host = mock(ICentralInterfaceHost.class);
        TileEntity tile = mock(TileEntity.class);
        when(host.getTileEntity()).thenReturn(tile);
        when(tile.getWorld()).thenReturn(mock(World.class));
        when(host.getProxy()).thenReturn(mock(AENetworkProxy.class));
        this.duality = new DualityCentralInterface(host);
    }

    // ---- 能力组合 × 有卡/无卡 ----

    /** 只声明物理能力（TE / EIO / NC / 通用兜底）：装不装卡都走物理。 */
    @Test
    public void physicalOnlyStaysPhysicalRegardlessOfCard() {
        IRemoteHandler handler = handler(HandlerCapabilities.physicalOnly(), false);

        assertThat(ExecutionMode.resolve(handler, false)).isEqualTo(ExecutionMode.PHYSICAL);
        // 用户决策（方案 B）：虚拟卡对无虚拟能力的目标不生效，不回退失败、也不跳过目标
        assertThat(ExecutionMode.resolve(handler, true)).isEqualTo(ExecutionMode.PHYSICAL);
    }

    /** 双能力设备（Botania / Thaumcraft / EC Core 等）：卡即模式开关。 */
    @Test
    public void dualCapabilityIsSwitchedByTheCard() {
        IRemoteHandler handler = handler(HandlerCapabilities.all(), true);

        assertThat(ExecutionMode.resolve(handler, false)).isEqualTo(ExecutionMode.PHYSICAL);
        assertThat(ExecutionMode.resolve(handler, true)).isEqualTo(ExecutionMode.VIRTUAL_CARDED);
    }

    /** 纯虚拟设备（工作台类，无物理交互空间）：常驻虚拟，卡只影响并行度。 */
    @Test
    public void virtualOnlyDeviceIsAlwaysVirtual() {
        IRemoteHandler handler = handler(HandlerCapabilities.virtualOnly(), true);

        assertThat(ExecutionMode.resolve(handler, false)).isEqualTo(ExecutionMode.VIRTUAL_NATIVE);
        assertThat(ExecutionMode.resolve(handler, true)).isEqualTo(ExecutionMode.VIRTUAL_NATIVE);
    }

    /** 声称具备虚拟能力但未实现虚拟接口的退化 handler：不得被当成虚拟目标。 */
    @Test
    public void virtualClaimWithoutInterfaceFallsBackToPhysical() {
        IRemoteHandler handler = handler(HandlerCapabilities.all(), false);

        assertThat(ExecutionMode.resolve(handler, true)).isEqualTo(ExecutionMode.PHYSICAL);
    }

    @Test
    public void nullHandlerIsPhysical() {
        assertThat(ExecutionMode.resolve(null, true)).isEqualTo(ExecutionMode.PHYSICAL);
    }

    @Test
    public void isVirtualOnlyForTheTwoVirtualModes() {
        assertThat(ExecutionMode.PHYSICAL.isVirtual()).isFalse();
        assertThat(ExecutionMode.VIRTUAL_NATIVE.isVirtual()).isTrue();
        assertThat(ExecutionMode.VIRTUAL_CARDED.isVirtual()).isTrue();
    }

    // ---- hasCard 判据 ----

    /**
     * {@link ExecutionMode#resolve} 的 {@code hasCard} 来自
     * {@link DualityCentralInterface#hasVirtualCard()}；未装卡时必须为 false。
     */
    @Test
    public void noVirtualCardIsDetected() {
        assertThat(this.duality.hasVirtualCard()).isFalse();
        assertThat(this.duality.getVirtualParallel()).isEqualTo(1L);
    }

    // ---- helpers ----

    /**
     * 构造 mock handler。
     *
     * <p><b>必须用 {@code CALLS_REAL_METHODS}</b>：{@code IRemoteHandler#hasCapability} 是
     * default 方法，普通 mock 会让它直接返回 {@code false}，从而把每个 handler 都当成
     * "无任何能力"——判据失去意义、测试还会"碰巧通过"。{@link #mockHarnessReflectsDeclaredCapabilities}
     * 专门守住这一点。</p>
     *
     * @param capabilities               声明返回的能力集合
     * @param implementsVirtualInterface 是否实现 {@link IVirtualBatchCraftingHandler}
     *                                   （决定 {@code instanceof} 判定结果）
     */
    private static IRemoteHandler handler(EnumSet<HandlerCapabilities> capabilities,
                                          boolean implementsVirtualInterface) {
        Class<? extends IRemoteHandler> type =
                implementsVirtualInterface ? IVirtualBatchCraftingHandler.class : IRemoteHandler.class;
        IRemoteHandler handler = mock(type, CALLS_REAL_METHODS);
        when(handler.getCapabilities()).thenReturn(capabilities);
        return handler;
    }

    /** 守住测试夹具本身：default 方法 {@code hasCapability} 必须真实反映声明的能力集合。 */
    @Test
    public void mockHarnessReflectsDeclaredCapabilities() {
        IRemoteHandler dual = handler(HandlerCapabilities.all(), true);
        assertThat(dual.hasCapability(HandlerCapabilities.PHYSICAL)).isTrue();
        assertThat(dual.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isTrue();

        IRemoteHandler physical = handler(HandlerCapabilities.physicalOnly(), false);
        assertThat(physical.hasCapability(HandlerCapabilities.PHYSICAL)).isTrue();
        assertThat(physical.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();

        IRemoteHandler virtual = handler(HandlerCapabilities.virtualOnly(), true);
        assertThat(virtual.hasCapability(HandlerCapabilities.PHYSICAL)).isFalse();
        assertThat(virtual.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isTrue();
    }
}
