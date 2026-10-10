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
     * 纯虚拟设备（不存在物理交互空间的工作台类）必须覆盖此方法并只返回
     * {@link HandlerCapabilities#VIRTUAL_BATCH}，否则会被 {@link ExecutionMode} 判定为
     * 双能力设备，在未装虚拟卡时误走物理发配。
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
     * <p><b>返回值语义（由引擎强制）</b>：</p>
     * <ul>
     *   <li>{@code null} —— <b>无法计价</b>，例如目标 mod 的源质 / 魔力 / 星光通道缺失。
     *       引擎视为失败并放弃本次虚拟合成；</li>
     *   <li>空列表 —— 该配方<b>确实零消耗</b>（仅限真正无投入的设备）。引擎按请求的并行数执行、
     *       仅受 AE 能量上限钳制，并对该 handler <b>每个类告警一次</b>；</li>
     *   <li>非空列表 —— 正常计价结果。</li>
     * </ul>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品(来自 {@link InventoryCrafting})
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @param details     完整的配方详情（可为 null）
     * @return 资源清单；无法计价时必须返回 {@code null}（不得返回空列表）
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
     * <p>{@code null} 与空列表的语义区别见上方带 {@code patternDetails} 参数的重载。</p>
     *
     * @param world       目标世界
     * @param pos         目标位置
     * @param ingredients 单份配方的输入物品(来自 {@link InventoryCrafting})
     * @param outputs     单份配方的预期产物
     * @param count       虚拟合成份数
     * @return 资源清单；无法计价时必须返回 {@code null}（不得返回空列表）
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
     * 本设备自带的虚拟并行度.
     *
     * <p>仅对 {@link ExecutionMode#VIRTUAL_NATIVE}（不存在物理交互空间的工作台类设备）生效：
     * 这类设备不装卡也走虚拟批量，本方法给出其固有并行度（例如 Extended Crafting 工作台为 8），
     * 装卡时再被卡等级抬高。</p>
     *
     * <p>对 {@link ExecutionMode#VIRTUAL_CARDED} 不生效——并行度由卡等级直接决定；
     * 对 {@link ExecutionMode#PHYSICAL} 不生效——完全不经过虚拟路径。</p>
     *
     * <p>默认值 1 表示无固有并行度（仅单份）。</p>
     */
    default long getDefaultParallel() {
        return 1;
    }

    /**
     * 当本次虚拟批量实际只处理了 1 份时，是否跳过目标/全局冷却。
     * <p>
     * 某些设备（如 Extended Crafting 工作台）希望“未成功应用并行”时
     * 表现得和普通物理发配一样：不进入虚拟冷却，使后续 CPU 调度可以
     * 继续立即尝试其它目标或同目标的下一次合成。</p>
     *
     * <p>仅在虚拟路径（{@link ExecutionMode#VIRTUAL_NATIVE} /
     * {@link ExecutionMode#VIRTUAL_CARDED}）下被查询。</p>
     *
     * @return true 表示当实际并行数为 1 时不设置冷却；默认 false
     */
    default boolean skipCooldownOnSingleBatch() {
        return false;
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
