package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class PacketOmniPageV2Test {
    @BeforeAll
    static void boot() {
        Bootstrap.register();
    }

    private static PacketOmniPageRequestV2 request(long session, long generation, long requestId) {
        return new PacketOmniPageRequestV2(session, generation, requestId, "", (byte) 0,
                (byte) 0, (byte) 0, (byte) 1, 0, 45, Collections.emptyList());
    }

    private static ByteBuf buffer(net.minecraftforge.fml.common.network.simpleimpl.IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        message.toBytes(buffer);
        return buffer;
    }

    @Test
    void requestValidatesSessionGenerationAndMonotonicRequestIdentity() {
        assertThat(request(1, 0, 1).isValid()).isTrue();
        assertThat(request(0, 0, 1).isValid()).isFalse();
        assertThat(request(1, -1, 1).isValid()).isFalse();
        assertThat(request(1, 0, 0).isValid()).isFalse();
        PacketOmniPageRequestV2 original = request(0x1020304050607080L, 9, 11);
        PacketOmniPageRequestV2 decoded = new PacketOmniPageRequestV2();
        decoded.fromBytes(buffer(original));
        assertThat(decoded.getContainerSessionId()).isEqualTo(original.getContainerSessionId());
        assertThat(decoded.getQueryGeneration()).isEqualTo(9);
        assertThat(decoded.getRequestId()).isEqualTo(11);
        assertThat(decoded.isValid()).isTrue();
    }

    @Test
    void oversizedNbtDegradesOnlyThatEntryAndPreservesFollowingAndNullEntries() {
        ItemStack largeStack = new ItemStack(Items.DIAMOND);
        NBTTagCompound large = new NBTTagCompound();
        char[] chars = new char[PacketOmniPageResultV2.MAX_ENTRY_BYTES + 4096];
        Arrays.fill(chars, 'x');
        large.setString("oversized", new String(chars));
        largeStack.setTagCompound(large);
        IAEItemStack largeAe = AEItemStack.fromItemStack(largeStack);
        largeAe.setStackSize(5_000_000_000L);
        IAEItemStack stick = AEItemStack.fromItemStack(new ItemStack(Items.STICK));
        stick.setStackSize(7);
        PacketOmniPageResultV2 original = new PacketOmniPageResultV2(31, 4, 8, 3, 0,
                Arrays.asList(new PacketOmniPageResultV2.Entry(1, largeAe, 5_000_000_000L),
                        new PacketOmniPageResultV2.Entry(2, stick, 7),
                        new PacketOmniPageResultV2.Entry(3, null, 0)));

        PacketOmniPageResultV2 decoded = new PacketOmniPageResultV2();
        decoded.fromBytes(buffer(original));

        assertThat(decoded.getEntries()).hasSize(3);
        assertThat(decoded.getEntries().get(0).stack.getDefinition().getItem()).isEqualTo(Items.DIAMOND);
        assertThat(decoded.getEntries().get(0).stack.getDefinition().getTagCompound()).isNull();
        assertThat(decoded.getEntries().get(0).count).isEqualTo(5_000_000_000L);
        assertThat(decoded.getEntries().get(1).stack.getDefinition().getItem()).isEqualTo(Items.STICK);
        assertThat(decoded.getEntries().get(1).count).isEqualTo(7);
        assertThat(decoded.getEntries().get(2).stack).isNull();
        assertThat(decoded.getContainerSessionId()).isEqualTo(31);
        assertThat(decoded.getQueryGeneration()).isEqualTo(4);
        assertThat(decoded.getRequestId()).isEqualTo(8);
    }

    @Test
    void nullAndMalformedEntriesDoNotShiftFollowingPayload() {
        IAEItemStack stick = AEItemStack.fromItemStack(new ItemStack(Items.STICK));
        PacketOmniPageResultV2 original = new PacketOmniPageResultV2(1, 0, 1, 2, 0,
                Arrays.asList(new PacketOmniPageResultV2.Entry(1, null, 0),
                        new PacketOmniPageResultV2.Entry(2, stick, 9)));
        PacketOmniPageResultV2 decoded = new PacketOmniPageResultV2();
        decoded.fromBytes(buffer(original));
        assertThat(decoded.getEntries().get(0).stack).isNull();
        assertThat(decoded.getEntries().get(1).stack.getDefinition().getItem()).isEqualTo(Items.STICK);
        assertThat(decoded.getEntries().get(1).count).isEqualTo(9);
    }
}
