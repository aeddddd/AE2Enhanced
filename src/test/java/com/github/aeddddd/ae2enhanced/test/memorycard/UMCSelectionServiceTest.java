package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.core.UMCSelectionService;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link UMCSelectionService} 单元测试.
 *
 * <p>Level/Player 用 Mockito mock;连通块搜索(BFS)经 mock 的 getBlockEntity/isLoaded 驱动.
 * 绑定无线访问点只覆盖到绑定写入内存卡,真实网格可用性依赖服务端世界,不在单测范围.</p>
 */
class UMCSelectionServiceTest {

    private static final String DIM_OVERWORLD = "minecraft:overworld";
    private static final BlockPos POS = new BlockPos(1, 64, 1);

    @BeforeAll
    static void bootstrap() {
        UMCTestSupport.bootstrap();
    }

    private static Player mockPlayer(Level level) {
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        return player;
    }

    private static Level mockOverworld() {
        Level level = mock(Level.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        return level;
    }

    private static TranslatableContents captureMessage(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).displayClientMessage(captor.capture(), eq(false));
        return (TranslatableContents) captor.getValue().getContents();
    }

    // ==================== 选取 ====================

    @Test
    void testSelectExistingEntryDeselects() {
        Level level = mockOverworld();
        ItemStack card = UMCTestSupport.newCardStack();
        UniversalMemoryCardItem.addSelection(card,
                new UniversalMemoryCardItem.SelectionEntry(POS, DIM_OVERWORLD, "test.Tile", -1));
        Player player = mockPlayer(level);

        UMCSelectionService.handleSelect(player, card, POS, Direction.NORTH);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.deselect");
        assertThat(UniversalMemoryCardItem.getSelectionCount(card)).isZero();
    }

    @Test
    void testSelectPartAddsEntryWithSide() {
        Level level = mockOverworld();
        IPart part = mock(IPart.class);
        BlockEntity hostBe = mock(BlockEntity.class, withSettings().extraInterfaces(IPartHost.class));
        when(((IPartHost) hostBe).getPart(Direction.EAST)).thenReturn(part);
        when(level.getBlockEntity(POS)).thenReturn(hostBe);

        ItemStack card = UMCTestSupport.newCardStack();
        Player player = mockPlayer(level);

        UMCSelectionService.handleSelect(player, card, POS, Direction.EAST);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.select_part");
        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(card);
        assertThat(selections).hasSize(1);
        UniversalMemoryCardItem.SelectionEntry entry = selections.get(0);
        assertThat(entry.pos).isEqualTo(POS);
        assertThat(entry.dim).isEqualTo(DIM_OVERWORLD);
        assertThat(entry.tileId).isEqualTo(part.getClass().getName());
        assertThat(entry.side).isEqualTo(Direction.EAST.get3DDataValue());
    }

    @Test
    void testSelectTileAddsConnectedBlocksOfSameClass() {
        // 起点与东侧相邻位置各有一个同类型方块实体 -> 连通搜索选出 2 个
        Level level = mockOverworld();
        BlockEntity beA = mock(BlockEntity.class);
        BlockEntity beB = mock(BlockEntity.class);
        when(level.getBlockEntity(any(BlockPos.class))).thenAnswer(inv -> {
            BlockPos pos = inv.getArgument(0);
            if (pos.equals(POS)) {
                return beA;
            }
            if (pos.equals(POS.east())) {
                return beB;
            }
            return null;
        });

        ItemStack card = UMCTestSupport.newCardStack();
        Player player = mockPlayer(level);

        UMCSelectionService.handleSelect(player, card, POS, Direction.NORTH);

        TranslatableContents message = captureMessage(player);
        assertThat(message.getKey()).isEqualTo("gui.ae2enhanced.umc.msg.select_tile");
        assertThat(message.getArgs()[0]).isEqualTo(2);

        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(card);
        assertThat(selections).hasSize(2);
        assertThat(selections.get(0).pos).isEqualTo(POS);
        assertThat(selections.get(0).side).isEqualTo(-1);
        assertThat(selections.get(0).tileId).isEqualTo(beA.getClass().getName());
        assertThat(selections.get(1).pos).isEqualTo(POS.east());
    }

    @Test
    void testSelectPlainBlockAddsEntryWithBlockId() {
        Level level = mockOverworld();
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(null);
        when(level.getBlockState(POS)).thenReturn(Blocks.STONE.defaultBlockState());

        ItemStack card = UMCTestSupport.newCardStack();
        Player player = mockPlayer(level);

        UMCSelectionService.handleSelect(player, card, POS, Direction.NORTH);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.select_block");
        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(card);
        assertThat(selections).hasSize(1);
        assertThat(selections.get(0).tileId).isEqualTo("minecraft:stone");
        assertThat(selections.get(0).side).isEqualTo(-1);
    }

    // ==================== 无线访问点绑定 ====================

    @Test
    void testBindAccessPointRejectsNonAccessPoint() {
        Level level = mockOverworld();
        when(level.getBlockEntity(POS)).thenReturn(mock(BlockEntity.class));
        ItemStack card = UMCTestSupport.newCardStack();
        Player player = mockPlayer(level);

        UMCSelectionService.handleBindAccessPoint(player, card, POS);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.bind_invalid_ap");
        assertThat(UMCNetworkLink.isLinked(card)).isFalse();
    }

    @Test
    void testBindAccessPointRejectsOfflineGrid() {
        Level level = mockOverworld();
        BlockEntity apBe = mock(BlockEntity.class,
                withSettings().extraInterfaces(IWirelessAccessPoint.class));
        when(((IWirelessAccessPoint) apBe).getGrid()).thenReturn(null);
        when(level.getBlockEntity(POS)).thenReturn(apBe);

        ItemStack card = UMCTestSupport.newCardStack();
        Player player = mockPlayer(level);

        UMCSelectionService.handleBindAccessPoint(player, card, POS);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.bind_ap_offline");
        assertThat(UMCNetworkLink.isLinked(card)).isFalse();
    }

    @Test
    void testBindAccessPointLinksPosition() {
        Level level = mockOverworld();
        BlockEntity apBe = mock(BlockEntity.class,
                withSettings().extraInterfaces(IWirelessAccessPoint.class));
        when(((IWirelessAccessPoint) apBe).getGrid()).thenReturn(mock(IGrid.class));
        when(level.getBlockEntity(POS)).thenReturn(apBe);

        ItemStack card = UMCTestSupport.newCardStack();
        Player player = mockPlayer(level);

        UMCSelectionService.handleBindAccessPoint(player, card, POS);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.bind_success");
        GlobalPos linked = UMCNetworkLink.getLinkedPos(card);
        assertThat(linked).isNotNull();
        assertThat(linked.dimension()).isEqualTo(Level.OVERWORLD);
        assertThat(linked.pos()).isEqualTo(POS);
    }

    // ==================== 解除绑定 ====================

    @Test
    void testClearBindingWithoutLinkShowsNoBinding() {
        ItemStack card = UMCTestSupport.newCardStack();
        Player player = mockPlayer(mockOverworld());

        UMCSelectionService.handleClearBinding(player, card);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.no_binding");
    }

    @Test
    void testClearBindingUnlinksCard() {
        ItemStack card = UMCTestSupport.newCardStack();
        UMCNetworkLink.link(card, GlobalPos.of(Level.OVERWORLD, POS));
        Player player = mockPlayer(mockOverworld());

        UMCSelectionService.handleClearBinding(player, card);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.binding_cleared");
        assertThat(UMCNetworkLink.isLinked(card)).isFalse();
    }
}
