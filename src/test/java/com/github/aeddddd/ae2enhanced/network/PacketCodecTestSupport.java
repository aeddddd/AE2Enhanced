package com.github.aeddddd.ae2enhanced.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 网络包编解码对称性测试的公共工具.
 * <p>{@link FriendlyByteBuf} 包装 {@link Unpooled#buffer()} 即可使用,不依赖游戏运行时,
 * 无需原版引导；仅当负载涉及注册表内容（如 ItemStack、AEKey）时才需要 bootstrap.</p>
 */
final class PacketCodecTestSupport {

    private PacketCodecTestSupport() {
    }

    /**
     * 创建基于堆内存的缓冲区.
     */
    static FriendlyByteBuf newBuffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    /**
     * 编码 → 解码 → 二次编码,验证：
     * <ol>
     * <li>解码后缓冲区恰好读尽（无多写/少读字段）;</li>
     * <li>解码结果二次编码的字节与原始编码完全一致.</li>
     * </ol>
     *
     * @return 解码结果,便于调用方进一步断言字段
     */
    static <T> T roundTrip(T packet, BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder) {
        FriendlyByteBuf buffer = newBuffer();
        encoder.accept(packet, buffer);
        byte[] expected = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), expected);

        T decoded = decoder.apply(buffer);
        assertEquals(0, buffer.readableBytes(), "解码后缓冲区应恰好读尽");

        FriendlyByteBuf reencoded = newBuffer();
        encoder.accept(decoded, reencoded);
        byte[] actual = new byte[reencoded.readableBytes()];
        reencoded.readBytes(actual);
        assertArrayEquals(expected, actual, "解码结果二次编码的字节应与原始编码一致");
        return decoded;
    }

    /**
     * 反射读取私有字段值（被测类未提供 getter 时使用）.
     */
    static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取字段: " + target.getClass().getSimpleName() + "." + name, e);
        }
    }
}
