package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import appeng.api.util.AEColor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PlacementConfig} 单元测试.
 * <p>全部为 ItemStack NBT 读写逻辑,使用真实 ItemStack + 原版引导.</p>
 */
class PlacementConfigTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private static ItemStack newToolStack() {
        return new ItemStack(Items.STICK);
    }

    private static CompoundTag rootOf(ItemStack stack) {
        // getCompound 在键不存在时返回未挂载的新实例,必须先显式 put
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(PlacementConfig.NBT_ROOT)) {
            tag.put(PlacementConfig.NBT_ROOT, new CompoundTag());
        }
        return tag.getCompound(PlacementConfig.NBT_ROOT);
    }

    // ========== 预设槽 ==========

    @Test
    void freshConfigHasNoPresets() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        assertThat(config.getPresetCount()).isZero();
        assertThat(config.getFirstEmptySlot()).isZero();
    }

    @Test
    void setAndGetStackInSlot() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        ItemStack stone = new ItemStack(Blocks.STONE, 3);
        config.setStackInSlot(2, stone);
        assertThat(config.getStackInSlot(2).is(Blocks.STONE.asItem())).isTrue();
        assertThat(config.getStackInSlot(2).getCount()).isEqualTo(3);
        assertThat(config.getPresetCount()).isEqualTo(1);
    }

    @Test
    void setStackInSlotReplacesExisting() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setStackInSlot(0, new ItemStack(Blocks.STONE));
        config.setStackInSlot(0, new ItemStack(Blocks.DIRT));
        // 替换而不是追加
        assertThat(config.getPresetCount()).isEqualTo(1);
        assertThat(config.getStackInSlot(0).is(Blocks.DIRT.asItem())).isTrue();
    }

    @Test
    void setStackInSlotIgnoresOutOfRangeSlots() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setStackInSlot(-1, new ItemStack(Blocks.STONE));
        config.setStackInSlot(PlacementConfig.MAX_PRESETS, new ItemStack(Blocks.STONE));
        assertThat(config.getPresetCount()).isZero();
    }

    @Test
    void getStackInSlotOutOfRangeReturnsEmpty() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        assertThat(config.getStackInSlot(-1).isEmpty()).isTrue();
        assertThat(config.getStackInSlot(PlacementConfig.MAX_PRESETS).isEmpty()).isTrue();
    }

    @Test
    void clearSlotRemovesEntry() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setStackInSlot(4, new ItemStack(Blocks.STONE));
        config.clearSlot(4);
        assertThat(config.getStackInSlot(4).isEmpty()).isTrue();
        assertThat(config.getPresetCount()).isZero();
    }

    @Test
    void getFirstEmptySlotSkipsUsedSlots() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setStackInSlot(0, new ItemStack(Blocks.STONE));
        config.setStackInSlot(1, new ItemStack(Blocks.DIRT));
        assertThat(config.getFirstEmptySlot()).isEqualTo(2);
    }

    @Test
    void getFirstEmptySlotReturnsMinusOneWhenFull() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        for (int i = 0; i < PlacementConfig.MAX_PRESETS; i++) {
            config.setStackInSlot(i, new ItemStack(Blocks.STONE));
        }
        assertThat(config.getFirstEmptySlot()).isEqualTo(-1);
        assertThat(config.getPresetCount()).isEqualTo(PlacementConfig.MAX_PRESETS);
    }

    @Test
    void presetCountIgnoresOutOfRangeSlotEntries() {
        // 直接注入槽位号 >= MAX_PRESETS 的脏数据,计数时应被忽略
        ItemStack stack = newToolStack();
        CompoundTag root = rootOf(stack);
        ListTag list = new ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putByte("Slot", (byte) 12);
        new ItemStack(Blocks.STONE).save(bad);
        list.add(bad);
        root.put(PlacementConfig.NBT_PRESETS, list);

        PlacementConfig config = new PlacementConfig(stack);
        assertThat(config.getPresetCount()).isZero();
        assertThat(config.getStackInSlot(12).isEmpty()).isTrue();
    }

    // ========== 当前选中槽 ==========

    @Test
    void defaultSelectedSlotIsZero() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        assertThat(config.getSelectedSlot()).isZero();
    }

    @Test
    void setSelectedSlotClampsToValidRange() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setSelectedSlot(5);
        assertThat(config.getSelectedSlot()).isEqualTo(5);
        // 下界钳制到 -1（无选中）
        config.setSelectedSlot(-100);
        assertThat(config.getSelectedSlot()).isEqualTo(-1);
        // 上界钳制到最后一个槽
        config.setSelectedSlot(100);
        assertThat(config.getSelectedSlot()).isEqualTo(PlacementConfig.MAX_PRESETS - 1);
    }

    @Test
    void getSelectedStackEmptyWhenNoSelection() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setSelectedSlot(-1);
        assertThat(config.getSelectedStack().isEmpty()).isTrue();
    }

    @Test
    void getSelectedStackReturnsPreset() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setStackInSlot(3, new ItemStack(Blocks.STONE));
        config.setSelectedSlot(3);
        assertThat(config.getSelectedStack().is(Blocks.STONE.asItem())).isTrue();
    }

    // ========== 放置子模式 / 方向锁 / 颜色 / 触及距离 ==========

    @Test
    void placementModeRoundTrip() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        assertThat(config.getPlacementMode()).isEqualTo(PlacementMode.SINGLE);
        config.setPlacementMode(PlacementMode.BULK);
        assertThat(config.getPlacementMode()).isEqualTo(PlacementMode.BULK);
        config.setPlacementMode(PlacementMode.CABLE);
        assertThat(config.getPlacementMode()).isEqualTo(PlacementMode.CABLE);
    }

    @Test
    void placementRestrictionRoundTrip() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        assertThat(config.getPlacementRestriction()).isEqualTo(PlacementRestriction.NO_LOCK);
        config.setPlacementRestriction(PlacementRestriction.NORTH_SOUTH);
        assertThat(config.getPlacementRestriction()).isEqualTo(PlacementRestriction.NORTH_SOUTH);
    }

    @Test
    void cableColorRoundTrip() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        // 未写入时使用 ordinal 0 对应的颜色
        assertThat(config.getCableColor()).isEqualTo(AEColor.values()[0]);
        config.setCableColor(AEColor.LIME);
        assertThat(config.getCableColor()).isEqualTo(AEColor.LIME);
        config.setCableColor(AEColor.TRANSPARENT);
        assertThat(config.getCableColor()).isEqualTo(AEColor.TRANSPARENT);
    }

    @Test
    void invalidCableColorOrdinalFallsBackToTransparent() {
        ItemStack stack = newToolStack();
        rootOf(stack).putByte(PlacementConfig.NBT_CABLE_COLOR, (byte) 200);
        PlacementConfig config = new PlacementConfig(stack);
        assertThat(config.getCableColor()).isEqualTo(AEColor.TRANSPARENT);
    }

    @Test
    void reachDistanceDefaultsAndClamps() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        assertThat(config.getReachDistance()).isEqualTo(PlacementConfig.DEFAULT_REACH_DISTANCE);

        config.setReachDistance(20.0f);
        assertThat(config.getReachDistance()).isEqualTo(20.0f);

        // 写入端钳制
        config.setReachDistance(1.0f);
        assertThat(config.getReachDistance()).isEqualTo(PlacementConfig.MIN_REACH_DISTANCE);
        config.setReachDistance(1000.0f);
        assertThat(config.getReachDistance()).isEqualTo(PlacementConfig.MAX_REACH_DISTANCE);
    }

    @Test
    void reachDistanceClampsRawOutOfRangeValueOnRead() {
        // 绕过 setter 直接写入越界值,读取端同样钳制
        ItemStack stack = newToolStack();
        rootOf(stack).putFloat(PlacementConfig.NBT_REACH_DISTANCE, 9999.0f);
        PlacementConfig config = new PlacementConfig(stack);
        assertThat(config.getReachDistance()).isEqualTo(PlacementConfig.MAX_REACH_DISTANCE);
    }

    // ========== 线缆起点 ==========

    @Test
    void cableStartDefaultsToNull() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        assertThat(config.getCableStart()).isNull();
    }

    @Test
    void cableStartRoundTrip() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        BlockPos pos = new BlockPos(-12, 64, 300);
        config.setCableStart(pos);
        assertThat(config.getCableStart()).isEqualTo(pos);
    }

    @Test
    void cableStartClearedByNull() {
        PlacementConfig config = new PlacementConfig(newToolStack());
        config.setCableStart(new BlockPos(1, 2, 3));
        config.setCableStart(null);
        assertThat(config.getCableStart()).isNull();
    }

    // ========== 旧版数据迁移 ==========

    @Test
    void legacyDataIsMigrated() {
        ItemStack stack = newToolStack();
        CompoundTag root = rootOf(stack);
        ListTag legacyItems = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putByte("Slot", (byte) 5); // 旧格式允许 0~17
        new ItemStack(Blocks.STONE, 7).save(entry);
        legacyItems.add(entry);
        root.put(PlacementConfig.LEGACY_NBT_ITEMS, legacyItems);
        root.putInt(PlacementConfig.NBT_SELECTED_SLOT, 0);

        PlacementConfig config = new PlacementConfig(stack);

        // 迁移到新槽位 0,旧字段被清除,默认模式 SINGLE
        assertThat(config.getStackInSlot(0).is(Blocks.STONE.asItem())).isTrue();
        assertThat(config.getStackInSlot(0).getCount()).isEqualTo(7);
        assertThat(config.getSelectedSlot()).isZero();
        assertThat(config.getPlacementMode()).isEqualTo(PlacementMode.SINGLE);
        assertThat(root.contains(PlacementConfig.LEGACY_NBT_ITEMS)).isFalse();
        assertThat(root.contains(PlacementConfig.LEGACY_NBT_PLACEMENT_COUNT)).isFalse();
    }

    @Test
    void legacyMigrationCapsAtMaxPresets() {
        ItemStack stack = newToolStack();
        CompoundTag root = rootOf(stack);
        ListTag legacyItems = new ListTag();
        for (int i = 0; i < 12; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            new ItemStack(Blocks.STONE).save(entry);
            legacyItems.add(entry);
        }
        root.put(PlacementConfig.LEGACY_NBT_ITEMS, legacyItems);

        PlacementConfig config = new PlacementConfig(stack);
        assertThat(config.getPresetCount()).isEqualTo(PlacementConfig.MAX_PRESETS);
    }

    @Test
    void legacyMigrationSkipsOutOfRangeLegacySlots() {
        ItemStack stack = newToolStack();
        CompoundTag root = rootOf(stack);
        ListTag legacyItems = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putByte("Slot", (byte) 30); // 旧格式也只允许 0~17
        new ItemStack(Blocks.STONE).save(entry);
        legacyItems.add(entry);
        root.put(PlacementConfig.LEGACY_NBT_ITEMS, legacyItems);

        PlacementConfig config = new PlacementConfig(stack);
        assertThat(config.getPresetCount()).isZero();
    }

    @Test
    void legacyMigrationFixesOutOfRangeSelectedSlot() {
        ItemStack stack = newToolStack();
        CompoundTag root = rootOf(stack);
        ListTag legacyItems = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putByte("Slot", (byte) 0);
        new ItemStack(Blocks.STONE).save(entry);
        legacyItems.add(entry);
        root.put(PlacementConfig.LEGACY_NBT_ITEMS, legacyItems);
        root.putInt(PlacementConfig.NBT_SELECTED_SLOT, 9); // 超出迁移后数量

        PlacementConfig config = new PlacementConfig(stack);
        // 迁移了 1 个预设,选中槽回退到 0
        assertThat(config.getSelectedSlot()).isZero();
    }

    @Test
    void newFormatIsNotMigratedAgain() {
        // 已含新格式 presets 时不再走旧迁移
        ItemStack stack = newToolStack();
        PlacementConfig config = new PlacementConfig(stack);
        config.setStackInSlot(1, new ItemStack(Blocks.DIRT));
        rootOf(stack).put(PlacementConfig.LEGACY_NBT_ITEMS, new ListTag());

        PlacementConfig reloaded = new PlacementConfig(stack);
        assertThat(reloaded.getStackInSlot(1).is(Blocks.DIRT.asItem())).isTrue();
        assertThat(reloaded.getPresetCount()).isEqualTo(1);
    }

    // ========== hasConfig ==========

    @Test
    void hasConfigFalseForPlainStack() {
        assertThat(PlacementConfig.hasConfig(newToolStack())).isFalse();
    }

    @Test
    void hasConfigTrueAfterConfigCreated() {
        ItemStack stack = newToolStack();
        new PlacementConfig(stack);
        assertThat(PlacementConfig.hasConfig(stack)).isTrue();
    }

    @Test
    void configPersistsAcrossInstances() {
        // 写入的 NBT 挂在 ItemStack 上,新实例能读到
        ItemStack stack = newToolStack();
        PlacementConfig first = new PlacementConfig(stack);
        first.setPlacementMode(PlacementMode.CABLE);
        first.setCableColor(AEColor.RED);

        PlacementConfig second = new PlacementConfig(stack);
        assertThat(second.getPlacementMode()).isEqualTo(PlacementMode.CABLE);
        assertThat(second.getCableColor()).isEqualTo(AEColor.RED);
    }
}
