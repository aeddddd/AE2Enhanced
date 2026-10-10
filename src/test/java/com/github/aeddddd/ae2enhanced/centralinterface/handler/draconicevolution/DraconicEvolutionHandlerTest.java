package com.github.aeddddd.ae2enhanced.centralinterface.handler.draconicevolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link DraconicEvolutionHandler} 迁移到通用骨架后的等价性测试（离线）.
 *
 * <h3>本类的环境耦合（改测试环境前先读这段）</h3>
 * <p>凡是走 {@code initReflection()} 的入口（目录构造、推料、收集、空闲判定）在测试运行时必然失败，
 * 但<b>失败以哪种类型冒出来取决于目标 mod 的依赖是否在 classpath 上</b>，两种形态都必须容纳：</p>
 * <ul>
 *   <li><b>MOD 类缺失</b>（DE 自己的类不在）→ {@code Class.forName} 抛 {@code ClassNotFoundException}，
 *       被生产代码的 {@code catch (Exception)} 包装成 {@link RuntimeException}
 *       （message 为 {@code [AE2E] DraconicEvolution reflection init failed}）；</li>
 *   <li><b>MOD 的依赖缺失</b>（DE 的类在、其父类 {@code com.brandon3055.brandonscore.blocks.TileInventoryBase}
 *       之类不在）→ 类<b>解析阶段</b>就抛 {@link NoClassDefFoundError}。它继承 {@link Error} 而非
 *       {@link Exception}，因此生产代码的 {@code catch (Exception e)} <b>抓不到</b>，会原样冒泡到调用方
 *       （生产侧由 {@code PhysicalDispatcher} 的 {@code catch (Throwable)} 兜住，不会崩服务端）。
 *       当前测试运行时正是这一支。</li>
 * </ul>
 * <p>结论：断言不能写死 `RuntimeException`，只能要求"显式抛出（不是静默返回）且信息链能指明
 * 是 DE 反射初始化所缺的东西"。生产代码的异常类型是既有语义，不得为了测试而改。</p>
 *
 * <p>本测试覆盖能离线驱动的部分：</p>
 * <ul>
 *   <li>表项与能力（{@code canHandle}、{@code getCapabilities}）；</li>
 *   <li>"是否在忙"的纯函数（{@code isMachineWorking}）——反射句柄缺失时退化为 false（不忙）；</li>
 *   <li>空世界/空坐标先于反射初始化判为无效目标；</li>
 *   <li>反射入口在"环境缺依赖"这一支的行为（{@link #testReflectiveEntriesRequireModClasses}，双分支断言）。</li>
 * </ul>
 *
 * <p><b>已知覆盖缺口</b>（需要带真实 DE 的实机/集成夹具）：Core 的 0 号槽取 capability 的目录装配、
 * 注入器基座的落位顺序（{@code getStackInPedestal}/{@code setStackInPedestal}）、
 * 推料失败时的主材料回滚、产物 NBT 归一化、以及把本类的
 * {@code canStart/pushMaterials/collectProducts/clearOutputs/revertMaterials} 与框架默认的差异
 * 逐条钉住的用例。</p>
 */
public class DraconicEvolutionHandlerTest {

    private static final BlockPos POS = new BlockPos(5, 64, 5);

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    // ------------------------------------------------------------------
    // 表项与能力
    // ------------------------------------------------------------------

    /** 表项只匹配聚合核心；能力仍为 physical-only。 */
    @Test
    public void testSpecAndCapabilities() {
        DraconicEvolutionHandler handler = new DraconicEvolutionHandler();

        assertThat(handler.canHandle("draconicevolution:fusion_crafting_core")).isTrue();
        assertThat(handler.canHandle("draconicevolution:crafting_injector")).isFalse();
        assertThat(handler.canHandle("")).isFalse();
        assertThat(handler.canHandle(null)).isFalse();

        assertThat(handler.spec().exactBlockIds()).containsExactly("draconicevolution:fusion_crafting_core");
        assertThat(handler.spec().blockIdPrefixes()).isEmpty();
        assertThat(handler.spec().access()).isSameAs(handler);

        assertThat(handler.getCapabilities()).containsExactly(HandlerCapabilities.PHYSICAL);
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();
    }

    // ------------------------------------------------------------------
    // 处理中判定的纯函数
    // ------------------------------------------------------------------

    /**
     * 反射句柄缺失（无 DE 环境）或目标不是聚合核心时，"是否在忙"退化为 false.
     *
     * <p>与迁移前一致：{@code isCrafting} 读不到字段返回 false、{@code craftingStage} 读不到返回 0，
     * 于是旧 {@code isIdle} 的 {@code !isCrafting && stage == 0} 为 true（可收集）。</p>
     */
    @Test
    public void testMachineWorkingDegradesToFalseWithoutReflection() {
        assertThat(DraconicEvolutionHandler.isMachineWorking(null)).isFalse();
        assertThat(DraconicEvolutionHandler.isMachineWorking(mock(TileEntity.class))).isFalse();
    }

    // ------------------------------------------------------------------
    // 无效目标
    // ------------------------------------------------------------------

    /** 空世界/空坐标先于反射初始化判为无效（不会因缺类抛异常）。 */
    @Test
    public void testNullWorldOrPosIsInvalidBeforeReflection() {
        DraconicEvolutionHandler handler = new DraconicEvolutionHandler();

        assertThat(handler.isValidTarget(null, POS)).isFalse();
        assertThat(handler.isValidTarget(mock(World.class), null)).isFalse();
    }

    /**
     * 反射入口在"测试运行时缺 mod 依赖"这一支的表现.
     *
     * <p><b>断言只锁两件事</b>（异常类型不得写死，见类注释的环境耦合说明）：</p>
     * <ol>
     *   <li>入口显式抛出而不是静默返回——重构前的 {@code isValidTarget} 也是先 {@code initReflection}，
     *       这条保证"缺依赖不会被误判成无效目标"；</li>
     *   <li>异常类型属于 {@link RuntimeException} 或 {@link Error}（后者是
     *       {@link NoClassDefFoundError} 这一类"类解析失败"，{@code catch (Exception)} 抓不到），
     *       且异常链文本能指明是 DE 反射初始化所缺的东西。</li>
     * </ol>
     *
     * <p>当前测试运行时命中"DE 类在、其 Brandon's Core 父类不在"这一形态，因此实际抛的是
     * {@link NoClassDefFoundError}；若将来把 DE 全量依赖挂进测试运行时，本用例自动改判另一支：
     * 目标位置没有 TileEntity 时视为无效目标。</p>
     */
    @Test
    public void testReflectiveEntriesRequireModClasses() {
        DraconicEvolutionHandler handler = new DraconicEvolutionHandler();
        World world = mock(World.class);

        if (!draconicEvolutionPresent()) {
            Throwable thrown = catchThrowable(() -> handler.isValidTarget(world, POS));

            assertThat(thrown).as("缺 mod 依赖时反射入口必须显式抛出，不得静默返回").isNotNull();
            assertThat(thrown)
                    .as("可能是包装后的 RuntimeException（mod 类缺失），也可能是类解析阶段的 "
                            + "NoClassDefFoundError（mod 的依赖缺失）")
                    .isInstanceOfAny(RuntimeException.class, Error.class);
            String failureChain = failureChainText(thrown);
            assertThat(failureChain.contains("brandon3055") || failureChain.contains("DraconicEvolution"))
                    .as("异常信息应指向 DE 反射初始化缺失的类或成员（brandon3055 为 DE/Brandon's Core 的包名前缀），"
                            + "实际异常链：%s", failureChain)
                    .isTrue();
            return;
        }

        assertThat(handler.isValidTarget(world, POS)).isFalse();
    }

    /**
     * 测试运行时 {@code initReflection()} 能否成功（需要 DE 与 Brandon's Core 的类都可解析）.
     *
     * <p>必须连依赖一起探测：DE 的方块实体继承 Brandon's Core 的基类，只探测 DE 自己的类名会在
     * {@code Class.forName} 时因父类缺失抛 {@link NoClassDefFoundError}，所以这里把
     * {@link LinkageError} 一并当作"不可用"。</p>
     */
    private static boolean draconicEvolutionPresent() {
        try {
            Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore");
            Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileCraftingInjector");
            Class.forName("com.brandon3055.draconicevolution.api.fusioncrafting.ICraftingInjector");
            Class.forName("com.brandon3055.brandonscore.lib.datamanager.ManagedBool");
            Class.forName("com.brandon3055.brandonscore.lib.datamanager.ManagedShort");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * 汇总异常链上的"类名 + message"，用于断言"这是 DE 反射初始化失败".
     *
     * <p>走 cause 链而不是只看顶层 message：包装形态（{@code RuntimeException("[AE2E] ... init failed", cause)}）
     * 的缺失类名在 cause 里，未包装形态（{@code NoClassDefFoundError}）的类名才在顶层 message 里。</p>
     */
    private static String failureChainText(Throwable thrown) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            text.append(current.getClass().getName()).append(": ").append(current.getMessage()).append(' ');
            if (current.getCause() == current) {
                break; // 自引用的 cause（病态但可能），避免死循环
            }
        }
        return text.toString();
    }
}
