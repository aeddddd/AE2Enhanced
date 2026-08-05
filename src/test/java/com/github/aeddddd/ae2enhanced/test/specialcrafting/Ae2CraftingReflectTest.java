package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import appeng.me.helpers.BaseActionSource;

import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraft;

/**
 * {@link Ae2CraftingReflect} 单元测试.
 * <p>核心目的:校验反射目标(字段/方法名与签名)在当前 AE2 版本中存在,
 * 防止 AE2 升级后静态初始化失败或反射成员静默漂移;
 * 另用真实 {@link CraftingCalculation} 实例验证读写访问的功能正确性.</p>
 */
@BootstrapMinecraft
class Ae2CraftingReflectTest {

    // ==================== 反射目标存在性校验 ====================

    /** CraftingCalculation 的三个目标字段存在且类型匹配. */
    @Test
    void testTargetFieldsExist() throws Exception {
        Field networkInv = CraftingCalculation.class.getDeclaredField("networkInv");
        assertThat(networkInv.getType()).isEqualTo(NetworkCraftingSimulationState.class);

        Field simulate = CraftingCalculation.class.getDeclaredField("simulate");
        assertThat(simulate.getType()).isEqualTo(boolean.class);

        Field level = CraftingCalculation.class.getDeclaredField("level");
        assertThat(level.getType()).isEqualTo(Level.class);
    }

    /** CraftingCalculation 的四个目标方法存在且签名匹配. */
    @Test
    void testTargetMethodsExist() throws Exception {
        Method computePlan = CraftingCalculation.class.getDeclaredMethod("computePlan");
        assertThat(computePlan.getReturnType()).isEqualTo(ICraftingPlan.class);

        CraftingCalculation.class.getDeclaredMethod("finish");
        CraftingCalculation.class.getDeclaredMethod("handlePausing");

        Method addMissing = CraftingCalculation.class.getDeclaredMethod("addMissing", AEKey.class, long.class);
        assertThat(addMissing.getReturnType()).isEqualTo(void.class);
    }

    /** CraftingTreeProcess.request(CraftingSimulationState, long) 存在. */
    @Test
    void testTreeProcessRequestExists() throws Exception {
        CraftingTreeProcess.class.getDeclaredMethod("request", CraftingSimulationState.class, long.class);
    }

    // ==================== 功能正确性 ====================

    /** 构造一个真实 CraftingCalculation(网格与请求方 mock,与 SimulationEnv 同模式). */
    private static CraftingCalculation newCalculation(Level level) {
        IGrid grid = mock(IGrid.class);
        IStorageService storage = mock(IStorageService.class);
        // NetworkCraftingSimulationState 构造时遍历网络缓存库存,提供空库存即可
        when(storage.getCachedInventory()).thenReturn(new KeyCounter());
        when(grid.getStorageService()).thenReturn(storage);
        when(grid.getCraftingService()).thenReturn(mock(ICraftingService.class));
        ICraftingSimulationRequester requester = mock(ICraftingSimulationRequester.class);
        IActionSource source = new BaseActionSource();
        when(requester.getActionSource()).thenReturn(source);
        return new CraftingCalculation(level, grid, requester,
                GenericStack.fromItemStack(new ItemStack(Items.STONE)),
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /** getLevel / getNetworkInv 返回构造时注入的成员. */
    @Test
    void testFieldAccessors() {
        Level level = mock(Level.class);
        CraftingCalculation calc = newCalculation(level);

        assertThat(Ae2CraftingReflect.getLevel(calc)).isSameAs(level);
        assertThat(Ae2CraftingReflect.getNetworkInv(calc)).isNotNull();
    }

    /** setSimulate 真正写入 simulate 标志(自有求解器缺料路径依赖). */
    @Test
    void testSetSimulateWritesFlag() throws Exception {
        CraftingCalculation calc = newCalculation(mock(Level.class));
        Field simulate = CraftingCalculation.class.getDeclaredField("simulate");
        simulate.setAccessible(true);

        assertThat(simulate.getBoolean(calc)).isFalse();
        Ae2CraftingReflect.setSimulate(calc, true);
        assertThat(simulate.getBoolean(calc)).isTrue();
        Ae2CraftingReflect.setSimulate(calc, false);
        assertThat(simulate.getBoolean(calc)).isFalse();
    }

    /** addMissing 真正累加到 missing 计数器. */
    @Test
    void testAddMissingAccumulates() throws Exception {
        CraftingCalculation calc = newCalculation(mock(Level.class));
        AEKey key = AEItemKey.of(Items.STONE);

        Ae2CraftingReflect.addMissing(calc, key, 3);
        Ae2CraftingReflect.addMissing(calc, key, 4);

        Field missing = CraftingCalculation.class.getDeclaredField("missing");
        missing.setAccessible(true);
        assertThat(((KeyCounter) missing.get(calc)).get(key)).isEqualTo(7);
    }
}
