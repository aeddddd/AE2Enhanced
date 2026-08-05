package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

/**
 * {@link PersonalDimensionManager} 可单测部分:维度键推导/识别/反推所有者的纯逻辑.
 * <p>动态 ServerLevel 创建、传送与事件强制分支依赖运行中的服务器,
 * 不在单测覆盖范围,由 GameTest 覆盖.</p>
 */
class PersonalDimensionManagerTest {

    private static final UUID OWNER = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");

    /** dimensionKeyFor:命名空间与路径格式确定,且对同一 UUID 幂等. */
    @Test
    void dimensionKeyForShouldBeDeterministic() {
        ResourceKey<Level> key = PersonalDimensionManager.dimensionKeyFor(OWNER);

        assertThat(key.registry()).isEqualTo(Registries.DIMENSION.location());
        // UUID 去掉横线后的 32 位小写十六进制
        assertThat(key.location()).isEqualTo(new ResourceLocation("ae2enhanced",
                "pd_123456789abcdef0123456789abcdef0"));
        assertThat(PersonalDimensionManager.dimensionKeyFor(OWNER)).isEqualTo(key);
    }

    /** isPersonalDimension:仅识别本模组 pd_ 前缀的维度键. */
    @Test
    void isPersonalDimensionShouldMatchOnlyOwnKeys() {
        assertThat(PersonalDimensionManager.isPersonalDimension(PersonalDimensionManager.dimensionKeyFor(OWNER)))
                .isTrue();

        assertThat(PersonalDimensionManager.isPersonalDimension(Level.OVERWORLD)).isFalse();
        assertThat(PersonalDimensionManager.isPersonalDimension(Level.NETHER)).isFalse();
        // 其他命名空间的同名前缀不识别
        assertThat(PersonalDimensionManager.isPersonalDimension(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation("minecraft", "pd_123456789abcdef0123456789abcdef0")))).isFalse();
        // 本模组但非 pd_ 前缀不识别
        assertThat(PersonalDimensionManager.isPersonalDimension(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation("ae2enhanced", "other_dim")))).isFalse();
    }

    /** ownerFromKey:与 dimensionKeyFor 互为逆运算. */
    @Test
    void ownerFromKeyShouldRoundTrip() {
        UUID random = UUID.randomUUID();
        ResourceKey<Level> key = PersonalDimensionManager.dimensionKeyFor(random);

        assertThat(PersonalDimensionManager.ownerFromKey(key)).isEqualTo(random);
        assertThat(PersonalDimensionManager.ownerFromKey(PersonalDimensionManager.dimensionKeyFor(OWNER)))
                .isEqualTo(OWNER);
    }

    /** ownerFromKey:非个人维度键返回 null. */
    @Test
    void ownerFromKeyShouldReturnNullForForeignKeys() {
        assertThat(PersonalDimensionManager.ownerFromKey(Level.OVERWORLD)).isNull();
        assertThat(PersonalDimensionManager.ownerFromKey(Level.END)).isNull();
        assertThat(PersonalDimensionManager.ownerFromKey(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation("ae2enhanced", "other_dim")))).isNull();
    }

    /** ownerFromKey:pd_ 前缀但 UUID 片段非法时返回 null 而非抛异常. */
    @Test
    void ownerFromKeyShouldReturnNullForMalformedUuid() {
        // 非十六进制字符
        assertThat(PersonalDimensionManager.ownerFromKey(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation("ae2enhanced", "pd_zzzz")))).isNull();
        // 长度不足
        assertThat(PersonalDimensionManager.ownerFromKey(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation("ae2enhanced", "pd_1234")))).isNull();
        // 空前缀内容
        assertThat(PersonalDimensionManager.ownerFromKey(ResourceKey.create(Registries.DIMENSION,
                new ResourceLocation("ae2enhanced", "pd_")))).isNull();
    }
}
