package com.github.aeddddd.ae2enhanced.storage.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link ItemDescriptorCodec} 二进制编解码测试。
 *
 * <p>覆盖 write/read 往返（含 NBT、无 NBT 两个分支）、meta 保留，
 * 以及未知物品 id 反序列化返回 null 的防御分支。</p>
 */
public class ItemDescriptorCodecTest {

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack，需无头引导初始化物品注册表
        AE2TestBootstrap.boot();
    }

    /** 将 descriptor 编码为字节数组。 */
    private static byte[] write(ItemDescriptor descriptor) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ItemDescriptorCodec.INSTANCE.write(new DataOutputStream(baos), descriptor);
        return baos.toByteArray();
    }

    /** 从字节数组解码 descriptor。 */
    private static ItemDescriptor read(byte[] bytes) throws IOException {
        return ItemDescriptorCodec.INSTANCE.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    /** 二进制往返（含 NBT 分支）：还原后与原始 descriptor 相等。 */
    @Test
    public void testBinaryRoundTripWithNbt() throws IOException {
        ItemStack stack = new ItemStack(Items.DYE, 1, 4);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("k", "v");
        tag.setInteger("n", 42);
        stack.setTagCompound(tag);
        ItemDescriptor original = new ItemDescriptor(stack);

        ItemDescriptor restored = read(write(original));

        assertThat(restored).isNotNull();
        assertThat(restored).isEqualTo(original);
        assertThat(restored.hashCode()).isEqualTo(original.hashCode());
        assertThat(restored.getNbt().getInteger("n")).isEqualTo(42);
    }

    /** 二进制往返（无 NBT 分支）：写 hasNbt=0，meta 非 0 时正确保留。 */
    @Test
    public void testBinaryRoundTripWithoutNbt() throws IOException {
        ItemDescriptor original = new ItemDescriptor(new ItemStack(Items.DYE, 1, 13));

        ItemDescriptor restored = read(write(original));

        assertThat(restored).isNotNull();
        assertThat(restored).isEqualTo(original);
        assertThat(restored.getMeta()).isEqualTo(13);
        assertThat(restored.getNbt()).isNull();
    }

    /** 无注册名物品拒绝写成空气，编码失败交给既有隔离流程处理。 */
    @Test
    public void testWriteUnregisteredItemIsRejected() throws IOException {
        ItemDescriptor unregistered = new ItemDescriptor(new ItemStack(new net.minecraft.item.Item(), 1, 0));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> write(unregistered))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("refusing to substitute minecraft:air");
    }

    /**
     * 未知物品 id 反序列化返回 null。
     * 已验证 Forge 1.12.2 中 Item.REGISTRY 为普通 NamespacedWrapper，
     * getObject 对未注册 id 返回 null，故该分支可达。
     */
    @Test
    public void testReadUnknownItemIdReturnsNull() throws IOException {
        // 手工构造字节流：id + meta + hasNbt=0
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        byte[] idBytes = "minecraft:no_such_item_zzz".getBytes(StandardCharsets.UTF_8);
        out.writeInt(idBytes.length);
        out.write(idBytes);
        out.writeShort(0);
        out.writeByte(0);
        out.flush();

        assertThat(read(baos.toByteArray())).isNull();
    }
}
