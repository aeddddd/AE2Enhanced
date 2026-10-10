package com.github.aeddddd.ae2enhanced.centralinterface.handler.nuclearcraft;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.FluidTransferHelper;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerUtils;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineStorageAccess;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.centralinterface.reflection.ReflectionCache;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * NuclearCraft 机器远程处理器.
 *
 * <p>同时支持 NuclearCraft 2.19a (非重制版) 与 NuclearCraft Overhauled (重制版).
 * 通过反射直接访问 {@code ITileInventory#getInventoryStacks()},绕过 NC 机器的
 * 侧面配置(I/O mode),避免因为配置面导致中枢 ME 接口无法输入输出.</p>
 *
 * <p>所有 NuclearCraft 类均通过 {@link Class#forName(String)} + 反射调用,不直接 import,
 * 保证 NuclearCraft 未安装时本类即使被加载也不会触发 {@link NoClassDefFoundError}.</p>
 *
 * <p>本类继承通用骨架 {@link AbstractMachineHandler}:库存访问抽象为
 * {@link ISlotCatalog}(见 {@link #catalogFor} 与把反射库存包成 {@link IItemHandler} 的
 * {@link ReflectedInventory}),推料(两阶段原子)与输出槽清理由框架提供;本类只回答
 * "槽位目录是什么"、"机器什么时候算在忙"与表项声明(见 {@link #defineSpec}:{@code nuclearcraft:} 前缀),
 * 并把框架默认语义与机器实际语义不一致的地方覆盖回去
 * (见 {@link #isIdle}、{@link #hasFinished}、{@link #collectProducts}、{@link #revertMaterials} 的说明).
 * 其中"产物是流体"的机器额外依赖 {@link #hasProductFluid}:NC 熔融机没有任何物品输出槽,
 * 只看槽位的空闲判据永远不成立.</p>
 */
public class NuclearCraftLegacyMachineHandler extends AbstractMachineHandler {

    private static final boolean AVAILABLE;
    private static final boolean IS_OVERHAULED;

    // ---------- 重制版 (Overhauled) ----------
    private static Class<?> OH_TILE_ENERGY_PROCESSOR_CLASS;
    private static Class<?> OH_PROCESSOR_CONTAINER_INFO_CLASS;
    private static Field OH_INFO_FIELD;
    private static Field OH_ITEM_INPUT_SIZE_FIELD;
    private static Field OH_ITEM_OUTPUT_SIZE_FIELD;
    private static Field OH_ITEM_INPUT_SLOTS_FIELD;
    private static Field OH_ITEM_OUTPUT_SLOTS_FIELD;

    // ---------- 非重制版 (2.19a) ----------
    private static Class<?> LEGACY_TILE_ITEM_PROCESSOR_CLASS;
    private static Class<?> LEGACY_TILE_FLUID_PROCESSOR_CLASS;
    private static Class<?> LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS;

    // ---------- 共享 ----------
    private static Class<?> ITILE_INVENTORY_CLASS;
    private static Method GET_INVENTORY_STACKS_METHOD;
    private static Method IS_ITEM_VALID_FOR_SLOT_METHOD;

    static {
        boolean available = false;
        boolean overhauled = false;
        try {
            if (Loader.isModLoaded("nuclearcraft")) {
                // 先检测是否为重制版：重制版有 TileEnergyProcessor
                try {
                    OH_TILE_ENERGY_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileEnergyProcessor");
                    overhauled = true;
                } catch (ClassNotFoundException e) {
                    overhauled = false;
                }

                if (overhauled) {
                    available = initOverhauled();
                } else {
                    available = initLegacy();
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NuclearCraft handler", e);
        }
        AVAILABLE = available;
        IS_OVERHAULED = overhauled;
    }

    private static boolean initOverhauled() throws Exception {
        OH_PROCESSOR_CONTAINER_INFO_CLASS = Class.forName("nc.tile.processor.info.ProcessorContainerInfo");
        OH_INFO_FIELD = OH_TILE_ENERGY_PROCESSOR_CLASS.getDeclaredField("info");
        OH_INFO_FIELD.setAccessible(true);
        OH_ITEM_INPUT_SIZE_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemInputSize");
        OH_ITEM_OUTPUT_SIZE_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemOutputSize");
        OH_ITEM_INPUT_SLOTS_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemInputSlots");
        OH_ITEM_OUTPUT_SLOTS_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemOutputSlots");

        ITILE_INVENTORY_CLASS = Class.forName("nc.tile.inventory.ITileInventory");
        GET_INVENTORY_STACKS_METHOD = ITILE_INVENTORY_CLASS.getMethod("getInventoryStacks");
        IS_ITEM_VALID_FOR_SLOT_METHOD = findIsItemValidForSlot(ITILE_INVENTORY_CLASS);
        return true;
    }

    private static boolean initLegacy() throws Exception {
        LEGACY_TILE_ITEM_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemProcessor");
        LEGACY_TILE_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileFluidProcessor");
        LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemFluidProcessor");

        ITILE_INVENTORY_CLASS = Class.forName("nc.tile.inventory.ITileInventory");
        GET_INVENTORY_STACKS_METHOD = ITILE_INVENTORY_CLASS.getMethod("getInventoryStacks");
        IS_ITEM_VALID_FOR_SLOT_METHOD = findIsItemValidForSlot(ITILE_INVENTORY_CLASS);
        return true;
    }

    /**
     * 查找 {@code isItemValidForSlot(int, ItemStack)}。
     * 该方法是 vanilla {@code IInventory} 方法：开发环境为 MCP 名 {@code isItemValidForSlot}，
     * 生产环境 reobf 后为 SRG 名 {@code func_94041_b}，需双名回退。
     */
    private static Method findIsItemValidForSlot(Class<?> clazz) throws NoSuchMethodException {
        try {
            return clazz.getMethod("isItemValidForSlot", int.class, ItemStack.class);
        } catch (NoSuchMethodException e) {
            return clazz.getMethod("func_94041_b", int.class, ItemStack.class);
        }
    }

    /** 表项：{@code nuclearcraft:} 前缀匹配整个 mod，行为描述即本类. */
    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder().prefixes("nuclearcraft:").access(this).build();
    }

    // ---- 目录与空闲判定(框架要求的两个问题) ----

    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) {
            return null;
        }
        NonNullList<ItemStack> inventory = inventoryOf(te);
        if (inventory == null) {
            // 反射读不到库存 = 没有任何可访问槽位。目录为 null 后框架的降级语义
            // (推料 false / 收集为空 / isIdle 与 hasFinished 为 true)与反射失败时的兜底一致。
            return null;
        }
        IItemHandler view = new ReflectedInventory(te, inventory, IS_ITEM_VALID_FOR_SLOT_METHOD);
        return buildCatalog(view, inventory.size(), getItemInputSlots(te), getItemOutputSlots(te));
    }

    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        // "正在处理"的反射读取集中在 isProcessingNC 内(lambda 只做转发),te 由框架传入且可能为 null
        return (w, p, te) -> isProcessingNC(te);
    }

    /**
     * 按 NC 的输入/输出槽下标表构造目录.
     *
     * <p>纯逻辑、不碰反射,便于单测直接喂槽位定义.</p>
     */
    static ISlotCatalog buildCatalog(IItemHandler handler, int slotCount, int[] inputSlots, int[] outputSlots) {
        return ISlotCatalog.builder().addAll(handler, null, slotRoles(slotCount, inputSlots, outputSlots)).build();
    }

    /**
     * 槽位下标 → 角色映射.
     *
     * <p>NC 的库存布局是 {@code [输入…, 输出…, (速度升级, 能量升级)]}
     * (2.19a 与 Overhauled 都只在可升级时多出末尾两个槽),因此不属于输入/输出的槽位标
     * {@link SlotRole#UPGRADE}:它们是升级槽,本类从不读写,标出来是把"不得触碰"写进目录.</p>
     *
     * <p>同一下标同时出现在两表中时按 {@link SlotRole#INPUT} 处理:推料目标比"能否从该槽收款"
     * 更关键,而落到输入槽的非本批物品仍会被收集的第 3 阶段带走.</p>
     */
    static SlotRole[] slotRoles(int slotCount, int[] inputSlots, int[] outputSlots) {
        SlotRole[] roles = new SlotRole[Math.max(0, slotCount)];
        Arrays.fill(roles, SlotRole.UPGRADE);
        if (outputSlots != null) {
            for (int index : outputSlots) {
                if (inRange(index, roles)) {
                    roles[index] = SlotRole.OUTPUT;
                }
            }
        }
        if (inputSlots != null) {
            for (int index : inputSlots) {
                if (inRange(index, roles)) {
                    roles[index] = SlotRole.INPUT;
                }
            }
        }
        return roles;
    }

    private static boolean inRange(int index, SlotRole[] roles) {
        return index >= 0 && index < roles.length;
    }

    // ---- 与框架默认语义不一致、必须覆盖回去的部分 ----

    /**
     * 覆盖框架默认:判据是"未在处理 + 输出槽里有东西",再补上产物为流体时的判据。
     *
     * <p>框架默认用 {@code hasCollectableProduct},它会把与本批材料同型的物品排除在产物之外;
     * 而 NC 的输出槽是机器的私有产物位,产物与本批输入同型(自定义配方)时框架永远判不出产物,
     * 会话只能空等到超时,产物要等下一批发配的 {@code clearOutputs} 才被带走。</p>
     *
     * <p>另一方面,产物是流体的机器（熔融机 Melter 等）<b>没有任何物品输出槽</b>,
     * 只认输出槽会让 {@code isIdle} 永远为假 → 永不进入 COLLECTING → 会话锁到
     * {@code processingTimeoutTicks} 超时,而 {@code revertSession} 对流体的处理是
     * "只记录不抽取" → 产物流体永远留在机器罐里。因此这里把
     * {@link #hasProductFluid(TileEntity)}（输出罐有流体）与输出槽判据并列。</p>
     *
     * <p>推料保护窗口沿用框架默认(2 tick):NC 的 {@code isProcessing} 由机器自己的 tick 更新,
     * 推料当 tick 读到的可能仍是 {@code false};代价只是"极快出产物"的场景推迟两 tick 收集。</p>
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
        if (gate.isWorking(world, pos, te)) {
            return false;
        }
        return hasFilledOutputSlot(catalog) || hasProductFluid(te);
    }

    /**
     * 覆盖框架默认:框架默认不看机器的处理状态,只要"本批输入已不在槽里且没有可收产物"就报完成.
     *
     * <p>NC 会把输入从槽位搬进内部进度缓冲,此刻两个条件同时成立,框架会当场结束会话,
     * 产物再无人接管;判据先用 {@code isProcessing} 挡住了这段窗口.</p>
     *
     * <p>输出槽判据同样是"非空即未完成",而不是框架的 {@code hasCollectableProduct}
     * ——后者会忽略落在输出槽里、与本批输入同型的产物(同 {@link #isIdle} 的理由).
     * 输出罐判据见 {@link #hasProductFluid}:罐里还有流体就不算完成,否则会话会在产物
     * 尚未抽走时关闭,让流体卡在机器里(与 TE handler 的 {@code hasTankFluid} 闸门对齐).</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (isProcessingNC(te)) {
            return false;
        }
        return !hasBatchMaterialInInput(catalog, inputs) && !hasFilledOutputSlot(catalog) && !hasProductFluid(te);
    }

    /**
     * 覆盖框架默认:收集是三阶段,框架默认只做其中的前两个对应阶段.
     *
     * <p>两点差异:</p>
     * <ul>
     *     <li>第三阶段(输入槽里不属于本批输入的物品)框架默认不做——角色已知时它只收
     *     {@link SlotRole#OUTPUT} 槽;而 NC 确实会把产物吐回输入槽,框架默认会让它永远留在机器里;</li>
     *     <li>阶段一按"整槽取走"处理,框架默认会按样板声明的数量截断,多出的部分被拆成
     *     第二条返回项——本类保持"整槽一条"口径.</li>
     * </ul>
     */
    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        return collect(catalog, expectedOutputs, inputs);
    }

    /**
     * 回退输入槽.
     *
     * <p><b>不能用框架默认</b>:框架的 {@code ItemTransferHelper.revertBatch} 只回退"本批材料快照"
     * 内的物品,本类无条件清空<b>全部</b>输入槽(不做快照过滤),其余槽位(输出槽、升级槽)一律不动.</p>
     *
     * <p><b>风险</b>:机器自己从相邻容器拉来的料、玩家预置在输入槽里的物品,都会在发配失败/
     * 超时回退时被一并送进 AE 网络。避免它需要先定义"本批材料"的判定(数量/时间窗).</p>
     */
    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return new ArrayList<>();
        }
        return revertAllInputs(catalog);
    }

    @Override
    public List<FluidStack> clearOutputFluids(World world, BlockPos pos, IActionSource source, TargetSession session, List<FluidStack> batchFluids) {
        // NC 的输出罐被 TankSorption 标为 OUT、输入罐标为 IN；
        // 通过真实面并按机器自身罐模式抽取，因此不会动输入罐。
        return FluidTransferHelper.drainExtractableFluids(world, pos, batchFluids);
    }

    // ---- 收集/判定的纯逻辑(与反射解耦,便于单测) ----

    /** 三阶段收集:预期产物 → 输出槽剩余 → 输入槽里不属于本批输入的物品. */
    static List<ItemStack> collect(ISlotCatalog catalog, IAEItemStack[] expectedOutputs, List<ItemStack> inputs) {
        List<ItemStack> collected = new ArrayList<>();
        List<SlotRef> outputSlots = catalog.outputSlots();
        List<ItemStack> inputsSafe = inputs != null ? inputs : new ArrayList<>();

        // 阶段 1：优先收集匹配预期产物的物品(整槽取走,不按预期数量截断)
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) {
                    continue;
                }
                ItemStack expectedStack = expected.createItemStack();
                for (SlotRef slot : outputSlots) {
                    ItemStack inSlot = slot.peek();
                    if (inSlot.isEmpty() || !HandlerUtils.matchesLoosely(inSlot, expectedStack)) {
                        continue;
                    }
                    drain(slot, collected);
                }
            }
        }

        // 阶段 2：输出槽中所有剩余物品(副产物/容器等)一律收走
        for (SlotRef slot : outputSlots) {
            drain(slot, collected);
        }

        // 阶段 3：输入槽中不属于本批输入的物品(机器可能把产物吐回输入槽)
        for (SlotRef slot : catalog.inputSlots()) {
            ItemStack inSlot = slot.peek();
            if (inSlot.isEmpty() || HandlerUtils.isInputMaterial(inSlot, inputsSafe)) {
                continue;
            }
            drain(slot, collected);
        }
        return collected;
    }

    /** 取走全部输入槽内容,不按本批材料快照过滤(原因与风险见 {@link #revertMaterials}). */
    static List<ItemStack> revertAllInputs(ISlotCatalog catalog) {
        List<ItemStack> reverted = new ArrayList<>();
        for (SlotRef slot : catalog.inputSlots()) {
            drain(slot, reverted);
        }
        return reverted;
    }

    /** 取走槽位当前全部内容并计入结果(空槽无动作). */
    private static void drain(SlotRef slot, List<ItemStack> sink) {
        ItemStack inSlot = slot.peek();
        if (inSlot.isEmpty()) {
            return;
        }
        ItemStack got = slot.extract(inSlot.getCount(), false);
        if (!got.isEmpty()) {
            sink.add(got);
        }
    }

    /** 输出槽里是否有任何内容(NC 不区分"是否属于本批材料"). */
    static boolean hasFilledOutputSlot(ISlotCatalog catalog) {
        for (SlotRef slot : catalog.outputSlots()) {
            if (!slot.peek().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 输入槽里是否还留着本批材料. */
    static boolean hasBatchMaterialInInput(ISlotCatalog catalog, List<ItemStack> batchInputs) {
        for (SlotRef slot : catalog.inputSlots()) {
            ItemStack stack = slot.peek();
            if (!stack.isEmpty() && HandlerUtils.isInputMaterial(stack, batchInputs)) {
                return true;
            }
        }
        return false;
    }

    // ---- 输出罐判据(与反射解耦,便于单测) ----

    /**
     * 机器的<b>输出罐</b>里是否有流体产物.
     *
     * <p>为什么要这条判据:NC 的"产物是流体"机器(熔融机 Melter、离心机 Centrifuge 等)可能
     * 一个物品输出槽都没有,只认输出槽的 {@code isIdle} 永远不成立,会话只能空等到超时,
     * 产物流体永远留在罐里(见 {@link #isIdle}).</p>
     *
     * <p>判定来源按版本分两条,先命中者为准:</p>
     * <ol>
     *   <li>重制版(Overhauled):{@code IProcessor#getFluidOutputs()},该方法按
     *       {@code ProcessorContainerInfo.fluidOutputTanks} 返回输出罐子列表;</li>
     *   <li>2.19a:{@code ITileFluid#getTanks()} 拿到全部罐,再用 {@code fluidInputSize}
     *       (字段声明在 {@code TileFluidProcessor} 上,重制版则在 {@code info} 上)切掉输入罐
     *       ——输入罐里可能正装着本批推入的流体,绝不能当成产物。</li>
     * </ol>
     *
     * <p>罐对象只按 Forge 的 {@link FluidTank} 读取(NC 的 {@code Tank} 继承它),
     * 本类不硬引用任何 NC 类型。方法/字段缺失、反射抛异常时返回 {@code false},
     * 即退化为"看不到流体"。</p>
     */
    static boolean hasProductFluid(TileEntity te) {
        if (te == null) {
            return false;
        }
        for (Object tank : outputTanksOf(te)) {
            if (fluidAmountOf(tank) > 0) {
                return true;
            }
        }
        return false;
    }

    /** 输出罐列表;两条来源都拿不到时返回空列表(不是 null,调用方无需判空). */
    private static List<?> outputTanksOf(TileEntity te) {
        Method getFluidOutputs = ReflectionCache.method(te.getClass(), "getFluidOutputs");
        if (getFluidOutputs != null) {
            try {
                Object result = getFluidOutputs.invoke(te);
                if (result instanceof List) {
                    return (List<?>) result;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC fluid outputs", e);
                return Collections.emptyList();
            }
        }
        return inputSuffixTanksOf(te);
    }

    /** 2.19a 口径:全部罐里下标 ≥ {@code fluidInputSize} 的部分即输出罐. */
    private static List<?> inputSuffixTanksOf(TileEntity te) {
        Method getTanks = ReflectionCache.method(te.getClass(), "getTanks");
        if (getTanks == null) {
            return Collections.emptyList();
        }
        List<?> tanks;
        try {
            Object result = getTanks.invoke(te);
            if (!(result instanceof List)) {
                return Collections.emptyList();
            }
            tanks = (List<?>) result;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC tanks", e);
            return Collections.emptyList();
        }
        int inputSize = fluidInputSizeOf(te);
        if (inputSize < 0 || inputSize >= tanks.size()) {
            // 读不到输入罐数量时宁可什么都不认:把输入罐当输出罐会抽走本批推入的流体
            return Collections.emptyList();
        }
        return tanks.subList(inputSize, tanks.size());
    }

    /**
     * 输入罐数量({@code fluidInputSize}).
     *
     * @return 读不到时返回 {@code -1}
     */
    private static int fluidInputSizeOf(TileEntity te) {
        // 重制版:字段在 ProcessorContainerInfo(info)上,而 info 本身是 protected 字段,只能用既有句柄读
        if (IS_OVERHAULED && OH_INFO_FIELD != null && OH_TILE_ENERGY_PROCESSOR_CLASS != null
                && OH_TILE_ENERGY_PROCESSOR_CLASS.isInstance(te)) {
            try {
                Object info = OH_INFO_FIELD.get(te);
                if (info != null) {
                    Field sizeField = ReflectionCache.field(info.getClass(), "fluidInputSize");
                    if (sizeField != null) {
                        return sizeField.getInt(info);
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC fluid input size from info", e);
            }
        }
        // 2.19a:public final int fluidInputSize 直接声明在 TileFluidProcessor 上
        try {
            Field sizeField = ReflectionCache.field(te.getClass(), "fluidInputSize");
            return sizeField != null ? sizeField.getInt(te) : -1;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC fluid input size", e);
            return -1;
        }
    }

    /** 读罐内流体量;非 Forge {@link FluidTank}(= NC 的 Tank)一律按 0 处理. */
    private static int fluidAmountOf(Object tank) {
        if (!(tank instanceof FluidTank)) {
            return 0;
        }
        return ((FluidTank) tank).getFluidAmount();
    }

    // ---- 反射访问 ----

    private boolean isProcessorTile(TileEntity te) {
        if (te == null || !AVAILABLE) return false;
        if (IS_OVERHAULED) {
            return OH_TILE_ENERGY_PROCESSOR_CLASS != null && OH_TILE_ENERGY_PROCESSOR_CLASS.isInstance(te);
        }
        return (LEGACY_TILE_ITEM_PROCESSOR_CLASS != null && LEGACY_TILE_ITEM_PROCESSOR_CLASS.isInstance(te))
                || (LEGACY_TILE_FLUID_PROCESSOR_CLASS != null && LEGACY_TILE_FLUID_PROCESSOR_CLASS.isInstance(te))
                || (LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS != null && LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS.isInstance(te));
    }

    @SuppressWarnings("unchecked")
    private static NonNullList<ItemStack> inventoryOf(TileEntity te) {
        try {
            return (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft getInventoryStacks failed", e);
            return null;
        }
    }

    private int[] getItemInputSlots(TileEntity te) {
        if (IS_OVERHAULED) {
            int[] slots = getOverhauledIntArray(te, OH_ITEM_INPUT_SLOTS_FIELD);
            if (slots != null) return slots;
            int size = getOverhauledInt(te, OH_ITEM_INPUT_SIZE_FIELD, 0);
            return buildRange(0, size);
        }
        int size = getTileIntField(te, "itemInputSize", 0);
        return buildRange(0, size);
    }

    private int[] getItemOutputSlots(TileEntity te) {
        if (IS_OVERHAULED) {
            int[] slots = getOverhauledIntArray(te, OH_ITEM_OUTPUT_SLOTS_FIELD);
            if (slots != null) return slots;
            int inputSize = getOverhauledInt(te, OH_ITEM_INPUT_SIZE_FIELD, 0);
            int outputSize = getOverhauledInt(te, OH_ITEM_OUTPUT_SIZE_FIELD, 0);
            return buildRange(inputSize, outputSize);
        }
        int inputSize = getTileIntField(te, "itemInputSize", 0);
        int outputSize = getTileIntField(te, "itemOutputSize", 0);
        return buildRange(inputSize, outputSize);
    }

    private int[] buildRange(int start, int length) {
        if (length <= 0) return new int[0];
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = start + i;
        }
        return result;
    }

    private int getOverhauledInt(TileEntity te, Field field, int fallback) {
        try {
            Object info = OH_INFO_FIELD.get(te);
            if (info == null || field == null) return fallback;
            return field.getInt(info);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int[] getOverhauledIntArray(TileEntity te, Field field) {
        try {
            Object info = OH_INFO_FIELD.get(te);
            if (info == null || field == null) return null;
            Object value = field.get(info);
            if (value instanceof int[]) {
                return (int[]) value;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC Overhauled int array", e);
        }
        return null;
    }

    private int getTileIntField(TileEntity te, String name, int fallback) {
        try {
            return te.getClass().getField(name).getInt(te);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 机器是否正在处理.
     *
     * <p>两次方法名回退({@code isProcessing} → {@code getIsProcessing})对应 2.19a 与 Overhauled
     * 的命名差异;方法不存在时返回 {@code false}(等价于"看不到处理信号").</p>
     *
     * <p>这是每 tick 调用的路径,方法查找经 {@link ReflectionCache}:失败结果也被缓存,因此
     * "该名字不存在"这条正常回退路径不再每次执行一遍 {@code getMethod} 并构造
     * {@code NoSuchMethodException}。调用失败(方法存在但 invoke 抛异常)与查找失败仍是两回事:
     * 前者按原语义直接返回 {@code false},<b>不会</b>再退回另一个方法名。</p>
     *
     * <p>包可见是为了让测试能在没有 NuclearCraft 的环境下单独验证这条回退链.</p>
     */
    static boolean isProcessingNC(TileEntity te) {
        if (te == null) {
            return false;
        }
        Method isProcessing = ReflectionCache.method(te.getClass(), "isProcessing");
        if (isProcessing != null) {
            try {
                return (Boolean) isProcessing.invoke(te);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC processing state", e);
                return false;
            }
        }
        // Overhauled 使用 getIsProcessing
        Method getIsProcessing = ReflectionCache.method(te.getClass(), "getIsProcessing");
        if (getIsProcessing == null) {
            return false;
        }
        try {
            return (Boolean) getIsProcessing.invoke(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC Overhauled processing state", e);
            return false;
        }
    }

    private static void markDirty(TileEntity te) {
        try {
            te.markDirty();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] markDirty failed", e);
        }
    }

    /**
     * 把反射拿到的 {@code ITileInventory#getInventoryStacks()} 包装成 {@link IItemHandler},
     * 供框架的 {@link SlotRef} 读写.
     *
     * <p>目录只能通过 {@link IItemHandler} 访问槽位,而 NC 的库存是一个 {@code NonNullList},
     * 且插入合法性由机器自身的 {@code isItemValidForSlot} 决定(Overhauled 还会校验配方是否接受该物品).
     * 本视图把这两点包在一起,使框架的原子推料/收集逻辑能直接作用于反射库存,
     * 同时保住"绕过侧面配置"这一权衡.</p>
     *
     * <p>插入语义:<b>不拆分</b>——空槽经校验后整堆放入;
     * 同型槽要求"现有数量 + 入料数量 ≤ 该物品的堆叠上限"且校验通过,否则整个槽位拒绝
     * (剩余部分由框架去试下一个输入槽,因此框架仍能保证整批推料的原子性).</p>
     */
    static final class ReflectedInventory implements IItemHandler {

        private final TileEntity tile;
        private final NonNullList<ItemStack> stacks;
        private final Method isItemValidForSlot;

        ReflectedInventory(TileEntity tile, NonNullList<ItemStack> stacks, Method isItemValidForSlot) {
            this.tile = tile;
            this.stacks = stacks;
            this.isItemValidForSlot = isItemValidForSlot;
        }

        @Override
        public int getSlots() {
            return this.stacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (!inRange(slot)) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = this.stacks.get(slot);
            return stack != null ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!inRange(slot)) {
                return stack;
            }
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty()) {
                if (!isSlotAcceptable(slot, stack)) {
                    return stack;
                }
                if (!simulate) {
                    // 整堆写入,不按堆叠上限裁剪
                    this.stacks.set(slot, stack.copy());
                    markDirty(this.tile);
                }
                return ItemStack.EMPTY;
            }
            // 异类物品不得混入已有堆:此处直接跳到下一个槽,连合法性校验都不问
            if (!ItemStack.areItemsEqual(existing, stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                return stack;
            }
            // 放不下就整个槽位拒绝:既不填充到上限,也不拆分到下一个槽
            if (existing.getCount() + stack.getCount() > existing.getMaxStackSize()) {
                return stack;
            }
            if (!isSlotAcceptable(slot, stack)) {
                return stack;
            }
            if (!simulate) {
                existing.grow(stack.getCount());
                markDirty(this.tile);
            }
            return ItemStack.EMPTY;
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
            int taken = Math.min(amount, existing.getCount());
            ItemStack out = existing.copy();
            out.setCount(taken);
            if (!simulate) {
                ItemStack left = existing.copy();
                left.setCount(existing.getCount() - taken);
                this.stacks.set(slot, left.getCount() <= 0 ? ItemStack.EMPTY : left);
                markDirty(this.tile);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        /**
         * 问机器自己该槽能否接受该物品.
         *
         * <p>校验方法缺失或抛异常时视为不可接受:跳过该槽(继续试下一个),整体效果等同"该槽拒绝此物品".</p>
         */
        private boolean isSlotAcceptable(int slot, ItemStack stack) {
            if (this.isItemValidForSlot == null) {
                return false;
            }
            try {
                return (Boolean) this.isItemValidForSlot.invoke(this.tile, slot, stack);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] isItemValidForSlot failed for slot {}", slot, e);
                return false;
            }
        }

        private boolean inRange(int slot) {
            return slot >= 0 && slot < this.stacks.size();
        }
    }
}
