package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 一个机器族的<b>行为描述</b>：库存怎么读写（{@link #catalog}）、什么时候算在处理（{@link #idle}），
 * 以及本族与框架默认语义不同的可覆盖点（启动前置条件、空闲/完成判据、收集/回退/清理口径）。
 *
 * <p><b>本接口就是该族的远程处理器本体</b>（继承 {@link IRemoteHandler}）：</p>
 * <ul>
 *   <li>框架默认实现全部写在这里的 {@code default} 方法里（推料两阶段原子、按台账回退、
 *       两阶段收集、输出槽清理、"产物确实出现"判据……），<b>只有一个副本</b>；</li>
 *   <li>各机器族用 {@link AbstractMachineHandler} 作为骨架，只覆盖自己特有的部分
 *       （如 TE 的流体罐闸门、EIO 的输出队列、TR/NC 的三阶段收集）；</li>
 *   <li>{@link MachineSpec} 是"表项"：把 blockId 匹配规则与本行为描述绑在一起，
 *       {@code HandlerRegistry} 据此注册与建索引（表项是匹配规则的唯一声明处）。</li>
 * </ul>
 *
 * <p><b>为什么不做成"1 个通用 handler 持有全部家族"</b>：{@code HandlerRegistry} 按 mod 门控懒加载
 * （{@code Loader.isModLoaded} + {@code Class.forName}），未安装的 mod 对应的类永远不会被加载
 * ——这是项目的反射隔离约定。若把六个家族塞进同一个 handler 类，只要其中一个 mod 存在，
 * 该"通用 handler"就会被加载，进而把其余五个 mod 的反射入口一并装进内存，违反该约定
 * （见 {@code AGENTS.md} 第 11 条）。因此这里的分工是：
 * <b>1 个与 mod 无关的通用骨架（{@link AbstractMachineHandler}） + N 个各自被 mod 门控的家族行为描述</b>。</p>
 *
 * <p><b>目录为 null 的统一约定</b>：{@link #catalog} 返回 {@code null} 表示"目标不是本族的机器"，
 * 此时框架的各入口一律退化为"目标无效/无需等待"：推料与启动失败、收集回退清理为空、
 * {@code isIdle} 与 {@code hasFinished} 为 true（交由调用方结束会话）。各族的覆盖方法必须保持这条口径，
 * 不要再各自判一遍 null。</p>
 */
public interface MachineStorageAccess extends IRemoteHandler {

    /**
     * 构造该目标的槽位目录.
     *
     * @return 目录；目标方块类型不符、反射失败或没有任何可访问槽位时返回 {@code null}
     *         （调用方据此判定目标无效）
     */
    ISlotCatalog catalog(World world, BlockPos pos);

    /**
     * 该机器族的"是否正在处理"判定策略.
     *
     * <p>返回的是策略对象而不是一次判定结果：策略在调用时拿到 {@code TileEntity}，
     * 而同一族的机器可能按实例类型读不同字段（如 NuclearCraft 的两套体系）.</p>
     */
    MachineIdle idle(World world, BlockPos pos);

    /**
     * 能力集合：机器族默认只支持物理发配.
     *
     * <p>虚拟批量合成能力由 {@code IVirtualBatchCraftingHandler} 家族提供，本接口的实现者
     * （纯物理机器）无需覆盖.</p>
     */
    @Override
    default EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.physicalOnly();
    }

    /** 目标有效 = 目录构造成功. */
    @Override
    default boolean isValidTarget(World world, BlockPos pos) {
        return catalog(world, pos) != null;
    }

    /** 可开始 = 目录构造成功（条件启动型机器覆盖此方法）. */
    @Override
    default boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        return catalog(world, pos) != null;
    }

    /**
     * 两阶段原子推料：目录全部输入槽做纯模拟，全部放得下才真实插入.
     *
     * <p>成功后把逐槽台账交给会话（{@link TargetSession#recordPushLedger}）：目标容器会把同型物品
     * 并入槽内原有堆叠，没有这份台账，回退时只能整槽取走，连带把机器原有的物品一起偷走.</p>
     */
    @Override
    default boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients,
                                  IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalog(world, pos);
        if (catalog == null) {
            return false;
        }
        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(catalog, itemsOf(ingredients));
        if (outcome.isSuccess() && session != null) {
            session.recordPushLedger(outcome.ledger());
        }
        return outcome.isSuccess();
    }

    /** 物品插入后机器自行开工（条件启动型机器覆盖此方法）. */
    @Override
    default boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    /** 两阶段收集：先按样板声明的预期产物匹配，再收输出槽剩余（本批输入快照内的物品永不算产物）. */
    @Override
    default List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalog(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        return ItemTransferHelper.collectProducts(catalog, expectedOutputs, inputs);
    }

    /**
     * 空闲判定：推料保护窗口 → 机器是否仍在处理 → "产物确实已经出现".
     *
     * <p>只认"产物确实已经出现"。不能用"本批输入已被消耗"当空闲依据：大量机器会把输入从槽位搬进
     * 内部进度缓冲，此时输入槽已空而产物尚未产生，据此判定会当场收走空气并结束会话，产物再无人接管.</p>
     */
    @Override
    default boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalog(world, pos);
        if (catalog == null) {
            // 目标已不存在：没有可等待的东西，交由 hasFinished 结束会话
            return true;
        }
        MachineIdle gate = idle(world, pos);
        if (session != null && !session.isPushGraceElapsed(world.getTotalWorldTime(), gate.graceTicks())) {
            return false;
        }
        if (gate.isWorking(world, pos, world.getTileEntity(pos))) {
            return false;
        }
        return ItemTransferHelper.hasCollectableProduct(catalog, inputs);
    }

    /** 完成判定：本批输入已耗尽且没有可收集产物. */
    @Override
    default boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalog(world, pos);
        if (catalog == null) {
            return true;
        }
        return ItemTransferHelper.allBatchInputsConsumed(catalog, inputs)
                && !ItemTransferHelper.hasCollectableProduct(catalog, inputs);
    }

    /**
     * 回退本批材料.
     *
     * <p>优先按推料台账精确取回（每个槽最多取回台账记录的数量，且以该槽现存量为上限）：
     * 目标容器会自动合并同型堆叠，只按"本批材料快照"整槽取回会把机器原有的物品一并取走。
     * 拿不到台账（本族自行推料、或本批尚未推料）时退回快照口径.</p>
     */
    @Override
    default List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalog(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        List<ItemStack> batchInputs = session != null ? session.getInputs() : null;
        PushLedger ledger = session != null ? session.getPushLedger() : null;
        return ItemTransferHelper.revertBatch(catalog, batchInputs, ledger);
    }

    /** 发配前回收目标输出槽残留（角色未知时退化为"匹配预期产物或本批材料"的宽松口径）. */
    @Override
    default List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalog(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        List<ItemStack> batchInputs = session != null ? session.getInputs() : null;
        IAEItemStack[] expected = session != null ? session.getExpectedOutputs() : null;
        return ItemTransferHelper.clearOutputs(catalog, batchInputs, expected);
    }

    /**
     * 取合成台中的非空物品快照.
     *
     * <p>放在接口上是因为 {@link #pushMaterials} 的默认实现需要它，而接口的 static 方法
     * 不被实现类继承，故骨架 {@link AbstractMachineHandler} 另有一份同名转发（见其 {@code ingredientsOf}）.</p>
     */
    static List<ItemStack> itemsOf(InventoryCrafting ingredients) {
        List<ItemStack> result = new ArrayList<>();
        if (ingredients == null) {
            return result;
        }
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return result;
    }
}
