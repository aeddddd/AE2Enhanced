package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TerminalQueryServiceTest {
    private static IItemStorageChannel channel;

    @BeforeAll
    static void boot() throws Exception {
        AE2TestBootstrap.boot();
        channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    private static TerminalQueryService.Query query(String search, byte viewMode,
            HashSet<ItemDescriptor> filter) {
        return new TerminalQueryService.Query(search, (byte) 0, (byte) 0, (byte) 0,
                viewMode, 0, 135, null, filter);
    }

    private static TerminalQueryService.Query modQuery(String search, HashSet<ItemDescriptor> filter) {
        return new TerminalQueryService.Query(search, (byte) 1, (byte) 0, (byte) 0,
                (byte) 1, 0, 135, null, filter);
    }

    private static HashSet<ItemDescriptor> descriptors(Item... items) {
        HashSet<ItemDescriptor> result = new HashSet<>();
        Arrays.stream(items).forEach(item -> result.add(new ItemDescriptor(new ItemStack(item))));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static IMEMonitor<IAEItemStack> monitor(IItemList<IAEItemStack> list) {
        IMEMonitor<IAEItemStack> monitor = mock(IMEMonitor.class);
        when(monitor.getStorageList()).thenReturn(list);
        return monitor;
    }

    private static IAEItemStack stack(Item item, int count, boolean craftable) {
        IAEItemStack stack = channel.createStack(new ItemStack(item));
        stack.setStackSize(count);
        stack.setCraftable(craftable);
        return stack;
    }

    @Test
    void quantitiesComeFromWholeNetworkAggregateWithoutNexusDifferenceLoss() {
        IItemList<IAEItemStack> aggregate = channel.createList();
        aggregate.add(stack(Items.DIAMOND, 10, false));
        aggregate.add(stack(Items.DIAMOND, 5, false));
        TerminalQueryService service = new TerminalQueryService(monitor(aggregate));

        TerminalQueryService.PageResult result = service.query(query("", (byte) 1, null));

        assertThat(result.totalCount).isEqualTo(1);
        assertThat(result.items.get(0).getStackSize()).isEqualTo(15);
        service.close();
    }

    @Test
    void craftableViewAndSearchKeepNetworkCraftableMarkers() {
        IItemList<IAEItemStack> aggregate = channel.createList();
        aggregate.add(stack(Items.DIAMOND, 0, true));
        aggregate.add(stack(Items.DIAMOND, 7, false));
        aggregate.add(stack(Items.APPLE, 0, true));
        TerminalQueryService service = new TerminalQueryService(monitor(aggregate));

        TerminalQueryService.PageResult result = service.query(query("diamond", (byte) 2, null));

        assertThat(result.totalCount).isEqualTo(1);
        assertThat(result.items).allMatch(IAEItemStack::isCraftable);
        assertThat(result.items.get(0).getDefinition().getItem()).isEqualTo(Items.DIAMOND);
        service.close();
    }

    @Test
    void differentFiltersNeverMutateCachedSearchOrSortedLists() {
        IItemList<IAEItemStack> aggregate = channel.createList();
        aggregate.add(stack(Items.DIAMOND, 3, false));
        aggregate.add(stack(Items.APPLE, 4, false));
        TerminalQueryService service = new TerminalQueryService(monitor(aggregate));
        HashSet<ItemDescriptor> diamonds = descriptors(Items.DIAMOND);
        HashSet<ItemDescriptor> apples = descriptors(Items.APPLE);

        assertThat(service.query(modQuery("minecraft", diamonds)).items)
                .extracting(s -> s.getDefinition().getItem()).containsExactly(Items.DIAMOND);
        assertThat(service.query(modQuery("minecraft", apples)).items)
                .extracting(s -> s.getDefinition().getItem()).containsExactly(Items.APPLE);
        assertThat(service.query(modQuery("minecraft", null)).totalCount).isEqualTo(2);
        assertThat(service.query(query("", (byte) 1, diamonds)).totalCount).isEqualTo(1);
        assertThat(service.query(query("", (byte) 1, null)).totalCount).isEqualTo(2);
        service.close();
    }

    @Test
    void networksAndExplicitSubscriptionsAreIsolatedAndCloseIsIdentitySafe() {
        IItemList<IAEItemStack> firstItems = channel.createList();
        firstItems.add(stack(Items.DIAMOND, 12, false));
        IItemList<IAEItemStack> secondItems = channel.createList();
        secondItems.add(stack(Items.APPLE, 34, false));
        IMEMonitor<IAEItemStack> firstMonitor = monitor(firstItems);
        IMEMonitor<IAEItemStack> secondMonitor = monitor(secondItems);
        AtomicInteger firstNotifications = new AtomicInteger();
        AtomicInteger secondNotifications = new AtomicInteger();
        TerminalQueryService first = new TerminalQueryService(firstMonitor, firstNotifications::incrementAndGet);
        TerminalQueryService second = new TerminalQueryService(secondMonitor, secondNotifications::incrementAndGet);

        assertThat(first.query(query("", (byte) 1, null)).items.get(0).getStackSize()).isEqualTo(12);
        assertThat(second.query(query("", (byte) 1, null)).items.get(0).getStackSize()).isEqualTo(34);
        first.onListUpdate();
        assertThat(firstNotifications).hasValue(1);
        assertThat(secondNotifications).hasValue(0);

        first.close();
        verify(firstMonitor).removeListener(first);
        verify(secondMonitor, org.mockito.Mockito.never()).removeListener(second);
        second.close();
    }
}
