package com.github.aeddddd.ae2enhanced.centralinterface.handler.bewitchment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

/**
 * {@link BewitchmentHandler} 迁移到通用骨架后的等价性测试（离线）.
 *
 * <p><b>测试环境没有 Bewitchment</b>（{@code build.gradle} 里没有该依赖），
 * 因此走反射的入口（{@code catalogFor} 取 capability、坩埚内部 inventory、进度/mode 字段）
 * 在这里不可达：{@code initReflection()} 会按原语义抛 {@link RuntimeException}
 * （生产侧由 {@code PhysicalDispatcher} 的 try/catch 隔离）。因此本测试覆盖两类内容：</p>
 * <ol>
 *   <li>不依赖反射的纯逻辑：表项与能力、UP/DOWN 目录的装配（{@code catalogOf}）、
 *       进度/mode 的三个判定函数、"非本族目标一律不可收集"的兜底分支；</li>
 *   <li>反射入口对"环境缺类"这一支的行为（{@link #testReflectiveEntriesRequireModClasses}），
 *       该断言是双分支的：若将来把 Bewitchment 挂进测试 classpath，会自动改判"非目标返回无效"。</li>
 * </ol>
 *
 * <p>未覆盖（需要带真实 Bewitchment 的实机/集成夹具）：坩埚推料时的"推料即出产物"链路、
 * 三台机器的进度/mode 字段读取、UP/DOWN capability 的真实取用。</p>
 */
public class BewitchmentHandlerTest {

    private static final BlockPos POS = new BlockPos(4, 64, 4);

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    // ------------------------------------------------------------------
    // 表项与能力
    // ------------------------------------------------------------------

    /** 表项覆盖三个方块 id，能力仍为 physical-only（与迁移前逐字一致）。 */
    @Test
    public void testSpecAndCapabilities() {
        BewitchmentHandler handler = new BewitchmentHandler();

        assertThat(handler.canHandle("bewitchment:spinning_wheel")).isTrue();
        assertThat(handler.canHandle("bewitchment:distillery")).isTrue();
        assertThat(handler.canHandle("bewitchment:witches_cauldron")).isTrue();
        assertThat(handler.canHandle("bewitchment:other")).isFalse();
        assertThat(handler.canHandle("")).isFalse();
        assertThat(handler.canHandle(null)).isFalse();

        assertThat(handler.spec().exactBlockIds())
                .containsExactlyInAnyOrder(
                        "bewitchment:spinning_wheel",
                        "bewitchment:distillery",
                        "bewitchment:witches_cauldron");
        assertThat(handler.spec().blockIdPrefixes()).isEmpty();
        assertThat(handler.spec().access()).isSameAs(handler);

        assertThat(handler.getCapabilities()).containsExactly(HandlerCapabilities.PHYSICAL);
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();
    }

    // ------------------------------------------------------------------
    // 目录装配
    // ------------------------------------------------------------------

    /** UP 面槽位为输入、DOWN 面槽位为输出，顺序与面一致（推料/收集目标由此确定）。 */
    @Test
    public void testCatalogPlacesUpFaceAsInputDownFaceAsOutput() {
        FakeItemHandler up = new FakeItemHandler(5);
        FakeItemHandler down = new FakeItemHandler(2);

        ISlotCatalog catalog = BewitchmentHandler.catalogOf(up, down);
        List<SlotRef> slots = catalog.slots();

        assertThat(catalog.rolesKnown()).isTrue();
        assertThat(slots).hasSize(7);
        for (int i = 0; i < 5; i++) {
            assertThat(slots.get(i).handler()).isSameAs(up);
            assertThat(slots.get(i).index()).isEqualTo(i);
            assertThat(slots.get(i).face()).isEqualTo(EnumFacing.UP);
            assertThat(slots.get(i).role()).isEqualTo(SlotRole.INPUT);
        }
        for (int i = 0; i < 2; i++) {
            SlotRef slot = slots.get(5 + i);
            assertThat(slot.handler()).isSameAs(down);
            assertThat(slot.index()).isEqualTo(i);
            assertThat(slot.face()).isEqualTo(EnumFacing.DOWN);
            assertThat(slot.role()).isEqualTo(SlotRole.OUTPUT);
        }

        assertThat(catalog.inputSlots()).hasSize(5);
        assertThat(catalog.outputSlots()).hasSize(2);
    }

    /** 缺一面 capability 时目录只剩另一面的槽位（推料/收集各自降级）。 */
    @Test
    public void testCatalogWithoutOneFace() {
        FakeItemHandler up = new FakeItemHandler(3);

        ISlotCatalog upOnly = BewitchmentHandler.catalogOf(up, null);
        assertThat(upOnly.inputSlots()).hasSize(3);
        assertThat(upOnly.outputSlots()).isEmpty();

        ISlotCatalog downOnly = BewitchmentHandler.catalogOf(null, up);
        assertThat(downOnly.inputSlots()).isEmpty();
        assertThat(downOnly.outputSlots()).hasSize(3);
    }

    /** 两面都取不到时目录为空但非 null：目标仍算有效（与迁移前的 `isValidTarget` 只看方块类型一致）。 */
    @Test
    public void testCatalogWithoutAnyFaceIsEmptyButNotNull() {
        ISlotCatalog catalog = BewitchmentHandler.catalogOf(null, null);

        assertThat(catalog).isNotNull();
        assertThat(catalog.slots()).isEmpty();
        assertThat(catalog.rolesKnown()).isFalse();
        assertThat(catalog.pushTargets()).isEmpty();
    }

    // ------------------------------------------------------------------
    // 空闲判定纯逻辑
    // ------------------------------------------------------------------

    /** SpinningWheel / Distillery：只有进度归零才算空闲；读不到进度按处理中处理。 */
    @Test
    public void testProgressIdlePrimitive() {
        assertThat(BewitchmentHandler.progressIdle(0)).isTrue();
        assertThat(BewitchmentHandler.progressIdle(1)).isFalse();
        assertThat(BewitchmentHandler.progressIdle(-1)).isFalse();
        assertThat(BewitchmentHandler.progressIdle(null)).isFalse();
    }

    /** 坩埚：mode 2/3/4/5 为处理中，0/1 与未知取值都算空闲；读不到按处理中处理。 */
    @Test
    public void testCauldronIdlePrimitive() {
        assertThat(BewitchmentHandler.isCauldronProcessingMode(2)).isTrue();
        assertThat(BewitchmentHandler.isCauldronProcessingMode(3)).isTrue();
        assertThat(BewitchmentHandler.isCauldronProcessingMode(4)).isTrue();
        assertThat(BewitchmentHandler.isCauldronProcessingMode(5)).isTrue();
        assertThat(BewitchmentHandler.isCauldronProcessingMode(0)).isFalse();
        assertThat(BewitchmentHandler.isCauldronProcessingMode(1)).isFalse();
        assertThat(BewitchmentHandler.isCauldronProcessingMode(6)).isFalse();

        assertThat(BewitchmentHandler.cauldronIdle(0)).isTrue();
        assertThat(BewitchmentHandler.cauldronIdle(1)).isTrue();
        assertThat(BewitchmentHandler.cauldronIdle(6)).isTrue();  // 旧实现：非 2..5 一律算空闲
        assertThat(BewitchmentHandler.cauldronIdle(5)).isFalse();
        assertThat(BewitchmentHandler.cauldronIdle(null)).isFalse();
    }

    /** 非本族目标一律"不可收集"（旧 isIdle 的口径，框架默认为 true）。 */
    @Test
    public void testIdleStateRejectsUnknownTileEntity() {
        assertThat(BewitchmentHandler.isIdleState(null)).isFalse();
        assertThat(BewitchmentHandler.isIdleState(mock(TileEntity.class))).isFalse();
    }

    // ------------------------------------------------------------------
    // 收集 / 清理
    // ------------------------------------------------------------------

    /**
     * 发配前清理保持"什么都不清"（迁移前未覆盖该钩子）.
     *
     * <p>框架默认会清 OUTPUT 角色槽，对 SpinningWheel 就是清掉 DOWN 面的产物，属于行为变更，
     * 因此本用例钉住空操作语义；该入口不触碰反射，故在无 Bewitchment 的环境也可驱动。</p>
     */
    @Test
    public void testClearOutputsIsNoOp() {
        BewitchmentHandler handler = new BewitchmentHandler();

        assertThat(handler.clearOutputs(mock(World.class), POS, null, null)).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------
    // 反射入口的环境契约
    // ------------------------------------------------------------------

    /**
     * 反射入口在"环境缺类"这一支的表现.
     *
     * <p>与本用例耦合的前提：Bewitchment 当前<b>不在</b>测试 classpath（{@code libs-unused} 下的 jar 不参与构建）。
     * 此时 {@code initReflection()} 按原语义抛 {@link RuntimeException}——迁移前后一致，
     * 生产侧由 {@code PhysicalDispatcher} 的 try/catch 隔离。若将来把该 mod 挂进测试运行时，
     * 本用例自动改判另一支：目标位置没有 TileEntity 时视为无效目标且不抛异常。</p>
     */
    @Test
    public void testReflectiveEntriesRequireModClasses() {
        BewitchmentHandler handler = new BewitchmentHandler();
        World world = mock(World.class);

        if (!bewitchmentPresent()) {
            assertThatThrownBy(() -> handler.isValidTarget(world, POS))
                    .isInstanceOf(RuntimeException.class);
            return;
        }

        assertThat(handler.isValidTarget(world, POS)).isFalse();
        assertThat(handler.canStart(world, POS, null, null)).isFalse();
    }

    /** 空世界/空坐标必须先于反射初始化判定为无效目标（不依赖 mod 是否安装）。 */
    @Test
    public void testNullWorldOrPosIsInvalidBeforeReflection() {
        BewitchmentHandler handler = new BewitchmentHandler();

        assertThat(handler.isValidTarget(null, POS)).isFalse();
        assertThat(handler.isValidTarget(mock(World.class), null)).isFalse();
    }

    /** 测试运行时 {@code initReflection()} 能否成功（需要 Bewitchment 方块实体与配方两类都在 classpath）。 */
    private static boolean bewitchmentPresent() {
        try {
            Class.forName("com.bewitchment.common.block.tile.entity.TileEntitySpinningWheel");
            Class.forName("com.bewitchment.common.block.tile.entity.TileEntityDistillery");
            Class.forName("com.bewitchment.common.block.tile.entity.TileEntityWitchesCauldron");
            Class.forName("com.bewitchment.api.registry.CauldronRecipe");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
