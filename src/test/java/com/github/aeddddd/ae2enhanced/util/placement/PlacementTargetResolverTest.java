package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.FacadeItem;
import appeng.items.parts.PartItem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link PlacementTargetResolver} 单元测试.
 * <p>createCableOfColor 的正路径依赖 AE2 注册表（AEParts）,仅在游戏运行时可用,此处只测非线缆入参；
 * 其余解析逻辑用 mock + 真实原版物品覆盖.</p>
 */
class PlacementTargetResolverTest {

    /** 假线缆 Part 类型（实现 ICablePart）. */
    private interface DummyCablePart extends ICablePart {
    }

    /** 假非线缆 Part 类型（只实现 IPart）. */
    private interface DummyPlainPart extends IPart {
    }

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private static ItemStack stackOf(Item item) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(item);
        return stack;
    }

    private static PartItem<?> partItemOf(Class<?> partClass) {
        PartItem<?> partItem = mock(PartItem.class);
        when(partItem.getPartClass()).thenAnswer(inv -> partClass);
        return partItem;
    }

    // ========== isPlaceable ==========

    @Test
    void isPlaceableChecks() {
        assertThat(PlacementTargetResolver.isPlaceable(ItemStack.EMPTY)).isFalse();
        assertThat(PlacementTargetResolver.isPlaceable(stackOf(mock(BlockItem.class)))).isTrue();
        assertThat(PlacementTargetResolver.isPlaceable(stackOf(mock(PartItem.class)))).isTrue();
        assertThat(PlacementTargetResolver.isPlaceable(stackOf(mock(FacadeItem.class)))).isTrue();
        assertThat(PlacementTargetResolver.isPlaceable(stackOf(Items.STICK))).isFalse();
    }

    // ========== 线缆类型判定 ==========

    @Test
    void getCablePartClassRejectsNonCable() {
        assertThat(PlacementTargetResolver.getCablePartClass(ItemStack.EMPTY)).isNull();
        assertThat(PlacementTargetResolver.getCablePartClass(stackOf(Items.STICK))).isNull();
        assertThat(PlacementTargetResolver.getCablePartClass(stackOf(partItemOf(DummyPlainPart.class))))
                .isNull();
    }

    @Test
    void getCablePartClassAcceptsCable() {
        ItemStack cable = stackOf(partItemOf(DummyCablePart.class));
        assertThat(PlacementTargetResolver.getCablePartClass(cable)).isEqualTo(DummyCablePart.class);
        assertThat(PlacementTargetResolver.isCable(cable)).isTrue();
    }

    @Test
    void isSameCableTypeIgnoresColor() {
        // 两个不同的物品实例,只要 Part 类相同即为同类型线缆
        ItemStack a = stackOf(partItemOf(DummyCablePart.class));
        ItemStack b = stackOf(partItemOf(DummyCablePart.class));
        ItemStack plain = stackOf(partItemOf(DummyPlainPart.class));
        assertThat(PlacementTargetResolver.isSameCableType(a, b)).isTrue();
        assertThat(PlacementTargetResolver.isSameCableType(a, plain)).isFalse();
        assertThat(PlacementTargetResolver.isSameCableType(a, ItemStack.EMPTY)).isFalse();
    }

    @Test
    void getCableColorReadsColoredPartItem() {
        ColoredPartItem<?> colored = mock(ColoredPartItem.class);
        when(colored.getColor()).thenReturn(AEColor.LIME);
        assertThat(PlacementTargetResolver.getCableColor(stackOf(colored))).isEqualTo(AEColor.LIME);
        // 非染色 Part 物品回退 TRANSPARENT
        assertThat(PlacementTargetResolver.getCableColor(stackOf(Items.STICK)))
                .isEqualTo(AEColor.TRANSPARENT);
    }

    @Test
    void createCableOfColorRejectsNonCable() {
        // 非线缆直接返回 EMPTY,不触碰 AE2 注册表
        assertThat(PlacementTargetResolver.createCableOfColor(ItemStack.EMPTY, AEColor.RED).isEmpty()).isTrue();
        assertThat(PlacementTargetResolver.createCableOfColor(stackOf(Items.STICK), AEColor.RED).isEmpty())
                .isTrue();
    }

    @Test
    void findCableOfTypeRejectsNonCable() {
        MEStorage storage = mock(MEStorage.class);
        assertThat(PlacementTargetResolver.findCableOfType(storage, stackOf(Items.STICK))).isNull();
    }

    // ========== resolveSingleOrCable ==========

    @Test
    void resolveSinglePrefersOffhand() {
        Player player = mock(Player.class);
        ItemStack offhand = new ItemStack(Blocks.STONE);
        when(player.getOffhandItem()).thenReturn(offhand);
        PlacementConfig config = new PlacementConfig(new ItemStack(Items.STICK));
        config.setStackInSlot(0, new ItemStack(Blocks.DIRT));
        config.setSelectedSlot(0);

        ItemStack resolved = PlacementTargetResolver.resolveSingleOrCable(player, config, mock(Level.class),
                BlockPos.ZERO);
        // 副手优先,且返回副本
        assertThat(resolved.is(Blocks.STONE.asItem())).isTrue();
        assertThat(resolved).isNotSameAs(offhand);
    }

    @Test
    void resolveSingleFallsBackToPreset() {
        Player player = mock(Player.class);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        PlacementConfig config = new PlacementConfig(new ItemStack(Items.STICK));
        config.setStackInSlot(2, new ItemStack(Blocks.DIRT));
        config.setSelectedSlot(2);

        ItemStack resolved = PlacementTargetResolver.resolveSingleOrCable(player, config, mock(Level.class),
                BlockPos.ZERO);
        assertThat(resolved.is(Blocks.DIRT.asItem())).isTrue();
    }

    @Test
    void resolveSingleEmptyWhenNothingConfigured() {
        Player player = mock(Player.class);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        PlacementConfig config = new PlacementConfig(new ItemStack(Items.STICK));
        config.setSelectedSlot(-1);

        ItemStack resolved = PlacementTargetResolver.resolveSingleOrCable(player, config, mock(Level.class),
                BlockPos.ZERO);
        assertThat(resolved.isEmpty()).isTrue();
    }

    // ========== resolveBulk ==========

    @Test
    void resolveBulkPrefersOffhandBlockItem() {
        Player player = mock(Player.class);
        when(player.getOffhandItem()).thenReturn(new ItemStack(Blocks.STONE));

        ItemStack resolved = PlacementTargetResolver.resolveBulk(player, mock(Level.class), BlockPos.ZERO);
        assertThat(resolved.is(Blocks.STONE.asItem())).isTrue();
    }

    @Test
    void resolveBulkNonBlockOffhandIgnored() {
        // 副手不是 BlockItem（木棍）→ 走被点击方块路径
        Player player = mock(Player.class);
        when(player.getOffhandItem()).thenReturn(new ItemStack(Items.STICK));
        Level level = mock(Level.class);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE.defaultBlockState());

        ItemStack resolved = PlacementTargetResolver.resolveBulk(player, level, BlockPos.ZERO);
        assertThat(resolved.is(Blocks.STONE.asItem())).isTrue();
    }

    @Test
    void resolveBulkClickedAirReturnsEmpty() {
        Player player = mock(Player.class);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        Level level = mock(Level.class);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.AIR.defaultBlockState());

        ItemStack resolved = PlacementTargetResolver.resolveBulk(player, level, BlockPos.ZERO);
        assertThat(resolved.isEmpty()).isTrue();
    }

    // ========== pickRepresentativeStack ==========

    @Test
    void pickRepresentativeStackFallsBackToBlock() {
        Level level = mock(Level.class);
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(null);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE.defaultBlockState());

        ItemStack pick = PlacementTargetResolver.pickRepresentativeStack(level, BlockPos.ZERO);
        assertThat(pick.is(Blocks.STONE.asItem())).isTrue();
    }

    @Test
    void pickRepresentativeStackIgnoresHostWithoutCenterPart() {
        // BlockEntity 是 IPartHost 但没有中心 Part → 回退到方块本身的 pick
        BlockEntity hostBe = mock(BlockEntity.class, withSettings().extraInterfaces(IPartHost.class));
        IPartHost host = (IPartHost) hostBe;
        when(host.getPart(Mockito.<net.minecraft.core.Direction>isNull())).thenReturn(null);

        Level level = mock(Level.class);
        when(level.getBlockEntity(any(BlockPos.class))).thenReturn(hostBe);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE.defaultBlockState());

        ItemStack pick = PlacementTargetResolver.pickRepresentativeStack(level, BlockPos.ZERO);
        assertThat(pick.is(Blocks.STONE.asItem())).isTrue();
    }
}
