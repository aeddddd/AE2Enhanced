package com.github.aeddddd.ae2enhanced.centralinterface.handler.bewitchment;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
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

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Bewitchment 处理器：SpinningWheel + Distillery + WitchesCauldron.
 *
 * <p><b>SpinningWheel / Distillery</b>：通过 {@link EnumFacing#UP} 的 IItemHandler 推入材料,
 * 通过 {@link EnumFacing#DOWN} 的 IItemHandler 收集产物.每个槽位仅放入单个物品.</p>
 *
 * <p><b>WitchesCauldron</b>：不暴露 IItemHandler capability,IO 完全通过反射操作内部字段完成.
 * push 时直接写入 inventory 并立即触发配方匹配与产物生成；collect 时回收周围 EntityItem 与 inventory 残余.</p>
 */
public class BewitchmentHandler implements IRemoteHandler {

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

    @Override
    public boolean canHandle(String blockId) {
        return SPINNING_WHEEL_ID.equals(blockId)
            || DISTILLERY_ID.equals(blockId)
            || CAULDRON_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_SPINNING_WHEEL.isInstance(te)
            || CLASS_DISTILLERY.isInstance(te)
            || CLASS_CAULDRON.isInstance(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.physicalOnly();
    }

    // ===================== canStart =====================

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            return canStartSpinningWheel(te, ingredients);
        }
        if (CLASS_DISTILLERY.isInstance(te)) {
            return canStartDistillery(te, ingredients);
        }
        if (CLASS_CAULDRON.isInstance(te)) {
            return canStartCauldron(te, ingredients);
        }
        return false;
    }

    private boolean canStartSpinningWheel(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (up == null) return false;
        for (int i = 0; i < up.getSlots(); i++) {
            if (!up.getStackInSlot(i).isEmpty()) return false;
        }
        int needed = countNonEmpty(ingredients);
        return up.getSlots() >= needed;
    }

    private boolean canStartDistillery(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
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

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        boolean success = false;
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            success = pushSpinningWheel(te, ingredients);
        } else if (CLASS_DISTILLERY.isInstance(te)) {
            success = pushDistillery(te, ingredients);
        } else if (CLASS_CAULDRON.isInstance(te)) {
            success = pushCauldron(world, pos, te, ingredients);
        }
        if (success && session != null) {
            session.setPushTick(world.getTotalWorldTime());
        }
        return success;
    }

    private boolean pushSpinningWheel(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
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

    private boolean pushDistillery(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (up == null) return false;
        int slotIdx = 0;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack single = stack.copy();
            single.setCount(1);
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

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
            if (up != null) {
                for (int i = 0; i < up.getSlots(); i++) {
                    ItemStack stack = up.extractItem(i, Integer.MAX_VALUE, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
        } else if (CLASS_DISTILLERY.isInstance(te)) {
            IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
            if (up != null) {
                for (int i = 0; i < up.getSlots(); i++) {
                    ItemStack stack = up.extractItem(i, Integer.MAX_VALUE, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
        } else if (CLASS_CAULDRON.isInstance(te)) {
            try {
                ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
                if (inv != null) {
                    for (int i = 0; i < inv.getSlots(); i++) {
                        ItemStack stack = inv.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            result.add(stack.copy());
                            inv.setStackInSlot(i, ItemStack.EMPTY);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ===================== isIdle =====================

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            return isIdleSpinningWheel(world, pos, te, session);
        }
        if (CLASS_DISTILLERY.isInstance(te)) {
            return isIdleDistillery(world, pos, te, session);
        }
        if (CLASS_CAULDRON.isInstance(te)) {
            return isIdleCauldron(world, pos, te, session);
        }
        return false;
    }

    private boolean isGraceElapsed(TargetSession session, long currentWorldTime, int graceTicks) {
        return session == null || session.isPushGraceElapsed(currentWorldTime, graceTicks);
    }

    private boolean isIdleSpinningWheel(World world, BlockPos pos, TileEntity te, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        try {
            int progress = (int) FIELD_SPINNING_PROGRESS.get(te);
            return progress == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isIdleDistillery(World world, BlockPos pos, TileEntity te, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        try {
            int progress = (int) FIELD_DISTILLERY_PROGRESS.get(te);
            return progress == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isIdleCauldron(World world, BlockPos pos, TileEntity te, TargetSession session) {
        if (!isGraceElapsed(session, world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) return false;
        try {
            int mode = (int) FIELD_CAULDRON_MODE.get(te);
            // mode 0/1 为空闲或已完成
            if (mode == 0 || mode == 1) return true;
            // mode 2/3/4/5 为处理中
            if (mode == 2 || mode == 3 || mode == 4 || mode == 5) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== collectProducts =====================

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            collectSpinningWheel(te, result);
        } else if (CLASS_DISTILLERY.isInstance(te)) {
            collectDistillery(te, result);
        } else if (CLASS_CAULDRON.isInstance(te)) {
            collectCauldron(world, pos, te, result);
        }
        return result;
    }

    private void collectSpinningWheel(TileEntity te, List<ItemStack> result) {
        IItemHandler down = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (down == null) return;
        for (int i = 0; i < down.getSlots(); i++) {
            ItemStack stack = down.extractItem(i, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
    }

    private void collectDistillery(TileEntity te, List<ItemStack> result) {
        IItemHandler down = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
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
            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv != null) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        result.add(stack.copy());
                        inv.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }

        } catch (Exception ignored) {}
    }
}
