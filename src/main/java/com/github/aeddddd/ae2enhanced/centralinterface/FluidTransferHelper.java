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
 * <p>从 {@link DualityCentralInterface} 抽出的纯静态逻辑：流体假物品推送与
 * 产物收集（两阶段）。不持有任何状态，全部通过参数传递。</p>
 *
 * <p>填充尝试顺序：null（内部 tank）优先，然后按 UP/DOWN/NORTH/SOUTH/WEST/EAST
 * 逐个尝试，第一个能完整接受流体的面即被使用，避免向多个 face 重复推送或污染输出槽。</p>
 *
 * <p>抽取一律走真实面并按机器声明的罐模式（{@link IFluidTankProperties#canDrain()}）
 * 判断，不使用 null 面——null 面会绕过机器的侧面配置模式。</p>
 *
 * <p>本类不提供“抽取回退”能力：从目标抽出的流体没有回注 AE 网络的通道，
 * 抽取只会把流体销毁，因此发配失败时流体一律保留在目标内，由调用方记录（见
 * {@code PhysicalDispatcher#logPushedFluids}）。</p>
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
     * CPU 已事先将物品提取到 table 中,此处只做转换与推送,不再从网络提取.
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
     * 将溢出流体尝试重新推回目标机器的 tank。
     * 用于网络已满时保留流体在目标中，避免产物丢失。
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
            for (EnumFacing face : FLUID_FACE_ORDER) {
                if (tryFillFluidHandler(te, face, fluid, dummy)) {
                    pushed = true;
                    break;
                }
            }
            if (!pushed) {
                remaining.add(fluid.copy());
            }
        }
        return remaining;
    }

    private static boolean tryDrainFluidHandler(TileEntity te, EnumFacing face, FluidStack fluid) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        FluidStack drained = fh.drain(fluid, false);
        if (drained != null && drained.amount > 0) {
            // 只回退实际存在的量,避免过度抽取
            FluidStack toDrain = fluid.copy();
            toDrain.amount = Math.min(fluid.amount, drained.amount);
            fh.drain(toDrain, true);
            return true;
        }
        return false;
    }

    /**
     * 从目标的 IFluidHandler 收集流体产物.
     *
     * <p>直接返回 FluidStack 列表，不再转换为流体假物品；调用方应通过
     * 网络注入将流体注入 AE 流体网络。</p>
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

        IAEItemStack[] expectedOutputs = session != null ? session.getExpectedOutputs() : null;
        List<FluidStack> inputFluids = session != null ? session.getInputFluids() : Collections.emptyList();

        // 阶段 1：按预期产物精确收集
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null || expected.getStackSize() <= 0) continue;
                ItemStack stack = expected.createItemStack();
                FluidStack expectedFluid = extractFluidFromItemStack(stack);
                if (expectedFluid == null) continue;

                for (EnumFacing face : FLUID_DRAIN_FACE_ORDER) {
                    if (tryCollectExpectedFluid(te, face, expectedFluid, inputFluids, fluids)) {
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
            collectRemainingFluid(fh, inputFluids, fluids);
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
     * @param skipFluids 本批将要推送的流体，不得被回收
     * @return 回收到的流体列表
     */
    public static List<FluidStack> drainExtractableFluids(World world, BlockPos pos, List<FluidStack> skipFluids) {
        List<FluidStack> drainedFluids = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return drainedFluids;

        List<FluidStack> skip = skipFluids != null ? skipFluids : Collections.emptyList();
        for (EnumFacing face : FLUID_DRAIN_FACE_ORDER) {
            if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) continue;
            IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
            if (fh == null) continue;
            collectRemainingFluid(fh, skip, drainedFluids);
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

    private static boolean tryCollectExpectedFluid(TileEntity te, EnumFacing face, FluidStack expectedFluid,
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
     * 抽取指定 IFluidHandler 中的剩余流体。
     *
     * <p>逐罐判断：只处理机器声明为可抽取（{@code canDrain}）的罐，并跳过本批输入流体；
     * 某个罐被跳过不会中断同一面上其它罐的收集——否则输入罐会挡住同面其它罐中的产物。</p>
     */
    private static void collectRemainingFluid(IFluidHandler fh, List<FluidStack> inputFluids, List<FluidStack> fluids) {
        IFluidTankProperties[] properties;
        try {
            properties = fh.getTankProperties();
        } catch (Throwable t) {
            return;
        }
        if (properties == null || properties.length == 0) {
            return;
        }
        for (IFluidTankProperties property : properties) {
            if (property == null || !property.canDrain()) continue;
            FluidStack contents = property.getContents();
            if (contents == null || contents.amount <= 0) continue;
            if (isInputFluid(contents, inputFluids)) continue;
            FluidStack drained = fh.drain(contents, true);
            if (drained != null && drained.amount > 0) {
                fluids.add(drained.copy());
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
