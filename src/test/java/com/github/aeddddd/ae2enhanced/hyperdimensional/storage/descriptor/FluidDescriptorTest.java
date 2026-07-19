package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link FluidDescriptor} 单元测试。
 */
class FluidDescriptorTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testEqualityForSameFluid() {
        FluidDescriptor a = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));
        FluidDescriptor b = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testInequalityForDifferentFluids() {
        FluidDescriptor water = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));
        FluidDescriptor lava = new FluidDescriptor(AEFluidKey.of(Fluids.LAVA));

        assertNotEquals(water, lava);
    }

    @Test
    void testInequalityAgainstOtherTypes() {
        FluidDescriptor descriptor = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));
        assertNotEquals(descriptor, new Object());
        assertFalse(descriptor.equals(null));
    }

    @Test
    void testNbtAffectsEquality() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", 3);

        FluidDescriptor plain = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));
        FluidDescriptor taggedA = new FluidDescriptor(AEFluidKey.of(Fluids.WATER, tag.copy()));
        FluidDescriptor taggedB = new FluidDescriptor(AEFluidKey.of(Fluids.WATER, tag.copy()));

        assertEquals(taggedA, taggedB);
        assertEquals(taggedA.hashCode(), taggedB.hashCode());
        assertNotEquals(plain, taggedA);
    }

    @Test
    void testConstructorCopiesNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", 3);
        FluidDescriptor descriptor = new FluidDescriptor(AEFluidKey.of(Fluids.WATER, tag));

        tag.putInt("level", 99);
        assertEquals(3, descriptor.getNbt().getInt("level"));
    }

    @Test
    void testGetNbtForPlainFluid() {
        FluidDescriptor descriptor = new FluidDescriptor(AEFluidKey.of(Fluids.WATER));
        assertNull(descriptor.getNbt());
    }

    @Test
    void testGetKeys() {
        AEFluidKey key = AEFluidKey.of(Fluids.LAVA);
        FluidDescriptor descriptor = new FluidDescriptor(key);

        assertSame(key, descriptor.getAEFluidKey());
        assertSame(key, descriptor.getAEKey());
    }

    @Test
    void testToNBTRoundTrip() {
        FluidDescriptor descriptor = new FluidDescriptor(AEFluidKey.of(Fluids.LAVA));

        CompoundTag nbt = descriptor.toNBT();
        AEFluidKey restored = AEFluidKey.fromTag(nbt);

        assertNotNull(restored);
        assertEquals(descriptor, new FluidDescriptor(restored));
    }
}
