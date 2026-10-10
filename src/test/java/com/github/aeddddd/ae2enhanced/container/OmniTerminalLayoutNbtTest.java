package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 阶段 4a：布局在终端物品 NBT 上的读写。
 *
 * <p>重点锁两件事：长度闸门（两端都卡）与"与既有 NBT 键互不干扰"
 * （{@code ContainerOmniTerm} 的 omni_crafting_mode / omni_substitute / omni_scroll_offset）。</p>
 */
class OmniTerminalLayoutNbtTest {

    private static final String JSON = "{\"version\":1,\"roots\":[]}";

    @BeforeAll
    static void boot() {
        // 只需 MC 物品注册表（构造 ItemStack 用）；Bootstrap.register() 自带幂等 guard
        net.minecraft.init.Bootstrap.register();
    }

    @Test
    void writeThenReadRoundTrip() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertTrue(OmniTerminalLayoutNbt.write(stack, JSON));
        assertEquals(JSON, OmniTerminalLayoutNbt.read(stack));
    }

    @Test
    void missingKeyReadsNull() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertNull(OmniTerminalLayoutNbt.read(stack));
        assertNull(OmniTerminalLayoutNbt.read(ItemStack.EMPTY));
        assertNull(OmniTerminalLayoutNbt.read(null));
    }

    @Test
    void emptyStringIsTreatedAsAbsent() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString(OmniTerminalLayoutNbt.KEY_LAYOUT, "");
        assertNull(OmniTerminalLayoutNbt.read(stack), "空布局按没有布局处理");
    }

    @Test
    void writeRejectsEmptyAndOversizedPayloads() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertFalse(OmniTerminalLayoutNbt.write(stack, null));
        assertFalse(OmniTerminalLayoutNbt.write(stack, ""));
        assertFalse(OmniTerminalLayoutNbt.write(stack, repeat('x', OmniTerminalLayoutNbt.MAX_LAYOUT_BYTES + 1)));
        assertNull(OmniTerminalLayoutNbt.read(stack), "拒绝的载荷不应该落进 NBT");

        assertTrue(OmniTerminalLayoutNbt.write(stack, repeat('x', OmniTerminalLayoutNbt.MAX_LAYOUT_BYTES)));
        assertEquals(OmniTerminalLayoutNbt.MAX_LAYOUT_BYTES, OmniTerminalLayoutNbt.read(stack).length());
    }

    @Test
    void byteLimitIsStricterThanTheCharacterCountForMultibyteText() {
        // Forge 的 writeUTF8String 卡的是字节数（长度前缀 ≤ 2 字节 → 16383 字节）
        assertEquals(3, OmniTerminalLayoutNbt.utf8Length("物"));
        assertFalse(OmniTerminalLayoutNbt.isAcceptable(repeat('物', 6000)), "6000 个汉字 = 18000 字节，超限");
        assertTrue(OmniTerminalLayoutNbt.isAcceptable(repeat('物', 5000)), "5000 个汉字 = 15000 字节，可接受");
    }

    @Test
    void layoutCoexistsWithTheExistingOmniStateKeys() {
        ItemStack stack = new ItemStack(Items.STICK);
        // 先写入 ContainerOmniTerm 那批键（模拟旧终端的状态持久化）
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("omni_crafting_mode", false);
        tag.setBoolean("omni_substitute", true);
        tag.setInteger("omni_scroll_offset", 3);
        stack.setTagCompound(tag);

        assertTrue(OmniTerminalLayoutNbt.write(stack, JSON));

        NBTTagCompound after = stack.getTagCompound();
        assertEquals(JSON, after.getString(OmniTerminalLayoutNbt.KEY_LAYOUT));
        assertFalse(after.getBoolean("omni_crafting_mode"));
        assertTrue(after.getBoolean("omni_substitute"));
        assertEquals(3, after.getInteger("omni_scroll_offset"));
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
