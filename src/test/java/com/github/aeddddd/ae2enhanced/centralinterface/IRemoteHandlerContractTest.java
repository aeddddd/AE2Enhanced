package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * {@link IRemoteHandler} 接口默认方法契约测试。
 *
 * <p>用仅实现抽象方法的最小 fake 验证：
 * hasFinished 默认委托 isIdle；revertMaterials / clearOutputs 默认返回空列表（非 null）。
 * 默认实现均不触碰入参，故传 null 验证。</p>
 */
public class IRemoteHandlerContractTest {

    /** hasFinished 默认委托 isIdle：isIdle 为 true 时 hasFinished 也为 true，且确实调用了 isIdle。 */
    @Test
    public void testHasFinishedDelegatesToIsIdleTrue() {
        MinimalHandler handler = new MinimalHandler();
        handler.idle = true;

        assertThat(handler.hasFinished(null, null, null, null)).isTrue();
        assertThat(handler.idleCalls).isEqualTo(1);
    }

    /** hasFinished 默认委托 isIdle：isIdle 为 false 时 hasFinished 也为 false。 */
    @Test
    public void testHasFinishedDelegatesToIsIdleFalse() {
        MinimalHandler handler = new MinimalHandler();
        handler.idle = false;

        assertThat(handler.hasFinished(null, null, null, null)).isFalse();
        assertThat(handler.idleCalls).isEqualTo(1);
    }

    /** revertMaterials 默认返回空列表（非 null），表示不回退。 */
    @Test
    public void testRevertMaterialsDefaultReturnsEmptyList() {
        MinimalHandler handler = new MinimalHandler();

        List<ItemStack> result = handler.revertMaterials(null, null, null, null);

        assertThat(result).isNotNull().isEmpty();
    }

    /** clearOutputs 默认返回空列表（非 null），表示不回收任何物品。 */
    @Test
    public void testClearOutputsDefaultReturnsEmptyList() {
        MinimalHandler handler = new MinimalHandler();

        List<ItemStack> result = handler.clearOutputs(null, null, null, null);

        assertThat(result).isNotNull().isEmpty();
    }

    /** 仅实现抽象方法的最小 fake，isIdle 行为可由测试控制并记录调用次数。 */
    private static final class MinimalHandler implements IRemoteHandler {
        private boolean idle;
        private int idleCalls;

        @Override
        public EnumSet<HandlerCapabilities> getCapabilities() {
            return HandlerCapabilities.all();
        }

        @Override
        public boolean canHandle(String blockId) {
            return false;
        }

        @Override
        public boolean isValidTarget(World world, BlockPos pos) {
            return false;
        }

        @Override
        public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
            return false;
        }

        @Override
        public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients,
                IActionSource source, TargetSession session) {
            return false;
        }

        @Override
        public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
            return true;
        }

        @Override
        public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                List<ItemStack> inputs, IActionSource source, TargetSession session) {
            return Collections.emptyList();
        }

        @Override
        public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
            idleCalls++;
            return idle;
        }
    }
}
