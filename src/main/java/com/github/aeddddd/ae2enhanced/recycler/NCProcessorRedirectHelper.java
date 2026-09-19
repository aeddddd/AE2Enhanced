package com.github.aeddddd.ae2enhanced.recycler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * NC 机器产物直注重定向的共享逻辑（供 nuclearcraft 包下各 Mixin 回调调用）。
 *
 * <p>所有 NuclearCraft 类均通过字符串 + 反射访问；本类只会在对应版本的 NC
 * 存在且对应 Mixin 被应用时才被加载，NC 未安装时不会触发类加载错误。</p>
 *
 * <p>注意：本类刻意放在 recycler 包而非 mixin 包下——mixin 包在部分启动器/核心mod
 * 环境中会接受特殊处理，非 mixin 类放在其中有加载失败的风险。</p>
 *
 * <p>支持两个大版本：</p>
 * <ul>
 *     <li>NC:O 2o.x（重制版）：{@code IProcessor#produceProducts()} 为接口 default 方法，
 *     物品输出槽位由 {@code ProcessorContainerInfo#itemOutputSlots} 给出；</li>
 *     <li>NC 2.19a（非重制版）：{@code produceProducts()} 定义在三个具体处理器基类中，
 *     物品输出槽为连续区间 [itemInputSize, itemInputSize+itemOutputSize)，
 *     流体输出罐为连续区间 [fluidInputSize, fluidInputSize+fluidOutputSize)。</li>
 * </ul>
 */
public final class NCProcessorRedirectHelper {

    private NCProcessorRedirectHelper() {
    }

    // ==================== NC:O 2o.x（重制版） ====================

    private static boolean ohInitDone = false;
    private static Method ohGetContainerInfo;       // IProcessor#getContainerInfo
    private static Field ohItemOutputSlots;         // ProcessorContainerInfo#itemOutputSlots
    private static Method ohGetInventoryStacks;     // ITileInventory#getInventoryStacks

    /** 重制版入口：由 MixinIProcessor（接口 mixin）在 produceProducts 末尾调用。 */
    public static void redirectOverhauled(Object processor) {
        if (!initOverhauled()) {
            return;
        }
        try {
            TileEntity tile = (TileEntity) processor;
            World world = tile.getWorld();
            if (world == null || world.isRemote) {
                return;
            }
            BlockPos pos = tile.getPos();

            Object info = ohGetContainerInfo.invoke(processor);
            if (info == null) {
                return;
            }
            int[] outputSlots = (int[]) ohItemOutputSlots.get(info);
            @SuppressWarnings("unchecked")
            NonNullList<ItemStack> stacks = (NonNullList<ItemStack>) ohGetInventoryStacks.invoke(processor);
            if (outputSlots == null || stacks == null) {
                return;
            }

            boolean changed = redirectItemSlots(stacks, outputSlots, world, pos);
            if (changed) {
                tile.markDirty();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft output redirect failed", e);
        }
    }

    private static boolean initOverhauled() {
        if (ohInitDone) {
            return ohGetContainerInfo != null && ohItemOutputSlots != null && ohGetInventoryStacks != null;
        }
        ohInitDone = true;
        try {
            ohGetContainerInfo = Class.forName("nc.tile.processor.IProcessor").getMethod("getContainerInfo");
            ohItemOutputSlots = Class.forName("nc.tile.processor.info.ProcessorContainerInfo")
                    .getField("itemOutputSlots");
            ohGetInventoryStacks = Class.forName("nc.tile.inventory.ITileInventory").getMethod("getInventoryStacks");
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NC:O processor redirect reflection", e);
        }
        return ohGetContainerInfo != null && ohItemOutputSlots != null && ohGetInventoryStacks != null;
    }

    // ==================== NC 2.19a（非重制版） ====================

    private static boolean legacyInitDone = false;
    private static Method legacyGetInventoryStacks; // ITileInventory#getInventoryStacks
    private static Method legacyGetTanks;           // ITileFluid#getTanks
    private static Field legacyItemInputSize;       // TileItemProcessor#itemInputSize
    private static Field legacyItemOutputSize;      // TileItemProcessor#itemOutputSize
    private static Field legacyFluidInputSize;      // TileFluidProcessor#fluidInputSize
    private static Field legacyFluidOutputSize;     // TileFluidProcessor#fluidOutputSize
    private static Field legacyItemFluidItemInputSize;   // TileItemFluidProcessor#itemInputSize
    private static Field legacyItemFluidItemOutputSize;  // TileItemFluidProcessor#itemOutputSize
    private static Field legacyItemFluidFluidInputSize;  // TileItemFluidProcessor#fluidInputSize
    private static Field legacyItemFluidFluidOutputSize; // TileItemFluidProcessor#fluidOutputSize

    /** 非重制版入口：TileItemProcessor（仅物品产物）。 */
    public static void redirectLegacyItemProcessor(Object tile) {
        if (!initLegacy() || legacyItemInputSize == null || legacyItemOutputSize == null) {
            return;
        }
        try {
            int firstSlot = legacyItemInputSize.getInt(tile);
            int slotCount = legacyItemOutputSize.getInt(tile);
            redirectLegacyItems(tile, firstSlot, slotCount);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft output redirect failed", e);
        }
    }

    /** 非重制版入口：TileFluidProcessor（仅流体产物）。 */
    public static void redirectLegacyFluidProcessor(Object tile) {
        if (!initLegacy() || legacyFluidInputSize == null || legacyFluidOutputSize == null) {
            return;
        }
        try {
            int firstTank = legacyFluidInputSize.getInt(tile);
            int tankCount = legacyFluidOutputSize.getInt(tile);
            redirectLegacyFluids(tile, firstTank, tankCount);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft output redirect failed", e);
        }
    }

    /** 非重制版入口：TileItemFluidProcessor（物品 + 流体产物）。 */
    public static void redirectLegacyItemFluidProcessor(Object tile) {
        if (!initLegacy()) {
            return;
        }
        try {
            if (legacyItemFluidItemInputSize != null && legacyItemFluidItemOutputSize != null) {
                int firstSlot = legacyItemFluidItemInputSize.getInt(tile);
                int slotCount = legacyItemFluidItemOutputSize.getInt(tile);
                redirectLegacyItems(tile, firstSlot, slotCount);
            }
            if (legacyItemFluidFluidInputSize != null && legacyItemFluidFluidOutputSize != null) {
                int firstTank = legacyItemFluidFluidInputSize.getInt(tile);
                int tankCount = legacyItemFluidFluidOutputSize.getInt(tile);
                redirectLegacyFluids(tile, firstTank, tankCount);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft output redirect failed", e);
        }
    }

    private static boolean initLegacy() {
        if (legacyInitDone) {
            return legacyGetInventoryStacks != null && legacyGetTanks != null;
        }
        legacyInitDone = true;
        try {
            legacyGetInventoryStacks = Class.forName("nc.tile.inventory.ITileInventory").getMethod("getInventoryStacks");
            legacyGetTanks = Class.forName("nc.tile.fluid.ITileFluid").getMethod("getTanks");

            Class<?> itemProcessor = Class.forName("nc.tile.processor.TileItemProcessor");
            legacyItemInputSize = itemProcessor.getField("itemInputSize");
            legacyItemOutputSize = itemProcessor.getField("itemOutputSize");

            Class<?> fluidProcessor = Class.forName("nc.tile.processor.TileFluidProcessor");
            legacyFluidInputSize = fluidProcessor.getField("fluidInputSize");
            legacyFluidOutputSize = fluidProcessor.getField("fluidOutputSize");

            Class<?> itemFluidProcessor = Class.forName("nc.tile.processor.TileItemFluidProcessor");
            legacyItemFluidItemInputSize = itemFluidProcessor.getField("itemInputSize");
            legacyItemFluidItemOutputSize = itemFluidProcessor.getField("itemOutputSize");
            legacyItemFluidFluidInputSize = itemFluidProcessor.getField("fluidInputSize");
            legacyItemFluidFluidOutputSize = itemFluidProcessor.getField("fluidOutputSize");
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NC legacy processor redirect reflection", e);
        }
        return legacyGetInventoryStacks != null && legacyGetTanks != null;
    }

    // ==================== 公共逻辑 ====================

    /** 非重制版：重定向连续区间内的物品输出槽。 */
    private static void redirectLegacyItems(Object tile, int firstSlot, int slotCount) throws Exception {
        TileEntity te = (TileEntity) tile;
        World world = te.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        @SuppressWarnings("unchecked")
        NonNullList<ItemStack> stacks = (NonNullList<ItemStack>) legacyGetInventoryStacks.invoke(tile);
        if (stacks == null) {
            return;
        }
        boolean changed = false;
        for (int slot = firstSlot; slot < firstSlot + slotCount && slot < stacks.size(); slot++) {
            if (slot < 0) {
                continue;
            }
            ItemStack stack = stacks.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, te.getPos());
            if (remainder.getCount() != stack.getCount()) {
                stacks.set(slot, remainder);
                changed = true;
            }
        }
        if (changed) {
            te.markDirty();
        }
    }

    /** 非重制版：重定向连续区间内的流体输出罐。 */
    private static void redirectLegacyFluids(Object tile, int firstTank, int tankCount) throws Exception {
        TileEntity te = (TileEntity) tile;
        World world = te.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        List<?> tanks = (List<?>) legacyGetTanks.invoke(tile);
        if (tanks == null) {
            return;
        }
        BlockPos pos = te.getPos();
        boolean changed = false;
        for (int i = firstTank; i < firstTank + tankCount && i < tanks.size(); i++) {
            if (i < 0 || !(tanks.get(i) instanceof FluidTank)) {
                continue;
            }
            FluidTank tank = (FluidTank) tanks.get(i);
            FluidStack fluid = tank.getFluid();
            if (fluid == null || fluid.amount <= 0) {
                continue;
            }
            FluidStack remainder = MachineOutputRedirector.tryRedirectFluid(fluid, world, pos);
            if (remainder == null || remainder.amount != fluid.amount) {
                // 全部注入时返回 null，清空该罐
                tank.setFluid(remainder);
                changed = true;
            }
        }
        if (changed) {
            te.markDirty();
        }
    }

    /** 重制版：按输出槽位数组重定向物品产物。 */
    private static boolean redirectItemSlots(NonNullList<ItemStack> stacks, int[] outputSlots,
                                             World world, BlockPos pos) {
        boolean changed = false;
        for (int slot : outputSlots) {
            if (slot < 0 || slot >= stacks.size()) {
                continue;
            }
            ItemStack stack = stacks.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, pos);
            if (remainder.getCount() != stack.getCount()) {
                stacks.set(slot, remainder);
                changed = true;
            }
        }
        return changed;
    }
}
