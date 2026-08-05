package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.util.IdentityHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.helpers.IPriorityHost;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.core.MemoryCardHandlerRegistry;
import com.github.aeddddd.ae2enhanced.memorycard.core.UMCPasteService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link UMCPasteService} 单元测试.
 *
 * <p>单个粘贴的各种结果分支用可配置结果的假 handler 覆盖;批量粘贴经选区 NBT 触发,
 * 目标解析(含按 side 解析线缆部件)用 mock Level 提供.真实 ME 网络请求不在单测范围.</p>
 */
class UMCPasteServiceTest {

    private static final String DIM_OVERWORLD = "minecraft:overworld";

    /** 假设备标记接口.必须 public,mockito 生成的代理类在其他包. */
    public interface FakeDevice {
    }

    /** 按目标实例返回不同粘贴结果的假 handler. */
    private static class FakePasteHandler implements IMemoryCardHandler {
        private final Map<Object, PasteResult> results = new IdentityHashMap<>();
        private PasteResult defaultResult = PasteResult.SUCCESS;

        @Override
        public boolean canHandle(Object target) {
            return target instanceof FakeDevice;
        }

        @Override
        public CompoundTag copy(Object target) {
            return new CompoundTag();
        }

        @Override
        public PasteResult paste(Object target, CompoundTag data, Player player) {
            return results.getOrDefault(target, defaultResult);
        }

        @Override
        public String getDisplayName(Object target) {
            return "假设备";
        }
    }

    private static FakePasteHandler fakeHandler;

    @BeforeAll
    static void bootstrap() {
        UMCTestSupport.bootstrap();
        fakeHandler = new FakePasteHandler();
        MemoryCardHandlerRegistry.register(fakeHandler);
    }

    private static Player mockPlayer(Level level) {
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        return player;
    }

    /** mock 主世界维度,默认无方块实体. */
    private static Level mockOverworld() {
        Level level = mock(Level.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        return level;
    }

    private static BlockEntity mockDevice() {
        return mock(BlockEntity.class, withSettings().extraInterfaces(FakeDevice.class));
    }

    /** 带基础配置的内存卡,data 为传入的配置数据. */
    private static ItemStack cardWithConfig(CompoundTag data) {
        ItemStack card = UMCTestSupport.newCardStack();
        UniversalMemoryCardItem.setConfig(card, "ae2_part", "假设备", data);
        return card;
    }

    private static void handlePaste(Player player, ItemStack stack, BlockPos pos, Direction face) {
        ModList modList = mock(ModList.class);
        when(modList.isLoaded(anyString())).thenReturn(false);
        try (MockedStatic<ModList> mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            UMCPasteService.handlePaste(player, stack, pos, face);
        }
    }

    private static TranslatableContents captureMessage(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).displayClientMessage(captor.capture(), eq(false));
        return (TranslatableContents) captor.getValue().getContents();
    }

    @Test
    void testPasteWithoutConfigShowsNoConfig() {
        Player player = mockPlayer(mockOverworld());

        handlePaste(player, UMCTestSupport.newCardStack(), BlockPos.ZERO, Direction.NORTH);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.no_config");
    }

    @Test
    void testPasteWithoutTargetShowsInvalid() {
        Level level = mockOverworld();
        Player player = mockPlayer(level);

        handlePaste(player, cardWithConfig(new CompoundTag()), BlockPos.ZERO, Direction.NORTH);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.paste_invalid");
    }

    @Test
    void testPasteUnsupportedTargetShowsUnsupported() {
        Level level = mockOverworld();
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(mock(BlockEntity.class));
        Player player = mockPlayer(level);

        handlePaste(player, cardWithConfig(new CompoundTag()), BlockPos.ZERO, Direction.NORTH);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.paste_unsupported");
    }

    @Test
    void testPasteSuccessShowsSuccessWithDisplayName() {
        Level level = mockOverworld();
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(mockDevice());
        Player player = mockPlayer(level);
        fakeHandler.defaultResult = PasteResult.SUCCESS;

        handlePaste(player, cardWithConfig(new CompoundTag()), BlockPos.ZERO, Direction.NORTH);

        TranslatableContents message = captureMessage(player);
        assertThat(message.getKey()).isEqualTo("gui.ae2enhanced.umc.msg.paste_success");
        assertThat(message.getArgs()[0]).isEqualTo("假设备");
    }

    @Test
    void testPasteMissingUpgradesWithoutUpgradeDataShowsEmptyRequirement() {
        Level level = mockOverworld();
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(mockDevice());
        Player player = mockPlayer(level);
        fakeHandler.defaultResult = PasteResult.MISSING_UPGRADES;

        handlePaste(player, cardWithConfig(new CompoundTag()), BlockPos.ZERO, Direction.NORTH);

        TranslatableContents message = captureMessage(player);
        assertThat(message.getKey()).isEqualTo("gui.ae2enhanced.umc.msg.missing_upgrades");
        assertThat(message.getArgs()[0]).isEqualTo("");
    }

    @Test
    void testPasteMissingUpgradesListsUpgradeNames() {
        Level level = mockOverworld();
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(mockDevice());
        Player player = mockPlayer(level);
        fakeHandler.defaultResult = PasteResult.MISSING_UPGRADES;

        // 配置数据内含两个钻石升级卡,消息应列出名称与数量
        CompoundTag data = new CompoundTag();
        ListTag upgrades = new ListTag();
        CompoundTag entry = new ItemStack(Items.DIAMOND, 2).save(new CompoundTag());
        entry.putInt("Slot", 0);
        upgrades.add(entry);
        data.put("ae2e:upgrades", upgrades);

        handlePaste(player, cardWithConfig(data), BlockPos.ZERO, Direction.NORTH);

        TranslatableContents message = captureMessage(player);
        assertThat(message.getKey()).isEqualTo("gui.ae2enhanced.umc.msg.missing_upgrades");
        assertThat((String) message.getArgs()[0]).contains("×2");
    }

    @Test
    void testPasteInvalidMachineResultShowsInvalidMachine() {
        Level level = mockOverworld();
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(mockDevice());
        Player player = mockPlayer(level);
        fakeHandler.defaultResult = PasteResult.INVALID_MACHINE;

        handlePaste(player, cardWithConfig(new CompoundTag()), BlockPos.ZERO, Direction.NORTH);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.invalid_machine");
    }

    @Test
    void testPasteFailedResultShowsFailed() {
        Level level = mockOverworld();
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(mockDevice());
        Player player = mockPlayer(level);
        fakeHandler.defaultResult = PasteResult.FAILED;

        handlePaste(player, cardWithConfig(new CompoundTag()), BlockPos.ZERO, Direction.NORTH);

        assertThat(captureMessage(player).getKey()).isEqualTo("gui.ae2enhanced.umc.msg.paste_failed");
    }

    @Test
    void testBulkPasteCountsSuccessAndFailure() {
        BlockPos posA = new BlockPos(1, 64, 1);
        BlockPos posB = new BlockPos(2, 64, 1);
        BlockPos posC = new BlockPos(3, 64, 1);

        BlockEntity deviceA = mockDevice();
        BlockEntity deviceB = mockDevice();
        fakeHandler.results.put(deviceA, PasteResult.SUCCESS);
        fakeHandler.results.put(deviceB, PasteResult.FAILED);

        Level level = mockOverworld();
        when(level.getBlockEntity(posA)).thenReturn(deviceA);
        when(level.getBlockEntity(posB)).thenReturn(deviceB);

        ItemStack card = cardWithConfig(new CompoundTag());
        UniversalMemoryCardItem.addSelection(card,
                new UniversalMemoryCardItem.SelectionEntry(posA, DIM_OVERWORLD, "fake.A", -1));
        UniversalMemoryCardItem.addSelection(card,
                new UniversalMemoryCardItem.SelectionEntry(posB, DIM_OVERWORLD, "fake.B", -1));
        // 其他维度的选区应被跳过
        UniversalMemoryCardItem.addSelection(card,
                new UniversalMemoryCardItem.SelectionEntry(posC, "minecraft:the_nether", "fake.C", -1));

        Player player = mockPlayer(level);
        handlePaste(player, card, posA, Direction.NORTH);

        TranslatableContents message = captureMessage(player);
        assertThat(message.getKey()).isEqualTo("gui.ae2enhanced.umc.msg.bulk_success");
        assertThat(message.getArgs()[0]).isEqualTo(1);
        assertThat(message.getArgs()[1]).isEqualTo(1);
    }

    @Test
    void testBulkPasteResolvesPartBySelectionSide() {
        // 选区 side >= 0 且方块实体为线缆宿主时,按面解析部件再走 AE2PartHandler
        BlockPos pos = new BlockPos(5, 64, 5);
        IPart part = mock(IPart.class, withSettings().extraInterfaces(IPriorityHost.class));
        BlockEntity hostBe = mock(BlockEntity.class, withSettings().extraInterfaces(IPartHost.class));
        when(((IPartHost) hostBe).getPart(Direction.UP)).thenReturn(part);

        Level level = mockOverworld();
        when(level.getBlockEntity(pos)).thenReturn(hostBe);

        CompoundTag data = new CompoundTag();
        data.putInt("priority", 11);
        ItemStack card = cardWithConfig(data);
        UniversalMemoryCardItem.addSelection(card,
                new UniversalMemoryCardItem.SelectionEntry(pos, DIM_OVERWORLD, "fake.Part",
                        Direction.UP.get3DDataValue()));

        Player player = mockPlayer(level);
        handlePaste(player, card, pos, Direction.NORTH);

        TranslatableContents message = captureMessage(player);
        assertThat(message.getKey()).isEqualTo("gui.ae2enhanced.umc.msg.bulk_success");
        assertThat(message.getArgs()[0]).isEqualTo(1);
        assertThat(message.getArgs()[1]).isEqualTo(0);
        // 优先级确实写入了部件
        verify((IPriorityHost) part).setPriority(11);
    }
}
