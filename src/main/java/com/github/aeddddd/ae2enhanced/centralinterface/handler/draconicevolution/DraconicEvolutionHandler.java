package com.github.aeddddd.ae2enhanced.centralinterface.handler.draconicevolution;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
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
 * Draconic Evolution 聚合核心 + 注入器处理器.
 *
 * <p>结构：中心 FusionCraftingCore,周围16格范围内放置 CraftingInjector.
 * slot 0 → Core 主材料,slot 1+ → Injectors 辅助材料(各1个).</p>
 *
 * <h3>与框架的关系</h3>
 * <p>本类继承通用骨架 {@link AbstractMachineHandler} 并声明表项（{@link #defineSpec}：
 * {@code draconicevolution:fusion_crafting_core}），框架提供生命周期、调度入口与
 * "目录 → {@link ISlotCatalog}" 的统一视图（{@link #catalogFor}：Core 的 0 号槽 = 输入、1 号槽 = 输出），
 * 本类回答"什么时候算处理中"（{@link #idleGate}：{@code isCrafting} / {@code craftingStage}）。</p>
 *
 * <p><b>为什么推料/收集/回退/清理全部保留本类实现</b>：聚合核心的材料分两处落位——
 * 主材料进 Core 的 0 号槽、辅助材料进<b>周围注入器的基座</b>（不在这台机器的库存里，
 * 只能经 {@code ICraftingInjector#getStackInPedestal/setStackInPedestal} 访问，没有
 * {@code IItemHandler} 桥），框架的目录模型表达不了"按顺序找空注入器、每个放 1 个"这条落位语义。
 * 逐条保留与框架默认不同的行为（每处注明差异）：</p>
 * <ul>
 *   <li>{@link #canStart}：重新扫描注入器结构、Core 主槽必须为空、空注入器数不少于辅助材料数；</li>
 *   <li>{@link #pushMaterials}：主材料进 Core 0 号槽、辅助材料按序各 1 个进空注入器，失败时回滚主材料；</li>
 *   <li>{@link #collectProducts}：Core 1 号槽 → 0 号槽 → 注入器基座残余，并把产物的 NBT 归一到样板预期；</li>
 *   <li>{@link #clearOutputs} / {@link #revertMaterials}：同一份"取空 Core 两槽 + 注入器残余"逻辑；</li>
 *   <li>{@link #isIdle} / {@link #hasFinished}：只看"是否在合成"，不带推料保护期、也不看产物是否已出现
 *       ；目标不是聚合核心时返回"不可收集"。</li>
 * </ul>
 *
 * <p>所有第三方类均通过 {@link Class#forName(String)} + 反射访问,不直接 import,
 * 保证 Draconic Evolution 未安装时本类即使被加载也不会触发 {@link NoClassDefFoundError}.</p>
 */
public class DraconicEvolutionHandler extends AbstractMachineHandler {

    private static final String CORE_ID = "draconicevolution:fusion_crafting_core";

    /** Core 的槽位：0 号放主材料（某些配方完成后产物会写回这里），1 号是产物槽. */
    private static final int CORE_MAIN_SLOT = 0;
    private static final int CORE_OUTPUT_SLOT = 1;

    // 反射缓存
    private static Class<?> CLASS_CORE;
    private static Class<?> CLASS_INJECTOR;
    private static Class<?> CLASS_ICRAFTING_INJECTOR;
    private static Class<?> CLASS_MANAGED_BOOL;
    private static Class<?> CLASS_MANAGED_SHORT;
    private static Method METHOD_ATTEMPT_START;
    private static Method METHOD_UPDATE_INJECTORS;
    private static Method METHOD_GET_INJECTORS;
    private static Method METHOD_GET_STACK_IN_PEDESTAL;
    private static Method METHOD_SET_STACK_IN_PEDESTAL;
    private static Field FIELD_IS_CRAFTING;
    private static Field FIELD_CRAFTING_STAGE;
    private static Field FIELD_VALUE_BOOL;
    private static Field FIELD_VALUE_SHORT;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_CORE = Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore");
            CLASS_INJECTOR = Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileCraftingInjector");
            CLASS_ICRAFTING_INJECTOR = Class.forName("com.brandon3055.draconicevolution.api.fusioncrafting.ICraftingInjector");
            CLASS_MANAGED_BOOL = Class.forName("com.brandon3055.brandonscore.lib.datamanager.ManagedBool");
            CLASS_MANAGED_SHORT = Class.forName("com.brandon3055.brandonscore.lib.datamanager.ManagedShort");

            METHOD_ATTEMPT_START = CLASS_CORE.getMethod("attemptStartCrafting");
            METHOD_UPDATE_INJECTORS = CLASS_CORE.getMethod("updateInjectors");
            METHOD_GET_INJECTORS = CLASS_CORE.getMethod("getInjectors");
            METHOD_GET_STACK_IN_PEDESTAL = CLASS_ICRAFTING_INJECTOR.getMethod("getStackInPedestal");
            METHOD_SET_STACK_IN_PEDESTAL = CLASS_ICRAFTING_INJECTOR.getMethod("setStackInPedestal", ItemStack.class);

            FIELD_IS_CRAFTING = CLASS_CORE.getField("isCrafting");
            FIELD_CRAFTING_STAGE = CLASS_CORE.getField("craftingStage");
            FIELD_VALUE_BOOL = CLASS_MANAGED_BOOL.getField("value");
            FIELD_VALUE_SHORT = CLASS_MANAGED_SHORT.getField("value");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] DraconicEvolution reflection init failed", e);
        }
    }

    /** 表项：只匹配聚合核心一个方块，行为描述即本类. */
    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder().exactIds(CORE_ID).access(this).build();
    }

    // ===================== 目录与处理中判定 =====================

    /**
     * 构造目标目录：Core 的 0 号槽 = {@link SlotRole#INPUT}、1 号槽 = {@link SlotRole#OUTPUT}.
     *
     * <p>注入器基座不登记为槽位：它们的访问接口是 {@code ICraftingInjector}（不是 {@code IItemHandler}），
     * 且所有触碰注入器的 IO 方法在本类里都被覆盖了，目录对它们没有可用表达.</p>
     *
     * <p>Core 没有 item capability 时返回空目录而不是 {@code null}：这类目标仍算有效
     * （读写走 {@code func_70301_a} 等兜底分支，见各类覆盖方法）.</p>
     */
    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) {
            return null;
        }
        IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (coreInv == null) {
            return ISlotCatalog.builder().build();
        }
        return ISlotCatalog.builder()
                .add(coreInv, CORE_MAIN_SLOT, null, SlotRole.INPUT)
                .add(coreInv, CORE_OUTPUT_SLOT, null, SlotRole.OUTPUT)
                .build();
    }

    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        return (w, p, te) -> isMachineWorking(te);
    }

    /**
     * "处理中"口径：正在合成，或合成阶段未归零.
     *
     * <p>两个字段都通过 {@link #isCrafting} / {@link #getCraftingStage} 读取，读失败时分别退化为
     * {@code false} / {@code 0}（即"不忙"）.</p>
     */
    static boolean isMachineWorking(@Nullable TileEntity core) {
        return isCrafting(core) || getCraftingStage(core) != 0;
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (isCrafting(te)) return false;

        // 重新加载后 injectors 列表可能为空,先刷新
        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception e) {
            return false;
        }

        // Core slot 0 必须为空
        IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (coreInv == null) {
            // fallback: ISidedInventory
            try {
                ItemStack slot0 = (ItemStack) te.getClass().getMethod("func_70301_a", int.class).invoke(te, 0);
                if (!slot0.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        } else {
            if (!coreInv.getStackInSlot(0).isEmpty()) return false;
        }

        // 检查有足够空 injectors
        List<Object> injectors = getConnectedInjectors(te);
        int needed = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) needed++;
        }
        int empty = 0;
        for (Object injector : injectors) {
            try {
                ItemStack stack = (ItemStack) METHOD_GET_STACK_IN_PEDESTAL.invoke(injector);
                if (stack.isEmpty()) empty++;
            } catch (Exception ignored) {}
        }
        return empty >= needed;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (isCrafting(te)) return false;

        // 刷新 injectors 列表
        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception e) {
            return false;
        }

        List<Object> injectors = getConnectedInjectors(te);
        ItemStack main = ingredients.getStackInSlot(0);

        // 预检：收集需要使用的 injector，确保全部材料都有位置
        List<Object> chosenInjectors = new ArrayList<>();
        int injectorIdx = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            Object chosen = null;
            while (injectorIdx < injectors.size()) {
                Object injector = injectors.get(injectorIdx);
                injectorIdx++;
                try {
                    ItemStack existing = (ItemStack) METHOD_GET_STACK_IN_PEDESTAL.invoke(injector);
                    if (existing.isEmpty()) {
                        chosen = injector;
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (chosen == null) {
                return false; // 辅助材料无处可放
            }
            chosenInjectors.add(chosen);
        }

        // 实际推送，失败时回滚
        boolean mainPushed = false;
        try {
            if (!main.isEmpty()) {
                IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (coreInv != null) {
                    ItemStack inserted = coreInv.insertItem(0, main.copy(), false);
                    if (!inserted.isEmpty()) {
                        return false;
                    }
                } else {
                    try {
                        te.getClass().getMethod("setStackInCore", int.class, ItemStack.class).invoke(te, 0, main.copy());
                    } catch (Exception ex) {
                        return false;
                    }
                }
                mainPushed = true;
            }

            int auxIdx = 0;
            for (int i = 1; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                Object injector = chosenInjectors.get(auxIdx++);
                ItemStack single = stack.copy();
                single.setCount(1);
                METHOD_SET_STACK_IN_PEDESTAL.invoke(injector, single);
            }
            // 推料完成：让 isIdle 的注入器缓存失效，下一批重新扫描一次注入器结构
            invalidateInjectors(session);
            return true;
        } catch (Exception e) {
            invalidateInjectors(session);
            if (mainPushed) {
                // 尽量回滚主材料
                try {
                    IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                    if (coreInv != null) {
                        coreInv.extractItem(0, 64, false);
                    } else {
                        te.getClass().getMethod("setStackInCore", int.class, ItemStack.class).invoke(te, 0, ItemStack.EMPTY);
                    }
                } catch (Exception ignored) {}
            }
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        try {
            METHOD_ATTEMPT_START.invoke(te);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 可安全收集判定（保留旧口径）.
     *
     * <p>与框架默认的差异：只看"是否在合成"（{@code isCrafting}/{@code craftingStage}），
     * <b>不带推料保护期、也不要求"产物已出现"</b>。DE 的产物可能落在 Core 的 0 号槽
     * （角色是 INPUT）或注入器基座上——框架的"可收集产物"判据完全看不见它们，套用框架默认会让会话
     * 一直判不到空闲而空等到超时。</p>
     *
     * <p>目标不是聚合核心时返回 {@code false}（不可收集）：与框架"目录为 null 即可安全结束"
     * 的约定不同；该分支在生产路径上不可达（{@code isValidTarget} 已要求目标有效）.</p>
     */
    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        // updateInjectors 会扫描 16 格半径内的三片方块（约 900 次 getTileEntity）并重建注入器列表。
        // isIdle 每 tick 调用一次，但注入器列表只随结构变化，所以按 session 缓存：
        // 同一批次内只刷新一次，推料后失效（见 pushMaterials），解绑/重绑时由 TargetSession.reset 清空。
        ensureInjectorsRefreshed(te, session);
        return !idleGate(world, pos).isWorking(world, pos, te);
    }

    /**
     * 完成判定 = {@code IRemoteHandler} 未覆盖时的默认行为（即 {@code return isIdle(...)}）.
     *
     * <p>不能用框架默认（"本批输入已耗尽 + 无产物"）：本类的材料在注入器基座上、产物可能落在
     * Core 的 0 号槽（角色 INPUT），框架默认会在合成结束后仍判"未完成"或反向提前结束.</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        return isIdle(world, pos, inputs, session);
    }

    /**
     * 保证本 session 内的注入器列表已刷新过（每 session 至多一次真实扫描）.
     *
     * <p>缓存写入 {@link TargetSession#getHandlerCache()}，不持久化；session 重置（含解绑）时自动清空。
     * 无 session 时退回"每次刷新"。</p>
     */
    private static void ensureInjectorsRefreshed(TileEntity core, TargetSession session) {
        if (session == null) {
            refreshInjectors(core);
            return;
        }
        if (session.getHandlerCache() == null) {
            session.setHandlerCache(refreshInjectors(core));
        }
    }

    /**
     * 反射刷新注入器列表并返回（{@code updateInjectors} + {@code getInjectors}）.
     *
     * <p>异常与 {@link #getConnectedInjectors} 一致地吞掉：与 isIdle 判定无关的辅助刷新，
     * 不应因为第三方 mod 结构变化把异常抛到网格 tick。</p>
     */
    private static List<Object> refreshInjectors(TileEntity core) {
        try {
            METHOD_UPDATE_INJECTORS.invoke(core);
        } catch (Exception ignored) {
        }
        return getConnectedInjectors(core);
    }

    /** 使 isIdle 使用的注入器缓存失效（推料后调用；无 session 时无操作）. */
    private static void invalidateInjectors(TargetSession session) {
        if (session != null) {
            session.clearHandlerCache();
        }
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;
        if (isCrafting(te)) return result;
        // 重新加载后 injectors 列表可能为空,先刷新
        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception ignored) {}

        // 从 Core 提取产物(slot 0 或 slot 1)
        IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (coreInv != null) {
            // 先尝试 slot 1(输出槽)
            ItemStack out1 = coreInv.extractItem(1, 64, false);
            if (!out1.isEmpty()) result.add(out1);
            // 再检查 slot 0(某些配方可能替换主材料)
            ItemStack out0 = coreInv.extractItem(0, 64, false);
            if (!out0.isEmpty()) result.add(out0);
        } else {
            // fallback: 先 slot 1(输出槽),再 slot 0(某些配方可能替换主材料)
            extractCoreSlotsFallback(te, new int[]{1, 0}, result);
        }

        // 清理 injectors 中的残余
        drainInjectorResiduals(te, result);

        // 龙研聚合产物 NBT 可能与样板预期不一致，按 item+meta 匹配后回写预期 NBT，
        // 使 AE2 CPU 的 waitingFor 精确匹配能够完成合成。
        normalizeToExpectedOutputs(result, expectedOutputs);
        return result;
    }

    private static void normalizeToExpectedOutputs(List<ItemStack> products, IAEItemStack[] expectedOutputs) {
        if (expectedOutputs == null || products == null) {
            return;
        }
        for (ItemStack product : products) {
            if (product.isEmpty()) continue;
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null || expected.getStackSize() <= 0) continue;
                ItemStack expectedStack = expected.createItemStack();
                if (expectedStack.isEmpty()) continue;
                if (ItemStack.areItemsEqual(product, expectedStack) && expectedStack.hasTagCompound()) {
                    product.setTagCompound(expectedStack.getTagCompound().copy());
                    break;
                }
            }
        }
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllFromTarget(world, pos);
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllFromTarget(world, pos);
    }

    private List<ItemStack> collectAllFromTarget(World world, BlockPos pos) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;

        IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (coreInv != null) {
            for (int i = 0; i < coreInv.getSlots(); i++) {
                ItemStack stack = coreInv.extractItem(i, 64, false);
                if (!stack.isEmpty()) result.add(stack);
            }
        } else {
            extractCoreSlotsFallback(te, new int[]{0, 1}, result);
        }

        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception ignored) {}
        drainInjectorResiduals(te, result);
        return result;
    }

    /** 无 capability 时的兜底提取：按给定槽位顺序通过 ISidedInventory 混淆名读写. */
    private static void extractCoreSlotsFallback(TileEntity core, int[] slots, List<ItemStack> result) {
        try {
            for (int slot : slots) {
                ItemStack stack = (ItemStack) core.getClass().getMethod("func_70301_a", int.class).invoke(core, slot);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    core.getClass().getMethod("func_70299_a", int.class, ItemStack.class).invoke(core, slot, ItemStack.EMPTY);
                }
            }
        } catch (Exception ignored) {}
    }

    /** 回收全部 CraftingInjector 基座上的残余. */
    private static void drainInjectorResiduals(TileEntity core, List<ItemStack> result) {
        for (Object injector : getConnectedInjectors(core)) {
            try {
                ItemStack residual = (ItemStack) METHOD_GET_STACK_IN_PEDESTAL.invoke(injector);
                if (!residual.isEmpty()) {
                    METHOD_SET_STACK_IN_PEDESTAL.invoke(injector, ItemStack.EMPTY);
                    result.add(residual);
                }
            } catch (Exception ignored) {}
        }
    }

    private static boolean isCrafting(TileEntity core) {
        try {
            Object managedBool = FIELD_IS_CRAFTING.get(core);
            return (boolean) FIELD_VALUE_BOOL.get(managedBool);
        } catch (Exception e) {
            return false;
        }
    }

    private static short getCraftingStage(TileEntity core) {
        try {
            Object managedShort = FIELD_CRAFTING_STAGE.get(core);
            return (short) FIELD_VALUE_SHORT.get(managedShort);
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getConnectedInjectors(TileEntity core) {
        try {
            return (List<Object>) METHOD_GET_INJECTORS.invoke(core);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
