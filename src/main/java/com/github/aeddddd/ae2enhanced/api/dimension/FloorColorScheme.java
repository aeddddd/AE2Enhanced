package com.github.aeddddd.ae2enhanced.api.dimension;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 个人维度地板颜色方案:把样式中的占位方块(见 {@link FloorColorRole})替换为目标方块状态.
 *
 * <p>空方案表示不换色(全部使用样式默认).除三个内置角色的混凝土换色外,
 * 也可通过 {@link #put} 建立任意方块到方块状态的映射,供 API 使用方自定义.</p>
 */
public class FloorColorScheme {

    private final Map<Block, BlockState> overrides = new LinkedHashMap<>();

    public static FloorColorScheme createDefault() {
        return new FloorColorScheme();
    }

    /**
     * 按三个角色的染料色混凝土构建方案.
     */
    public static FloorColorScheme ofConcrete(DyeColor roadBase, DyeColor roadLine, DyeColor platformBase) {
        return new FloorColorScheme()
                .setConcrete(FloorColorRole.ROAD_BASE, roadBase)
                .setConcrete(FloorColorRole.ROAD_LINE, roadLine)
                .setConcrete(FloorColorRole.PLATFORM_BASE, platformBase);
    }

    /**
     * 将角色占位方块替换为指定染料色混凝土;与默认色相同时清除替换.
     */
    public FloorColorScheme setConcrete(FloorColorRole role, DyeColor color) {
        if (color == role.getDefaultColor()) {
            overrides.remove(role.getPlaceholder());
        } else {
            overrides.put(role.getPlaceholder(), concreteOf(color));
        }
        return this;
    }

    /**
     * 建立任意方块到方块状态的替换映射(API 自定义入口).
     */
    public FloorColorScheme put(Block from, BlockState to) {
        overrides.put(from, to);
        return this;
    }

    /**
     * 应用方案:命中映射的方块被替换,否则原样返回.
     */
    @Nullable
    public BlockState apply(@Nullable BlockState state) {
        if (state == null) {
            return null;
        }
        BlockState mapped = overrides.get(state.getBlock());
        return mapped != null ? mapped : state;
    }

    /**
     * 查询角色当前的染料色;替换目标不是混凝土时按默认色处理.
     */
    public DyeColor getConcreteColor(FloorColorRole role) {
        BlockState target = overrides.get(role.getPlaceholder());
        if (target == null) {
            return role.getDefaultColor();
        }
        DyeColor color = dyeColorOf(target.getBlock());
        return color != null ? color : role.getDefaultColor();
    }

    public FloorColorScheme copy() {
        FloorColorScheme copy = new FloorColorScheme();
        copy.overrides.putAll(this.overrides);
        return copy;
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<Block, BlockState> entry : overrides.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("from", BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString());
            item.putString("to", BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()).toString());
            list.add(item);
        }
        tag.put("overrides", list);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        overrides.clear();
        ListTag list = tag.getList("overrides", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            ResourceLocation from = ResourceLocation.tryParse(item.getString("from"));
            ResourceLocation to = ResourceLocation.tryParse(item.getString("to"));
            if (from == null || to == null
                    || !BuiltInRegistries.BLOCK.containsKey(from) || !BuiltInRegistries.BLOCK.containsKey(to)) {
                continue;
            }
            overrides.put(BuiltInRegistries.BLOCK.get(from), BuiltInRegistries.BLOCK.get(to).defaultBlockState());
        }
    }

    private static BlockState concreteOf(DyeColor color) {
        return BuiltInRegistries.BLOCK
                .get(new ResourceLocation("minecraft", color.getName() + "_concrete"))
                .defaultBlockState();
    }

    @Nullable
    private static DyeColor dyeColorOf(Block block) {
        for (DyeColor color : DyeColor.values()) {
            if (concreteOf(color).getBlock() == block) {
                return color;
            }
        }
        return null;
    }
}
