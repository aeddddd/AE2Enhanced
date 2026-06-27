package com.github.aeddddd.ae2enhanced.mixin.late.industrialforegoing;

import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Industrial Foregoing 机器产物直注 Mixin（Tier 1 + Tier 2）。
 *
 * <p>在 {@code CustomElectricMachine.protectedUpdate()} 调用 {@code workTransferAddon} 之前，
 * 扫描当前机器实例中所有输出型 {@link IItemHandler} 字段：</p>
 * <ul>
 *     <li>字段名包含 {@code output}/{@code outout}/{@code result}/{@code export}/{@code extract}/{@code out}
 *         的 {@link IItemHandler}（Tier 1，扩展名称匹配）；</li>
 *     <li>上述名称的数组或 {@link Collection} 类型字段中包含的 {@code IItemHandler}（Tier 2 扩展）；</li>
 *     <li>{@link Map} 类型字段中包含的 {@code IItemHandler}（保留 Tier 1 行为，兼容特殊机器结构）。</li>
 * </ul>
 *
 * <p>扫描到的产物会被重定向到已绑定的 ME 网络回收节点；未能注入的部分保留在原槽位。</p>
 */
@Mixin(targets = "com.buuz135.industrial.tile.CustomElectricMachine", remap = false)
public class MixinCustomElectricMachine {

    private static final Field FIELD_WORLD;
    private static final Field FIELD_POS;

    static {
        Field worldField = null;
        Field posField = null;
        try {
            Class<?> clazz = Class.forName("com.buuz135.industrial.tile.CustomElectricMachine");
            worldField = findField(clazz, "field_145850_b");
            posField = findField(clazz, "field_174879_c");
        } catch (Exception ignored) {
        }
        FIELD_WORLD = worldField;
        FIELD_POS = posField;
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
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

    private static boolean isOutputFieldName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("input") || lower.contains("import") || lower.contains("material") || lower.contains("fuel")) {
            return false;
        }
        return lower.contains("output") || lower.contains("outout") || lower.contains("result")
                || lower.contains("export") || lower.contains("extract") || lower.contains("out");
    }

    @Inject(method = "protectedUpdate",
            at = @At(value = "INVOKE",
                    target = "Lcom/buuz135/industrial/tile/CustomElectricMachine;workTransferAddon(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraftforge/items/ItemStackHandler;)V"))
    private void ae2enhanced$redirectOutputsBeforeTransfer(CallbackInfo ci) {
        if (FIELD_WORLD == null || FIELD_POS == null) {
            return;
        }
        try {
            World world = (World) FIELD_WORLD.get(this);
            if (world == null || world.isRemote) {
                return;
            }
            BlockPos pos = (BlockPos) FIELD_POS.get(this);

            Set<IItemHandler> visited = new HashSet<>();
            Class<?> clazz = this.getClass();
            while (clazz != null && !Object.class.getName().equals(clazz.getName())) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value;
                    try {
                        value = field.get(this);
                    } catch (IllegalAccessException e) {
                        continue;
                    }
                    if (value == null) {
                        continue;
                    }

                    String fieldName = field.getName();
                    if (value instanceof IItemHandler) {
                        if (isOutputFieldName(fieldName) && visited.add((IItemHandler) value)) {
                            redirectHandler((IItemHandler) value, world, pos);
                        }
                    } else if (isOutputFieldName(fieldName)) {
                        collectAndRedirect(value, world, pos, visited);
                    } else if (value instanceof Map) {
                        // 保留 Tier 1 对 Map 类型字段的处理逻辑
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                            if (entry.getKey() instanceof IItemHandler && visited.add((IItemHandler) entry.getKey())) {
                                redirectHandler((IItemHandler) entry.getKey(), world, pos);
                            }
                            if (entry.getValue() instanceof IItemHandler && visited.add((IItemHandler) entry.getValue())) {
                                redirectHandler((IItemHandler) entry.getValue(), world, pos);
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private void collectAndRedirect(Object value, World world, BlockPos pos, Set<IItemHandler> visited) {
        if (value instanceof IItemHandler && visited.add((IItemHandler) value)) {
            redirectHandler((IItemHandler) value, world, pos);
        } else if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() instanceof IItemHandler && visited.add((IItemHandler) entry.getKey())) {
                    redirectHandler((IItemHandler) entry.getKey(), world, pos);
                }
                if (entry.getValue() instanceof IItemHandler && visited.add((IItemHandler) entry.getValue())) {
                    redirectHandler((IItemHandler) entry.getValue(), world, pos);
                }
            }
        } else if (value instanceof IItemHandler[]) {
            for (IItemHandler handler : (IItemHandler[]) value) {
                if (handler != null && visited.add(handler)) {
                    redirectHandler(handler, world, pos);
                }
            }
        } else if (value instanceof Collection) {
            for (Object element : (Collection<?>) value) {
                if (element instanceof IItemHandler && visited.add((IItemHandler) element)) {
                    redirectHandler((IItemHandler) element, world, pos);
                }
            }
        }
    }

    private void redirectHandler(IItemHandler handler, World world, BlockPos pos) {
        if (handler == null) {
            return;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, pos);
            if (remainder.getCount() != stack.getCount()) {
                handler.extractItem(i, stack.getCount(), false);
                if (!remainder.isEmpty()) {
                    handler.insertItem(i, remainder, false);
                }
            }
        }
    }
}
