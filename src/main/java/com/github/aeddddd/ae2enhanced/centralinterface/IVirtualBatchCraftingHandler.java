package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * 支持批量虚拟合成的远程目标处理器接口.
 *
 * <p>实现此接口的 handler 可以在不占用物理设备的情况下,按指定份数直接从 AE2 网络
 * 扣除资源并返回产物.用于 Central Interface 安装虚拟并行卡后的高并行合成。</p>
 *
 * <p>此接口继承 {@link IRemoteHandler}，因此实现者仍需声明是否支持物理发配。
 * 对于仅有虚拟合成意义的设备（如 Extended Crafting 工作台），可只返回
 * {@link HandlerCapabilities#VIRTUAL_BATCH}，让调度器在无卡时直接跳过该目标。</p>
 */
public interface IVirtualBatchCraftingHandler extends IRemoteHandler {

    /**
     * 默认能力：虚拟批量 + 物理发配（大多数设备两者都支持）。
     * 纯虚拟设备应覆盖此方法并只返回 {@link HandlerCapabilities#VIRTUAL_BATCH}。
     */
    @Override
    default EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.all();
    }

    /**
     * 验证该配方是否可以通过此目标虚拟合成.
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品
     * @param outputs     单份配方的预期产物
     * @param details     完整的配方详情（可为 null，用于需要区分处理/合成样板的场景）
     * @return true 表示可以执行虚拟合成
     */
    default boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients,
                                      IAEItemStack[] outputs, ICraftingPatternDetails details) {
        return canCraftVirtually(world, pos, ingredients, outputs);
    }

    /**
     * 验证该配方是否可以通过此目标虚拟合成.
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品
     * @param outputs     单份配方的预期产物
     * @return true 表示可以执行虚拟合成
     */
    boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs);

    /**
     * 计算 {@code count} 次虚拟合成所需的全部资源.
     *
     * <p>返回值应包含完整 {@code count} 份所需的所有 AE 存储栈(物品、流体、RF、
     * Mana、Starlight、源质、气体等).调用方会自行决定如何从网络提取.</p>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品(来自 {@link InventoryCrafting})
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @param details     完整的配方详情（可为 null）
     * @return 资源清单；若无法计算则返回空列表或 null（均视为失败）
     */
    default List<IAEStack> getVirtualCost(World world, BlockPos pos,
                                          InventoryCrafting ingredients,
                                          IAEItemStack[] outputs,
                                          long count,
                                          ICraftingPatternDetails details) {
        return getVirtualCost(world, pos, ingredients, outputs, count);
    }

    /**
     * 计算 {@code count} 次虚拟合成所需的全部资源.
     *
     * <p>返回值应包含完整 {@code count} 份所需的所有 AE 存储栈(物品、流体、RF、
     * Mana、Starlight、源质、气体等).调用方会自行决定如何从网络提取.</p>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品(来自 {@link InventoryCrafting})
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @return 资源清单；若无法计算则返回空列表或 null（均视为失败）
     */
    List<IAEStack> getVirtualCost(World world, BlockPos pos,
                                  InventoryCrafting ingredients,
                                  IAEItemStack[] outputs,
                                  long count);

    /**
     * 执行 {@code count} 次虚拟合成.
     *
     * <p>调用方保证资源已扣除,handler 内部不再重复扣资源,仅负责验证配方并返回产物。</p>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @param source      AE 动作源
     * @param details     完整的配方详情（可为 null）
     * @return 合成产物列表；失败返回空列表
     */
    default List<ItemStack> virtualCraftBatch(World world, BlockPos pos,
                                              InventoryCrafting ingredients,
                                              IAEItemStack[] outputs,
                                              long count,
                                              IActionSource source,
                                              ICraftingPatternDetails details) {
        return virtualCraftBatch(world, pos, ingredients, outputs, count, source);
    }

    /**
     * 执行 {@code count} 次虚拟合成.
     *
     * <p>调用方保证资源已扣除,handler 内部不再重复扣资源,仅负责验证配方并返回产物。</p>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @param source      AE 动作源
     * @return 合成产物列表；失败返回空列表
     */
    List<ItemStack> virtualCraftBatch(World world, BlockPos pos,
                                      InventoryCrafting ingredients,
                                      IAEItemStack[] outputs,
                                      long count,
                                      IActionSource source);

    /**
     * 获取该 handler 在虚拟合成时使用的粒子效果.
     *
     * @param world 目标世界
     * @param pos   目标位置
     * @return 粒子类型列表
     */
    default List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Collections.singletonList(EnumParticleTypes.PORTAL);
    }

    /**
     * 未安装虚拟并行卡时，该 handler 是否仍应以多少并行进入虚拟批量模式.
     * <p>
     * 返回大于 1 的值可使该 handler 在无虚拟并行卡时也走虚拟批量路径。
     * 默认值 1 表示必须由虚拟并行卡触发。
     * </p>
     */
    default long getDefaultParallel() {
        return 1;
    }

    /**
     * 默认实现：按 count 缩放单份产物，避免每个 handler 都手写循环。
     *
     * <p>若产物有随机副产物或 count 不能简单缩放，handler 应覆盖此方法。</p>
     *
     * @param outputs 单份产物模板
     * @param count   份数
     * @return 合并后的产物列表
     */
    default List<ItemStack> scaleOutputsByCount(IAEItemStack[] outputs, long count) {
        if (outputs == null || outputs.length == 0 || count <= 0) {
            return Collections.emptyList();
        }
        List<ItemStack> products = new ArrayList<>();
        for (IAEItemStack output : outputs) {
            if (output == null || output.getStackSize() <= 0) continue;
            long total = output.getStackSize() * count;
            if (total <= 0) continue;
            ItemStack template = output.createItemStack();
            if (template.isEmpty()) continue;
            int maxSize = template.getMaxStackSize();
            while (total > 0) {
                int slice = (int) Math.min(total, maxSize);
                ItemStack copy = template.copy();
                copy.setCount(slice);
                products.add(copy);
                total -= slice;
            }
        }
        return products;
    }
}
