package com.github.aeddddd.ae2enhanced.centralinterface.handler.actuallyadditions;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.CatalogBuilder;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.centralinterface.reflection.ReflectionCache;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Actually Additions Empowerer（充能台）+ Display Stand 处理器.
 *
 * <p>结构：中心 Empowerer，水平四方向距离 3 格各 1 个 Display Stand；库存都不在主方块内，
 * 目录因此把 4 个 Stand 的槽位一并纳入（{@link #buildCatalog}）。Empowerer 的槽装主材料、
 * 配方完成时被产物直接覆盖；Stand 的槽装辅助材料、配方完成时被消耗；槽上限均为 1。</p>
 *
 * <p>样板布局约定：{@code ingredients} 槽 0 = 主材料，槽 1 起为辅助材料（按序对应各 Stand）。</p>
 *
 * <p>目录构造、推料的原子性、槽位遍历由框架 {@link AbstractMachineHandler} 提供；推料的落位语义、
 * 产物收集/清理/回退/空闲判定因本机特有的三点而与框架默认不同，各自覆盖并注明原因：
 * 没有独立产物槽、存在产物与本批输入同型的（充能类）配方、Empowerer 拒绝移出"仍是合法输入"的物品。</p>
 */
public class ActuallyAdditionsHandler extends AbstractMachineHandler {

    private static final String EMPOWERER_ID = "actuallyadditions:block_empowerer";

    private static final String FIELD_PROCESS_TIME = "processTime";

    private static final String EMPOWERER_CLASS = "de.ellpeck.actuallyadditions.mod.tile.TileEntityEmpowerer";
    private static final String DISPLAY_STAND_CLASS = "de.ellpeck.actuallyadditions.mod.tile.TileEntityDisplayStand";
    private static final String GET_NEARBY_STANDS_METHOD = "getNearbyStands";

    /**
     * Stand 数量固定为 4：{@code getNearbyStands()} 遍历水平四方向、距中心 3 格，
     * 任何一格不是 Stand 即整体返回 null（只有"四格齐全"与 null 两种结果）。
     */
    private static final int DISPLAY_STAND_COUNT = 4;

    /** 推料后的保护 tick 数：材料刚进机器的那个 tick processTime 还是 0。 */
    private static final int PUSH_IDLE_GRACE_TICKS = 2;

    /** 逐槽抽取上限（本机槽上限为 1）。 */
    private static final int DRAIN_LIMIT = 64;

    // 反射缓存（首次使用时惰性初始化，失败即抛）
    private static Class<?> CLASS_EMPOWERER;
    private static Class<?> CLASS_DISPLAY_STAND;
    private static Method METHOD_GET_NEARBY_STANDS;
    private static boolean reflectionReady = false;

    /**
     * 本机的"是否正在处理"判定：反射读 Empowerer 的 {@code processTime}，为 0 即不在处理.
     *
     * <p>这是本机唯一可靠的进度信号：找不到配方（或某个 Stand 能量不足）时 processTime 保持 0，
     * 配方跑完写产物后也归零为 0。</p>
     */
    static final MachineIdle EMPOWERER_IDLE = new MachineIdle() {
        @Override
        public boolean isWorking(World world, BlockPos pos, TileEntity te) {
            return readProcessTimeWorking(te);
        }

        @Override
        public int graceTicks() {
            return PUSH_IDLE_GRACE_TICKS;
        }
    };

    private static void initReflection() {
        if (reflectionReady) return;
        // 经 ReflectionCache 查找:失败结果也被缓存,因此"没装 AA / 版本不匹配"时不再每 tick 重新
        // Class.forName 并构造异常。抛异常仍是惰性的、每次失败都抛,由 PhysicalDispatcher 的
        // try/catch 隔离;DisplayStand 类的存在性仍参与失败判定)。
        Class<?> empowerer = ReflectionCache.clazz(EMPOWERER_CLASS);
        Class<?> displayStand = ReflectionCache.clazz(DISPLAY_STAND_CLASS);
        Method getNearbyStands = ReflectionCache.declaredMethod(empowerer, GET_NEARBY_STANDS_METHOD);
        if (empowerer == null || displayStand == null || getNearbyStands == null) {
            throw new RuntimeException("[AE2E] ActuallyAdditions reflection init failed");
        }
        CLASS_EMPOWERER = empowerer;
        CLASS_DISPLAY_STAND = displayStand;
        METHOD_GET_NEARBY_STANDS = getNearbyStands;
        reflectionReady = true;
    }

    /** 表项：只匹配 Empowerer 一个方块（Display Stand 不是中枢接口的直接目标），行为描述即本类. */
    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder().exactIds(EMPOWERER_ID).access(this).build();
    }

    // ---- 目录 ----

    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) {
            return null;
        }
        // AA 的 TileEntityBase#getItemHandler(facing) 忽略面参数、直接返回 tile 内部的
        // ItemStackHandlerAA，因此 null 面与任何侧面等价。
        IItemHandler empowererInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        return buildCatalog(empowererInv, standInventories(te));
    }

    /**
     * 构造目标目录（纯逻辑，不触碰反射，供测试直接驱动）.
     *
     * <p>角色一律 {@link SlotRole#INPUT}：这些槽都是"接受本批材料"的槽位。本机没有独立产物槽
     * （Empowerer 的槽在配方完成后被产物覆盖），单值 {@link SlotRole} 表达不了"输入槽兼产物槽"，
     * 因此产物侧的判据由 {@link #collectProducts} 等按原语义接管，而不是靠目录角色推断。</p>
     *
     * <p>Stand 结构不完整（{@code stands == null}）时只登记 Empowerer 自身的槽：目标本身仍有效，
     * 缺 Stand 由 {@link #canStart} 拒绝。</p>
     *
     * @return 目录；{@code empowererInv == null}（取不到库存）时返回 {@code null}，框架据此判目标无效
     */
    static ISlotCatalog buildCatalog(@Nullable IItemHandler empowererInv, @Nullable IItemHandler[] stands) {
        if (empowererInv == null) {
            return null;
        }
        CatalogBuilder builder = ISlotCatalog.builder();
        builder.addAll(empowererInv, null, SlotRole.INPUT);
        if (stands != null) {
            for (IItemHandler stand : stands) {
                // addAll 自身跳过 null handler；AA 的 Stand 恒有 item capability，缺 handler 不可达
                builder.addAll(stand, null, SlotRole.INPUT);
            }
        }
        return builder.build();
    }

    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        return EMPOWERER_IDLE;
    }

    // ---- 启动前置条件 ----

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        if (world == null || pos == null) {
            return false;
        }
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) {
            return false;
        }
        IItemHandler empowererInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        return canStart(empowererInv, standInventories(te), ingredients);
    }

    /**
     * 启动前置条件（纯逻辑，供测试直接驱动）：结构齐全、主槽为空、主材料能被主槽完整接受、
     * 本批要用到的每个 Stand 槽为空.
     *
     * <p>必须覆盖框架默认（只判"目录非 null"）：Empowerer 的槽只接受 {@code isPossibleInput} 的物品、
     * Stand 的槽上限为 1，目标里若已留有物品，框架推料会把主材料顶到 Stand 槽、辅助材料挤到更后面，
     * 机器拿到错位布局后无法开始。</p>
     *
     * <p>只检查"前 auxCount 个 Stand"，不检查能量与配方——Empowerer 每 tick 自查配方，
     * 缺能量时 processTime 保持 0，由 grace 之后的收集路径把材料收回。</p>
     */
    static boolean canStart(@Nullable IItemHandler empowererInv, @Nullable IItemHandler[] stands,
                            @Nullable InventoryCrafting ingredients) {
        if (empowererInv == null || stands == null || empowererInv.getSlots() == 0) {
            return false;
        }
        if (!empowererInv.getStackInSlot(0).isEmpty()) {
            return false;
        }
        // 主材料必须能被主槽完整接受（主槽上限为 1，且 Empowerer 只接受 isPossibleInput 的物品）。
        // 框架推料是贪心填槽，主材料若不被 Empowerer 接受（样板编码错误、或数量 >1）会被塞进
        // Stand 而不是失败，因此拒绝必须前移到此处的预检。
        ItemStack main = ingredients != null && ingredients.getSizeInventory() > 0
                ? ingredients.getStackInSlot(0)
                : ItemStack.EMPTY;
        if (!main.isEmpty() && !empowererInv.insertItem(0, main.copy(), true).isEmpty()) {
            return false;
        }
        int auxCount = countAuxiliaryMaterials(ingredients);
        for (int i = 0; i < Math.min(auxCount, stands.length); i++) {
            IItemHandler standInv = stands[i];
            if (standInv == null || standInv.getSlots() == 0 || !standInv.getStackInSlot(0).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 配方槽 1 起（0 号槽是主材料）的非空物品数。 */
    static int countAuxiliaryMaterials(@Nullable InventoryCrafting ingredients) {
        if (ingredients == null) {
            return 0;
        }
        int count = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // ---- 空闲 / 完成判定 ----

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            // 框架约定：目录为 null（目标已不是 Empowerer）时没有可等待的东西，交由 hasFinished 结束会话
            return true;
        }
        MachineIdle gate = idleGate(world, pos);
        boolean graceElapsed = session == null
                || session.isPushGraceElapsed(world.getTotalWorldTime(), gate.graceTicks());
        return isCollectable(graceElapsed, gate, world, pos, world.getTileEntity(pos));
    }

    /**
     * 空闲判定核心（纯函数，供测试直接驱动）：grace 期内或机器仍在处理中，都不可收集.
     *
     * <p>必须覆盖框架默认 isIdle（= 机器不再处理 <b>且</b> 有可收集产物）：框架的可收集性由
     * {@code ProductFilter} 判定，其第一条规则是"本批输入快照内的物品一律不算产物"，
     * 而本机的产物写回主输入槽、还有产物与输入同型（充能类）的配方 → 框架默认永远判不出空闲，
     * 会话要白等 processingTimeoutTicks 超时。</p>
     */
    static boolean isCollectable(boolean graceElapsed, MachineIdle gate, World world, BlockPos pos, TileEntity te) {
        return graceElapsed && !gate.isWorking(world, pos, te);
    }

    /**
     * 完成判定 = {@code IRemoteHandler} 未覆盖时的默认行为（即 {@code return isIdle(...)}）.
     *
     * <p>不能用框架默认（本批输入已耗尽 <b>且</b> 无残留产物）：Empowerer 拒绝移出"仍是合法输入"的
     * 物品，这类残留在 {@link #drainTarget} 里取不走、也不会被识别为产物 → 框架默认会让会话在
     * PROCESSING / COLLECTING 之间每 tick 反复 beginCollect，直到超时。</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        return isIdle(world, pos, inputs, session);
    }

    // ---- 收集 / 清理 / 回退 ----

    /**
     * 收集产物：整机里"抽得出来的一切"都算本批.
     *
     * <p>不能用框架默认（阶段 1 按预期产物匹配、阶段 2 只收 OUTPUT 角色槽）：本机没有独立产物槽；
     * 存在产物与本批输入同型的配方；Stand 里未消耗的辅助材料也属于本批输入快照——三者叠加会让产物与
     * 残余永久留在机器里，而 {@link #canStart} 又要求这些槽为空，目标就此卡死。</p>
     */
    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source, TargetSession session) {
        return drainTarget(catalogFor(world, pos));
    }

    /**
     * 发配前清理：取空全部槽位.
     *
     * <p>不能用框架默认：目录角色全为 INPUT（本机没有 OUTPUT 槽）时框架只清 OUTPUT 槽，
     * 等于什么都不清，上一轮残留会让 {@link #canStart} 永远为 false。</p>
     */
    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return drainTarget(catalogFor(world, pos));
    }

    /**
     * 回退材料：取空全部槽位.
     *
     * <p>不能用框架默认 {@code revertBatch}（只回退本批输入快照内的物品）：本机槽位少且互不混杂，
     * 按"取空"语义即可完整回退；框架默认在 PUSHING 早期失败（快照尚未记录）时什么也取不回。</p>
     */
    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return drainTarget(catalogFor(world, pos));
    }

    /**
     * 依次抽取目录中"抽得出来"的全部槽位（纯逻辑，供测试直接驱动）.
     *
     * <p>Empowerer 的槽 + 各 Stand 的槽，逐槽最多
     * {@link #DRAIN_LIMIT} 个；抽不出来的槽（Empowerer 对"仍是合法输入"的物品会拒绝移出）跳过。</p>
     */
    static List<ItemStack> drainTarget(@Nullable ISlotCatalog catalog) {
        List<ItemStack> result = new ArrayList<>();
        if (catalog == null) {
            return result;
        }
        for (SlotRef slot : catalog.slots()) {
            ItemStack inSlot = slot.peek();
            if (inSlot.isEmpty()) {
                continue;
            }
            ItemStack got = slot.extract(Math.min(DRAIN_LIMIT, inSlot.getCount()), false);
            if (!got.isEmpty()) {
                result.add(got);
            }
        }
        return result;
    }

    // ---- 反射 ----

    /**
     * 反射读 {@code processTime}；读不到时按"仍在处理"处理.
     *
     * <p>字段查找走 {@link ReflectionCache}：这是每 tick 调用的路径，缓存后不再每次
     * {@code CLASS_EMPOWERER.getField(...)}；{@code CLASS_EMPOWERER} 尚未解析（没装 AA / 尚未
     * 调用 {@link #initReflection}）时缓存直接返回 null。</p>
     */
    private static boolean readProcessTimeWorking(@Nullable TileEntity te) {
        Field processTime = ReflectionCache.field(CLASS_EMPOWERER, FIELD_PROCESS_TIME);
        if (processTime == null) {
            return isWorkingProcessTime(null);
        }
        try {
            return isWorkingProcessTime((Integer) processTime.get(te));
        } catch (Exception e) {
            return isWorkingProcessTime(null);
        }
    }

    /**
     * processTime 判定的纯函数（供测试直接驱动）.
     *
     * @param processTime 反射读到的配方进度；{@code null} 表示读取失败
     * @return true 表示仍在处理中（不可收集）
     */
    static boolean isWorkingProcessTime(@Nullable Integer processTime) {
        return processTime == null || processTime != 0;
    }

    /**
     * 取 4 个 Display Stand 的 item handler，顺序即 {@code getNearbyStands()} 的枚举顺序
     * （与 Empowerer 自己拼装配方的槽位顺序一致）.
     *
     * @return 长度恒为 4 的数组（取不到 handler 的 Stand 位置为 null）；结构不完整时返回 null
     */
    @Nullable
    private static IItemHandler[] standInventories(TileEntity empowerer) {
        Object[] stands = getNearbyStands(empowerer);
        if (stands == null || stands.length != DISPLAY_STAND_COUNT) {
            return null;
        }
        IItemHandler[] result = new IItemHandler[stands.length];
        for (int i = 0; i < stands.length; i++) {
            if (!(stands[i] instanceof TileEntity)) {
                return null;
            }
            result[i] = ((TileEntity) stands[i]).getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        }
        return result;
    }

    /** 调用 Empowerer 的私有 {@code getNearbyStands()}：四格齐全返回 4 个 Stand，否则返回 null。 */
    @Nullable
    private static Object[] getNearbyStands(TileEntity empowerer) {
        try {
            return (Object[]) METHOD_GET_NEARBY_STANDS.invoke(empowerer);
        } catch (Exception e) {
            return null;
        }
    }
}
