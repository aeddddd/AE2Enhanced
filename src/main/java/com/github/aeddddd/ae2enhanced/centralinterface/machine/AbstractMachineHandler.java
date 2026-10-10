package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 通用机器 handler 骨架：把"库存访问"抽象为 {@link ISlotCatalog}、"处理中信号"抽象为
 * {@link MachineIdle} 之后，推料（两阶段原子）、收集（两阶段 + 反复制过滤）、回退（按台账精确取回）、
 * 输出槽清理、空闲与完成判定<b>全部由框架提供</b>，机器族只回答两个问题。
 *
 * <h3>通用 handler 模型</h3>
 * <ul>
 *   <li><b>1 个通用骨架</b>：本类是<b>唯一</b>实现 {@link IRemoteHandler} 生命周期的地方，
 *       且不含任何 mod 相关代码；生命周期默认值写在 {@link MachineStorageAccess} 的 default 方法里
 *       （本类只把两个 seam 接上去）。</li>
 *   <li><b>N 个家族行为描述</b>：每个机器族一个 {@link MachineStorageAccess} 实现（本类的子类），
 *       只装"槽位目录怎么来""什么算处理中"以及本族特有的覆盖。它们各自被 {@code HandlerRegistry}
 *       按 mod 门控懒加载，未安装的 mod 对应的类永远不会被加载（反射隔离约定）。</li>
 *   <li><b>声明式表项</b>：{@link #spec()} 返回本族的 {@link MachineSpec} —— blockId 匹配规则 +
 *       本行为描述。{@link #canHandle} 与 {@code HandlerRegistry} 的两级索引都从这份表项取值，
 *       匹配规则只有一处声明。</li>
 * </ul>
 *
 * <p>子类只需回答 {@link #catalogFor}（这台机器的槽位目录是什么）与 {@link #idleGate}
 * （它什么时候算"正在处理"），其余覆盖按需。</p>
 *
 * <p><b>加一台机器</b>：如果它属于已有家族，往该族表项的 id/前缀里加一条即可；
 * 如果是新家族，写一个本类的子类（声明表项 + 两个 seam），再在 {@code HandlerRegistry} 里加一行
 * mod 门控的类名。</p>
 */
public abstract class AbstractMachineHandler implements MachineStorageAccess {

    /** 惰性构造的表项（匹配规则 + 本行为描述）。 */
    private MachineSpec spec;

    /**
     * 构造目标槽位目录.
     *
     * <p>这是框架读取机器库存的唯一入口，也是测试替换目录的接缝：
     * 覆盖它即可把目录换成任意 {@link ISlotCatalog}，框架的推料/收集/回退/判定全部随之生效.</p>
     *
     * @return 目录；目标无效或没有可访问槽位时返回 {@code null}
     */
    protected abstract ISlotCatalog catalogFor(World world, BlockPos pos);

    /** 该目标的空闲判定策略（测试替换"机器是否在处理"的接缝）。 */
    protected abstract MachineIdle idleGate(World world, BlockPos pos);

    /**
     * 声明本族的表项：匹配哪些 blockId，以及以本处理器为行为描述.
     *
     * <p>实现应为 {@code MachineSpec.builder().exactIds(...) / .prefixes(...).access(this).build()}；
     * 兜底处理器用具名 {@code fallback()} 声明一条不匹配任何 blockId 的表项.</p>
     */
    protected abstract MachineSpec defineSpec();

    /** 本族的表项（首次调用时由 {@link #defineSpec()} 构造并缓存）。 */
    public final MachineSpec spec() {
        if (this.spec == null) {
            this.spec = defineSpec();
        }
        return this.spec;
    }

    @Override
    public final ISlotCatalog catalog(World world, BlockPos pos) {
        return catalogFor(world, pos);
    }

    @Override
    public final MachineIdle idle(World world, BlockPos pos) {
        return idleGate(world, pos);
    }

    /** blockId 匹配完全由表项裁定。 */
    @Override
    public final boolean canHandle(String blockId) {
        return spec().matches(blockId);
    }

    /** {@link MachineStorageAccess#itemsOf} 的转发：接口 static 方法不被实现类继承。 */
    protected static List<ItemStack> ingredientsOf(InventoryCrafting ingredients) {
        return MachineStorageAccess.itemsOf(ingredients);
    }
}
