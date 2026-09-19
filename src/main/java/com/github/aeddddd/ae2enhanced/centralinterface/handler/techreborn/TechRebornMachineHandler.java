package com.github.aeddddd.ae2enhanced.centralinterface.handler.techreborn;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.*;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

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
 */
public class TechRebornMachineHandler implements IRemoteHandler {

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

    // ---------- TileGenericMachine 体系(输入/输出槽在 RecipeCrafter) ----------
    private static Field FIELD_INVENTORY_GENERIC;
    private static Field FIELD_CRAFTER;
    private static Field FIELD_INPUT_SLOTS_CRAFTER;
    private static Field FIELD_OUTPUT_SLOTS_CRAFTER;
    private static Field FIELD_CURRENT_RECIPE_CRAFTER;
    private static Field FIELD_CURRENT_TICK_TIME_CRAFTER;

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

        FIELD_INVENTORY_GENERIC = findField(TILE_GENERIC_MACHINE_CLASS, "inventory");
        FIELD_CRAFTER = findField(TILE_GENERIC_MACHINE_CLASS, "crafter");
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

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && MACHINE_BLOCK_IDS.contains(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        return isSupportedMachine(world.getTileEntity(pos));
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.physicalOnly();
    }

    /**
     * 目标是否为本处理器支持且反射句柄齐全的 TR 机器.
     *
     * <p>句柄缺失时一律拒绝,避免后续方法里出现半可用的状态.</p>
     */
    private boolean isSupportedMachine(TileEntity te) {
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

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) return false;

        try {
            // 机器正在处理上一批时不接收新材料,避免两批材料混在一起无法区分归属
            if (isProcessing(te)) return false;

            ItemStack[] contents = getContents(getInventoryObject(te));
            int[] inputSlots = getInputSlots(te);
            if (contents == null || inputSlots == null) return false;

            List<ItemStack> batch = collectBatch(ingredients);
            for (int slot : inputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack existing = contents[slot];
                if (existing == null || existing.isEmpty()) continue;
                // 输入槽里已有本批之外的物品(玩家放入或上一批残留) → 不开始
                if (!HandlerUtils.isInputMaterial(existing, batch)) return false;
            }
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn canStart failed", e);
            return false;
        }
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) return false;

        List<ItemStack> batch = collectBatch(ingredients);
        if (batch.isEmpty()) return true;

        try {
            Object inventory = getInventoryObject(te);
            ItemStack[] contents = getContents(inventory);
            int[] inputSlots = getInputSlots(te);
            if (contents == null || inputSlots == null) return false;

            // 先整批模拟,任何一件放不下就整体放弃,不存在"推了一半"的中间态
            ItemStack[] planned = planInsertion(te, inventory, contents, inputSlots, batch);
            if (planned == null) return false;

            return commitInsertion(inventory, te, contents, inputSlots, planned);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn pushMaterials failed", e);
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        // TR 机器在自己的 tick 中读取输入槽并自动开工(TileMachine#startWork /
        // RecipeCrafter#updateCurrentRecipe),不提供外部强制启动入口.
        return true;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) return Collections.emptyList();

        List<ItemStack> inputs = session != null ? session.getInputs() : null;
        if (inputs == null || inputs.isEmpty()) return Collections.emptyList();

        List<ItemStack> reverted = new ArrayList<>();
        try {
            Object inventory = getInventoryObject(te);
            ItemStack[] contents = getContents(inventory);
            int[] inputSlots = getInputSlots(te);
            if (contents == null || inputSlots == null) return Collections.emptyList();

            // 只回退匹配本批输入快照的槽位:TileMachine 会在开工时消耗输入,
            // 此处不能无条件清空输入槽(那会连同玩家放入的物品一起取走).
            for (int slot : inputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack stack = contents[slot];
                if (stack == null || stack.isEmpty()) continue;
                if (!HandlerUtils.isInputMaterial(stack, inputs)) continue;
                reverted.add(stack.copy());
                contents[slot] = ItemStack.EMPTY;
            }
            if (!reverted.isEmpty()) {
                markInventoryChanged(inventory, te);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn revertMaterials failed", e);
        }
        return reverted;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) return Collections.emptyList();

        List<ItemStack> cleared = new ArrayList<>();
        try {
            Object inventory = getInventoryObject(te);
            ItemStack[] contents = getContents(inventory);
            int[] outputSlots = getOutputSlots(te);
            if (contents == null || outputSlots == null) return Collections.emptyList();

            for (int slot : outputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack stack = contents[slot];
                if (stack == null || stack.isEmpty()) continue;
                cleared.add(stack.copy());
                contents[slot] = ItemStack.EMPTY;
            }
            if (!cleared.isEmpty()) {
                markInventoryChanged(inventory, te);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn clearOutputs failed", e);
        }
        return cleared;
    }

    @Override
    public List<FluidStack> clearOutputFluids(World world, BlockPos pos, IActionSource source, TargetSession session, List<FluidStack> batchFluids) {
        // TR 机器的储罐通过 RebornFluidHandler 暴露流体能力,其 fill/drain 会按机器
        // 自身的侧面流体配置(FluidConfiguration)判定,因此按真实面抽取即可,不自己实现流体逻辑.
        return FluidTransferHelper.drainExtractableFluids(world, pos, batchFluids);
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) return Collections.emptyList();

        List<ItemStack> collected = new ArrayList<>();
        try {
            Object inventory = getInventoryObject(te);
            ItemStack[] contents = getContents(inventory);
            int[] outputSlots = getOutputSlots(te);
            int[] inputSlots = getInputSlots(te);
            if (contents == null || outputSlots == null || inputSlots == null) return Collections.emptyList();

            List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

            // 阶段 1：优先收集匹配预期产物的物品
            if (expectedOutputs != null) {
                for (IAEItemStack expected : expectedOutputs) {
                    if (expected == null) continue;
                    ItemStack expectedStack = expected.createItemStack();
                    for (int slot : outputSlots) {
                        if (slot < 0 || slot >= contents.length) continue;
                        ItemStack inSlot = contents[slot];
                        if (inSlot == null || inSlot.isEmpty()) continue;
                        if (HandlerUtils.matchesLoosely(inSlot, expectedStack)) {
                            collected.add(inSlot.copy());
                            contents[slot] = ItemStack.EMPTY;
                        }
                    }
                }
            }

            // 阶段 2：收集输出槽中所有剩余物品(副产物/容器等)
            for (int slot : outputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack stack = contents[slot];
                if (stack == null || stack.isEmpty()) continue;
                collected.add(stack.copy());
                contents[slot] = ItemStack.EMPTY;
            }

            // 阶段 3：收集输入槽中不属于本批输入的物品(机器可能把产物吐回输入槽)
            for (int slot : inputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack stack = contents[slot];
                if (stack == null || stack.isEmpty()) continue;
                if (HandlerUtils.isInputMaterial(stack, inputsSafe)) continue;
                collected.add(stack.copy());
                contents[slot] = ItemStack.EMPTY;
            }

            if (!collected.isEmpty()) {
                markInventoryChanged(inventory, te);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn collectProducts failed", e);
        }
        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) return true;

        try {
            if (isProcessing(te)) return false;

            ItemStack[] contents = getContents(getInventoryObject(te));
            int[] outputSlots = getOutputSlots(te);
            // 读不到槽位定义时不能宣称可收集,否则会在产物尚未取出时提前结束会话
            if (contents == null || outputSlots == null) return false;

            // 宽松语义：只要输出槽有产物,即可收集(支持流水线模式)
            for (int slot : outputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack stack = contents[slot];
                if (stack != null && !stack.isEmpty()) return true;
            }
            return false;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn isIdle failed", e);
            return false;
        }
    }

    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isSupportedMachine(te)) return true;

        try {
            if (isProcessing(te)) return false;

            ItemStack[] contents = getContents(getInventoryObject(te));
            int[] inputSlots = getInputSlots(te);
            int[] outputSlots = getOutputSlots(te);
            // 读不到槽位定义时保持"未完成",让会话继续跟踪而不是提前释放目标
            if (contents == null || inputSlots == null || outputSlots == null) return false;

            // 输入槽还有本批材料 → 尚未处理完
            for (int slot : inputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack stack = contents[slot];
                if (stack != null && !stack.isEmpty() && HandlerUtils.isInputMaterial(stack, inputs)) {
                    return false;
                }
            }

            // 输入已耗尽,输出槽还有产物残留 → 尚未收集完
            for (int slot : outputSlots) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack stack = contents[slot];
                if (stack != null && !stack.isEmpty()) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn hasFinished failed", e);
            return false;
        }
    }

    // ---- Internal helpers ----

    private List<ItemStack> collectBatch(InventoryCrafting ingredients) {
        List<ItemStack> batch = new ArrayList<>();
        if (ingredients == null) return batch;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                batch.add(stack.copy());
            }
        }
        return batch;
    }

    /**
     * 在副本上模拟整批插入,返回每个输入槽的最终内容；任何一件放不下则返回 {@code null}.
     *
     * <p>模拟阶段只读真实库存,不在真实库存上做任何修改.</p>
     */
    private ItemStack[] planInsertion(TileEntity te, Object inventory, ItemStack[] contents, int[] inputSlots, List<ItemStack> batch) {
        ItemStack[] planned = new ItemStack[inputSlots.length];
        for (int i = 0; i < inputSlots.length; i++) {
            int slot = inputSlots[i];
            ItemStack existing = (slot >= 0 && slot < contents.length) ? contents[slot] : null;
            planned[i] = (existing == null || existing.isEmpty()) ? ItemStack.EMPTY : existing.copy();
        }

        for (ItemStack stack : batch) {
            int remaining = stack.getCount();
            for (int i = 0; i < inputSlots.length && remaining > 0; i++) {
                int slot = inputSlots[i];
                if (slot < 0 || slot >= contents.length) continue;
                if (!isSlotAcceptable(te, slot, stack)) continue;

                int limit = getSlotLimit(inventory, stack);
                ItemStack inSlot = planned[i];
                if (inSlot.isEmpty()) {
                    int move = Math.min(remaining, limit);
                    if (move <= 0) continue;
                    ItemStack placed = stack.copy();
                    placed.setCount(move);
                    planned[i] = placed;
                    remaining -= move;
                    continue;
                }
                if (!ItemStack.areItemsEqual(inSlot, stack) || !ItemStack.areItemStackTagsEqual(inSlot, stack)) continue;

                int space = limit - inSlot.getCount();
                if (space <= 0) continue;
                int move = Math.min(remaining, space);
                inSlot.grow(move);
                remaining -= move;
            }
            if (remaining > 0) return null;
        }
        return planned;
    }

    /**
     * 把模拟结果写回真实库存；写入过程中出错则还原所有已写入槽位并返回 {@code false}.
     */
    private boolean commitInsertion(Object inventory, TileEntity te, ItemStack[] contents, int[] inputSlots, ItemStack[] planned) {
        // 模拟阶段从不原地修改原有 ItemStack,因此回滚只需还原数组元素引用
        ItemStack[] snapshot = new ItemStack[inputSlots.length];
        for (int i = 0; i < inputSlots.length; i++) {
            int slot = inputSlots[i];
            snapshot[i] = (slot >= 0 && slot < contents.length) ? contents[slot] : null;
        }

        try {
            for (int i = 0; i < inputSlots.length; i++) {
                int slot = inputSlots[i];
                if (slot < 0 || slot >= contents.length) continue;
                contents[slot] = planned[i];
            }
        } catch (Throwable t) {
            for (int i = 0; i < inputSlots.length; i++) {
                int slot = inputSlots[i];
                if (slot < 0 || slot >= contents.length) continue;
                contents[slot] = snapshot[i];
            }
            AE2Enhanced.LOGGER.warn("[AE2E] TechReborn pushMaterials commit failed, rolled back", t);
            return false;
        }

        markInventoryChanged(inventory, te);
        return true;
    }

    /**
     * 槽位级别的插入校验.
     *
     * <p>调用机器自身的 {@code isItemValidForSlot}:RebornCore 在该方法里会检查玩家为
     * 该槽位开启的"仅接受配方输入"过滤器.槽位配置未初始化时无法取得过滤器信息,
     * 此时放行(等同于库存层面的实现,它恒返回 true).</p>
     */
    private boolean isSlotAcceptable(TileEntity te, int slot, ItemStack stack) {
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

    private int getSlotLimit(Object inventory, ItemStack stack) {
        int limit = Math.max(1, stack.getMaxStackSize());
        if (FIELD_STACK_LIMIT == null || inventory == null) return limit;
        try {
            int inventoryLimit = FIELD_STACK_LIMIT.getInt(inventory);
            if (inventoryLimit > 0) {
                return Math.min(limit, inventoryLimit);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read TechReborn inventory stack limit", e);
        }
        return limit;
    }

    private Object getInventoryObject(TileEntity te) {
        Field field = resolveInventoryField(te);
        return getFieldValue(te, field);
    }

    /**
     * 按目标实际所属的机器基类选择对应的 inventory 字段.
     */
    private Field resolveInventoryField(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return FIELD_INVENTORY_MACHINE;
        }
        if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            return FIELD_INVENTORY_GENERIC;
        }
        return null;
    }

    private ItemStack[] getContents(Object inventory) {
        if (inventory == null || FIELD_CONTENTS == null) return null;
        Object value = getFieldValue(inventory, FIELD_CONTENTS);
        return value instanceof ItemStack[] ? (ItemStack[]) value : null;
    }

    private int[] getInputSlots(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(te, FIELD_INPUT_SLOTS_MACHINE);
        }
        if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(getCrafter(te), FIELD_INPUT_SLOTS_CRAFTER);
        }
        return null;
    }

    private int[] getOutputSlots(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(te, FIELD_OUTPUT_SLOTS_MACHINE);
        }
        if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(te)) {
            return getIntArray(getCrafter(te), FIELD_OUTPUT_SLOTS_CRAFTER);
        }
        return null;
    }

    private Object getCrafter(TileEntity te) {
        return getFieldValue(te, FIELD_CRAFTER);
    }

    /**
     * 机器是否正在处理某一批材料.
     *
     * <p>不能只看 {@code progress}:TileMachine 在开工首 tick 时 {@code progress} 仍为 0
     * (next tick 才自增),因此 {@code operationLength} 非零同样表示在处理;
     * TileGenericMachine 则看 RecipeCrafter 是否已选出配方或已走过进度.</p>
     */
    private boolean isProcessing(TileEntity te) {
        if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(te)) {
            return getIntValue(te, FIELD_PROGRESS_MACHINE) > 0
                    || getIntValue(te, FIELD_OPERATION_LENGTH_MACHINE) > 0;
        }
        Object crafter = getCrafter(te);
        if (crafter == null) return false;
        if (getFieldValue(crafter, FIELD_CURRENT_RECIPE_CRAFTER) != null) return true;
        return getIntValue(crafter, FIELD_CURRENT_TICK_TIME_CRAFTER) > 0;
    }

    private Object getFieldValue(Object target, Field field) {
        if (target == null || field == null) return null;
        try {
            return field.get(target);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read TechReborn field {}", field.getName(), e);
            return null;
        }
    }

    private int getIntValue(Object target, Field field) {
        if (target == null || field == null) return 0;
        try {
            return field.getInt(target);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read TechReborn int field {}", field.getName(), e);
            return 0;
        }
    }

    private int[] getIntArray(Object target, Field field) {
        Object value = getFieldValue(target, field);
        return value instanceof int[] ? (int[]) value : null;
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
    private void markInventoryChanged(Object inventory, TileEntity te) {
        try {
            if (FIELD_IS_DIRTY != null) FIELD_IS_DIRTY.setBoolean(inventory, true);
            if (FIELD_HAS_CHANGED != null) FIELD_HAS_CHANGED.setBoolean(inventory, true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to mark TechReborn inventory dirty", e);
        }
        markDirty(te);
    }

    private void markDirty(TileEntity te) {
        try {
            te.markDirty();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] markDirty failed", e);
        }
    }
}
