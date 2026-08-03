package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UniversalMemoryCardItem} NBT 辅助方法与 {@link UMCNetworkLink} 绑定的纯单测:
 * 配置快照读写/清除、选区增删去重、模式读写、无线访问点绑定 GlobalPos 序列化.
 * 需要真实世界/网格的复制粘贴与合成请求不在单测范围.
 */
class UniversalMemoryCardItemTest {

    private ItemStack stack;

    @BeforeAll
    static void bootstrap() {
        UMCTestSupport.bootstrap();
    }

    @BeforeEach
    void setUp() {
        stack = UMCTestSupport.newCardStack();
    }

    // ==================== 配置快照 ====================

    @Test
    void testConfigRoundTrip() {
        assertThat(UniversalMemoryCardItem.hasConfig(stack)).isFalse();
        assertThat(UniversalMemoryCardItem.getConfig(stack)).isNull();

        CompoundTag data = new CompoundTag();
        data.putInt("priority", 7);
        UniversalMemoryCardItem.setConfig(stack, "ae2_part", "测试设备", data);

        assertThat(UniversalMemoryCardItem.hasConfig(stack)).isTrue();
        CompoundTag config = UniversalMemoryCardItem.getConfig(stack);
        assertThat(config.getString("handler")).isEqualTo("ae2_part");
        assertThat(config.getString("name")).isEqualTo("测试设备");
        assertThat(config.getCompound("data").getInt("priority")).isEqualTo(7);

        UniversalMemoryCardItem.clearConfig(stack);
        assertThat(UniversalMemoryCardItem.hasConfig(stack)).isFalse();
    }

    // ==================== 选区 ====================

    @Test
    void testSelectionAddAndDedupe() {
        BlockPos pos = new BlockPos(1, 64, -3);
        var entry = new UniversalMemoryCardItem.SelectionEntry(pos, "minecraft:overworld", "test.Tile", -1);
        UniversalMemoryCardItem.addSelection(stack, entry);
        // 同位置同维度重复添加应被去重
        UniversalMemoryCardItem.addSelection(stack, entry);
        assertThat(UniversalMemoryCardItem.getSelectionCount(stack)).isEqualTo(1);

        // 不同维度同位置允许共存
        UniversalMemoryCardItem.addSelection(stack,
                new UniversalMemoryCardItem.SelectionEntry(pos, "minecraft:the_nether", "test.Tile", -1));
        assertThat(UniversalMemoryCardItem.getSelectionCount(stack)).isEqualTo(2);

        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(stack);
        assertThat(selections.get(0).pos).isEqualTo(pos);
        assertThat(selections.get(0).dim).isEqualTo("minecraft:overworld");
        assertThat(selections.get(0).tileId).isEqualTo("test.Tile");
        assertThat(selections.get(0).side).isEqualTo(-1);
        assertThat(selections.get(1).dim).isEqualTo("minecraft:the_nether");
    }

    @Test
    void testSelectionRemoveAndClear() {
        UniversalMemoryCardItem.addSelection(stack,
                new UniversalMemoryCardItem.SelectionEntry(new BlockPos(0, 0, 0), "minecraft:overworld", "a", 2));
        UniversalMemoryCardItem.addSelection(stack,
                new UniversalMemoryCardItem.SelectionEntry(new BlockPos(5, 5, 5), "minecraft:overworld", "b", -1));
        assertThat(UniversalMemoryCardItem.getSelectionCount(stack)).isEqualTo(2);

        UniversalMemoryCardItem.removeSelection(stack, 0);
        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(stack);
        assertThat(selections).hasSize(1);
        assertThat(selections.get(0).tileId).isEqualTo("b");
        // side 需要按 Direction.get3DDataValue 原样保留
        UniversalMemoryCardItem.removeSelection(stack, 0);
        assertThat(UniversalMemoryCardItem.getSelectionCount(stack)).isZero();

        // 越界删除不应抛异常
        UniversalMemoryCardItem.removeSelection(stack, 5);
        UniversalMemoryCardItem.clearSelections(stack);
        assertThat(UniversalMemoryCardItem.getSelectionCount(stack)).isZero();
    }

    @Test
    void testSelectionEntryNbtRoundTrip() {
        var entry = new UniversalMemoryCardItem.SelectionEntry(new BlockPos(-10, 70, 33),
                "minecraft:the_end", "appeng.parts.reporting.InterfacePart", 3);
        var restored = UniversalMemoryCardItem.SelectionEntry.fromNBT(entry.toNBT());
        assertThat(restored.pos).isEqualTo(entry.pos);
        assertThat(restored.dim).isEqualTo(entry.dim);
        assertThat(restored.tileId).isEqualTo(entry.tileId);
        assertThat(restored.side).isEqualTo(entry.side);
    }

    // ==================== 模式 ====================

    @Test
    void testModeDefaultAndSet() {
        assertThat(UniversalMemoryCardItem.getMode(stack)).isEqualTo(UniversalMemoryCardItem.Mode.CONFIG_COPY);
        UniversalMemoryCardItem.setMode(stack, UniversalMemoryCardItem.Mode.CONFIG_COPY);
        assertThat(UniversalMemoryCardItem.getMode(stack)).isEqualTo(UniversalMemoryCardItem.Mode.CONFIG_COPY);
    }

    // ==================== 无线访问点绑定 ====================

    @Test
    void testNetworkLinkRoundTrip() {
        assertThat(UMCNetworkLink.isLinked(stack)).isFalse();
        assertThat(UMCNetworkLink.getLinkedPos(stack)).isNull();

        GlobalPos pos = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 65, -40));
        UMCNetworkLink.link(stack, pos);

        assertThat(UMCNetworkLink.isLinked(stack)).isTrue();
        GlobalPos restored = UMCNetworkLink.getLinkedPos(stack);
        assertThat(restored).isNotNull();
        assertThat(restored.dimension()).isEqualTo(Level.OVERWORLD);
        assertThat(restored.pos()).isEqualTo(new BlockPos(12, 65, -40));

        UMCNetworkLink.unlink(stack);
        assertThat(UMCNetworkLink.isLinked(stack)).isFalse();
    }

    @Test
    void testIsUniversalMemoryCard() {
        assertThat(UniversalMemoryCardItem.isUniversalMemoryCard(stack)).isTrue();
        assertThat(UniversalMemoryCardItem.isUniversalMemoryCard(ItemStack.EMPTY)).isFalse();
        assertThat(UniversalMemoryCardItem.isUniversalMemoryCard(
                new ItemStack(net.minecraft.world.item.Items.DIAMOND))).isFalse();
    }
}
