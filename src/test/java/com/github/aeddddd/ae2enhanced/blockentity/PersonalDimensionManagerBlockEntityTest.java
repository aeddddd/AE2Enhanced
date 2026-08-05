package com.github.aeddddd.ae2enhanced.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;

/**
 * {@link PersonalDimensionManagerBlockEntity} 单元测试.
 * <p>仅记录所有者 UUID,覆盖读写与 NBT 往返（含无所有者的兼容路径）.</p>
 */
class PersonalDimensionManagerBlockEntityTest {

    private static final BlockPos POS = new BlockPos(1, 65, 2);

    @BeforeAll
    static void bootstrap() {
        BlockEntityTestSupport.bootstrap();
    }

    private static PersonalDimensionManagerBlockEntity newEntity() {
        return new PersonalDimensionManagerBlockEntity(POS, Blocks.STONE.defaultBlockState());
    }

    /** 初始无所有者. */
    @Test
    void testInitialOwnerIsNull() {
        assertNull(newEntity().getOwner());
    }

    /** 所有者读写与清空. */
    @Test
    void testOwnerAccessors() {
        PersonalDimensionManagerBlockEntity be = newEntity();
        UUID owner = UUID.randomUUID();
        be.setOwner(owner);
        assertEquals(owner, be.getOwner());

        be.setOwner(null);
        assertNull(be.getOwner());
    }

    /** NBT 往返：所有者 UUID 持久化. */
    @Test
    void testNbtRoundTripWithOwner() {
        PersonalDimensionManagerBlockEntity source = newEntity();
        UUID owner = UUID.randomUUID();
        source.setOwner(owner);

        CompoundTag tag = new CompoundTag();
        source.saveAdditional(tag);
        assertTrue(tag.hasUUID("owner"));

        PersonalDimensionManagerBlockEntity target = newEntity();
        target.load(tag);
        assertEquals(owner, target.getOwner());
    }

    /** NBT 中无 owner 键时加载结果为 null,且不写出该键. */
    @Test
    void testNbtWithoutOwner() {
        PersonalDimensionManagerBlockEntity source = newEntity();
        CompoundTag tag = new CompoundTag();
        source.saveAdditional(tag);
        assertFalse(tag.hasUUID("owner"));

        PersonalDimensionManagerBlockEntity target = newEntity();
        target.setOwner(UUID.randomUUID());
        target.load(tag);
        assertNull(target.getOwner());
    }
}
