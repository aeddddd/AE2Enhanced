package com.github.aeddddd.ae2enhanced.omnitool.network;

import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 先进ME全能工具支持的网络链接注册表。
 */
public final class OmniToolNetworkLinks {

    private OmniToolNetworkLinks() {}

    private static final List<IOmniToolNetworkLink> LINKS = Arrays.asList(
            SecurityTerminalNetworkLink.INSTANCE,
            WirelessTransmitterNetworkLink.INSTANCE
    );

    public static List<IOmniToolNetworkLink> getAll() {
        return Collections.unmodifiableList(LINKS);
    }

    /**
     * 获取当前物品所有已绑定的链接。
     */
    public static List<IOmniToolNetworkLink> getActiveLinks(ItemStack stack) {
        if (stack.isEmpty()) return Collections.emptyList();
        List<IOmniToolNetworkLink> active = new java.util.ArrayList<>();
        for (IOmniToolNetworkLink link : LINKS) {
            if (link.isLinked(stack)) {
                active.add(link);
            }
        }
        return active;
    }

    /**
     * 判断物品是否通过任一方式链接到了 ME 网络。
     */
    public static boolean isAnyLinked(ItemStack stack) {
        for (IOmniToolNetworkLink link : LINKS) {
            if (link.isLinked(stack)) return true;
        }
        return false;
    }
}
