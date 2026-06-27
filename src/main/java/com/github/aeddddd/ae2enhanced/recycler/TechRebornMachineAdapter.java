package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Tech Reborn 机器专用适配器。
 *
 * <p>Tech Reborn 的 {@code Inventory} 实现 {@link net.minecraft.inventory.ISidedInventory} 时
 * 对所有面返回空槽位并禁止提取；只有在 GUI 中手动配置输出槽/自动输出面后，
 * 外部 {@link net.minecraftforge.items.IItemHandler} 才能取出产物。</p>
 *
 * <p>本适配器绕过侧面配置，直接通过反射访问机器内部库存与输出槽索引，
 * 从而在未配置输出槽的情况下也能回收产物。</p>
 *
 * <p>支持两类 Tech Reborn 机器：</p>
 * <ul>
 *     <li>{@code techreborn.tiles.processing.TileMachine} 及其子类：输出槽在 {@code outputSlots} 字段；</li>
 *     <li>{@code techreborn.tiles.TileGenericMachine} 及其子类：输出槽在 {@code RecipeCrafter#outputSlots}。</li>
 * </ul>
 */
public class TechRebornMachineAdapter implements TargetAdapter {

    private static final boolean AVAILABLE;
    private static final Class<?> TILE_MACHINE_CLASS;
    private static final Class<?> TILE_GENERIC_MACHINE_CLASS;
    private static final Field FIELD_INVENTORY;
    private static final Field FIELD_OUTPUT_SLOTS_TILE;
    private static final Field FIELD_CRAFTER;
    private static final Field FIELD_OUTPUT_SLOTS_CRAFTER;
    private static final Field FIELD_CONTENTS;

    static {
        Class<?> tileMachineClass = null;
        Class<?> tileGenericMachineClass = null;
        Field inventoryField = null;
        Field outputSlotsTileField = null;
        Field crafterField = null;
        Field outputSlotsCrafterField = null;
        Field contentsField = null;
        boolean available = false;
        try {
            tileMachineClass = Class.forName("techreborn.tiles.processing.TileMachine");
            tileGenericMachineClass = Class.forName("techreborn.tiles.TileGenericMachine");

            inventoryField = findField(tileMachineClass, "inventory");
            if (inventoryField == null) {
                inventoryField = findField(tileGenericMachineClass, "inventory");
            }
            outputSlotsTileField = findField(tileMachineClass, "outputSlots");
            crafterField = findField(tileGenericMachineClass, "crafter");
            if (crafterField != null) {
                Class<?> recipeCrafterClass = Class.forName("reborncore.common.recipes.RecipeCrafter");
                outputSlotsCrafterField = findField(recipeCrafterClass, "outputSlots");
            }

            Class<?> inventoryClass = Class.forName("reborncore.common.util.Inventory");
            contentsField = inventoryClass.getDeclaredField("contents");
            contentsField.setAccessible(true);

            available = inventoryField != null && contentsField != null
                    && (outputSlotsTileField != null || outputSlotsCrafterField != null);
        } catch (Exception e) {
            // Tech Reborn 未安装或版本不兼容，静默禁用
        }
        AVAILABLE = available;
        TILE_MACHINE_CLASS = tileMachineClass;
        TILE_GENERIC_MACHINE_CLASS = tileGenericMachineClass;
        FIELD_INVENTORY = inventoryField;
        FIELD_OUTPUT_SLOTS_TILE = outputSlotsTileField;
        FIELD_CRAFTER = crafterField;
        FIELD_OUTPUT_SLOTS_CRAFTER = outputSlotsCrafterField;
        FIELD_CONTENTS = contentsField;
    }

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

    private final TileEntity tile;
    private final EnumFacing face;

    public TechRebornMachineAdapter(TileEntity tile, EnumFacing face) {
        this.tile = tile;
        this.face = face;
    }

    /**
     * 判断目标 TileEntity 是否为 Tech Reborn 机器且本适配器可用。
     */
    public static boolean isApplicable(TileEntity tile) {
        return AVAILABLE && tile != null && !tile.isInvalid()
                && ((TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(tile))
                || (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(tile)));
    }

    @Override
    @Nonnull
    public List<ItemStack> scan(boolean simulate) {
        List<ItemStack> result = new ArrayList<>();
        ItemStack[] contents = getContents();
        int[] outputSlots = getOutputSlots();
        if (contents == null || outputSlots == null) return result;

        for (int slot : outputSlots) {
            if (slot < 0 || slot >= contents.length) continue;
            ItemStack stack = contents[slot];
            if (stack != null && !stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    @Override
    @Nullable
    public ItemStack extract(@Nonnull IAEItemStack requested, boolean simulate) {
        if (tile == null || tile.isInvalid() || requested == null) return null;

        ItemStack wanted = requested.createItemStack();
        int remaining = wanted.getCount();
        ItemStack collected = ItemStack.EMPTY;

        ItemStack[] contents = getContents();
        int[] outputSlots = getOutputSlots();
        if (contents == null || outputSlots == null) return null;

        for (int slot : outputSlots) {
            if (remaining <= 0) break;
            if (slot < 0 || slot >= contents.length) continue;

            ItemStack slotStack = contents[slot];
            if (slotStack == null || slotStack.isEmpty()
                    || !slotStack.isItemEqual(wanted)
                    || !ItemStack.areItemStackTagsEqual(slotStack, wanted)) {
                continue;
            }

            int extractCount = Math.min(remaining, slotStack.getCount());
            if (collected.isEmpty()) {
                collected = slotStack.copy();
                collected.setCount(extractCount);
            } else {
                collected.grow(extractCount);
            }

            if (!simulate) {
                slotStack.shrink(extractCount);
                tile.markDirty();
            }

            remaining -= extractCount;
        }

        return collected.isEmpty() ? null : collected;
    }

    @Override
    public void invalidate() {
        // 无需要清理的句柄
    }

    @Nullable
    private ItemStack[] getContents() {
        if (tile == null || tile.isInvalid()) return null;
        try {
            Object inventory = FIELD_INVENTORY.get(tile);
            if (inventory == null) return null;
            return (ItemStack[]) FIELD_CONTENTS.get(inventory);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechRebornMachineAdapter getContents failed", e);
            return null;
        }
    }

    @Nullable
    private int[] getOutputSlots() {
        if (tile == null || tile.isInvalid()) return null;
        try {
            if (TILE_MACHINE_CLASS != null && TILE_MACHINE_CLASS.isInstance(tile)
                    && FIELD_OUTPUT_SLOTS_TILE != null) {
                return (int[]) FIELD_OUTPUT_SLOTS_TILE.get(tile);
            }
            if (TILE_GENERIC_MACHINE_CLASS != null && TILE_GENERIC_MACHINE_CLASS.isInstance(tile)
                    && FIELD_CRAFTER != null && FIELD_OUTPUT_SLOTS_CRAFTER != null) {
                Object crafter = FIELD_CRAFTER.get(tile);
                if (crafter == null) return null;
                return (int[]) FIELD_OUTPUT_SLOTS_CRAFTER.get(crafter);
            }
            return null;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] TechRebornMachineAdapter getOutputSlots failed", e);
            return null;
        }
    }
}
