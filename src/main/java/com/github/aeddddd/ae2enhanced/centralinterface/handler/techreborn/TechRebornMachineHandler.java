package com.github.aeddddd.ae2enhanced.centralinterface.handler.techreborn;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.*;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Tech Reborn 机器远程处理器.
 *
 * <p>Tech Reborn 的 {@code Inventory} 虽然实现了 {@link net.minecraft.inventory.ISidedInventory},
 * 但对所有面都返回空槽位并拒绝插入/提取({@code getSlotsForFace} 返回空数组、
 * {@code canInsertItem}/{@code canExtractItem} 恒为 false),因此外部 {@code IItemHandler}
 * 无法作为中枢 ME 接口收发通道.本处理器通过反射直接读写机器内部库存数组与机器自身的
 * 输入/输出槽定义,绕过侧面配置限制.</p>
 *
 * <p>TR 的两类机器基类是兄弟关系(都继承 {@code TilePowerAcceptor}),字段位置完全不同,
 * 必须按实例分别解析:</p>
 * <ul>
 *     <li>{@code techreborn.tiles.processing.TileMachine} 子类:输入/输出槽在自身的
 *     {@code inputSlots}/{@code outputSlots},处理状态看 {@code progress}/{@code operationLength}
 *     (该体系在开始工作时就消耗输入,产物先落在 {@code itemOutputsBuffer},完成后才写入输出槽);</li>
 *     <li>{@code techreborn.tiles.TileGenericMachine} 子类:输入/输出槽在
 *     {@code RecipeCrafter#inputSlots}/{@code outputSlots},处理状态看
 *     {@code currentRecipe}/{@code currentTickTime}.</li>
 * </ul>
 *
 * <p>所有 Tech Reborn / RebornCore 类均通过 {@link Class#forName(String)} + 反射访问,不直接 import,
 * 保证 Tech Reborn 未安装时本类即使被加载也不会触发 {@link NoClassDefFoundError}.</p>
 *
 * <p>本类继承 {@link AbstractMachineHandler}:库存访问抽象为
 * {@link ISlotCatalog}(见 {@link #catalogFor}),推料(两阶段原子)、输出槽清理、材料回退
 * 直接复用框架实现;本类只回答"槽位目录是什么"与"机器什么时候算在忙",并把框架默认
 * 语义与机器实际语义不一致的地方覆盖回去(见 {@link #canStart}、{@link #isIdle}、
 * {@link #hasFinished}、{@link #collectProducts} 的说明).</p>
 */
public class TechRebornMachineHandler extends AbstractMachineHandler {

    private static final boolean AVAILABLE;

    /**
     * Tech Reborn 机器方块注册 ID.
     *
     * <p>显式枚举而非 {@code techreborn:} 前缀通配:TR 的线缆/储电单元/量子箱等方块
     * 同样以 {@code techreborn:} 开头,但既不是收料机器也没有输入槽,通配会让它们
     * 抢走 {@link com.github.aeddddd.ae2enhanced.centralinterface.DefaultSingleBatchHandler} 的匹配.</p>
     */
    private static final Set<String> MACHINE_BLOCK_IDS;

    // ---------- 机器基类 ----------
    private static Class<?> TILE_MACHINE_CLASS;
    private static Class<?> TILE_GENERIC_MACHINE_CLASS;

    // ---------- TileMachine 体系(输入/输出槽在机器自身) ----------
    private static Field FIELD_INVENTORY_MACHINE;
    private static Field FIELD_INPUT_SLOTS_MACHINE;
    private static Field FIELD_OUTPUT_SLOTS_MACHINE;
    private static Field FIELD_PROGRESS_MACHINE;
    private static Field FIELD_OPERATION_LENGTH_MACHINE;
    private static Field FIELD_ENERGY_SLOT_MACHINE;

    // ---------- TileGenericMachine 体系(输入/输出槽在 RecipeCrafter) ----------
    private static Field FIELD_INVENTORY_GENERIC;
    private static Field FIELD_CRAFTER;
    private static Field FIELD_INPUT_SLOTS_CRAFTER;
    private static Field FIELD_OUTPUT_SLOTS_CRAFTER;
    private static Field FIELD_CURRENT_RECIPE_CRAFTER;
    private static Field FIELD_CURRENT_TICK_TIME_CRAFTER;
    private static Field FIELD_ENERGY_SLOT_GENERIC;

    // ---------- RebornCore 共享 ----------
    private static Field FIELD_CONTENTS;
    private static Field FIELD_STACK_LIMIT;
    private static Field FIELD_IS_DIRTY;
    private static Field FIELD_HAS_CHANGED;
    private static Field FIELD_SLOT_CONFIGURATION;
    private static Method IS_ITEM_VALID_FOR_SLOT_METHOD;

    static {
        Set<String> ids = new HashSet<>();
        // TileMachine 体系
        ids.add("techreborn:alloy_smelter");
        ids.add("techreborn:assembling_machine");
        ids.add("techreborn:chemical_reactor");
        ids.add("techreborn:compressor");
        ids.add("techreborn:extractor");
        ids.add("techreborn:grinder");
        ids.add("techreborn:industrial_centrifuge");
        ids.add("techreborn:plate_bending_machine");
        ids.add("techreborn:solid_canning_machine");
        ids.add("techreborn:wire_mill");
        // TileGenericMachine 体系
        ids.add("techreborn:distillation_tower");
        ids.add("techreborn:fluid_replicator");
        ids.add("techreborn:implosion_compressor");
        ids.add("techreborn:industrial_blast_furnace");
        ids.add("techreborn:industrial_grinder");
        ids.add("techreborn:industrial_sawmill");
        ids.add("techreborn:vacuum_freezer");
        ids.add("techreborn:industrial_electrolyzer");
        ids.add("techreborn:scrapboxinator");
        MACHINE_BLOCK_IDS = Collections.unmodifiableSet(ids);

        boolean available = false;
        try {
            if (Loader.isModLoaded("techreborn")) {
                available = initReflection();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Tech Reborn handler", e);
        }
        AVAILABLE = available;
    }

    private static boolean initReflection() throws Exception {
        TILE_MACHINE_CLASS = Class.forName("techreborn.tiles.processing.TileMachine");
        TILE_GENERIC_MACHINE_CLASS = Class.forName("techreborn.tiles.TileGenericMachine");

        // 两个基类各自声明自己的 inventory 字段,必须分别解析:
        // 拿一个类的 Field 去读另一个类的实例会抛 IllegalArgumentException.
        FIELD_INVENTORY_MACHINE = findField(TILE_MACHINE_CLASS, "inventory");
        FIELD_INPUT_SLOTS_MACHINE = findField(TILE_MACHINE_CLASS, "inputSlots");
        FIELD_OUTPUT_SLOTS_MACHINE = findField(TILE_MACHINE_CLASS, "outputSlots");
        FIELD_PROGRESS_MACHINE = findField(TILE_MACHINE_CLASS, "progress");
        FIELD_OPERATION_LENGTH_MACHINE = findField(TILE_MACHINE_CLASS, "operationLength");
        // 电池槽:该槽位仅供机器给玩家放入的工具/电池充电,本类从不读写,
        // 标成 UPGRADE 只是把"不得触碰"写进目录,便于框架侧统一排除.
        FIELD_ENERGY_SLOT_MACHINE = findField(TILE_MACHINE_CLASS, "energySlot");

        FIELD_INVENTORY_GENERIC = findField(TILE_GENERIC_MACHINE_CLASS, "inventory");
        FIELD_CRAFTER = findField(TILE_GENERIC_MACHINE_CLASS, "crafter");
        FIELD_ENERGY_SLOT_GENERIC = findField(TILE_GENERIC_MACHINE_CLASS, "energySlot");
        if (FIELD_CRAFTER != null) {
            Class<?> crafterClass = Class.forName("reborncore.common.recipes.RecipeCrafter");
            FIELD_INPUT_SLOTS_CRAFTER = findField(crafterClass, "inputSlots");
            FIELD_OUTPUT_SLOTS_CRAFTER = findField(crafterClass, "outputSlots");
            FIELD_CURRENT_RECIPE_CRAFTER = findField(crafterClass, "currentRecipe");
            FIELD_CURRENT_TICK_TIME_CRAFTER = findField(crafterClass, "currentTickTime");
        }

        Class<?> inventoryClass = Class.forName("reborncore.common.util.Inventory");
        FIELD_CONTENTS = inventoryClass.getDeclaredField("contents");
        FIELD_CONTENTS.setAccessible(true);
        FIELD_STACK_LIMIT = findField(inventoryClass, "stackLimit");
        FIELD_IS_DIRTY = findField(inventoryClass, "isDirty");
        FIELD_HAS_CHANGED = findField(inventoryClass, "hasChanged");

        Class<?> machineTileClass = Class.forName("reborncore.common.tile.RebornMachineTile");
        FIELD_SLOT_CONFIGURATION = findField(machineTileClass, "slotConfiguration");
        IS_ITEM_VALID_FOR_SLOT_METHOD = findIsItemValidForSlot(machineTileClass);

        boolean machineHierarchyOk = FIELD_INVENTORY_MACHINE != null && FIELD_INPUT_SLOTS_MACHINE != null
                && FIELD_OUTPUT_SLOTS_MACHINE != null && FIELD_PROGRESS_MACHINE != null
                && FIELD_OPERATION_LENGTH_MACHINE != null;
        boolean genericHierarchyOk = FIELD_INVENTORY_GENERIC != null && FIELD_CRAFTER != null
                && FIELD_INPUT_SLOTS_CRAFTER != null && FIELD_OUTPUT_SLOTS_CRAFTER != null
                && FIELD_CURRENT_RECIPE_CRAFTER != null && FIELD_CURRENT_TICK_TIME_CRAFTER != null;
        return FIELD_CONTENTS != null && (machineHierarchyOk || genericHierarchyOk);
    }

    /**
     * 沿继承链查找字段并开放访问权限(TR 的槽位数组是 protected,库存内容是 public).
     */
    private static Field findField(Class<?> clazz, String name) {
        if (clazz == null) return null;
        while (clazz != null && !Object.class.getName().equals(clazz.getName())) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 查找 {@code isItemValidForSlot(int, ItemStack)}。
     * 该方法是 vanilla {@code IInventory} 方法：开发环境为 MCP 名 {@code isItemValidForSlot}，
     * 生产环境 reobf 后为 SRG 名 {@code func_94041_b}，需双名回退。
     */
    private static Method findIsItemValidForSlot(Class<?> clazz) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod("isItemValidForSlot", int.class, ItemStack.class);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getMethod("func_94041_b", int.class, ItemStack.class);
            } catch (NoSuchMethodException e2) {
                return null;
            }
        }
    }

    /**
     * 表项：显式枚举的 19 个机器方块 id（{@link #MACHINE_BLOCK_IDS}），行为描述即本类.
     *
     * <p>匹配规则只有这一处声明：{@code canHandle} 与 {@code HandlerRegistry} 的两级索引
     * 都从本表项取值.</p>
     */
    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder()
                .exactIds(MACHINE_BLOCK_IDS.toArray(new String[0]))
                .access(this)
                .build();
    }

    // ---- 目录与空闲判定(框架要求的两个问题) ----

    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) {
            return null;
        }
        try {
            Object inventory = getInventoryObject(te);
            ItemStack[] contents = getContents(inventory);
            int[] inputSlots = getInputSlots(te);
            if (contents == null || inputSlots == null) {
                return null;
            }
            // 输出槽定义允许为空:TR 的流体复制机把 RecipeCrafter#outputSlots 传成 null
            // (产物是流体,物品侧没有输出槽).此时目录里只有输入槽,清理/收集阶段取不到东西;
            // 若在此直接判目标无效,流体复制机连流体产物也收不到了
            // (它的产物靠 FluidTransferHelper 收集).
            int[] outputSlots = getOutputSlots(te);
            IItemHandler view = new ReflectedInventory(contents, inventory, te, readStackLimit(inventory));
            return buildCatalog(view, contents.length, inputSlots, outputSlots, energySlotOf(te));
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn catalogFor failed", e);
            return null;
        }
    }

    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        // "正在处理"的反射读取集中在 isProcessing 内(lambda 只做转发),te 由框架传入且可能为 null
        return (w, p, te) -> isProcessing(te);
    }

    /**
     * 按 TR 的槽位定义构造目录.
     *
     * <p>角色只有三种:机器的 {@code inputSlots} → {@link SlotRole#INPUT}、
     * {@code outputSlots} → {@link SlotRole#OUTPUT}、电池槽 {@code energySlot} →
     * {@link SlotRole#UPGRADE}。其余槽位一律 {@link SlotRole#UNKNOWN}:它们有的是流体容器槽
     * (流体复制机的 1 号槽)、有的是空单元返还槽(工业磨粉机的 6 号槽,TR 自己的 GUI 把它当
     * outputSlot,但它不在机器的 {@code outputSlots} 定义里,本 handler 从不读写它)。
     * 把它们猜成 OUTPUT 会让框架在清理/收集阶段把玩家的容器收进网络.</p>
     *
     * <p>纯逻辑、不碰反射,便于单测直接喂槽位定义.</p>
     */
    static ISlotCatalog buildCatalog(IItemHandler handler, int slotCount, int[] inputSlots, int[] outputSlots,
                                     int energySlot) {
        SlotRole[] roles = slotRoles(slotCount, inputSlots, outputSlots, energySlot);
        return ISlotCatalog.builder().addAll(handler, null, roles).build();
    }

    /**
     * 槽位下标 → 角色映射.
     *
     * <p>TR 的三组下标实际互不相交(已对全部 19 个方块核对过),先写者优先只是防御:
     * 万一重叠,优先保留机器的推料目标,避免材料被推进一个"只收不还"的角色.</p>
     */
    static SlotRole[] slotRoles(int slotCount, int[] inputSlots, int[] outputSlots, int energySlot) {
        SlotRole[] roles = new SlotRole[Math.max(0, slotCount)];
        Arrays.fill(roles, SlotRole.UNKNOWN);
        if (inputSlots != null) {
            for (int index : inputSlots) {
                if (inRange(index, roles)) {
                    roles[index] = SlotRole.INPUT;
                }
            }
        }
        if (outputSlots != null) {
            for (int index : outputSlots) {
                if (inRange(index, roles) && roles[index] == SlotRole.UNKNOWN) {
                    roles[index] = SlotRole.OUTPUT;
                }
            }
        }
        if (inRange(energySlot, roles) && roles[energySlot] == SlotRole.UNKNOWN) {
            roles[energySlot] = SlotRole.UPGRADE;
        }
        return roles;
    }

    private static boolean inRange(int index, SlotRole[] roles) {
        return index >= 0 && index < roles.length;
    }

    // ---- 与框架默认语义不一致、必须覆盖回去的部分 ----

    /**
     * 覆盖框架默认:{@code canStart} 还有两个前置条件——机器正在处理上一批时
     * 拒收新材料(否则两批材料混在一起无法区分归属),以及输入槽里不得预置本批之外的物品
     * (玩家手动放入或上一批残留).框架默认只要求"目录可用",这两条会丢失.
     *
     * <p>"机器是否在忙"经 {@link #idleGate}(即 {@link MachineIdle})取,不直接调
     * {@link #isProcessing}:idleGate 是框架指定的机器状态抽象,子类与测试夹具只能通过它提供
     * 处理中信号,绕开它会让 canStart / isIdle / hasFinished 对同一台机器给出互相矛盾的回答。
     * 实机路径两者等价——生产 idleGate 的 lambda 体就是 {@code isProcessing(te)}.</p>
     */
    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return false;
        }
        if (isWorking(world, pos)) {
            return false;
        }
        List<ItemStack> batch = ingredientsOf(ingredients);
        for (SlotRef slot : catalog.inputSlots()) {
            ItemStack existing = slot.peek();
            if (existing.isEmpty() || HandlerUtils.isInputMaterial(existing, batch)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * 覆盖框架默认:TR 的"可收集"判据是"输出槽里有东西",不区分该物品是否与本批材料同型.
     *
     * <p>框架默认用 {@code hasCollectableProduct}(会把与本批材料同型的物品排除在产物之外),
     * 而本类的 {@link #collectProducts} 会把它整槽收走——两者不一致时,
     * 会话会一直判定"未空闲"直到超时.</p>
     */
    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return true;
        }
        MachineIdle gate = idleGate(world, pos);
        // 保留框架的推料保护窗口:推料与"机器开始处理"不在同一 tick,
        // TileMachine 此时 progress/operationLength 仍为 0,没有这层保护就可能当场收走刚推入的材料
        if (session != null && !session.isPushGraceElapsed(world.getTotalWorldTime(), gate.graceTicks())) {
            return false;
        }
        if (gate.isWorking(world, pos, world.getTileEntity(pos))) {
            return false;
        }
        return hasFilledOutputSlot(catalog);
    }

    /**
     * 本机"是否正在处理"的唯一入口：经框架规定的机器状态抽象 {@link #idleGate} 询问.
     *
     * <p>{@code canStart} / {@code isIdle} / {@code hasFinished} 统一经本方法取状态，
     * 两个入口表达同一件事时改一处忘另一处就会让三个方法的忙判定互相矛盾
     * （测试夹具也只能注入进 {@code idleGate}）。实机路径等价（生产 {@code idleGate} 的 lambda 体即
     * {@code isProcessing(te)}）.</p>
     */
    private boolean isWorking(World world, BlockPos pos) {
        return idleGate(world, pos).isWorking(world, pos, world.getTileEntity(pos));
    }

    /**
     * 覆盖框架默认:框架默认只看"本批输入已消耗且无产物",不检查机器是否还在跑.
     *
     * <p>TR 的 TileMachine 开工首 tick 就把输入从槽位搬进 {@code itemOutputsBuffer}
     * (输入槽已空、产物尚未落到输出槽),此时框架默认会判定"已完成"并结束会话,
     * 产物再无人接管;判据用"是否正在处理"挡住了这段窗口.</p>
     *
     * <p>"机器是否在忙"经 {@link #idleGate} 取(与本类 {@link #canStart}、{@link #isIdle} 同一入口),
     * 不直接调 {@link #isProcessing};实机路径两者等价.</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        ISlotCatalog catalog = catalogFor(world, pos);
        if (catalog == null) {
            return true;
        }
        if (isWorking(world, pos)) {
            return false;
        }
        return !hasBatchMaterialInInput(catalog, inputs) && !hasFilledOutputSlot(catalog);
    }

    /**
     * 覆盖框架默认:收集是三阶段,框架默认只做前两个对应阶段.
     *
     * <p>两点差异:</p>
     * <ul>
     *     <li>第三阶段(输入槽里不属于本批输入的物品)框架默认不做——角色已知时它只收
     *     {@link SlotRole#OUTPUT} 槽,而 TR 机器确实可能把东西留在输入槽(玩家/漏斗喂进去的料),
     *     框架默认会让它永远留在机器里;</li>
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

    /** 三阶段收集:预期产物 → 输出槽剩余 → 输入槽里不属于本批输入的物品. */
    static List<ItemStack> collect(ISlotCatalog catalog, IAEItemStack[] expectedOutputs, List<ItemStack> inputs) {
        List<ItemStack> collected = new ArrayList<>();
        List<SlotRef> outputSlots = catalog.outputSlots();
        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

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

        // 阶段 3：输入槽中不属于本批输入的物品(机器/玩家可能把与本批无关的东西留在输入槽)
        for (SlotRef slot : catalog.inputSlots()) {
            ItemStack inSlot = slot.peek();
            if (inSlot.isEmpty() || HandlerUtils.isInputMaterial(inSlot, inputsSafe)) {
                continue;
            }
            drain(slot, collected);
        }
        return collected;
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

    /** 输出槽里是否有任何内容(TR 不区分"是否属于本批材料"). */
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

    @Override
    public List<FluidStack> clearOutputFluids(World world, BlockPos pos, IActionSource source, TargetSession session, List<FluidStack> batchFluids) {
        // TR 机器的储罐通过 RebornFluidHandler 暴露流体能力,其 fill/drain 会按机器
        // 自身的侧面流体配置(FluidConfiguration)判定,因此按真实面抽取即可,不自己实现流体逻辑.
        return FluidTransferHelper.drainExtractableFluids(world, pos, batchFluids);
    }

    // ---- 空闲判定的纯逻辑(与反射解耦,便于单测) ----

    /**
     * TileMachine 体系是否正在处理.
     *
     * <p>不能只看 {@code progress}:TileMachine 在开工首 tick 时 {@code progress} 仍为 0
     * (next tick 才自增),因此 {@code operationLength} 非零同样表示在处理.</p>
     */
    static boolean machineInProgress(int progress, int operationLength) {
        return progress > 0 || operationLength > 0;
    }

    /** TileGenericMachine 体系是否正在处理:RecipeCrafter 已选出配方或已走过进度. */
    static boolean crafterInProgress(boolean hasRecipe, int currentTickTime) {
        return hasRecipe || currentTickTime > 0;
    }

    // ---- 反射访问 ----

    /**
     * 目标是否为本处理器支持且反射句柄齐全的 TR 机器.
     *
     * <p>句柄缺失时一律拒绝,避免后续方法里出现半可用的状态.</p>
     */
    private static boolean isSupportedMachine(TileEntity te) {
        if (te == null || te.isInvalid() || !AVAILABLE || FIELD_CONTENTS == null) return false;
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return FIELD_INVENTORY_MACHINE != null && FIELD_INPUT_SLOTS_MACHINE != null
                    && FIELD_OUTPUT_SLOTS_MACHINE != null;
        }
        if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            return FIELD_INVENTORY_GENERIC != null && FIELD_CRAFTER != null
                    && FIELD_INPUT_SLOTS_CRAFTER != null && FIELD_OUTPUT_SLOTS_CRAFTER != null;
        }
        return false;
    }

    private static boolean isProcessing(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return machineInProgress(getIntValue(te, FIELD_PROGRESS_MACHINE),
                    getIntValue(te, FIELD_OPERATION_LENGTH_MACHINE));
        }
        Object crafter = getCrafter(te);
        if (crafter == null) {
            return false;
        }
        return crafterInProgress(getFieldValue(crafter, FIELD_CURRENT_RECIPE_CRAFTER) != null,
                getIntValue(crafter, FIELD_CURRENT_TICK_TIME_CRAFTER));
    }

    /**
     * 目标机器电池槽的下标;无法判定返回 -1(该槽位不进目录).
     *
     * <p>不用 {@link #getIntValue}(读失败返回 0):0 是合法下标,读失败会被误标成电池槽.</p>
     */
    private static int energySlotOf(TileEntity te) {
        if (te == null) return -1;
        Field field = null;
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            field = FIELD_ENERGY_SLOT_MACHINE;
        } else if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            field = FIELD_ENERGY_SLOT_GENERIC;
        }
        if (field == null) return -1;
        try {
            return field.getInt(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read TechReborn energy slot", e);
            return -1;
        }
    }

    private static Object getInventoryObject(TileEntity te) {
        return getFieldValue(te, resolveInventoryField(te));
    }

    /**
     * 按目标实际所属的机器基类选择对应的 inventory 字段.
     */
    private static Field resolveInventoryField(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return FIELD_INVENTORY_MACHINE;
        }
        if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            return FIELD_INVENTORY_GENERIC;
        }
        return null;
    }

    private static ItemStack[] getContents(Object inventory) {
        if (inventory == null || FIELD_CONTENTS == null) return null;
        Object value = getFieldValue(inventory, FIELD_CONTENTS);
        return value instanceof ItemStack[] ? (ItemStack[]) value : null;
    }

    private static int[] getInputSlots(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(te, FIELD_INPUT_SLOTS_MACHINE);
        }
        if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(getCrafter(te), FIELD_INPUT_SLOTS_CRAFTER);
        }
        return null;
    }

    private static int[] getOutputSlots(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(te, FIELD_OUTPUT_SLOTS_MACHINE);
        }
        if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(getCrafter(te), FIELD_OUTPUT_SLOTS_CRAFTER);
        }
        return null;
    }

    private static Object getCrafter(TileEntity te) {
        return getFieldValue(te, FIELD_CRAFTER);
    }

    /** 读取 RebornCore 库存的 stackLimit;读不到返回 0(表示不额外限制). */
    private static int readStackLimit(Object inventory) {
        if (inventory == null || FIELD_STACK_LIMIT == null) return 0;
        try {
            int limit = FIELD_STACK_LIMIT.getInt(inventory);
            return limit > 0 ? limit : 0;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read TechReborn inventory stack limit", e);
            return 0;
        }
    }

    private static Object getFieldValue(Object target, Field field) {
        if (target == null || field == null) return null;
        try {
            return field.get(target);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read TechReborn field {}", field.getName(), e);
            return null;
        }
    }

    private static int getIntValue(Object target, Field field) {
        if (target == null || field == null) return 0;
        try {
            return field.getInt(target);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read TechReborn int field {}", field.getName(), e);
            return 0;
        }
    }

    private static int[] getIntArray(Object target, Field field) {
        Object value = getFieldValue(target, field);
        return value instanceof int[] ? (int[]) value : null;
    }

    /**
     * 槽位级别的插入校验.
     *
     * <p>调用机器自身的 {@code isItemValidForSlot}:RebornCore 在该方法里会检查玩家为
     * 该槽位开启的"仅接受配方输入"过滤器.槽位配置未初始化时无法取得过滤器信息,
     * 此时放行(等同于库存层面的实现,它恒返回 true).</p>
     */
    private static boolean isSlotAcceptable(TileEntity te, int slot, ItemStack stack) {
        if (IS_ITEM_VALID_FOR_SLOT_METHOD == null || FIELD_SLOT_CONFIGURATION == null) return true;
        Object slotConfiguration = getFieldValue(te, FIELD_SLOT_CONFIGURATION);
        if (slotConfiguration == null) return true;
        try {
            Object result = IS_ITEM_VALID_FOR_SLOT_METHOD.invoke(te, slot, stack);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] TechReborn isItemValidForSlot failed for slot {}", slot, e);
            return true;
        }
    }

    /**
     * 库存被本处理器直接改写后同步脏标记.
     *
     * <p>直接改 {@code contents} 数组不会经过 RebornCore 的改脏逻辑,而
     * {@code RecipeCrafter#updateEntity} 只在 {@code isInvDirty()}(实际读取 {@code isDirty})
     * 或当前无配方时才重新扫描配方,因此这里必须补上,否则推送的材料要等机器自身的
     * 20 tick 兜底改脏才会被识别.</p>
     *
     * <p>{@code hasChanged} 是 TR 自身代码使用的另一个标记,一并置位以免漏掉依赖它的机器.</p>
     */
    private static void markInventoryChanged(Object inventory, TileEntity te) {
        try {
            if (FIELD_IS_DIRTY != null) FIELD_IS_DIRTY.setBoolean(inventory, true);
            if (FIELD_HAS_CHANGED != null) FIELD_HAS_CHANGED.setBoolean(inventory, true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to mark TechReborn inventory dirty", e);
        }
        markDirty(te);
    }

    private static void markDirty(TileEntity te) {
        try {
            te.markDirty();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] markDirty failed", e);
        }
    }

    /**
     * 把 RebornCore 的 {@code Inventory#contents} 数组包装成 {@link IItemHandler},供框架
     * 的 {@link SlotRef} 读写.
     *
     * <p>TR 的 {@code Inventory} 虽然实现了 {@code ISidedInventory},但
     * {@code getSlotsForFace} 返回空数组、{@code canInsertItem}/{@code canExtractItem} 恒为 false,
     * Forge 的 {@code InvWrapper} 因此对外表现为空库存;只能直接读写数组.</p>
     *
     * <p>两个必须保留的副作用:插入要过机器自己的 {@code isItemValidForSlot}
     * (玩家为该槽位开启的"仅接受配方输入"过滤器),写入/取出后要补 RebornCore 的脏标记
     * (见 {@link #markInventoryChanged})——否则机器不会重新扫描配方,推入的材料要等
     * 机器自身 20 tick 的兜底改脏才被识别.</p>
     */
    static final class ReflectedInventory implements IItemHandler {

        private final ItemStack[] contents;
        private final Object inventory;
        private final TileEntity te;
        private final int inventoryLimit;

        ReflectedInventory(ItemStack[] contents, Object inventory, TileEntity te, int inventoryLimit) {
            this.contents = contents;
            this.inventory = inventory;
            this.te = te;
            this.inventoryLimit = inventoryLimit;
        }

        @Override
        public int getSlots() {
            return this.contents.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (!inRange(slot)) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = this.contents[slot];
            return stack != null ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !inRange(slot)) {
                return stack;
            }
            if (!isSlotAcceptable(this.te, slot, stack)) {
                return stack;
            }
            ItemStack existing = getStackInSlot(slot);
            int room;
            if (existing.isEmpty()) {
                room = limitFor(stack);
            } else if (ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                room = limitFor(stack) - existing.getCount();
            } else {
                return stack;
            }
            if (room <= 0) {
                return stack;
            }
            int placed = Math.min(room, stack.getCount());
            if (!simulate) {
                if (existing.isEmpty()) {
                    ItemStack placedStack = stack.copy();
                    placedStack.setCount(placed);
                    this.contents[slot] = placedStack;
                } else {
                    existing.grow(placed);
                }
                markInventoryChanged(this.inventory, this.te);
            }
            ItemStack remaining = stack.copy();
            remaining.shrink(placed);
            return remaining;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, existing.getCount());
            ItemStack out = existing.copy();
            out.setCount(taken);
            if (!simulate) {
                if (taken >= existing.getCount()) {
                    this.contents[slot] = ItemStack.EMPTY;
                } else {
                    existing.shrink(taken);
                }
                markInventoryChanged(this.inventory, this.te);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.inventoryLimit > 0 ? Math.min(this.inventoryLimit, 64) : 64;
        }

        private int limitFor(ItemStack stack) {
            int limit = Math.max(1, stack.getMaxStackSize());
            return this.inventoryLimit > 0 ? Math.min(limit, this.inventoryLimit) : limit;
        }

        private boolean inRange(int slot) {
            return slot >= 0 && slot < this.contents.length;
        }
    }
}
