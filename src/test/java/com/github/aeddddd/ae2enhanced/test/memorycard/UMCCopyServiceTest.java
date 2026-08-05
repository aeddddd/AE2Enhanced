package com.github.aeddddd.ae2enhanced.test.memorycard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartItem;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.core.UMCCopyService;

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
 * {@link UMCCopyService} 单元测试.
 *
 * <p>Level/Player 用 Mockito mock;目标方块实体经 mock + extraInterfaces 附加标记接口,
 * 自定义假 handler 只匹配该标记,不干扰注册表中的 AE2 handler.
 * 复制成功路径会调用 handler.getDisplayName,IPart 的显示名依赖已注册的 PartItem,
 * 因此测试环境注册了一个 mock PartItem.</p>
 */
class UMCCopyServiceTest {

    /** 假设备标记接口,供自定义 handler 匹配.必须 public,mockito 生成的代理类在其他包. */
    public interface FakeDevice {
    }

    /** 可配置复制结果的假 handler. */
    private static class FakeCopyHandler implements IMemoryCardHandler {
        private CompoundTag copyResult = new CompoundTag();

        @Override
        public boolean canHandle(Object target) {
            return target instanceof FakeDevice;
        }

        @Override
        public CompoundTag copy(Object target) {
            return copyResult;
        }

        @Override
        public PasteResult paste(Object target, CompoundTag data, Player player) {
            return PasteResult.SUCCESS;
        }

        @Override
        public String getDisplayName(Object target) {
            return "假设备";
        }
    }

    private static final BlockPos POS = new BlockPos(1, 64, 1);
    private static FakeCopyHandler fakeHandler;

    @BeforeAll
    static void bootstrap() {
        UMCTestSupport.bootstrap();
        fakeHandler = new FakeCopyHandler();
        com.github.aeddddd.ae2enhanced.memorycard.core.MemoryCardHandlerRegistry.register(fakeHandler);
    }

    /** 注册一个真实的 PartItem(mock 物品缺少 intrusive holder,无法注册),供 IPart.getPartItem() 返回. */
    private static PartItem<IPart> registerFakePartItem(String id) {
        PartItem<IPart> partItem = new PartItem<IPart>(new net.minecraft.world.item.Item.Properties(),
                IPart.class, item -> null);
        ForgeRegistries.ITEMS.register(new ResourceLocation("ae2enhanced", id), partItem);
        return partItem;
    }

    private static Player mockPlayer(Level level) {
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        return player;
    }

    private static Level mockLevel(BlockEntity be) {
        Level level = mock(Level.class);
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(be);
        return level;
    }

    private static BlockEntity mockDevice() {
        return mock(BlockEntity.class, withSettings().extraInterfaces(FakeDevice.class));
    }

    /** 在静态 mock 的 ModList 下执行复制(注册表 init 需要). */
    private static void handleCopy(Player player, ItemStack stack, BlockPos pos, Direction face) {
        ModList modList = mock(ModList.class);
        when(modList.isLoaded(anyString())).thenReturn(false);
        try (MockedStatic<ModList> mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            UMCCopyService.handleCopy(player, stack, pos, face);
        }
    }

    /** 捕获唯一一条客户端消息并返回其翻译键. */
    private static String captureMessageKey(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).displayClientMessage(captor.capture(), eq(false));
        return ((TranslatableContents) captor.getValue().getContents()).getKey();
    }

    @Test
    void testCopyWithoutTargetShowsInvalid() {
        Player player = mockPlayer(mockLevel(null));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_invalid");
        assertThat(UniversalMemoryCardItem.hasConfig(card)).isFalse();
    }

    @Test
    void testCopyUnsupportedTargetShowsUnsupported() {
        Player player = mockPlayer(mockLevel(mock(BlockEntity.class)));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_unsupported");
        assertThat(UniversalMemoryCardItem.hasConfig(card)).isFalse();
    }

    @Test
    void testCopyEmptyResultShowsEmpty() {
        fakeHandler.copyResult = new CompoundTag();
        Player player = mockPlayer(mockLevel(mockDevice()));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_empty");
        assertThat(UniversalMemoryCardItem.hasConfig(card)).isFalse();
    }

    @Test
    void testCopyNullResultShowsEmpty() {
        fakeHandler.copyResult = null;
        Player player = mockPlayer(mockLevel(mockDevice()));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_empty");
        assertThat(UniversalMemoryCardItem.hasConfig(card)).isFalse();
    }

    @Test
    void testCopyCustomDeviceWritesConfigWithCustomHandlerId() {
        CompoundTag data = new CompoundTag();
        data.putInt("mode", 3);
        fakeHandler.copyResult = data;
        Player player = mockPlayer(mockLevel(mockDevice()));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_success");
        CompoundTag config = UniversalMemoryCardItem.getConfig(card);
        assertThat(config.getString("handler")).isEqualTo("ae2e_custom");
        assertThat(config.getString("name")).isEqualTo("假设备");
        assertThat(config.getCompound("data").getInt("mode")).isEqualTo(3);
    }

    @Test
    void testCopyPartOnHostUsesPartHandlerId() {
        // 线缆宿主 + 指定面上的带优先级部件 -> AE2PartHandler 复制出 priority
        PartItem<IPart> partItem = registerFakePartItem("test_umc_copy_part");
        IPart part = mock(IPart.class, withSettings().extraInterfaces(IPriorityHost.class));
        org.mockito.Mockito.doReturn(partItem).when(part).getPartItem();
        when(((IPriorityHost) part).getPriority()).thenReturn(7);

        BlockEntity hostBe = mock(BlockEntity.class, withSettings().extraInterfaces(IPartHost.class));
        when(((IPartHost) hostBe).getPart(Direction.NORTH)).thenReturn(part);

        Player player = mockPlayer(mockLevel(hostBe));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_success");
        CompoundTag config = UniversalMemoryCardItem.getConfig(card);
        assertThat(config.getString("handler")).isEqualTo("ae2_part");
        assertThat(config.getCompound("data").getInt("priority")).isEqualTo(7);
    }

    @Test
    void testCopyHostWithoutPartFallsBackToBlockEntity() {
        // 宿主指定面上没有部件 -> 回退到方块实体本身(无 handler -> 不支持)
        BlockEntity hostBe = mock(BlockEntity.class, withSettings().extraInterfaces(IPartHost.class));
        when(((IPartHost) hostBe).getPart(any(Direction.class))).thenReturn(null);

        Player player = mockPlayer(mockLevel(hostBe));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_unsupported");
    }

    @Test
    void testCopyAe2TileUsesTileHandlerId() {
        AEBaseBlockEntity tile = mock(AEBaseBlockEntity.class,
                withSettings().extraInterfaces(IPriorityHost.class));
        when(((IPriorityHost) tile).getPriority()).thenReturn(3);
        when(tile.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());

        Player player = mockPlayer(mockLevel(tile));
        ItemStack card = UMCTestSupport.newCardStack();

        handleCopy(player, card, POS, Direction.NORTH);

        assertThat(captureMessageKey(player)).isEqualTo("gui.ae2enhanced.umc.msg.copy_success");
        CompoundTag config = UniversalMemoryCardItem.getConfig(card);
        assertThat(config.getString("handler")).isEqualTo("ae2_tile");
        assertThat(config.getCompound("data").getInt("priority")).isEqualTo(3);
    }
}
