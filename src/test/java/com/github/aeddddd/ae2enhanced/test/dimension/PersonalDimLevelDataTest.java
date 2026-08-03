package com.github.aeddddd.ae2enhanced.test.dimension;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.timers.TimerQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimLevelData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PersonalDimLevelData} 单元测试.
 *
 * <p>主世界数据用 Mockito mock,验证委托读取与独立时间/天气字段.</p>
 */
class PersonalDimLevelDataTest {

    private ServerLevelData wrapped;
    private PersonalDimLevelData data;

    @BeforeEach
    void setUp() {
        wrapped = mock(ServerLevelData.class);
        data = new PersonalDimLevelData(wrapped);
    }

    @Test
    void shouldDelegateReadOnlySharedProperties() {
        GameRules gameRules = new GameRules();
        WorldBorder.Settings border = mock(WorldBorder.Settings.class);
        @SuppressWarnings("unchecked")
        TimerQueue<MinecraftServer> timerQueue = mock(TimerQueue.class);

        when(wrapped.getLevelName()).thenReturn("world");
        when(wrapped.getXSpawn()).thenReturn(1);
        when(wrapped.getYSpawn()).thenReturn(2);
        when(wrapped.getZSpawn()).thenReturn(3);
        when(wrapped.getSpawnAngle()).thenReturn(0.5f);
        when(wrapped.getGameTime()).thenReturn(123456L);
        when(wrapped.isHardcore()).thenReturn(true);
        when(wrapped.getGameRules()).thenReturn(gameRules);
        when(wrapped.getDifficulty()).thenReturn(Difficulty.HARD);
        when(wrapped.isDifficultyLocked()).thenReturn(true);
        when(wrapped.getGameType()).thenReturn(GameType.CREATIVE);
        when(wrapped.getAllowCommands()).thenReturn(true);
        when(wrapped.getWorldBorder()).thenReturn(border);
        when(wrapped.getScheduledEvents()).thenReturn(timerQueue);

        assertThat(data.getLevelName()).isEqualTo("world");
        assertThat(data.getXSpawn()).isEqualTo(1);
        assertThat(data.getYSpawn()).isEqualTo(2);
        assertThat(data.getZSpawn()).isEqualTo(3);
        assertThat(data.getSpawnAngle()).isEqualTo(0.5f);
        assertThat(data.getGameTime()).isEqualTo(123456L);
        assertThat(data.isHardcore()).isTrue();
        assertThat(data.getGameRules()).isSameAs(gameRules);
        assertThat(data.getDifficulty()).isEqualTo(Difficulty.HARD);
        assertThat(data.isDifficultyLocked()).isTrue();
        assertThat(data.getGameType()).isEqualTo(GameType.CREATIVE);
        assertThat(data.getAllowCommands()).isTrue();
        assertThat(data.getWorldBorder()).isSameAs(border);
        assertThat(data.getScheduledEvents()).isSameAs(timerQueue);
    }

    @Test
    void dayTimeShouldBeIndependentFromWrapped() {
        when(wrapped.getDayTime()).thenReturn(9999L);

        // 初始值不取自主世界
        assertThat(data.getDayTime()).isZero();

        data.setDayTime(1200L);

        assertThat(data.getDayTime()).isEqualTo(1200L);
        verify(wrapped, never()).setDayTime(1200L);
    }

    @Test
    void setGameTimeShouldNotPropagateToWrapped() {
        data.setGameTime(42L);

        verify(wrapped, never()).setGameTime(42L);
    }

    @Test
    void weatherShouldBeIndependentFromWrapped() {
        data.setRaining(true);
        data.setThundering(true);
        data.setRainTime(100);
        data.setThunderTime(200);
        data.setClearWeatherTime(300);

        assertThat(data.isRaining()).isTrue();
        assertThat(data.isThundering()).isTrue();
        assertThat(data.getRainTime()).isEqualTo(100);
        assertThat(data.getThunderTime()).isEqualTo(200);
        assertThat(data.getClearWeatherTime()).isEqualTo(300);

        verify(wrapped, never()).setRaining(true);
        verify(wrapped, never()).setThundering(true);
        verify(wrapped, never()).setRainTime(100);
        verify(wrapped, never()).setThunderTime(200);
        verify(wrapped, never()).setClearWeatherTime(300);
    }

    @Test
    void spawnSettersShouldBeNoOp() {
        data.setSpawn(new BlockPos(1, 2, 3), 1.0f);
        data.setXSpawn(10);
        data.setYSpawn(20);
        data.setZSpawn(30);
        data.setSpawnAngle(2.0f);

        verify(wrapped, never()).setSpawn(new BlockPos(1, 2, 3), 1.0f);
        verify(wrapped, never()).setXSpawn(10);
        verify(wrapped, never()).setYSpawn(20);
        verify(wrapped, never()).setZSpawn(30);
        verify(wrapped, never()).setSpawnAngle(2.0f);
    }

    @Test
    void gameTypeAndWorldBorderSettersShouldBeNoOp() {
        data.setGameType(GameType.SURVIVAL);
        data.setWorldBorder(mock(WorldBorder.Settings.class));

        verify(wrapped, never()).setGameType(GameType.SURVIVAL);
        verify(wrapped, never()).setWorldBorder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void initializedShouldDefaultTrueAndBeSettable() {
        assertThat(data.isInitialized()).isTrue();

        data.setInitialized(false);

        assertThat(data.isInitialized()).isFalse();
        verify(wrapped, never()).setInitialized(false);
    }

    @Test
    void wanderingTraderShouldBeDisabled() {
        assertThat(data.getWanderingTraderSpawnDelay()).isZero();
        assertThat(data.getWanderingTraderSpawnChance()).isZero();
        assertThat(data.getWanderingTraderId()).isNull();

        // setter 为空操作,不抛异常
        data.setWanderingTraderSpawnDelay(100);
        data.setWanderingTraderSpawnChance(50);
        data.setWanderingTraderId(UUID.randomUUID());

        assertThat(data.getWanderingTraderSpawnDelay()).isZero();
        assertThat(data.getWanderingTraderSpawnChance()).isZero();
        assertThat(data.getWanderingTraderId()).isNull();
    }
}
