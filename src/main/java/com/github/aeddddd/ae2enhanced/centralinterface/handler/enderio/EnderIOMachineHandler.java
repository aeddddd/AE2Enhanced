package com.github.aeddddd.ae2enhanced.centralinterface.handler.enderio;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.CatalogBuilder;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ItemTransferHelper;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineStorageAccess;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Ender IO 机器远程处理器.
 *
 * <p>基于 Ender IO 5.3.72 + EnderCore 0.5.78 的实际类结构实现,通过反射访问：</p>
 * <ul>
 *   <li>legacy 机器 {@code AbstractInventoryMachineEntity} 的 {@code inventory[]} 与 {@code SlotDefinition}</li>
 *   <li>capability 机器 {@code AbstractCapabilityMachineEntity} 的 {@code EnderInventory} 及其 {@code Type.INPUT/OUTPUT} 视图</li>
 *   <li>{@code AbstractMachineEntity} 的 {@code isActive()} 与 {@code getOutputQueue()}</li>
 * </ul>
 *
 * <p>所有第三方类均通过 {@link Class#forName(String)} + 反射调用,不直接 import,
 * 保证 Ender IO 未安装时本类即使被加载也不会触发 {@link NoClassDefFoundError}.</p>
 *
 * <p>骨架归 {@link AbstractMachineHandler}：推料（两阶段原子）、槽位收集、清空输出槽、本批材料回退
 * 都由框架提供。本类只回答"槽位目录是什么"（{@link #catalogFor}）与"何时算处理中"
 * （{@link #idleGate}）、并声明表项（{@link #defineSpec}：{@code enderio:} 前缀），
 * 此外保留 Ender IO 的三处特有语义：</p>
 * <ol>
 *   <li><b>输出队列</b>（{@code AbstractMachineEntity.outputQueue}）不是槽位，目录无法表达，
 *       故收集/清空/回退/空闲判定都在框架结果之后追加队列处理；</li>
 *   <li><b>空闲与完成判据</b>用"输出范围内确实有东西"，而不是框架默认的
 *       {@code hasCollectableProduct}（后者会先排除本批材料、且不认队列）；</li>
 *   <li><b>处理中信号不只在 isIdle 里判</b>，{@code hasFinished} 也要判，否则机器仍在跑就会提前
 *       释放目标所有权。</li>
 * </ol>
 *
 * <p>三处与框架默认不同的行为（理由见各方法）：推料是原子的；
 * 回退只回退本批材料（否则会把玩家预置的物品收进 ME 网络）；
 * 不走六面兜底（Ender IO 六面视图是内部 INPUT 视图的子集，只在"既非 capability 也非 legacy 库存"
 * 时才退回六面目录）。</p>
 */
public class EnderIOMachineHandler extends AbstractMachineHandler {

    private static final boolean AVAILABLE;

    // ---- Ender IO classes ----
    private static Class<?> ABSTRACT_MACHINE_ENTITY_CLASS;
    private static Class<?> ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS;
    private static Class<?> ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS;
    private static Class<?> SLOT_DEFINITION_CLASS;

    // ---- EnderCore classes ----
    private static Class<?> ENDER_INVENTORY_CLASS;
    private static Class<?> ENDER_INVENTORY_TYPE_CLASS;

    // ---- Methods ----
    private static Method METHOD_IS_ACTIVE;
    private static Method METHOD_GET_OUTPUT_QUEUE;
    private static Method METHOD_GET_SLOT_DEFINITION;
    private static Method METHOD_IS_MACHINE_ITEM_VALID;
    private static Method METHOD_GET_INVENTORY_STACK_LIMIT;
    private static Method METHOD_GET_INVENTORY;
    private static Method METHOD_GET_VIEW;

    // ---- Fields ----
    private static Field FIELD_LEGACY_INVENTORY;
    private static Field FIELD_MIN_INPUT_SLOT;
    private static Field FIELD_MAX_INPUT_SLOT;
    private static Field FIELD_MIN_OUTPUT_SLOT;
    private static Field FIELD_MAX_OUTPUT_SLOT;

    // ---- Enum constants ----
    private static Object ENDER_INVENTORY_TYPE_INPUT;
    private static Object ENDER_INVENTORY_TYPE_OUTPUT;

    /**
     * 空目录占位.
     *
     * <p>对任何 {@code AbstractMachineEntity} 都算有效目标（不区分有没有可用槽位）：
     * 返回 null 会让调用方把目标标成 UNAVAILABLE 并解绑。</p>
     */
    private static final ISlotCatalog EMPTY_CATALOG = ISlotCatalog.builder().build();

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("enderio")) {
                // 核心基类
                ABSTRACT_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.base.te.AbstractMachineEntity");
                METHOD_IS_ACTIVE = ABSTRACT_MACHINE_ENTITY_CLASS.getMethod("isActive");
                METHOD_GET_OUTPUT_QUEUE = ABSTRACT_MACHINE_ENTITY_CLASS.getDeclaredMethod("getOutputQueue");
                METHOD_GET_OUTPUT_QUEUE.setAccessible(true);

                // legacy 机器
                try {
                    ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity");
                    SLOT_DEFINITION_CLASS = Class.forName("crazypants.enderio.base.machine.baselegacy.SlotDefinition");
                    FIELD_LEGACY_INVENTORY = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getDeclaredField("inventory");
                    FIELD_LEGACY_INVENTORY.setAccessible(true);
                    METHOD_GET_SLOT_DEFINITION = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getMethod("getSlotDefinition");
                    METHOD_IS_MACHINE_ITEM_VALID = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getMethod("isMachineItemValidForSlot", int.class, ItemStack.class);
                    METHOD_GET_INVENTORY_STACK_LIMIT = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getMethod("getInventoryStackLimit", int.class);
                    // SlotDefinition#isInputSlot/isOutputSlot 读的就是这四个字段；直接读字段可让
                    // 槽位角色映射成为不依赖 mod 的纯函数（见 legacyRoles）。
                    FIELD_MIN_INPUT_SLOT = SLOT_DEFINITION_CLASS.getField("minInputSlot");
                    FIELD_MAX_INPUT_SLOT = SLOT_DEFINITION_CLASS.getField("maxInputSlot");
                    FIELD_MIN_OUTPUT_SLOT = SLOT_DEFINITION_CLASS.getField("minOutputSlot");
                    FIELD_MAX_OUTPUT_SLOT = SLOT_DEFINITION_CLASS.getField("maxOutputSlot");
                } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] EIO legacy machine support not available: {}", e.toString());
                }

                // capability 机器
                try {
                    ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.base.te.AbstractCapabilityMachineEntity");
                    METHOD_GET_INVENTORY = ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.getMethod("getInventory");

                    ENDER_INVENTORY_CLASS = Class.forName("com.enderio.core.common.inventory.EnderInventory");
                    ENDER_INVENTORY_TYPE_CLASS = Class.forName("com.enderio.core.common.inventory.EnderInventory$Type");
                    METHOD_GET_VIEW = ENDER_INVENTORY_CLASS.getMethod("getView", ENDER_INVENTORY_TYPE_CLASS);
                    ENDER_INVENTORY_TYPE_INPUT = Enum.valueOf((Class<Enum>) ENDER_INVENTORY_TYPE_CLASS, "INPUT");
                    ENDER_INVENTORY_TYPE_OUTPUT = Enum.valueOf((Class<Enum>) ENDER_INVENTORY_TYPE_CLASS, "OUTPUT");
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] EIO capability machine support not available: {}", e.toString());
                }

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Ender IO handler", e);
        }
        AVAILABLE = available;
    }

    /**
     * 表项：{@code enderio:} 前缀匹配整个 mod，行为描述即本类.
     *
     * <p>前缀粒度无法收窄（{@code canHandle} 只拿得到 blockId），实际由 {@code isValidTarget}
     * （= 目录构造成功）把关：导管等无槽位方块会被判为 UNAVAILABLE。</p>
     */
    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder().prefixes("enderio:").access(this).build();
    }

    /**
     * 构造目标槽位目录.
     *
     * <p>优先级：capability 机器取 EnderInventory 的 INPUT/OUTPUT 视图
     * （绕过 IoMode），legacy 机器取 {@code inventory[]} 上的 SlotDefinition 区间；两者都不是时才落到
     * 六面 capability。</p>
     */
    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        if (!AVAILABLE || world == null || pos == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te == null || !ABSTRACT_MACHINE_ENTITY_CLASS.isInstance(te)) {
            return null;
        }
        if (ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            ISlotCatalog catalog = capabilityCatalogOf(te);
            if (catalog != null) {
                return catalog;
            }
        } else if (ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            ISlotCatalog catalog = legacyCatalogOf(te);
            if (catalog != null) {
                return catalog;
            }
        }
        ISlotCatalog faces = CatalogBuilder.facesOf(te);
        return faces != null ? faces : EMPTY_CATALOG;
    }

    /**
     * 机器特有的"处理中"判定.
     *
     * <p>信号是 {@code isActive()}；本 handler 没有专属 grace 常量，用
     * {@link MachineIdle#graceTicks()} 默认值。</p>
     */
    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        return (w, p, te) -> isMachineActive(te);
    }

    /**
     * 收集产物.
     *
     * <p>槽位部分交给框架两阶段收集（目录收窄到产物槽，见 {@link #productCatalog}），
     * 之后追加输出队列——队列内容不在任何 {@link SlotRef} 里，框架看不见。</p>
     */
    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        return collectFrom(catalog, expectedOutputs, inputs, () -> drainOutputQueue(world.getTileEntity(pos)));
    }

    /** 发配前回收输出槽残留：槽位走框架默认，队列追加在后。 */
    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        List<ItemStack> batchInputs = session != null ? session.getInputs() : null;
        IAEItemStack[] expected = session != null ? session.getExpectedOutputs() : null;
        return clearFrom(catalog, batchInputs, expected, () -> drainOutputQueue(world.getTileEntity(pos)));
    }

    /**
     * 回退已推送材料.
     *
     * <p>槽位部分用框架 {@link ItemTransferHelper#revertBatch}（只回退本批快照内的物品）：
     * 原子推料失败时目标里不会残留本批材料，清空整个输入范围会把玩家预置、或机器自行从相邻容器
     * 拉入输入槽的物品收进 ME 网络。队列部分另行处理。</p>
     */
    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        return revertFrom(catalog, session != null ? session.getInputs() : null,
                () -> drainOutputQueue(world.getTileEntity(pos)));
    }

    /**
     * 是否可安全收集.
     *
     * <p>不用框架默认的 {@code hasCollectableProduct}：判据是"输出范围内确实有东西"（队列非空或输出槽
     * 非空），不检查槽内物品是否属于本批材料、也不检查是否匹配预期产物。框架默认的额外过滤会在
     * "输入槽与输出槽重叠"的机器上改变行为（见 {@link #productSlots}），故保持本判据。</p>
     */
    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return true;
        }
        MachineIdle gate = idleGate(world, pos);
        if (session != null && !session.isPushGraceElapsed(world.getTotalWorldTime(), gate.graceTicks())) {
            return false;
        }
        TileEntity te = world.getTileEntity(pos);
        return isIdleState(catalog, gate.isWorking(world, pos, te), hasQueuedOutput(te));
    }

    /**
     * 是否已处理完.
     *
     * <p>覆盖框架默认两处理由：一是先看 {@code isActive()}，机器仍在处理时绝不能判完成，
     * 否则会提前释放目标所有权（框架默认只看"输入耗尽 + 无产物"）；二是产物判据同 {@link #isIdle}
     * （队列 + 输出范围非空），而框架默认的 {@code hasCollectableProduct} 会先排除本批材料。
     * 输入是否耗尽仍复用框架的 {@link ItemTransferHelper#allBatchInputsConsumed}。</p>
     *
     * <p>"是否处理中"必须经 {@link #idleGate} 取，不得直接调 {@link #isMachineActive}：idleGate 是框架
     * 指定的机器状态抽象（{@code MachineIdle}），子类与测试夹具只能通过它提供处理中信号，绕开它会让
     * isIdle 与 hasFinished 对同一台机器给出互相矛盾的回答。实机路径两者等价（gate 内部就是
     * {@code isMachineActive}）。</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        MachineIdle gate = idleGate(world, pos);
        return hasFinishedState(catalog, inputs, gate.isWorking(world, pos, te), hasQueuedOutput(te));
    }

    // ---- 与反射解耦的实现（可离线测试） ----

    /** 收集：框架两阶段收集 + 输出队列。{@code queueDrain} 生产侧为 {@link #drainOutputQueue}。 */
    static List<ItemStack> collectFrom(ISlotCatalog catalog, IAEItemStack[] expectedOutputs,
                                       List<ItemStack> inputs, Supplier<List<ItemStack>> queueDrain) {
        List<ItemStack> collected = ItemTransferHelper.collectProducts(productCatalog(catalog), expectedOutputs, inputs);
        collected.addAll(queueDrain.get());
        return collected;
    }

    /** 清空：框架清空 OUTPUT 槽 + 输出队列。 */
    static List<ItemStack> clearFrom(ISlotCatalog catalog, List<ItemStack> batchInputs,
                                     IAEItemStack[] expectedOutputs, Supplier<List<ItemStack>> queueDrain) {
        List<ItemStack> cleared = ItemTransferHelper.clearOutputs(catalog, batchInputs, expectedOutputs);
        cleared.addAll(queueDrain.get());
        return cleared;
    }

    /** 回退：框架只回退本批材料 + 输出队列。 */
    static List<ItemStack> revertFrom(ISlotCatalog catalog, List<ItemStack> batchInputs,
                                      Supplier<List<ItemStack>> queueDrain) {
        List<ItemStack> reverted = ItemTransferHelper.revertBatch(catalog, batchInputs);
        reverted.addAll(queueDrain.get());
        return reverted;
    }

    /** 空闲判据：处理中不算空闲；否则队列非空或输出范围内有物品即为空闲。 */
    static boolean isIdleState(ISlotCatalog catalog, boolean working, boolean queuedOutput) {
        if (working) {
            return false;
        }
        if (queuedOutput) {
            return true;
        }
        for (SlotRef slot : productSlots(catalog)) {
            if (!slot.peek().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 完成判据：处理中、输入未耗尽、输出范围还有东西，都算未完成。 */
    static boolean hasFinishedState(ISlotCatalog catalog, List<ItemStack> inputs, boolean working, boolean queuedOutput) {
        if (working) {
            return false;
        }
        if (!ItemTransferHelper.allBatchInputsConsumed(catalog, inputs)) {
            return false;
        }
        if (queuedOutput) {
            return false;
        }
        for (SlotRef slot : productSlots(catalog)) {
            if (!slot.peek().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 产物槽位集合.
     *
     * <p>角色已知时只认 OUTPUT 槽；角色未知时（框架降级，如输入/输出同槽的 legacy 缓冲器）整目录
     * 即输出范围，不做角色过滤。</p>
     */
    private static List<SlotRef> productSlots(ISlotCatalog catalog) {
        return catalog.rolesKnown() ? catalog.outputSlots() : catalog.slots();
    }

    /**
     * 交给框架做两阶段收集的目录.
     *
     * <p>框架收集阶段 1（按预期产物匹配）会扫描目录里的全部槽位，这对本 handler 不成立：只有
     * 输出范围里的物品才能当产物，输入槽里的同型物品（玩家预置、上一轮余料）不能。故角色已知时
     * 把目录收窄到 OUTPUT 槽；角色未知时保持原目录，让框架的降级语义覆盖整个输出范围。</p>
     */
    private static ISlotCatalog productCatalog(ISlotCatalog catalog) {
        if (!catalog.rolesKnown()) {
            return catalog;
        }
        CatalogBuilder builder = ISlotCatalog.builder();
        for (SlotRef slot : catalog.outputSlots()) {
            builder.add(slot.handler(), slot.index(), slot.face(), SlotRole.OUTPUT);
        }
        return builder.build();
    }

    /**
     * legacy 槽位角色表（纯函数）.
     *
     * <p>两区间重叠的槽位（{@code SlotDefinition(槽数)}，见物品缓冲器）既会被推料又会落进输出范围，
     * 无法如实归入 INPUT 或 OUTPUT，标 UNKNOWN 交给框架降级，不猜。</p>
     *
     * @param numSlots 库存数组长度
     * @param ranges   {minInput, maxInput, minOutput, maxOutput}
     */
    static SlotRole[] legacyRoles(int numSlots, int[] ranges) {
        SlotRole[] roles = new SlotRole[Math.max(0, numSlots)];
        for (int i = 0; i < roles.length; i++) {
            boolean input = inRange(i, ranges[0], ranges[1]);
            boolean output = inRange(i, ranges[2], ranges[3]);
            if (input && !output) {
                roles[i] = SlotRole.INPUT;
            } else if (output && !input) {
                roles[i] = SlotRole.OUTPUT;
            } else {
                roles[i] = SlotRole.UNKNOWN;
            }
        }
        return roles;
    }

    /**
     * 组装 legacy 机器目录.
     *
     * <p>{@code inventory[]} 是裸数组而非 IItemHandler，故以 {@link LegacyInventoryAccess} 适配；
     * 角色来自 SlotDefinition 区间，"是否允许该物品 / 单槽上限 / 落盘"由注入的机器反射调用承载。</p>
     *
     * @param ranges {minInput, maxInput, minOutput, maxOutput}
     */
    static ISlotCatalog legacyCatalog(int[] ranges, ItemStack[] inventory,
                                      BiPredicate<Integer, ItemStack> machineAccepts,
                                      IntUnaryOperator slotLimit, Runnable markDirty) {
        LegacyInventoryAccess access = new LegacyInventoryAccess(
                inventory, ranges[0], ranges[1], machineAccepts, slotLimit, markDirty);
        SlotRole[] roles = legacyRoles(inventory.length, ranges);
        CatalogBuilder builder = ISlotCatalog.builder();
        for (int i = 0; i < roles.length; i++) {
            builder.add(access, i, null, roles[i]);
        }
        return builder.build();
    }

    private static ISlotCatalog legacyCatalogOf(TileEntity te) {
        try {
            ItemStack[] inventory = (ItemStack[]) FIELD_LEGACY_INVENTORY.get(te);
            if (inventory == null) {
                return null;
            }
            int[] ranges = legacyRanges(te, inventory.length);
            return legacyCatalog(ranges, inventory,
                    (slot, stack) -> isMachineItemValid(te, slot, stack),
                    slot -> getLegacySlotLimit(te, slot),
                    te::markDirty);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to build EIO legacy machine catalog", e);
            return null;
        }
    }

    /**
     * capability 机器目录 = EnderInventory 的 INPUT 视图槽（INPUT）+ OUTPUT 视图槽（OUTPUT）.
     *
     * <p>这两个视图就是本 handler 读写的全部槽位（不触碰升级槽）。</p>
     */
    private static ISlotCatalog capabilityCatalogOf(TileEntity te) {
        IItemHandler inputs = capabilityView(te, ENDER_INVENTORY_TYPE_INPUT);
        IItemHandler outputs = capabilityView(te, ENDER_INVENTORY_TYPE_OUTPUT);
        if (inputs == null && outputs == null) {
            return null;
        }
        CatalogBuilder builder = ISlotCatalog.builder();
        builder.addAll(inputs, null, SlotRole.INPUT);
        builder.addAll(outputs, null, SlotRole.OUTPUT);
        return builder.build();
    }

    private static boolean inRange(int slot, int min, int max) {
        return min >= 0 && slot >= min && slot <= max;
    }

    // ---- 反射访问 ----

    /**
     * 读取 SlotDefinition 的四个区间字段.
     *
     * <p>读取失败时退化为"整段既算输入也算输出"（区间取 (0, Integer.MAX_VALUE)）：
     * 全部槽位落到 UNKNOWN，由框架走降级策略。</p>
     */
    private static int[] legacyRanges(TileEntity te, int numSlots) {
        int[] ranges = {0, numSlots - 1, 0, numSlots - 1};
        try {
            Object slotDef = METHOD_GET_SLOT_DEFINITION.invoke(te);
            if (slotDef == null) {
                return ranges;
            }
            ranges[0] = FIELD_MIN_INPUT_SLOT.getInt(slotDef);
            ranges[1] = FIELD_MAX_INPUT_SLOT.getInt(slotDef);
            ranges[2] = FIELD_MIN_OUTPUT_SLOT.getInt(slotDef);
            ranges[3] = FIELD_MAX_OUTPUT_SLOT.getInt(slotDef);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO legacy slot range", e);
        }
        return ranges;
    }

    private static boolean isMachineItemValid(TileEntity te, int slot, ItemStack stack) {
        if (METHOD_IS_MACHINE_ITEM_VALID == null) {
            return true;
        }
        try {
            return (boolean) METHOD_IS_MACHINE_ITEM_VALID.invoke(te, slot, stack);
        } catch (Exception e) {
            return true;
        }
    }

    private static int getLegacySlotLimit(TileEntity te, int slot) {
        if (METHOD_GET_INVENTORY_STACK_LIMIT == null) {
            return 64;
        }
        try {
            return (int) METHOD_GET_INVENTORY_STACK_LIMIT.invoke(te, slot);
        } catch (Exception e) {
            return 64;
        }
    }

    private static IItemHandler capabilityView(TileEntity te, Object typeConstant) {
        if (METHOD_GET_INVENTORY == null || METHOD_GET_VIEW == null) {
            return null;
        }
        try {
            Object inventory = METHOD_GET_INVENTORY.invoke(te);
            if (inventory == null) {
                return null;
            }
            Object view = METHOD_GET_VIEW.invoke(inventory, typeConstant);
            if (view instanceof IItemHandler) {
                return (IItemHandler) view;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO inventory view", e);
        }
        return null;
    }

    private static boolean isMachineActive(TileEntity te) {
        if (METHOD_IS_ACTIVE == null || te == null) {
            return false;
        }
        try {
            Object result = METHOD_IS_ACTIVE.invoke(te);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to check EIO machine active state", e);
            return false;
        }
    }

    private static Object outputQueueOf(TileEntity te) {
        if (METHOD_GET_OUTPUT_QUEUE == null || te == null) {
            return null;
        }
        try {
            return METHOD_GET_OUTPUT_QUEUE.invoke(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO output queue", e);
            return null;
        }
    }

    /** 输出队列是否非空（队列实现 EnderCore NNList，是 NonNullList 即 List 的子类）。 */
    private static boolean hasQueuedOutput(TileEntity te) {
        Object queue = outputQueueOf(te);
        return queue instanceof List && !((List<?>) queue).isEmpty();
    }

    /** 取走输出队列的全部内容。 */
    private static List<ItemStack> drainOutputQueue(TileEntity te) {
        Object queue = outputQueueOf(te);
        if (!(queue instanceof List)) {
            return Collections.emptyList();
        }
        List<ItemStack> result = new ArrayList<>();
        for (Object o : (List<?>) queue) {
            if (o instanceof ItemStack) {
                result.add(((ItemStack) o).copy());
            }
        }
        ((List<?>) queue).clear();
        return result;
    }

    /**
     * legacy 机器 {@code inventory[]} 裸数组的 IItemHandler 适配器.
     *
     * <p>绕开机器自己的 LegacyMachineWrapper（那个受各面 IoMode 限制），直接读写数组：
     * 插入只允许落在 SlotDefinition 输入区间且必须通过
     * {@code isMachineItemValidForSlot}；单槽上限取 {@code getInventoryStackLimit(slot)}（空槽不额外按
     * 物品堆叠上限收窄，合并进已有堆时取两者较小值）；抽取为裸抽取，
     * 抽空即清槽；simulate 不改数组。</p>
     *
     * <p>机器的"该物品是否允许 / 单槽上限 / 落盘标记"由参数注入，本类不硬引用 Ender IO 类型。</p>
     */
    static final class LegacyInventoryAccess implements IItemHandler {

        private final ItemStack[] inventory;
        private final int minInputSlot;
        private final int maxInputSlot;
        /** {@code isMachineItemValidForSlot}；null = 一律允许。 */
        private final BiPredicate<Integer, ItemStack> machineAccepts;
        /** {@code getInventoryStackLimit(int)}；null = 64。 */
        private final IntUnaryOperator slotLimit;
        private final Runnable markDirty;

        LegacyInventoryAccess(ItemStack[] inventory, int minInputSlot, int maxInputSlot,
                              BiPredicate<Integer, ItemStack> machineAccepts,
                              IntUnaryOperator slotLimit, Runnable markDirty) {
            this.inventory = inventory;
            this.minInputSlot = minInputSlot;
            this.maxInputSlot = maxInputSlot;
            this.machineAccepts = machineAccepts;
            this.slotLimit = slotLimit;
            this.markDirty = markDirty;
        }

        @Override
        public int getSlots() {
            return this.inventory.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= this.inventory.length) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = this.inventory[slot];
            return stack == null ? ItemStack.EMPTY : stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isInputSlot(slot)) {
                return stack;
            }
            if (this.machineAccepts != null && !this.machineAccepts.test(slot, stack)) {
                return stack;
            }
            int limit = getSlotLimit(slot);
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty()) {
                ItemStack placed = stack.copy();
                placed.setCount(Math.min(placed.getCount(), limit));
                if (!simulate) {
                    this.inventory[slot] = placed;
                    dirty();
                }
                return remainder(stack, placed.getCount());
            }
            if (!ItemStack.areItemsEqual(existing, stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                return stack;
            }
            int max = Math.min(existing.getMaxStackSize(), limit);
            int room = max - existing.getCount();
            if (room <= 0) {
                return stack;
            }
            int add = Math.min(room, stack.getCount());
            if (!simulate) {
                existing.grow(add);
                dirty();
            }
            return remainder(stack, add);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int take = Math.min(amount, existing.getCount());
            ItemStack out = existing.copy();
            out.setCount(take);
            if (!simulate) {
                existing.shrink(take);
                if (existing.isEmpty()) {
                    this.inventory[slot] = ItemStack.EMPTY;
                }
                dirty();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.slotLimit != null ? this.slotLimit.applyAsInt(slot) : 64;
        }

        private boolean isInputSlot(int slot) {
            return slot >= 0 && slot < this.inventory.length
                    && slot >= this.minInputSlot && slot <= this.maxInputSlot;
        }

        private static ItemStack remainder(ItemStack stack, int placed) {
            if (placed >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remaining = stack.copy();
            remaining.shrink(placed);
            return remaining;
        }

        private void dirty() {
            if (this.markDirty != null) {
                this.markDirty.run();
            }
        }
    }
}
