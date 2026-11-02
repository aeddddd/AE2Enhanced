package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.omnitool.placement.PlacementConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class PacketInputBudgetTest {
    @Test
    void omniToolConfigRejectsNonFiniteNegativeAndIllegalEnums() {
        assertThat(config(0, 0, 12.0D, 12.0F, 0, 0)).isTrue();
        assertThat(config(-1, 0, 12.0D, 12.0F, 0, 0)).isFalse();
        assertThat(config(0, 3, 12.0D, 12.0F, 0, 0)).isFalse();
        assertThat(config(0, 0, Double.NaN, 12.0F, 0, 0)).isFalse();
        assertThat(config(0, 0, Double.POSITIVE_INFINITY, 12.0F, 0, 0)).isFalse();
        assertThat(config(0, 0, 12.0D, Float.NaN, 0, 0)).isFalse();
        assertThat(config(0, 0, 12.0D, Float.NEGATIVE_INFINITY, 0, 0)).isFalse();
        assertThat(config(0, 0, 12.0D, -1.0F, 0, 0)).isFalse();
        assertThat(config(0, 0, 12.0D, 12.0F, -1, 0)).isFalse();
        assertThat(config(0, 0, 12.0D, 12.0F, 0, 99)).isFalse();
    }

    private boolean config(int mode, int drop, double blink, float reach, int cooldown, int restriction) {
        return new PacketOmniToolConfig(mode, drop, false, 0, blink, cooldown,
                0, false, false, false, false, 0, reach, restriction, new NBTTagList()).isValid();
    }

    @Test
    void pageRequestValidatesOffsetsLimitsFiltersAndEnums() {
        assertThat(page(0, 45, 0, 0)).isTrue();
        assertThat(page(-1, 45, 0, 0)).isFalse();
        assertThat(page(0, 0, 0, 0)).isFalse();
        assertThat(page(0, PacketOmniPageRequest.MAX_LIMIT + 1, 0, 0)).isFalse();
        assertThat(page(PacketOmniPageRequest.MAX_OFFSET + 1, 45, 0, 0)).isFalse();
        assertThat(page(0, 45, 4, 0)).isFalse();
        assertThat(page(0, 45, 0, 3)).isFalse();
        PacketOmniPageRequest tooMany = new PacketOmniPageRequest("", (byte) 0, (byte) 0,
                (byte) 0, (byte) 0, 0, 45,
                new ArrayList<>(Collections.nCopies(PacketOmniPageRequest.MAX_FILTER_COUNT + 1, null)));
        assertThat(tooMany.isValid()).isFalse();
    }

    private boolean page(int offset, int limit, int sort, int view) {
        return new PacketOmniPageRequest("", (byte) 0, (byte) sort, (byte) 0,
                (byte) view, offset, limit).isValid();
    }

    @Test
    void oversizedFilterCountIsRejectedBeforeAllocation() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            ByteBufUtils.writeUTF8String(buffer, "");
            buffer.writeByte(0).writeByte(0).writeByte(0).writeByte(0);
            buffer.writeInt(0).writeInt(45);
            buffer.writeBoolean(true);
            buffer.writeInt(PacketOmniPageRequest.MAX_FILTER_COUNT + 1);
            PacketOmniPageRequest request = new PacketOmniPageRequest();
            request.fromBytes(buffer);
            assertThat(request.isValid()).isFalse();
            assertThat(request.getClientFilter()).isNull();
        } finally {
            buffer.release();
        }
    }

    @Test
    void oversizedSearchDeclarationIsRejectedWithoutReadingPayload() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            ByteBufUtils.writeVarInt(buffer, PacketOmniPageRequest.MAX_SEARCH_LENGTH + 1, 3);
            PacketOmniPageRequest request = new PacketOmniPageRequest();
            request.fromBytes(buffer);
            assertThat(request.isValid()).isFalse();
        } finally {
            buffer.release();
        }
    }

    @Test
    void placementAndRingClampsCannotPreserveNonFiniteValues() throws Exception {
        Bootstrap.register();
        PlacementConfig config = new PlacementConfig(new ItemStack(Items.STICK));
        config.setReachDistance(Float.NaN);
        assertThat(config.getReachDistance()).isEqualTo(PlacementConfig.DEFAULT_REACH_DISTANCE);
        config.setReachDistance(Float.POSITIVE_INFINITY);
        assertThat(config.getReachDistance()).isEqualTo(PlacementConfig.DEFAULT_REACH_DISTANCE);
        ItemStack corrupted = new ItemStack(Items.STICK);
        corrupted.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        net.minecraft.nbt.NBTTagCompound root = new net.minecraft.nbt.NBTTagCompound();
        root.setFloat(PlacementConfig.NBT_REACH_DISTANCE, Float.NaN);
        corrupted.getTagCompound().setTag(PlacementConfig.NBT_ROOT, root);
        assertThat(new PlacementConfig(corrupted).getReachDistance())
                .isEqualTo(PlacementConfig.DEFAULT_REACH_DISTANCE);

        java.lang.reflect.Method clamp = PacketRingConfig.Handler.class
                .getDeclaredMethod("clamp", float.class, float.class, float.class);
        clamp.setAccessible(true);
        assertThat((float) clamp.invoke(null, Float.NaN, 5.0F, 32.0F)).isEqualTo(5.0F);
        assertThat((float) clamp.invoke(null, Float.POSITIVE_INFINITY, 5.0F, 32.0F)).isEqualTo(5.0F);
    }
}
