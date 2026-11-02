package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequestV2;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResultV2;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerminalQueryClientTest {
    @BeforeAll
    static void boot() {
        Bootstrap.register();
    }

    private static PacketOmniPageResultV2 result(PacketOmniPageRequestV2 request, int total) {
        IAEItemStack stack = AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND));
        return new PacketOmniPageResultV2(request.getContainerSessionId(), request.getQueryGeneration(),
                request.getRequestId(), total, request.getOffset(), Collections.singletonList(
                        new PacketOmniPageResultV2.Entry(1, stack, 5)));
    }

    private static ContainerOmniTerm container(long session, long generation) {
        ContainerOmniTerm container = mock(ContainerOmniTerm.class);
        when(container.getContainerSessionId()).thenReturn(session);
        when(container.getQueryGeneration()).thenReturn(generation);
        return container;
    }

    @Test
    void onlyCurrentContainerGenerationAndRequestCanReplaceCache() {
        RecordingRepo repo = new RecordingRepo();
        repo.bindContainer(container(71, 0));
        repo.onScrollChanged(0);
        PacketOmniPageRequestV2 first = repo.v2Requests.get(0);
        repo.onQueryParamsChanged("diamond", (byte) 0, (byte) 0, (byte) 0, (byte) 1);
        PacketOmniPageRequestV2 second = repo.v2Requests.get(1);
        assertThat(second.getQueryGeneration()).isEqualTo(1);
        assertThat(second.getRequestId()).isGreaterThan(first.getRequestId());

        repo.handlePageResult(result(first, 1));
        assertThat(repo.size()).isZero();
        repo.handlePageResult(result(second, 2));
        assertThat(repo.size()).isEqualTo(2);
        repo.handlePageResult(99, 0, Collections.emptyList());
        assertThat(repo.size()).isEqualTo(2);
    }

    @Test
    void dispatchDoesNotNeedVisibleParentAndNetworksDoNotCross() {
        RecordingRepo first = new RecordingRepo();
        RecordingRepo second = new RecordingRepo();
        first.bindContainer(container(101, 0));
        second.bindContainer(container(202, 0));
        first.onScrollChanged(0);
        second.onScrollChanged(0);

        TerminalQueryClient.dispatch(result(second.v2Requests.get(0), 8));

        assertThat(first.size()).isZero();
        assertThat(second.size()).isEqualTo(8);
        TerminalQueryClient.dispatch(result(first.v2Requests.get(0), 3));
        assertThat(first.size()).isEqualTo(3);
        assertThat(second.size()).isEqualTo(8);
    }

    private static final class RecordingRepo extends OmniItemRepo {
        final List<PacketOmniPageRequestV2> v2Requests = new ArrayList<>();

        RecordingRepo() {
            super(new GuiScrollbar(), new ISortSource() {
                @Override public Enum getSortBy() { return null; }
                @Override public Enum getSortDir() { return null; }
                @Override public Enum getSortDisplay() { return null; }
            });
        }

        @Override protected void sendToServer(PacketOmniPageRequest request) {
            throw new AssertionError("bound repo must use V2");
        }

        @Override protected void sendToServer(PacketOmniPageRequestV2 request) {
            v2Requests.add(request);
        }

        @Override protected void setChanged(boolean value) {
        }

        @Override protected void syncToParentView() {
        }
    }
}
