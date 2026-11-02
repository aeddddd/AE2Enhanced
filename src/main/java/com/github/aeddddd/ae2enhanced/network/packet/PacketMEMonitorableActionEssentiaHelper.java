package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.me.helpers.PlayerSource;
import com.github.aeddddd.ae2enhanced.item.fakeitem.EssentiaFakeItemChecks;
import com.github.aeddddd.ae2enhanced.terminal.packet.PacketMEMonitorableAction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

/**
 * PacketMEMonitorableAction 的源质处理辅助类.
 *
 * 本类包含所有对 Thaumcraft / ThaumicEnergistics 类的直接引用,
 * 由 {@link PacketMEMonitorableAction} 在确认 mod 存在后通过反射调用,
 * 避免在缺少对应 mod 时触发 NoClassDefFoundError.
 *
 * 装填/倾倒逻辑复刻 ThaumicEnergistics 的 ContainerEssentiaTerminal:
 * - 倾倒:容器内源质全部可注入网络才执行,随后清空 NBT 并复位 meta;
 * - 装填:安瓿(thaumcraft:phial)只有空/满两态,网络存量必须 ≥ 10;
 *        源质罐(jar_normal/jar_void)支持部分装填,装 min(250, 网络存量).
 */
public final class PacketMEMonitorableActionEssentiaHelper {

    private PacketMEMonitorableActionEssentiaHelper() {}

    public static void essentiaWork(PacketMEMonitorableAction message, ItemStack singleHeld,
                                     IStorageGrid grid, PlayerSource source, EntityPlayerMP player) {
        ItemStack actualHeld = player.inventory.getItemStack();
        if (!ItemStack.areItemsEqual(singleHeld, actualHeld) || !ItemStack.areItemStackTagsEqual(singleHeld, actualHeld)
                || actualHeld.isEmpty()) {
            return;
        }
        if (!(singleHeld.getItem() instanceof IEssentiaContainerItem)) {
            return;
        }
        int capacity = EssentiaFakeItemChecks.getEssentiaContainerCapacity(singleHeld);
        if (capacity <= 0) {
            return;
        }
        IEssentiaContainerItem container = (IEssentiaContainerItem) singleHeld.getItem();

        // 目标 aspect(点击的源质条目),null 表示仅倾倒
        String targetTag = null;
        NBTTagCompound nbt = message.getNbt();
        if (nbt != null && nbt.hasKey("Aspect", 8)) {
            targetTag = nbt.getString("Aspect");
        }

        IMEMonitor<IAEEssentiaStack> essentiaStorage = grid.getInventory(
                AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class));
        if (essentiaStorage == null) {
            return;
        }

        AspectList content = container.getAspects(singleHeld);
        boolean hasContent = content != null && content.size() > 0;

        if (hasContent) {
            // 倾倒:全部可注入才执行(复刻 ThE 行为)
            Aspect aspect = content.getAspects()[0];
            int amount = content.getAmount(aspect);
            if (amount <= 0) return;
            IAEEssentiaStack toInject = AEEssentiaStack.fromEssentiaStack(new EssentiaStack(aspect, amount));
            if (toInject == null) return;
            IAEEssentiaStack notInjected = essentiaStorage.injectItems(toInject, Actionable.SIMULATE, source);
            if (notInjected != null && notInjected.getStackSize() > 0) return;
            // 清空容器:清 NBT + meta 复位(安瓿 meta 回 0,罐子清 NBT 即为空罐)
            singleHeld.setTagCompound(null);
            singleHeld.setItemDamage(0);
            essentiaStorage.injectItems(toInject, Actionable.MODULATE, source);
        } else {
            // 装填:需要目标 aspect
            if (targetTag == null) return;
            Aspect aspect = Aspect.getAspect(targetTag);
            if (aspect == null) return;
            IAEEssentiaStack request = AEEssentiaStack.fromEssentiaStack(new EssentiaStack(aspect, capacity));
            if (request == null) return;
            IAEEssentiaStack extracted = essentiaStorage.extractItems(request, Actionable.SIMULATE, source);
            if (extracted == null || extracted.getStackSize() <= 0) return;

            boolean phial = isPhial(singleHeld);
            long available = extracted.getStackSize();
            int fillAmount;
            if (phial) {
                // 安瓿只有空/满两态,必须装满
                if (available < capacity) return;
                fillAmount = capacity;
            } else {
                // 源质罐支持部分装填
                fillAmount = (int) Math.min(capacity, available);
            }
            if (fillAmount <= 0) return;

            container.setAspects(singleHeld, new AspectList().add(aspect, fillAmount));
            if (phial) {
                // 安瓿装填必须同时设 meta=1 和 Aspects NBT,否则 onUpdate 会重置为空安瓿
                singleHeld.setItemDamage(1);
            }
            essentiaStorage.extractItems(request.setStackSize(fillAmount), Actionable.MODULATE, source);
        }

        // 手持多个容器时拆一份处理,产物放回背包
        if (actualHeld.getCount() > 1) {
            actualHeld.shrink(1);
            player.inventory.placeItemBackInInventory(player.world, singleHeld);
        } else {
            player.inventory.setItemStack(singleHeld);
        }
        PacketMEMonitorableAction.Handler.updateHeld(player);
    }

    private static boolean isPhial(ItemStack stack) {
        net.minecraft.util.ResourceLocation rl = stack.getItem().getRegistryName();
        return rl != null && "thaumcraft:phial".equals(rl.toString());
    }
}
