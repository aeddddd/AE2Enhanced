package com.github.aeddddd.ae2enhanced.util.reflection;
import com.github.aeddddd.ae2enhanced.util.essentia.EssentiaChannelAccessor;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.inventory.AppEngInternalAEInventory;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentias;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

/**
 * 所有涉及 thaumcraft / thaumicenergistics 的 Essentia 逻辑集中在此处.
 * 此类只在运行时通过 Class.forName 加载,避免在 Part 类加载时触发
 * thaumicenergistics 类缺失导致的 NoClassDefFoundError.
 */
public class EssentiaBusHelper {

    // region FakeEssentias 功能(供 Mixin / Helper 自身使用)

    public static IAEItemStack packEssentia(IAEEssentiaStack essentiaStack) {
        return FakeItemRegister.packAEStack(essentiaStack, ItemRegistry.ESSENTIA_DROP);
    }

    public static IAEEssentiaStack unpackEssentia(IAEItemStack itemStack) {
        return FakeEssentias.unpackEssentia(itemStack);
    }

    // endregion

    // region Import Bus

    @SuppressWarnings("unchecked")
    public static boolean importEssentias(appeng.api.networking.IGrid grid, TileEntity target, EnumFacing opposite,
                                           AppEngInternalAEInventory config, IActionSource source) throws Exception {
        thaumcraft.api.aspects.IEssentiaTransport transport = (thaumcraft.api.aspects.IEssentiaTransport) target;
        IMEMonitor<IAEEssentiaStack> inv = (IMEMonitor<IAEEssentiaStack>) EssentiaChannelAccessor.getEssentiaInventory(grid);
        if (inv == null) return false;

        for (int x = 0; x < config.getSlots(); x++) {
            if (tryImportEssentiaSlot(transport, inv, config.getAEStackInSlot(x), opposite, source)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static boolean importEssentiaSlot(appeng.api.networking.IGrid grid, TileEntity target, EnumFacing opposite,
                                              IAEItemStack filter, IActionSource source) throws Exception {
        thaumcraft.api.aspects.IEssentiaTransport transport = (thaumcraft.api.aspects.IEssentiaTransport) target;
        IMEMonitor<IAEEssentiaStack> inv = (IMEMonitor<IAEEssentiaStack>) EssentiaChannelAccessor.getEssentiaInventory(grid);
        if (inv == null) return false;
        return tryImportEssentiaSlot(transport, inv, filter, opposite, source);
    }

    private static boolean tryImportEssentiaSlot(thaumcraft.api.aspects.IEssentiaTransport transport,
                                                  IMEMonitor<IAEEssentiaStack> inv, IAEItemStack filter,
                                                  EnumFacing opposite, IActionSource source) {
        if (filter == null || !ItemEssentiaDrop.isEssentiaDrop(filter.createItemStack())) return false;

        IAEEssentiaStack wanted = unpackEssentia(filter);
        if (wanted == null || wanted.getAspect() == null) return false;

        int available = transport.getEssentiaAmount(opposite);
        if (available <= 0) return false;

        int toTake = Math.min(available, 64);
        EssentiaStack essStack = new EssentiaStack(wanted.getAspect().getTag(), toTake);
        IAEEssentiaStack aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
        if (aeEss == null) return false;

        IAEEssentiaStack notInserted = inv.injectItems(aeEss, Actionable.SIMULATE, source);
        long canInsert = aeEss.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
        if (canInsert <= 0) return false;

        int actual = transport.takeEssentia(wanted.getAspect(), (int) canInsert, opposite);
        if (actual > 0) {
            EssentiaStack actualStack = new EssentiaStack(wanted.getAspect().getTag(), actual);
            IAEEssentiaStack toInsert = AEEssentiaStack.fromEssentiaStack(actualStack);
            inv.injectItems(toInsert, Actionable.MODULATE, source);
            return true;
        }
        return false;
    }

    // endregion

    // region Export Bus (Single Slot)

    @SuppressWarnings("unchecked")
    public static boolean exportEssentiaSlot(appeng.api.networking.IGrid grid, TileEntity target, EnumFacing opposite,
                                              IAEItemStack filter, IActionSource source) throws Exception {
        thaumcraft.api.aspects.IEssentiaTransport transport = (thaumcraft.api.aspects.IEssentiaTransport) target;

        Class<?> essentiaChannelClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
        java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
        Object essentiaChannel = getChannel.invoke(AEApi.instance().storage(), essentiaChannelClass);

        appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
        IMEMonitor<IAEEssentiaStack> inv = (IMEMonitor<IAEEssentiaStack>) storageGrid.getInventory((appeng.api.storage.IStorageChannel<?>) essentiaChannel);

        if (filter == null || !ItemEssentiaDrop.isEssentiaDrop(filter.createItemStack())) return false;

        IAEEssentiaStack wanted = unpackEssentia(filter);
        if (wanted == null || wanted.getAspect() == null) return false;

        int toSend = (int) Math.min(wanted.getStackSize(), 64);
        EssentiaStack essStack = new EssentiaStack(wanted.getAspect().getTag(), toSend);
        IAEEssentiaStack aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
        if (aeEss == null) return false;

        IAEEssentiaStack out = inv.extractItems(aeEss, Actionable.SIMULATE, source);
        if (out == null || out.getStackSize() <= 0) return false;

        int actual = transport.addEssentia(wanted.getAspect(), (int) out.getStackSize(), opposite);
        if (actual > 0) {
            essStack = new EssentiaStack(wanted.getAspect().getTag(), actual);
            aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
            inv.extractItems(aeEss, Actionable.MODULATE, source);
            return true;
        }
        return false;
    }

    // endregion

    // region Export Bus

    @SuppressWarnings("unchecked")
    public static boolean exportEssentias(appeng.api.networking.IGrid grid, TileEntity target, EnumFacing opposite,
                                           AppEngInternalAEInventory config, IActionSource source) throws Exception {
        thaumcraft.api.aspects.IEssentiaTransport transport = (thaumcraft.api.aspects.IEssentiaTransport) target;

        Class<?> essentiaChannelClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
        java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
        Object essentiaChannel = getChannel.invoke(AEApi.instance().storage(), essentiaChannelClass);

        appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
        @SuppressWarnings("unchecked")
        IMEMonitor<IAEEssentiaStack> inv = (IMEMonitor<IAEEssentiaStack>) storageGrid.getInventory((appeng.api.storage.IStorageChannel<?>) essentiaChannel);

        boolean worked = false;

        for (int x = 0; x < config.getSlots(); x++) {
            IAEItemStack filter = config.getAEStackInSlot(x);
            if (filter == null || !ItemEssentiaDrop.isEssentiaDrop(filter.createItemStack())) continue;

            IAEEssentiaStack wanted = unpackEssentia(filter);
            if (wanted == null || wanted.getAspect() == null) continue;

            int toSend = (int) Math.min(wanted.getStackSize(), 64);
            EssentiaStack essStack = new EssentiaStack(wanted.getAspect().getTag(), toSend);
            IAEEssentiaStack aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
            if (aeEss == null) continue;

            IAEEssentiaStack out = inv.extractItems(aeEss, Actionable.SIMULATE, source);
            if (out == null || out.getStackSize() <= 0) continue;

            int actual = transport.addEssentia(wanted.getAspect(), (int) out.getStackSize(), opposite);
            if (actual > 0) {
                essStack = new EssentiaStack(wanted.getAspect().getTag(), actual);
                aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
                inv.extractItems(aeEss, Actionable.MODULATE, source);
                worked = true;
                break;
            }
        }

        return worked;
    }

    // endregion

    // region Stocking Bus

    @SuppressWarnings("unchecked")
    public static long stockEssentias(appeng.api.networking.IGrid grid, TileEntity target, EnumFacing opposite,
                                       IAEItemStack filter, long targetAmount, long maxWork,
                                       int modeOrdinal, IActionSource source) throws Exception {
        thaumcraft.api.aspects.IEssentiaTransport transport = (thaumcraft.api.aspects.IEssentiaTransport) target;

        Class<?> essentiaChannelClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
        java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
        Object essentiaChannel = getChannel.invoke(AEApi.instance().storage(), essentiaChannelClass);

        appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
        IMEMonitor<IAEEssentiaStack> inv = (IMEMonitor<IAEEssentiaStack>) storageGrid.getInventory((appeng.api.storage.IStorageChannel<?>) essentiaChannel);

        IAEEssentiaStack wanted = unpackEssentia(filter);
        if (wanted == null || wanted.getAspect() == null) {
            return 0;
        }

        int actual = 0;
        thaumcraft.api.aspects.Aspect faceAspect = transport.getEssentiaType(opposite);
        if (faceAspect != null && faceAspect.equals(wanted.getAspect())) {
            actual = transport.getEssentiaAmount(opposite);
        }
        long delta = targetAmount - actual;
        long consumed = 0;

        // 补货：网络 → 外部
        if (delta > 0 && modeOrdinal != 2) { // RECOVER_ONLY = 2
            int toSupply = (int) Math.min(delta, maxWork);
            EssentiaStack essStack = new EssentiaStack(wanted.getAspect().getTag(), toSupply);
            IAEEssentiaStack aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
            if (aeEss != null) {
                IAEEssentiaStack out = inv.extractItems(aeEss, Actionable.SIMULATE, source);
                long canExtract = out != null ? out.getStackSize() : 0;
                if (canExtract > 0) {
                    int added = transport.addEssentia(wanted.getAspect(), (int) canExtract, opposite);
                    if (added > 0) {
                        EssentiaStack actualStack = new EssentiaStack(wanted.getAspect().getTag(), added);
                        IAEEssentiaStack toExtract = AEEssentiaStack.fromEssentiaStack(actualStack);
                        inv.extractItems(toExtract, Actionable.MODULATE, source);
                        consumed = added;
                    }
                }
            }
        }

        // 回收：外部 → 网络
        if (delta < 0 && modeOrdinal != 1) { // SUPPLY_ONLY = 1
            int toRecover = (int) Math.min(-delta, maxWork);
            toRecover = (int) Math.min(toRecover, actual);
            if (toRecover > 0) {
                EssentiaStack essStack = new EssentiaStack(wanted.getAspect().getTag(), toRecover);
                IAEEssentiaStack aeEss = AEEssentiaStack.fromEssentiaStack(essStack);
                if (aeEss != null) {
                    IAEEssentiaStack notInserted = inv.injectItems(aeEss, Actionable.SIMULATE, source);
                    long canInsert = aeEss.getStackSize() - (notInserted != null ? notInserted.getStackSize() : 0);
                    if (canInsert > 0) {
                        int taken = transport.takeEssentia(wanted.getAspect(), (int) canInsert, opposite);
                        if (taken > 0) {
                            EssentiaStack insertStack = new EssentiaStack(wanted.getAspect().getTag(), taken);
                            IAEEssentiaStack toInsert = AEEssentiaStack.fromEssentiaStack(insertStack);
                            inv.injectItems(toInsert, Actionable.MODULATE, source);
                            consumed = taken;
                        }
                    }
                }
            }
        }

        return consumed;
    }

    // endregion
}
