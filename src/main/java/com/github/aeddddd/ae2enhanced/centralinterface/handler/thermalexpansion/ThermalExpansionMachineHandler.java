package com.github.aeddddd.ae2enhanced.centralinterface.handler.thermalexpansion;

import appeng.api.networking.security.IActionSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.FluidTransferHelper;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.CatalogBuilder;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineStorageAccess;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Thermal Expansion 机器远程处理器.
 *
 * <p>通过反射直接访问 CoFH {@code TileInventory#inventory} 字段，
 * 绕过 Thermal Expansion 机器的 SideCache 配置限制,避免因为配置面导致中枢 ME 接口无法输入输出。</p>
 *
 * <p>同时读取 {@code TileReconfigurable#slotConfig} 中的
 * {@code allowInsertionSlot}/{@code allowExtractionSlot}, 精确识别输入/输出槽,
 * 避免在输入槽解锁时将材料错误写入输出槽或升级槽。</p>
 *
 * <p>本类继承通用骨架 {@link AbstractMachineHandler}（并因此实现 {@link MachineStorageAccess}）：
 * 库存访问抽象为 {@link ISlotCatalog}（{@link #catalogFor}）,
 * 材料的原子推送、两阶段收集、按本批快照回退、输出槽清理与"产物已出现"判定全部由框架提供。
 * 本类只保留三处 TE 特有语义：角色映射（见 {@link #slotRoles}）、
 * "处理中"判定（见 {@link #idleGate}）以及"流体罐非空即视为可收集"（见 {@link #isIdle} / {@link #hasFinished}）。</p>
 *
 * <p>blockId 匹配规则声明在表项里（{@link #defineSpec}：{@code thermalexpansion:} 前缀），
 * {@code canHandle} 与 {@code HandlerRegistry} 的两级索引都从该表项取值.</p>
 *
 * <p><b>反射隔离</b>：TE / CoFH 的类一律以 {@code Class.forName} + 静态 {@link Field} 缓存访问,
 * 本类不 import 也不持有任何第三方类型（{@link InventoryArrayItemHandler} 只面对
 * {@link IInventory} 与 {@code ItemStack[]}）。</p>
 */
public class ThermalExpansionMachineHandler extends AbstractMachineHandler {

    private static final boolean AVAILABLE;
    private static Class<?> TILE_INVENTORY_CLASS;
    private static Class<?> TILE_MACHINE_BASE_CLASS;
    private static Class<?> SLOT_CONFIG_CLASS;
    private static Field INVENTORY_FIELD;
    private static Field PROCESS_REM_FIELD;
    private static Field SLOT_CONFIG_FIELD;
    private static Field ALLOW_INSERTION_SLOT_FIELD;
    private static Field ALLOW_EXTRACTION_SLOT_FIELD;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("thermalexpansion")) {
                TILE_INVENTORY_CLASS = Class.forName("cofh.core.block.TileInventory");
                // inventory 为 protected 字段,必须使用 getDeclaredField + setAccessible
                INVENTORY_FIELD = TILE_INVENTORY_CLASS.getDeclaredField("inventory");
                INVENTORY_FIELD.setAccessible(true);
                try {
                    TILE_MACHINE_BASE_CLASS = Class.forName("cofh.thermalexpansion.block.machine.TileMachineBase");
                    PROCESS_REM_FIELD = TILE_MACHINE_BASE_CLASS.getDeclaredField("processRem");
                    PROCESS_REM_FIELD.setAccessible(true);
                } catch (ClassNotFoundException | NoSuchFieldException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] ThermalExpansion processRem support not available");
                }
                try {
                    SLOT_CONFIG_CLASS = Class.forName("cofh.core.util.core.SlotConfig");
                    Class<?> tileReconfigurableClass = Class.forName("cofh.core.block.TileReconfigurable");
                    SLOT_CONFIG_FIELD = tileReconfigurableClass.getDeclaredField("slotConfig");
                    SLOT_CONFIG_FIELD.setAccessible(true);
                    ALLOW_INSERTION_SLOT_FIELD = SLOT_CONFIG_CLASS.getField("allowInsertionSlot");
                    ALLOW_EXTRACTION_SLOT_FIELD = SLOT_CONFIG_CLASS.getField("allowExtractionSlot");
                } catch (ClassNotFoundException | NoSuchFieldException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] ThermalExpansion SlotConfig support not available");
                }
                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Thermal Expansion handler", e);
        }
        AVAILABLE = available;
    }

    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder().prefixes("thermalexpansion:").access(this).build();
    }

    /**
     * 用反射取得的内部库存数组构造目录.
     *
     * <p>不使用侧面 capability：TE 机器的侧面可被玩家配置为"无",此时 capability 层看不到任何槽位,
     * 而中枢接口的语义是"把材料送进这台机器",因此这里始终读取机器自身的内部库存。</p>
     *
     * <p>零槽位（纯流体机器, 如流体缓冲器/岩浆发电机）仍返回目录而不是 {@code null}：
     * 其流体产物由 {@link FluidTransferHelper#collectFluidProducts} 接管; 返回 null 会把目标
     * 降级为 UNAVAILABLE, 反而丢掉这条能力。</p>
     */
    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        // 反射初始化失败时（类/字段缺失）直接拒绝,避免 NPE 逃逸导致服务端 tick 崩溃
        if (!AVAILABLE || TILE_INVENTORY_CLASS == null || world == null || pos == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof IInventory) || !TILE_INVENTORY_CLASS.isInstance(te)) {
            return null;
        }
        ItemStack[] inventory = inventoryArrayOf(te);
        if (inventory == null) {
            return null;
        }
        return catalogOf((IInventory) te, inventory, insertionFlagsOf(te), extractionFlagsOf(te));
    }

    /**
     * 由反射结果构造目录（纯装配, 不含反射调用, 便于单测直接驱动）.
     *
     * @param inv              机器自身（提供 {@code isItemValidForSlot} 与 {@code markDirty}）
     * @param inventory        反射得到的内部库存数组
     * @param insertionFlags   {@code slotConfig.allowInsertionSlot}, 缺失时为 null
     * @param extractionFlags  {@code slotConfig.allowExtractionSlot}, 缺失时为 null
     */
    static ISlotCatalog catalogOf(IInventory inv, ItemStack[] inventory,
                                  boolean[] insertionFlags, boolean[] extractionFlags) {
        if (inv == null || inventory == null) {
            return null;
        }
        CatalogBuilder builder = ISlotCatalog.builder();
        // face 传 null：这是机器内部数组视图,没有"哪个面"的语义
        builder.addAll(new InventoryArrayItemHandler(inv, inventory), null,
                slotRoles(inventory.length, inv.getSizeInventory(), insertionFlags, extractionFlags));
        return builder.build();
    }

    /**
     * 槽位角色映射（纯函数）.
     *
     * <p>槽位判据沿用各次遍历所用的口径,不"顺手修正"它的宽松处：</p>
     * <ul>
     *   <li>输入槽 = {@code insertionFlags[slot]}（下标越界或标志缺失时退化为"前 2/3 槽位"）,
     *       这是推料的唯一目标；</li>
     *   <li>输出槽 = {@code extractionFlags[slot]}（同样可退化）<b>或</b>落在后 1/3。
     *       "或后 1/3"来自 {@code isIdle}/{@code collectProducts} 里的
     *       {@code isOutputSlot(...) || isProbablyOutputSlotFallback(...)}：该启发式并未被
     *       "标志可用"所抑制, 因此充电/电池槽等既不可插也不可取的尾部槽位
     *       同样会被当作可收集产物。这里照实标注为 {@link SlotRole#OUTPUT}；代价是共用的
     *       {@code clearOutputs} 也会清这些槽位（只认抽取标志的 clearOutputs 不含该启发式）。</li>
     *   <li>其余 → {@link SlotRole#UNKNOWN}（对它们既不推料也不收集）。</li>
     * </ul>
     *
     * <p><b>同时可插可取的槽位取 INPUT</b>：TE 的顺序制造机（Sequential Fabricator）18 个合成网格槽
     * 两个标志都为 true, 而角色模型一槽只能一个角色。取 INPUT 保住"材料能推入"与
     * "本批材料未消耗完不算完成"；取 OUTPUT 会让该机器一个推料目标都没有, 直接不可用。</p>
     *
     * @param slotCount    内部数组长度（= 目录槽位数, 各遍历的上界）
     * @param declaredSize {@code IInventory#getSizeInventory()}, 启发式的基数。
     *                     多数机器等于数组长度, 但顺序制造机为 20（数组 30）、
     *                     词典/因素分解机为 2（数组 11/2）, 正是用它算 2/3
     */
    static SlotRole[] slotRoles(int slotCount, int declaredSize,
                                boolean[] insertionFlags, boolean[] extractionFlags) {
        SlotRole[] roles = new SlotRole[slotCount];
        for (int slot = 0; slot < slotCount; slot++) {
            if (isInputSlot(declaredSize, insertionFlags, slot)) {
                roles[slot] = SlotRole.INPUT;
            } else if (isOutputSlot(declaredSize, extractionFlags, slot) || isTailThird(declaredSize, slot)) {
                roles[slot] = SlotRole.OUTPUT;
            } else {
                roles[slot] = SlotRole.UNKNOWN;
            }
        }
        return roles;
    }

    /** {@code isInputSlot}：标志可用且下标在范围内时取标志,否则退化为"前 2/3"。 */
    static boolean isInputSlot(int declaredSize, boolean[] insertionFlags, int slot) {
        if (insertionFlags != null && slot >= 0 && slot < insertionFlags.length) {
            return insertionFlags[slot];
        }
        return slot < declaredSize * 2 / 3;
    }

    /** {@code isOutputSlot}：标志可用且下标在范围内时取标志,否则退化为"后 1/3"。 */
    static boolean isOutputSlot(int declaredSize, boolean[] extractionFlags, int slot) {
        if (extractionFlags != null && slot >= 0 && slot < extractionFlags.length) {
            return extractionFlags[slot];
        }
        return isTailThird(declaredSize, slot);
    }

    /** {@code isProbablyOutputSlotFallback}。 */
    static boolean isTailThird(int declaredSize, int slot) {
        return slot >= declaredSize * 2 / 3;
    }

    /**
     * 机器"正在处理"判定：{@code TileMachineBase#processRem > 0}.
     *
     * <p>发电机/装置等非机器类型的 TE 没有这个字段, 读它会抛
     * {@link IllegalArgumentException}, {@link #isProcessing} 吞掉后返回 false。</p>
     */
    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        return (w, p, te) -> isProcessing(te);
    }

    /**
     * 空闲判定：先处理中 → 再流体罐 → 最后才看槽位产物.
     *
     * <p>为什么必须覆盖框架默认：只要机器<b>任意</b>罐内有流体就判定空闲（流体产物不由槽位承载,
     * 如离心机/坩埚/精炼机/岩浆发电机）, 框架不知道流体罐的存在。顺序同样重要：
     * "处理中"优先于"有流体", 否则正在消耗输入罐里那批流体的机器会被当成空闲去收产物。</p>
     */
    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        if (catalogFor(world, pos) == null) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        // 流体产物先于"处理中"判定。熔炉类机器的流体是连续产出，只要有料就一直在处理，
        // 若等它完全停下才允许收集，成品会长期锁在罐里直到会话超时回退（而回退不抽流体）。
        // 输入罐不会因此被误收：抽取判据要求罐声明 canDrain，而 TE 的输入罐是可填不可抽。
        if (hasTankFluid(te)) {
            return true;
        }
        if (isProcessing(te)) {
            return false;
        }
        return super.isIdle(world, pos, inputs, session);
    }

    /**
     * 完成判定：处理中或罐内有流体都不算完成.
     *
     * <p>为什么必须覆盖框架默认：框架默认只看槽位（本批输入是否耗尽 + 是否还有可收集产物）,
     * 会把"输入已进内部进度缓冲、输出罐还留着上一批流体"的机器提前判为完成并关闭会话,
     * 残留流体会一直卡在机器里。</p>
     */
    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        if (catalogFor(world, pos) == null) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (isProcessing(te)) {
            return false;
        }
        if (hasTankFluid(te)) {
            return false;
        }
        return super.hasFinished(world, pos, inputs, session);
    }

    @Override
    public List<FluidStack> clearOutputFluids(World world, BlockPos pos, IActionSource source, TargetSession session, List<FluidStack> batchFluids) {
        // 按 TE 自身声明的罐模式抽取：输出罐(canDrain == true)会被回收，
        // 输入罐(如植物催生器的水罐、岩浆挤出机的岩浆罐)不会被碰。
        return FluidTransferHelper.drainExtractableFluids(world, pos, batchFluids);
    }

    // ---- 反射访问 ----

    private static ItemStack[] inventoryArrayOf(TileEntity te) {
        try {
            return (ItemStack[]) INVENTORY_FIELD.get(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get ThermalExpansion inventory field", e);
            return null;
        }
    }

    private static boolean[] insertionFlagsOf(TileEntity te) {
        return slotConfigArray(te, ALLOW_INSERTION_SLOT_FIELD);
    }

    private static boolean[] extractionFlagsOf(TileEntity te) {
        return slotConfigArray(te, ALLOW_EXTRACTION_SLOT_FIELD);
    }

    private static boolean[] slotConfigArray(TileEntity te, Field arrayField) {
        if (SLOT_CONFIG_FIELD == null || arrayField == null) {
            return null;
        }
        try {
            Object slotConfig = SLOT_CONFIG_FIELD.get(te);
            if (slotConfig == null) {
                return null;
            }
            return (boolean[]) arrayField.get(slotConfig);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get ThermalExpansion slot config", e);
            return null;
        }
    }

    private static boolean isProcessing(TileEntity te) {
        if (PROCESS_REM_FIELD == null || te == null) {
            return false;
        }
        try {
            int processRem = PROCESS_REM_FIELD.getInt(te);
            return processRem > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 目标是否有非空流体罐（{@code getTankFluid(te) != null}）。 */
    private static boolean hasTankFluid(TileEntity te) {
        // 不用反射:部分 TE 机器(TileRefinery/TileTransposer 等)声明了返回客户端类
        // TextureAtlasSprite 的 getTexture 方法,getMethod 解析方法签名会在
        // Dedicated Server 上抛 NoClassDefFoundError.改用 Forge 流体 Capability.
        if (te == null || !te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
            return false; // 该机器没有 tank
        }
        IFluidHandler handler = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
        if (handler == null) {
            return false;
        }
        // 只要存在非空储罐即视为有流体,全空则否
        IFluidTankProperties[] properties = handler.getTankProperties();
        if (properties == null) {
            return false;
        }
        for (IFluidTankProperties props : properties) {
            if (props == null) {
                continue;
            }
            FluidStack contents = props.getContents();
            if (contents != null && contents.amount > 0) {
                return true;
            }
        }
        return false;
    }
}
