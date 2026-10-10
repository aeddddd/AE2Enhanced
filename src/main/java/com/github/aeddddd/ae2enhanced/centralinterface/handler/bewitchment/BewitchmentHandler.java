package com.github.aeddddd.ae2enhanced.centralinterface.handler.bewitchment;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Bewitchment 处理器：SpinningWheel + Distillery + WitchesCauldron.
 *
 * <p><b>SpinningWheel / Distillery</b>：通过 {@link EnumFacing#UP} 的 IItemHandler 推入材料,
 * 通过 {@link EnumFacing#DOWN} 的 IItemHandler 收集产物.每个槽位仅放入单个物品.</p>
 *
 * <p><b>WitchesCauldron</b>：不暴露 IItemHandler capability,IO 完全通过反射操作内部字段完成.
 * push 时直接写入 inventory 并立即触发配方匹配与产物生成；collect 时回收周围 EntityItem 与 inventory 残余.</p>
 *
 * <h3>与框架的关系</h3>
 * <p>本类继承通用骨架 {@link AbstractMachineHandler}，并声明自己的表项
 * （{@link #defineSpec}：三个方块 id）。框架负责生命周期与调度入口，
 * 本类回答"目录怎么来"（{@link #catalogFor}：UP 面 = 输入槽、DOWN 面 = 输出槽、
 * 坩埚的内部 inventory）与"什么时候算处理中"（{@link #idleGate}：三个进度/mode 字段 + 4 tick 推料保护期）。</p>
 *
 * <p><b>为什么推料/收集/回退/清理仍由本类自己实现</b>：三台机器的 IO 语义都与框架默认不同，
 * 逐条保留与框架默认不同的行为（每处都注明差异）：</p>
 * <ul>
 *   <li>{@link #pushMaterials}：每个配方槽只放 <b>1 个</b>物品（不按数量拆分），
 *       坩埚还要在推料时立即触发配方匹配、生成产物、抽走罐内 1000mb 并复位 mode；</li>
 *   <li>{@link #collectProducts}：只抽 DOWN 面/坩埚周围，<b>不</b>按预期产物匹配、也<b>不</b>排除本批输入
 *       （"抽得出来就全抽"）；</li>
 *   <li>{@link #revertMaterials}：无条件抽空 UP 面全部槽位（非"只回退本批快照"）；</li>
 *   <li>{@link #clearOutputs}：保留为空操作——框架默认会清 OUTPUT 角色槽，
 *       对 SpinningWheel 就是清掉 DOWN 面的产物，属于行为变更；</li>
 *   <li>{@link #isIdle} / {@link #hasFinished}：判据是"进度/mode 归零"，与"产物是否已出现"无关，
 *       框架默认（grace + 可收集产物）会让"输入已进内部缓冲、产物未出"的窗口被误判为可收集。</li>
 * </ul>
 */
public class BewitchmentHandler extends AbstractMachineHandler {

    private static final String SPINNING_WHEEL_ID = "bewitchment:spinning_wheel";
    private static final String DISTILLERY_ID = "bewitchment:distillery";
    private static final String CAULDRON_ID = "bewitchment:witches_cauldron";

    // 反射缓存 — SpinningWheel
    private static Class<?> CLASS_SPINNING_WHEEL;
    private static Field FIELD_SPINNING_PROGRESS;

    // 反射缓存 — Distillery
    private static Class<?> CLASS_DISTILLERY;
    private static Field FIELD_DISTILLERY_PROGRESS;

    // 反射缓存 — Cauldron
    private static Class<?> CLASS_CAULDRON;
    private static Class<?> CLASS_CAULDRON_RECIPE;
    private static Field FIELD_CAULDRON_INVENTORY;
    private static Field FIELD_CAULDRON_MODE;
    private static Field FIELD_CAULDRON_CRAFTING_TIMER;
    private static Field FIELD_CAULDRON_HAS_POWER;
    private static Field FIELD_CAULDRON_HEAT_TIMER;
    private static Field FIELD_CAULDRON_TANK;
    private static Method METHOD_CAULDRON_SET_POWER;
    private static Method METHOD_CAULDRON_RECIPE_MATCHES;
    private static Field FIELD_CAULDRON_RECIPE_OUTPUT;

    /** 推料保护期：防止推料后进度尚未增加就被判定为 idle */
    private static final int PUSH_IDLE_GRACE_TICKS = 4;

    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_SPINNING_WHEEL = Class.forName("com.bewitchment.common.block.tile.entity.TileEntitySpinningWheel");
            FIELD_SPINNING_PROGRESS = CLASS_SPINNING_WHEEL.getField("progress");

            CLASS_DISTILLERY = Class.forName("com.bewitchment.common.block.tile.entity.TileEntityDistillery");
            FIELD_DISTILLERY_PROGRESS = CLASS_DISTILLERY.getField("progress");

            CLASS_CAULDRON = Class.forName("com.bewitchment.common.block.tile.entity.TileEntityWitchesCauldron");
            FIELD_CAULDRON_INVENTORY = CLASS_CAULDRON.getDeclaredField("inventory");
            FIELD_CAULDRON_INVENTORY.setAccessible(true);
            FIELD_CAULDRON_MODE = CLASS_CAULDRON.getField("mode");
            FIELD_CAULDRON_CRAFTING_TIMER = CLASS_CAULDRON.getDeclaredField("craftingTimer");
            FIELD_CAULDRON_CRAFTING_TIMER.setAccessible(true);
            FIELD_CAULDRON_HAS_POWER = CLASS_CAULDRON.getDeclaredField("hasPower");
            FIELD_CAULDRON_HAS_POWER.setAccessible(true);
            FIELD_CAULDRON_HEAT_TIMER = CLASS_CAULDRON.getDeclaredField("heatTimer");
            FIELD_CAULDRON_HEAT_TIMER.setAccessible(true);
            FIELD_CAULDRON_TANK = CLASS_CAULDRON.getField("tank");
            METHOD_CAULDRON_SET_POWER = CLASS_CAULDRON.getDeclaredMethod("setPower");
            METHOD_CAULDRON_SET_POWER.setAccessible(true);

            CLASS_CAULDRON_RECIPE = Class.forName("com.bewitchment.api.registry.CauldronRecipe");
            METHOD_CAULDRON_RECIPE_MATCHES = CLASS_CAULDRON_RECIPE.getMethod("matches", ItemStackHandler.class);
            FIELD_CAULDRON_RECIPE_OUTPUT = CLASS_CAULDRON_RECIPE.getField("output");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] Bewitchment reflection init failed", e);
        }
    }

    /** 表项：三个方块 id（SpinningWheel / Distillery / WitchesCauldron），行为描述即本类. */
    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder()
                .exactIds(SPINNING_WHEEL_ID, DISTILLERY_ID, CAULDRON_ID)
                .access(this)
                .build();
    }

    // ===================== 目录 =====================

    /**
     * 构造目标目录.
     *
     * <p>SpinningWheel / Distillery：UP 面槽位 = {@link SlotRole#INPUT}、DOWN 面槽位 =
     * {@link SlotRole#OUTPUT}。两台机器缺一面 capability 时目录里就只有另一面的槽位；
     * 两面都取不到时构造出空目录而不是 {@code null}——这种目标仍算有效（推料必然失败）。</p>
     *
     * <p>WitchesCauldron：不暴露 capability，目录取自反射拿到的内部 {@link ItemStackHandler}，
     * 角色一律 {@link SlotRole#INPUT}（产物由配对方块生成到世界里的 EntityItem，不在槽位中）。</p>
     *
     * @return 目录；目标不是这三种方块（或反射读不到坩埚库存）时返回 {@code null}
     */
    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (isUpDownMachine(te)) {
            return catalogOf(itemHandlerOf(te, EnumFacing.UP), itemHandlerOf(te, EnumFacing.DOWN));
        }
        if (CLASS_CAULDRON.isInstance(te)) {
            return cauldronCatalog(te);
        }
        return null;
    }

    /** 纯装配（不碰反射，便于单测直接驱动）：UP 面槽位为输入、DOWN 面槽位为输出。 */
    static ISlotCatalog catalogOf(@Nullable IItemHandler up, @Nullable IItemHandler down) {
        return ISlotCatalog.builder()
                .addAll(up, EnumFacing.UP, SlotRole.INPUT)
                .addAll(down, EnumFacing.DOWN, SlotRole.OUTPUT)
                .build();
    }

    @Nullable
    private static ISlotCatalog cauldronCatalog(TileEntity te) {
        try {
            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv == null) {
                // 读不到内部 inventory 仍返回空目录（不是 null）：这类目标照样"有效"，
                // 只是推料/启动必然失败。
                return ISlotCatalog.builder().build();
            }
            return ISlotCatalog.builder().addAll(inv, null, SlotRole.INPUT).build();
        } catch (Exception e) {
            return ISlotCatalog.builder().build();
        }
    }

    private static boolean isUpDownMachine(TileEntity te) {
        return CLASS_SPINNING_WHEEL.isInstance(te) || CLASS_DISTILLERY.isInstance(te);
    }

    @Nullable
    private static IItemHandler itemHandlerOf(TileEntity te, EnumFacing face) {
        return te == null ? null : te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
    }

    // ===================== 处理中判定 =====================

    /**
     * 三台机器共用的"是否在处理"：进度/mode 归零即空闲；读不到或类型不符按"处理中"处理.
     *
     * <p>注意 {@link #isIdleState} 的"读不到"包含"尚未调用 {@code initReflection}"（此时三个类句柄为
     * {@code null}），因此本策略把这种状态归为"处理中"（保守：不可收集）。生产路径上
     * {@link #isIdle} 一定先做反射初始化，不会走到该分支。</p>
     */
    private static final MachineIdle BEWITCHMENT_IDLE = new MachineIdle() {
        @Override
        public boolean isWorking(World world, BlockPos pos, TileEntity te) {
            return !isIdleState(te);
        }

        @Override
        public int graceTicks() {
            return PUSH_IDLE_GRACE_TICKS;
        }
    };

    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        return BEWITCHMENT_IDLE;
    }

    /**
     * 纯函数：机器当前是否空闲.
     *
     * <p>三台机器的判定合并于此：</p>
     * <ul>
     *   <li>SpinningWheel / Distillery：{@code progress == 0}；字段读失败 → 非空闲；</li>
     *   <li>WitchesCauldron：{@code mode} 为 0/1 或任意非处理中取值 → 空闲；2/3/4/5 → 处理中；</li>
     *   <li>其它 {@code TileEntity}：返回 {@code false}（非空闲）。</li>
     * </ul>
     */
    static boolean isIdleState(@Nullable TileEntity te) {
        if (CLASS_SPINNING_WHEEL != null && CLASS_SPINNING_WHEEL.isInstance(te)) {
            return progressIdle(readInt(te, FIELD_SPINNING_PROGRESS));
        }
        if (CLASS_DISTILLERY != null && CLASS_DISTILLERY.isInstance(te)) {
            return progressIdle(readInt(te, FIELD_DISTILLERY_PROGRESS));
        }
        if (CLASS_CAULDRON != null && CLASS_CAULDRON.isInstance(te)) {
            return cauldronIdle(readInt(te, FIELD_CAULDRON_MODE));
        }
        return false;
    }

    /** 进度判定：{@code 0} 才空闲；读不到（{@code null}）按处理中处理. */
    static boolean progressIdle(@Nullable Integer progress) {
        return progress != null && progress == 0;
    }

    /** 坩埚 mode 判定：2/3/4/5 为处理中，其余（含 0/1 与未知值）为空闲；读不到按处理中处理. */
    static boolean cauldronIdle(@Nullable Integer mode) {
        return mode != null && !isCauldronProcessingMode(mode);
    }

    /** 处理中 mode. */
    static boolean isCauldronProcessingMode(int mode) {
        return mode == 2 || mode == 3 || mode == 4 || mode == 5;
    }

    @Nullable
    private static Integer readInt(TileEntity te, Field field) {
        if (te == null || field == null) {
            return null;
        }
        try {
            return field.getInt(te);
        } catch (Exception e) {
            return null;
        }
    }

    // ===================== canStart =====================

    /**
     * 启动前置条件：SpinningWheel / Distillery 要求 UP 面槽位全空且槽位数不少于
     * 材料数；坩埚要求已沸腾、罐内有流体、内部 inventory 全空。
     */
    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (isUpDownMachine(te)) {
            return canStartUpSide(te, ingredients);
        }
        if (CLASS_CAULDRON.isInstance(te)) {
            return canStartCauldron(te, ingredients);
        }
        return false;
    }

    /** SpinningWheel / Distillery 共用：UP 面 IItemHandler 必须全空且槽位数不少于材料数. */
    private boolean canStartUpSide(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = itemHandlerOf(te, EnumFacing.UP);
        if (up == null) return false;
        for (int i = 0; i < up.getSlots(); i++) {
            if (!up.getStackInSlot(i).isEmpty()) return false;
        }
        int needed = countNonEmpty(ingredients);
        return up.getSlots() >= needed;
    }

    private boolean canStartCauldron(TileEntity te, InventoryCrafting ingredients) {
        try {
            int heatTimer = (int) FIELD_CAULDRON_HEAT_TIMER.get(te);
            if (heatTimer < 5) return false;

            Object tank = FIELD_CAULDRON_TANK.get(te);
            if (tank == null) return false;
            int fluidAmount = (int) tank.getClass().getMethod("getFluidAmount").invoke(tank);
            if (fluidAmount <= 0) return false;

            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv == null) return false;
            for (int i = 0; i < inv.getSlots(); i++) {
                if (!inv.getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int countNonEmpty(InventoryCrafting inv) {
        int count = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) count++;
        }
        return count;
    }

    // ===================== pushMaterials =====================

    /**
     * 推料（不用框架默认的两阶段推料）.
     *
     * <p>与框架默认的差异：每个配方槽只取 <b>1 个</b>物品放进下一个空槽（不按数量拆分、不与已有堆合并）；
     * 坩埚则在写入 inventory 之后立即更新祭坛能量、触发配方匹配生成产物、抽走 1000mb 流体并复位 mode
     * （即"推料即出产物"），这些都不在"往槽位插物品"的表达范围内.</p>
     *
     * <p>因此也不会向会话登记推料台账——本类的 {@link #revertMaterials} 用"整段抽空 UP 面"口径，
     * 不依赖台账.</p>
     */
    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        boolean success = false;
        if (isUpDownMachine(te)) {
            success = pushUpSide(te, ingredients);
        } else if (CLASS_CAULDRON.isInstance(te)) {
            success = pushCauldron(world, pos, te, ingredients);
        }
        if (success && session != null) {
            session.setPushTick(world.getTotalWorldTime());
        }
        return success;
    }

    /** SpinningWheel / Distillery 共用：逐个把材料放到 UP 面的下一个空槽,每槽 1 个. */
    private boolean pushUpSide(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = itemHandlerOf(te, EnumFacing.UP);
        if (up == null) return false;
        int slotIdx = 0;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack single = stack.copy();
            single.setCount(1);
            // 找到下一个空槽,避免合并到已有物品
            while (slotIdx < up.getSlots() && !up.getStackInSlot(slotIdx).isEmpty()) {
                slotIdx++;
            }
            if (slotIdx >= up.getSlots()) return false;
            ItemStack remaining = up.insertItem(slotIdx, single, false);
            if (!remaining.isEmpty()) return false;
            slotIdx++;
        }
        return true;
    }

    private boolean pushCauldron(World world, BlockPos pos, TileEntity te, InventoryCrafting ingredients) {
        try {
            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv == null) return false;

            // 1. 将材料逐个放入 inventory(每个槽位仅单个物品)
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                ItemStack single = stack.copy();
                single.setCount(1);
                int slot = getFirstEmptySlot(inv);
                if (slot < 0) return false;
                inv.setStackInSlot(slot, single);
            }

            // 2. 更新祭坛能量状态
            METHOD_CAULDRON_SET_POWER.invoke(te);

            // 3. 尝试立即触发配方匹配与产物生成
            boolean hasPower = (boolean) FIELD_CAULDRON_HAS_POWER.get(te);
            int mode = (int) FIELD_CAULDRON_MODE.get(te);
            int heatTimer = (int) FIELD_CAULDRON_HEAT_TIMER.get(te);

            // canCraft 条件：有能量、非 mode4、已沸腾
            boolean canCraft = hasPower && mode != 4 && heatTimer >= 5;
            // mode3 为酿造模式,需要额外判断 isBrewItem,自动化普通配方通常不走此分支

            if (canCraft && mode == 0) {
                FIELD_CAULDRON_MODE.set(te, 5);
                FIELD_CAULDRON_CRAFTING_TIMER.set(te, 0);
            }

            if (mode == 0 || mode == 5) {
                // 匹配 CauldronRecipe
                Object recipe = findMatchingCauldronRecipe(inv);
                if (recipe != null) {
                    @SuppressWarnings("unchecked")
                    List<ItemStack> outputs = (List<ItemStack>) FIELD_CAULDRON_RECIPE_OUTPUT.get(recipe);
                    for (ItemStack output : outputs) {
                        if (output.isEmpty()) continue;
                        EntityItem entity = new EntityItem(
                            world,
                            pos.getX() + 0.5,
                            pos.getY() + 1.0,
                            pos.getZ() + 0.5,
                            output.copy()
                        );
                        entity.setNoGravity(true);
                        entity.motionX = 0;
                        entity.motionY = 0;
                        entity.motionZ = 0;
                        world.spawnEntity(entity);
                    }

                    // drain tank 1000mb
                    Object tank = FIELD_CAULDRON_TANK.get(te);
                    if (tank != null) {
                        tank.getClass().getMethod("drain", int.class, boolean.class).invoke(tank, 1000, true);
                    }

                    // 清空 inventory
                    for (int i = 0; i < inv.getSlots(); i++) {
                        inv.setStackInSlot(i, ItemStack.EMPTY);
                    }

                    // 重置 mode
                    FIELD_CAULDRON_MODE.set(te, 0);
                }
            }

            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Bewitchment cauldron push failed", e);
            return false;
        }
    }

    private static Object findMatchingCauldronRecipe(ItemStackHandler inv) {
        try {
            Class<?> registryClass = Class.forName("net.minecraftforge.fml.common.registry.GameRegistry");
            Object registry = registryClass.getMethod("findRegistry", Class.class).invoke(null, CLASS_CAULDRON_RECIPE);
            @SuppressWarnings("unchecked")
            java.util.Collection<Object> recipes = (java.util.Collection<Object>) registry.getClass()
                .getMethod("getValuesCollection").invoke(registry);
            for (Object recipe : recipes) {
                boolean matches = (boolean) METHOD_CAULDRON_RECIPE_MATCHES.invoke(recipe, inv);
                if (matches) return recipe;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to match cauldron recipe", e);
        }
        return null;
    }

    private static int getFirstEmptySlot(ItemStackHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) return i;
        }
        return -1;
    }

    // ===================== 收集 / 清理 / 回退 =====================

    /**
     * 收集产物（抽得出来就全抽）.
     *
     * <p>与框架默认的差异：不按样板预期产物匹配、也不排除本批输入快照内的物品——
     * SpinningWheel / Distillery 的产物位与输入位分属不同面，坩埚的产物直接生成到世界里，
     * 都不会与本批材料混淆，因此不必做产物甄别.</p>
     */
    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (isUpDownMachine(te)) {
            collectDownSide(te, result);
        } else if (CLASS_CAULDRON.isInstance(te)) {
            collectCauldron(world, pos, te, result);
        }
        return result;
    }

    /**
     * 发配前清理：什么都不清.
     *
     * <p><b>不能用框架默认</b>：框架默认会清空 {@link SlotRole#OUTPUT} 槽，对本类就是清掉 DOWN 面的
     * 产物，会把上一轮的残留产物直接送进 ME 网络。</p>
     */
    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return new ArrayList<>();
    }

    /**
     * 回退已推送材料（无条件抽空 UP 面 / 坩埚内部 inventory）.
     *
     * <p>与框架默认的差异：框架只回退"本批材料快照/台账"内的物品，本类整段清空——
     * 推料不登记台账（见 {@link #pushMaterials}），故按整段清空回退.</p>
     */
    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (isUpDownMachine(te)) {
            revertUpSide(te, result);
        } else if (CLASS_CAULDRON.isInstance(te)) {
            try {
                drainCauldronInventory(te, result);
            } catch (Exception ignored) {}
        }
        return result;
    }

    /** SpinningWheel / Distillery 共用：抽取 UP 面全部槽位. */
    private static void revertUpSide(TileEntity te, List<ItemStack> result) {
        IItemHandler up = itemHandlerOf(te, EnumFacing.UP);
        if (up == null) return;
        for (int i = 0; i < up.getSlots(); i++) {
            ItemStack stack = up.extractItem(i, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) result.add(stack);
        }
    }

    /** 取走 WitchesCauldron 内部 inventory 的全部物品(先 copy 再清空槽位). */
    private static void drainCauldronInventory(TileEntity te, List<ItemStack> result) throws Exception {
        ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
        if (inv == null) return;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                result.add(stack.copy());
                inv.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** SpinningWheel / Distillery 共用：抽取 DOWN 面全部槽位. */
    private void collectDownSide(TileEntity te, List<ItemStack> result) {
        IItemHandler down = itemHandlerOf(te, EnumFacing.DOWN);
        if (down == null) return;
        for (int i = 0; i < down.getSlots(); i++) {
            ItemStack stack = down.extractItem(i, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
    }

    private void collectCauldron(World world, BlockPos pos, TileEntity te, List<ItemStack> result) {
        try {
            // 1. 扫描周围 EntityItem(产物或未收集物品)
            AxisAlignedBB aabb = new AxisAlignedBB(pos).grow(1.0);
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, aabb);
            for (EntityItem entity : items) {
                if (entity.isDead) continue;
                ItemStack stack = entity.getItem();
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    entity.setDead();
                }
            }

            // 2. 回收 inventory 残余
            drainCauldronInventory(te, result);

        } catch (Exception ignored) {}
    }

    // ===================== 空闲 / 完成判定 =====================

    /**
     * 可安全收集判定：推料保护期内一律不可收集，之后由 {@link #idleGate} 的进度/mode 信号决定.
     *
     * <p>与框架默认的差异：不看"是否已有可收集产物"；且目标不是这三种机器时返回
     * {@code false}（不可收集）——框架约定的是"目录为 null 即可安全结束"，本类按不可收集处理，
     * 该分支在生产路径上不可达（{@code isValidTarget} 已经要求目录非 null）。</p>
     */
    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!isBewitchmentMachine(te)) {
            return false;
        }
        MachineIdle gate = idleGate(world, pos);
        boolean graceElapsed = session == null
                || session.isPushGraceElapsed(world.getTotalWorldTime(), gate.graceTicks());
        return graceElapsed && !gate.isWorking(world, pos, te);
    }

    /**
     * 完成判定 = {@code IRemoteHandler} 未覆盖时的默认行为（即 {@code return isIdle(...)}）.
     *
     * <p>不能用框架默认（"本批输入已耗尽 + 无产物"）：本类的产物在 DOWN 面或世界里的 EntityItem，
     * 而机器开工时输入槽可能已空、进度仍在跑，框架默认会当场判完成并结束会话.</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        return isIdle(world, pos, inputs, session);
    }

    private static boolean isBewitchmentMachine(TileEntity te) {
        return CLASS_SPINNING_WHEEL.isInstance(te)
            || CLASS_DISTILLERY.isInstance(te)
            || CLASS_CAULDRON.isInstance(te);
    }
}
