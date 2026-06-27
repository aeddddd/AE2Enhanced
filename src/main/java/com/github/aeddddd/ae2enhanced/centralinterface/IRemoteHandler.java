package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * 远程目标处理器的抽象接口.
 *
 * 每个 {@link TargetBinding} 对应的目标方块,在合成推送和产物收集阶段
 * 会由匹配的 IRemoteHandler 处理实际物品交互.
 *
 * <p>P2 完整生命周期：</p>
 * <pre>
 * canHandle(blockId) ──→ isValidTarget(world, pos) ──→ canStart(world, pos, ingredients)
 *        ↓
 * pushMaterials(world, pos, ingredients, source) ──→ startProcess(world, pos, source)
 *        ↓
 * (等待机器处理)
 *        ↓
 * isIdle(world, pos) == true ──→ collectProducts(world, pos, expectedOutputs, source)
 * </pre>
 */
public interface IRemoteHandler {

    /**
     * 返回本处理器支持的能力集合.
     *
     * <p>用于 {@link DualityCentralInterface} 决定对该目标使用物理发配还是虚拟批量合成。
     * 例如虚拟-only 的 Extended Crafting Table 应只返回 {@link HandlerCapabilities#VIRTUAL_BATCH}，
     * 避免无虚拟并行卡时误走物理路径白嫖产物。</p>
     *
     * @return 能力集合，不能为空
     */
    EnumSet<HandlerCapabilities> getCapabilities();

    /**
     * 判断本处理器是否支持指定能力.
     */
    default boolean hasCapability(HandlerCapabilities capability) {
        return getCapabilities().contains(capability);
    }

    /**
     * 按方块 ID 字符串匹配此处理器是否支持该类型.
     *
     * <p>用于 {@link HandlerRegistry} 反射隔离查找,不涉及第三方类加载.</p>
     *
     * @param blockId 方块注册 ID(如 "minecraft:furnace"、"botania:pool")
     * @return true 表示此处理器可以处理该方块类型
     */
    boolean canHandle(String blockId);

    /**
     * 验证目标位置是否存在且为正确类型.
     *
     * @param world 世界实例
     * @param pos   方块位置
     * @return true 表示目标有效且可被处理
     */
    boolean isValidTarget(World world, BlockPos pos);

    /**
     * 判断当前目标是否可以开始执行该配方.
     *
     * <p>例如：检查能量是否充足、输入槽是否空闲、祭坛是否就绪等.
     * 对于简单机器(如熔炉),可始终返回 {@code true}.</p>
     *
     * @param world       世界实例
     * @param pos         方块位置
     * @param ingredients 配方输入物品(来自 {@link InventoryCrafting})
     * @return true 表示可以开始推送材料
     */
    boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session);

    /**
     * 将合成配方所需的全部材料推送到目标方块输入端.
     *
     * <p><b>原子性要求</b>：返回 {@code true} 时，{@code ingredients} 中所有非空物品必须全部进入
     * 目标机器的可跟踪位置；返回 {@code false} 时，handler 必须先把已经插入的部分回退到与调用前
     * 一致的状态。调用方仍会再调用 {@link #revertMaterials} 作为安全网。</p>
     *
     * @param world       世界实例
     * @param pos         方块位置
     * @param ingredients 配方输入物品
     * @param source      AE 动作源(用于能量扣除等审计)
     * @return true 表示所有材料均已成功推送
     */
    boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session);

    /**
     * 启动机器处理流程(如激活祭坛、开始注魔等).
     *
     * <p>对于简单机器(如熔炉)此方法应为空操作并返回 {@code true},
     * 因为物品插入后机器会自动开始处理.</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param source AE 动作源
     * @return true 表示成功触发启动
     */
    boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session);

    /**
     * 从目标方块收集已完成的产物(以及残余物品如空桶).
     *
     * @param world           世界实例
     * @param pos             方块位置
     * @param expectedOutputs 配方预期产物列表,handler 应优先收集匹配物品
     * @param inputs          该目标本次合成推送的输入材料快照(用于区分产物与残留输入)
     * @param source          AE 动作源
     * @return 实际收集到的物品列表；若无产物则返回空列表(非 null)
     */
    List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session);

    /**
     * 判断目标当前是否可以安全收集产物.
     *
     * <p>返回 {@code true} 时，{@link DualityCentralInterface} 会调用
     * {@link #collectProducts} 取走产物/残余。对于条件启动型或进度型机器，
     * 不能仅因为 {@code progress == 0} 或 {@code burnTime == 0} 就返回 true，
     * 必须确认机器已收到材料并至少开始处理，或已经过了推料保护期。</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param inputs 该目标本次合成推送的输入材料快照(用于区分产物与残留输入)
     * @return true 表示目标当前可以收集产物
     */
    boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session);

    /**
     * 判断目标是否已完全处理完本次推送的所有输入材料.
     *
     * <p>当 {@link #isIdle} 返回 {@code true} 且产物已被收集后,
     * DualityCentralInterface 调用此方法决定是否将目标移出跟踪列表.</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param inputs 该目标本次合成推送的输入材料快照
     * @return true 表示所有输入已处理完成,可以结束本次发配
     */
    default boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        return isIdle(world, pos, inputs, session);
    }

    /**
     * 当 {@code pushMaterials} 成功但 {@code startProcess} 失败时,
     * 调用此方法将已推送的材料从目标取回.
     *
     * <p>默认实现返回空列表,表示不回退.需要回退能力的 handler 应覆盖此方法.</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param source AE 动作源
     * @return 回退收集到的物品列表；无物品则返回空列表
     */
    default List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return java.util.Collections.emptyList();
    }

    /**
     * 在发配前回收目标输出槽中的全部残留内容,防止残留产物干扰新材料推送.
     *
     * <p>默认实现返回空列表.通用机器处理器应覆盖此方法以遍历 IItemHandler 提取所有物品.</p>
     *
     * @param world  世界实例
     * @param pos    方块位置
     * @param source AE 动作源
     * @return 回收到的物品列表；无物品则返回空列表
     */
    default List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return java.util.Collections.emptyList();
    }

    /**
     * 当目标从中枢接口解绑时调用，用于清理 handler 侧可能存在的 per-target 缓存。
     *
     * <p>默认实现为空。若 handler 内部保存了按坐标索引的状态（如推料时间戳、
     * 配方缓存等），应覆盖此方法并在目标解绑时清除，避免内存泄漏。</p>
     *
     * @param world 世界实例
     * @param pos   方块位置
     */
    default void onBindingRemoved(World world, BlockPos pos) {
    }
}
