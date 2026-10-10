package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidCompat;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 目标机器的流体 IO 助手.
 *
 * <p>从 {@link DualityCentralInterface} 抽出的纯静态逻辑：流体假物品推送、产物收集（两阶段）
 * 与发配前的输出罐探察/回收。不持有任何状态，全部通过参数传递。</p>
 *
 * <p>填充尝试顺序：null（内部 tank）优先，然后按 UP/DOWN/NORTH/SOUTH/WEST/EAST
 * 逐个尝试，第一个能完整接受流体的面即被使用，避免向多个 face 重复推送或污染输出槽。</p>
 *
 * <p>抽取一律走真实面并按机器声明的罐模式（{@link IFluidTankProperties#canDrain()}）
 * 判断，不使用 null 面——null 面会绕过机器的侧面配置模式。</p>
 *
 * <p>本类不提供“抽取回退”能力：从目标抽出的流体没有回注 AE 网络的通道，
 * 发配失败时流体一律保留在目标内，由调用方记录（见
 * {@code PhysicalDispatcher#logPushedFluids}）。</p>
 *
 * <p><b>本类没有“转成物品”与“就地销毁”两条出路</b>：调用方必须先用
 * {@link #peekExtractableFluids} 探察、确认流体送得走（ME 流体通道的模拟注入），
 * 送不走就不要抽——流体留在机器里，或由调用方留在接口流体缓冲里等通道能收时再送。</p>
 */
public final class FluidTransferHelper {

    private FluidTransferHelper() {
    }

    /** 流体填充尝试顺序：null（内部 tank）优先，然后六面。 */
    private static final List<EnumFacing> FLUID_FACE_ORDER;
    /**
     * 流体抽取尝试顺序：仅真实面。
     *
     * <p>抽取绝不能传 null 面——TE 的机器把 {@code from == null} 当作“绕过侧面配置”，
     * 用 null 面抽取会无视玩家设置的侧面模式。</p>
     */
    private static final List<EnumFacing> FLUID_DRAIN_FACE_ORDER;
    static {
        List<EnumFacing> list = new ArrayList<>();
        list.add(null);
        list.add(EnumFacing.UP);
        list.add(EnumFacing.DOWN);
        list.add(EnumFacing.NORTH);
        list.add(EnumFacing.SOUTH);
        list.add(EnumFacing.WEST);
        list.add(EnumFacing.EAST);
        FLUID_FACE_ORDER = Collections.unmodifiableList(list);

        List<EnumFacing> drainList = new ArrayList<>();
        drainList.add(EnumFacing.UP);
        drainList.add(EnumFacing.DOWN);
        drainList.add(EnumFacing.NORTH);
        drainList.add(EnumFacing.SOUTH);
        drainList.add(EnumFacing.WEST);
        drainList.add(EnumFacing.EAST);
        FLUID_DRAIN_FACE_ORDER = Collections.unmodifiableList(drainList);
    }

    /**
     * 将 table 中的流体假物品推送到目标的 IFluidHandler.
     * CPU 已事先将物品提取到 table 中,此处只做转换与推送.
     * 推送成功后,将对应槽位从 table 中清空,防止后续 handler.pushMaterials 再次处理.
     */
    public static boolean pushFluidInputs(World world, BlockPos pos, InventoryCrafting table, List<FluidStack> pushedFluids) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return true;

        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 只处理流体假物品（AE2E / ae2fc 的 FluidDrop / FluidPacket）。
            // 真实的流体容器（如水桶）应作为普通物品推送给机器，由机器自行处理空容器返还；
            // 否则这里会把容器直接抽干并清空槽位，导致空容器丢失。
            if (!Ae2fcFluidCompat.isAnyFluidFakeItem(stack)) continue;

            FluidStack fluid = Ae2fcFluidCompat.getFluidStack(stack);
            if (fluid == null) {
                // 兼容 ae2fc 的 ItemFluidPacket(旧版 fallback)
                String itemClass = stack.getItem().getClass().getName();
                if ("com.glodblock.github.common.item.ItemFluidPacket".equals(itemClass)) {
                    fluid = FakeFluids.unpackAe2fcFluid(stack);
                }
            }
            if (fluid == null) continue;

            boolean pushed = false;
            for (EnumFacing face : FLUID_FACE_ORDER) {
                if (tryFillFluidHandler(te, face, fluid, pushedFluids)) {
                    FluidFlowDiag.logAt(FluidFlowDiag.PUSH_INPUT, fluid, blockIdOf(te), pos,
                            "pushed (batch fluid input)", "face=" + (face == null ? "null(internal)" : face.name()));
                    pushed = true;
                    break;
                }
            }

            if (!pushed) {
                return false;
            }

            // 从 table 中移除已推送的流体,避免 handler.pushMaterials 再次尝试插入 ItemFluidDrop
            table.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        return true;
    }

    private static boolean tryFillFluidHandler(TileEntity te, EnumFacing face, FluidStack fluid, List<FluidStack> pushedFluids) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        int filled = fh.fill(fluid, false);
        if (filled >= fluid.amount) {
            fh.fill(fluid, true);
            pushedFluids.add(fluid.copy());
            return true;
        }
        return false;
    }

    /**
     * 把流体重新推回目标机器的 tank，语义是“撤销误抽”.
     *
     * <p><b>只走真实面</b>（{@link #FLUID_DRAIN_FACE_ORDER}），与本类其它抽取路径保持同一约定：
     * 传 null 面会被 TE 之类的机器当作"绕过侧面配置"，把产物灌进玩家明确标记为输入的罐，
     * 随后被机器当原料消耗掉——那是比丢弃更隐蔽的销毁。</p>
     *
     * <p>产物收集的溢出不走这里：机器可能把产物当原料吃掉。输出罐 {@code fill()} 恒返回 0 的机器
     * （如 TE 熔岩炉）也推不回去，调用方据此改放接口流体缓冲。</p>
     *
     * @return 未能成功推回目标而剩余的流体
     */
    public static List<FluidStack> pushFluidsToTarget(World world, BlockPos pos, List<FluidStack> fluids) {
        List<FluidStack> remaining = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            for (FluidStack f : fluids) {
                if (f != null && f.amount > 0) remaining.add(f.copy());
            }
            return remaining;
        }
        for (FluidStack fluid : fluids) {
            if (fluid == null || fluid.amount <= 0) continue;
            boolean pushed = false;
            List<FluidStack> dummy = new ArrayList<>();
            for (EnumFacing face : FLUID_DRAIN_FACE_ORDER) {
                if (tryFillFluidHandler(te, face, fluid, dummy)) {
                    FluidFlowDiag.logAt(FluidFlowDiag.PUSHBACK_OK, fluid, blockIdOf(te), pos,
                            "pushed", "back into target face=" + face.name());
                    pushed = true;
                    break;
                }
            }
            if (!pushed) {
                FluidFlowDiag.logAt(FluidFlowDiag.PUSHBACK_FAIL, fluid, blockIdOf(te), pos,
                        "target refused");
                remaining.add(fluid.copy());
            }
        }
        return remaining;
    }

    /** 方块的注册名，仅用于诊断日志；取不到时返回 {@code null}. */
    private static String blockIdOf(TileEntity te) {
        if (te == null || te.getBlockType() == null || te.getBlockType().getRegistryName() == null) {
            return null;
        }
        return te.getBlockType().getRegistryName().toString();
    }

    /**
     * 从目标的 IFluidHandler 收集流体产物.
     *
     * <p>返回值一律是 FluidStack，不出现流体假物品；调用方负责把这些流体送进 ME 流体通道
     * （收不下的部分放接口流体缓冲，见 {@code PhysicalDispatcher#retainFluids}）。</p>
     *
     * <p>两阶段收集，抽取一律走真实面（null 面会绕过机器的侧面配置模式）：</p>
     * <ol>
     *   <li>按本批预期产物精确收集。这是样板声明的产物，优先级高于机器自身的罐模式，
     *       即使机器把该罐标为不可抽取也照样收取；</li>
     *   <li>兜底收集剩余的、机器声明为可抽取（{@code canDrain}）且不属于本批输入流体的内容。</li>
     * </ol>
     */
    public static List<FluidStack> collectFluidProducts(World world, BlockPos pos, TargetSession session) {
        List<FluidStack> fluids = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return fluids;

        String blockId = session != null && session.getBinding() != null ? session.getBinding().blockId : null;
        if (blockId == null) {
            blockId = blockIdOf(te);
        }
        IAEItemStack[] expectedOutputs = session != null ? session.getExpectedOutputs() : null;
        List<FluidStack> inputFluids = session != null ? session.getInputFluids() : Collections.emptyList();

        // 阶段 1：按预期产物精确收集
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null || expected.getStackSize() <= 0) continue;
                ItemStack stack = expected.createItemStack();
                FluidStack expectedFluid = extractFluidFromItemStack(stack);
                if (expectedFluid == null) {
                    // 样板产物不是流体假物品（或无法解析）→ 阶段 1 对这条产物无事可做，
                    // 由阶段 2 的 canDrain 扫描兜住。这里留痕，便于区分"解析不出来"与"机器里没有"。
                    FluidFlowDiag.log(FluidFlowDiag.COLLECT_EXPECTED, "-",
                            "pattern output {} could not be parsed as a fluid (phase-1 skipped for it)",
                            stack.isEmpty() ? "empty" : stack.getItem().getRegistryName());
                    continue;
                }

                for (EnumFacing face : FLUID_DRAIN_FACE_ORDER) {
                    if (tryCollectExpectedFluid(te, face, expectedFluid, inputFluids, fluids)) {
                        FluidFlowDiag.logAt(FluidFlowDiag.COLLECT_EXPECTED, expectedFluid, blockId, pos,
                                "drained (pattern-declared product)", "face=" + face);
                        break;
                    }
                }
            }
        }

        // 阶段 2：兜底收集机器声明为可抽取的剩余非输入流体（主要面向 TE 流体产物机器）
        for (EnumFacing face : FLUID_DRAIN_FACE_ORDER) {
            if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) continue;
            IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
            if (fh == null) continue;
            collectRemainingFluid(fh, inputFluids, fluids, FluidFlowDiag.COLLECT_SCAN, blockId, pos, face, false);
        }

        return fluids;
    }

    /**
     * 按目标机器自身声明的罐模式抽取流体，用于发配前清理输出罐残留。
     *
     * <p>仅通过真实面访问，且只抽取 {@link IFluidTankProperties#canDrain()} 为 true 的罐：
     * 声明为不可抽取的输入罐（如 TE 植物催生器的水罐、岩浆挤出机的岩浆罐）不会被碰，
     * {@code skipFluids} 中列出的本批流体同样不会被抽走。</p>
     *
     * <p><b>调用方必须先确认这些流体送得走</b>（{@link #peekExtractableFluids} 探察 +
     * ME 流体通道的模拟注入）：本方法一旦返回，流体就已经离开机器，而<b>输出罐
     * {@code fill()} 恒返回 0 的机器（如 TE 熔岩炉）接不回自己的产物</b>。</p>
     *
     * @param skipFluids 本批将要推送的流体，不得被回收
     * @return 回收到的流体列表
     */
    public static List<FluidStack> drainExtractableFluids(World world, BlockPos pos, List<FluidStack> skipFluids) {
        return scanExtractableFluids(world, pos, skipFluids, false);
    }

    /**
     * 列出 {@link #drainExtractableFluids} 会抽走的流体，<b>不修改机器</b>。
     *
     * <p>选罐判据与真实抽取完全一致（同一批罐、同一 {@code canDrain} 闸门、同一
     * {@code skipFluids} 过滤，且用 {@code drain(..., false)} 探察机器真实放行的量），
     * 因此调用方可以据此"先模拟、确认送得走，再真抽"。</p>
     *
     * <p>探察不抽取，所以同一个罐若在多个面上都声明可抽取，会在结果里出现多条等量条目；
     * 逐条判定准入时结论不变（等量条目的模拟结果相同）。</p>
     *
     * @param skipFluids 本批将要推送的流体，不算作可回收的残留
     * @return 会被回收的流体列表（探察结果，机器内容不变）
     */
    public static List<FluidStack> peekExtractableFluids(World world, BlockPos pos, List<FluidStack> skipFluids) {
        return scanExtractableFluids(world, pos, skipFluids, true);
    }

    /**
     * 扫描目标的可抽取罐。
     *
     * @param simulate true 时只探察（不抽），false 时真实抽取
     */
    private static List<FluidStack> scanExtractableFluids(World world, BlockPos pos, List<FluidStack> skipFluids,
                                                          boolean simulate) {
        List<FluidStack> drainedFluids = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return drainedFluids;

        List<FluidStack> skip = skipFluids != null ? skipFluids : Collections.emptyList();
        for (EnumFacing face : FLUID_DRAIN_FACE_ORDER) {
            if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) continue;
            IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
            if (fh == null) continue;
            collectRemainingFluid(fh, skip, drainedFluids, FluidFlowDiag.PRE_CLEAN, blockIdOf(te), pos, face, simulate);
        }
        return drainedFluids;
    }

    /**
     * 读取样板表中本批将要推送的流体。
     *
     * <p>用于在推送之前区分“本批输入流体”与“机器内残留流体”。</p>
     */
    public static List<FluidStack> peekFluidInputs(InventoryCrafting table) {
        List<FluidStack> fluids = new ArrayList<>();
        if (table == null) return fluids;
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            FluidStack fluid = extractFluidFromItemStack(stack);
            if (fluid != null && fluid.amount > 0) {
                fluids.add(fluid.copy());
            }
        }
        return fluids;
    }

    private static FluidStack extractFluidFromItemStack(ItemStack stack) {
        if (!Ae2fcFluidCompat.isAnyFluidFakeItem(stack)) {
            return null;
        }
        FluidStack fluid = Ae2fcFluidCompat.getFluidStack(stack);
        if (fluid != null) {
            return fluid;
        }
        String itemClass = stack.getItem().getClass().getName();
        if ("com.glodblock.github.common.item.ItemFluidPacket".equals(itemClass)) {
            return FakeFluids.unpackAe2fcFluid(stack);
        }
        return null;
    }

    /**
     * 阶段 1 的单面判定：按样板声明的流体产物精确收取（足量才收，且不得收本批输入流体）.
     *
     * <p>包可见（而非 private）只为单测：阶段 1 的公共入口 {@link #collectFluidProducts}
     * 需先经 {@code extractFluidFromItemStack} 把样板产物 ItemStack 解析成 {@link FluidStack}；
     * 本方法只覆盖判定本体（不触碰兼容层），用例无需构造流体假物品产物即可驱动阶段 1 的全部判据。
     * 可见性放宽为零行为变更，与本仓库其它 handler 的"纯逻辑包可见供单测"同款做法。</p>
     */
    static boolean tryCollectExpectedFluid(TileEntity te, EnumFacing face, FluidStack expectedFluid,
                                                  List<FluidStack> inputFluids, List<FluidStack> fluids) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        FluidStack drained = fh.drain(expectedFluid, false);
        if (drained == null || drained.amount < expectedFluid.amount) return false;
        // 与预期产物同种的本批输入流体不得被当作产物抽走
        if (isInputFluid(drained, inputFluids)) return false;
        FluidStack actual = fh.drain(expectedFluid, true);
        if (actual == null || actual.amount <= 0) return false;
        fluids.add(actual.copy());
        return true;
    }

    /**
     * 抽取（或探察）指定 IFluidHandler 中的剩余流体。
     *
     * <p>逐罐判断：只处理机器声明为可抽取（{@code canDrain}）的罐，并跳过本批输入流体；
     * 某个罐被跳过不会中断同一面上其它罐的收集——否则输入罐会挡住同面其它罐中的产物。</p>
     *
     * @param node     诊断节点名（阶段 2 收集 / 发配前清理）
     * @param blockId  目标 blockId，仅用于日志
     * @param pos      目标坐标，仅用于日志
     * @param face     当前面，仅用于日志
     * @param simulate true 时只探察能被抽走的量（{@code drain(..., false)}），不修改机器
     */
    private static void collectRemainingFluid(IFluidHandler fh, List<FluidStack> inputFluids, List<FluidStack> fluids,
                                              String node, String blockId, BlockPos pos, EnumFacing face,
                                              boolean simulate) {
        IFluidTankProperties[] properties;
        try {
            properties = fh.getTankProperties();
        } catch (Throwable t) {
            FluidFlowDiag.log(node, "-", "{} getTankProperties threw: {}", blockId, t.toString());
            return;
        }
        if (properties == null || properties.length == 0) {
            return;
        }
        for (IFluidTankProperties property : properties) {
            if (property == null || !property.canDrain()) continue;
            FluidStack contents = property.getContents();
            if (contents == null || contents.amount <= 0) continue;
            if (isInputFluid(contents, inputFluids)) {
                // 本批输入流体不被当产物抽走（正常语义，留痕便于判断"输入罐挡住了产物"）
                FluidFlowDiag.logAt(node, contents, blockId, pos,
                        "skipped (declared as this batch's input fluid)", "face=" + face);
                continue;
            }
            FluidStack drained = fh.drain(contents, !simulate);
            if (drained != null && drained.amount > 0) {
                fluids.add(drained.copy());
                FluidFlowDiag.logAt(node, drained, blockId, pos,
                        simulate ? "peeked (canDrain scan, nothing drained)" : "drained (canDrain scan)",
                        "face=" + face);
            }
        }
    }

    private static boolean isInputFluid(FluidStack fluid, List<FluidStack> inputFluids) {
        for (FluidStack input : inputFluids) {
            if (input != null && input.isFluidEqual(fluid)) {
                return true;
            }
        }
        return false;
    }
}
