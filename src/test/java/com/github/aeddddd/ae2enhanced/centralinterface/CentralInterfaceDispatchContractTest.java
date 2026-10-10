package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.IMEInventory;
import appeng.me.helpers.AENetworkProxy;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * CPU 记账协议锚点测试（重构计划 P0 · §3.3）。
 *
 * <p>{@link DualityCentralInterface} 与 {@code MixinCraftingCPUClusterVirtualBatch} 之间存在
 * 四段隐式契约（调用前传上限 + 传 CPU 库存、调用后读实际并行、finally 复位）。该契约无编译期约束，
 * 且此前<b>零测试覆盖</b>。本测试只钉住廉价可测的入口/出口语义——完整 {@code pushPattern} 需要真实网格。</p>
 *
 * <p>另注：{@code CraftingPatternList#provideCrafting} 以 {@code this.host}（即
 * {@code TileCentralMEInterface} 本体）注册 medium，mixin 侧用
 * {@code instanceof TileCentralMEInterface} 反向识别。两侧必须始终保持同一点；
 * 一旦有人改成注册 Duality 本体，mixin 会<b>静默失配</b>（虚拟并行恒失败且 CPU 侧不报错）。</p>
 */
public class CentralInterfaceDispatchContractTest {

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

    /**
     * {@code pushPattern} 入口必须无条件复位记账状态：上一次的并行数清零、
     * CPU 传入的上限被消费一次即归零（否则会残留给下一次无关 push）。
     */
    @Test
    public void pushPatternResetsBatchAccountingAtEntry() throws Exception {
        this.duality.setNextVirtualBatchLimit(7);
        assertThat(nextVirtualBatchLimitField()).isEqualTo(7L);

        // 配方列表未初始化 → 在入口复位之后、网格判定处提前返回 false
        boolean pushed = this.duality.pushPattern(
                mock(ICraftingPatternDetails.class), mock(InventoryCrafting.class));

        assertThat(pushed).isFalse();
        assertThat(this.duality.getLastVirtualBatchSize()).isZero();
        assertThat(nextVirtualBatchLimitField()).isZero();
    }

    /** CPU 的 over-limit 上限必须被钳制为非负，负数不应被当成"无上限"。 */
    @Test
    public void negativeBatchLimitIsClampedToZero() throws Exception {
        this.duality.setNextVirtualBatchLimit(-5);
        assertThat(nextVirtualBatchLimitField()).isZero();
    }

    /** mixin 的 {@code finally} 以 {@code setVirtualItemSource(null)} 复位；两侧语义必须可往返。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void virtualItemSourceCanBeSetAndCleared() {
        assertThat(this.duality.getVirtualItemSource()).isNull();

        this.duality.setVirtualItemSource(mock(IMEInventory.class));
        assertThat(this.duality.getVirtualItemSource()).isNotNull();

        this.duality.setVirtualItemSource(null);
        assertThat(this.duality.getVirtualItemSource()).isNull();
    }

    /**
     * 未装虚拟卡时并行数基准必须是 1。
     *
     * <p>mock 宿主不实现 {@code IUpgradeableHost}，因此这里走的是
     * {@code getVirtualParallel()} 的 catch 兜底分支（"host 不是 IUpgradeableHost"），
     * 与真实 Tile 未装卡时的返回值一致。</p>
     */
    @Test
    public void noVirtualCardMeansParallelOne() {
        assertThat(this.duality.getVirtualParallel()).isEqualTo(1L);
    }

    // ---- helpers ----

    private long nextVirtualBatchLimitField() throws Exception {
        Field field = DualityCentralInterface.class.getDeclaredField("nextVirtualBatchLimit");
        field.setAccessible(true);
        return field.getLong(this.duality);
    }
}
