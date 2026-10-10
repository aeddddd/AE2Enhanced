package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 可拼接终端布局在<strong>终端物品 NBT</strong> 上的存取（阶段 4a）。
 *
 * <p>布局只占物品上的一个 String tag（{@link #KEY_LAYOUT}），与 {@code ContainerOmniTerm} 已用的
 * {@code omni_crafting_mode}/{@code omni_substitute}/{@code omni_scroll_offset} 是<strong>并列</strong>的键，
 * 各写各的、互不清空（两者都走 {@code Platform.openNbtData} 的同一个 compound）。</p>
 *
 * <p>读写时机完全不同：</p>
 * <ul>
 *   <li><strong>读</strong>：客户端打开终端时直接读自己那份物品栈的副本，零同步；</li>
 *   <li><strong>写</strong>：客户端只把 JSON 发给服务端（{@code PacketOmniLayout}），
 *       由服务端写进玩家手里那份权威物品栈。</li>
 * </ul>
 *
 * <p>长度上限在两侧都卡：客户端不发送超限载荷，服务端也不写入超限内容。</p>
 */
public final class OmniTerminalLayoutNbt {

    /** 布局 JSON 的 NBT 键（物品 NBT 顶层 compound 里的一个 String）。 */
    public static final String KEY_LAYOUT = "omni_layout";

    /**
     * Forge 1.12.2 网络字符串的**硬上限**：{@code ByteBufUtils.writeUTF8String} 要求长度前缀
     * （VarInt）不超过 2 字节，即 UTF-8 字节数 ≤ 16383，超了会直接抛 {@code IllegalArgumentException}。
     */
    public static final int MAX_WIRE_BYTES = 16383;

    /**
     * 服务端/客户端共同接受的布局 JSON 上限（UTF-8 字节数）。
     *
     * <p>比 {@link #MAX_WIRE_BYTES} 再留一点余量：7 个模块的布局实测只有 2~4 KB，
     * 16 KB 足够容纳未来加模块，同时保证任何被我们接受的载荷都必然能过网络。</p>
     */
    public static final int MAX_LAYOUT_BYTES = 16000;

    private OmniTerminalLayoutNbt() {
    }

    /**
     * 读取物品上的布局 JSON。
     *
     * @return 布局 JSON；没有该键、为空或物品为空时返回 {@code null}
     */
    public static String read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(KEY_LAYOUT, 8 /* NBTTagCompound.TAG_STRING */)) {
            return null;
        }
        String json = tag.getString(KEY_LAYOUT);
        return json.isEmpty() ? null : json;
    }

    /**
     * 把布局 JSON 写进物品 NBT。
     *
     * @return 是否写入成功（空载荷、超限或物品为空都拒绝）
     */
    public static boolean write(ItemStack stack, String json) {
        if (stack == null || stack.isEmpty() || !isAcceptable(json)) {
            return false;
        }
        // 直接走 MC 的 NBT API（与 AE2 的 Platform.openNbtData 完全等价，但少一层 AE2 静态初始化依赖）
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(KEY_LAYOUT, json);
        return true;
    }

    /** 载荷是否可接受（非空且 UTF-8 字节数不超过 {@link #MAX_LAYOUT_BYTES}）。 */
    public static boolean isAcceptable(String json) {
        return json != null && !json.isEmpty() && utf8Length(json) <= MAX_LAYOUT_BYTES;
    }

    /** 载荷的 UTF-8 字节数（判断能不能过 {@code ByteBufUtils} 的长度前缀）。 */
    public static int utf8Length(String json) {
        return json == null ? 0 : json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
