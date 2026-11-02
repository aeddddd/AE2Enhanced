package com.github.aeddddd.ae2enhanced.client.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest;
import org.junit.jupiter.api.Test;

/**
 * 阶段 2b：{@code OmniItemRepo} 的可变缓存容量。
 *
 * <p>可拼接终端的 repo 面板尺寸可变（列数 = 内容宽度 / 18）；这里验证容量推导、
 * "容量变化即重新拉页"，以及大缓存按协议上限分块串行请求的行为。</p>
 */
class OmniItemRepoTest {

    private static final ISortSource NO_SORT = new ISortSource() {
        @Override
        public Enum getSortBy() {
            return null;
        }

        @Override
        public Enum getSortDir() {
            return null;
        }

        @Override
        public Enum getSortDisplay() {
            return null;
        }
    };

    /** 记录分页请求而不依赖真实网络的 repo。 */
    private static final class RecordingRepo extends OmniItemRepo {
        final List<PacketOmniPageRequest> requests = new ArrayList<PacketOmniPageRequest>();

        RecordingRepo() {
            super(new GuiScrollbar(), NO_SORT);
        }

        @Override
        protected void sendToServer(PacketOmniPageRequest request) {
            requests.add(request);
        }
    }

    @Test
    void capacityIsThreePages() {
        assertEquals(3, OmniItemRepo.capacityFor(1));
        assertEquals(90 * 3, OmniItemRepo.capacityFor(90));
        assertEquals(144 * 3, OmniItemRepo.capacityFor(18 * 8));
    }

    @Test
    void capacityNeverCollapsesToZero() {
        assertEquals(3, OmniItemRepo.capacityFor(0));
        assertEquals(3, OmniItemRepo.capacityFor(-5));
    }

    @Test
    void defaultCapacityMatchesTheCompactDefaultLayout() {
        RecordingRepo repo = new RecordingRepo();
        assertEquals(OmniItemRepo.capacityFor(OmniItemRepo.DEFAULT_SLOTS_PER_PAGE), repo.cacheCapacity());
        assertEquals(60 * 3, repo.cacheCapacity());
    }

    @Test
    void changingSlotsPerPageResizesCacheAndRefetches() {
        RecordingRepo repo = new RecordingRepo();
        repo.requests.clear();

        repo.setSlotsPerPage(18 * 8);

        assertEquals(18 * 8, repo.getSlotsPerPage());
        assertEquals(18 * 8 * 3, repo.cacheCapacity());
        // 容量变化后旧缓存整段失效，必须立刻重新拉取
        assertEquals(1, repo.requests.size());
        assertEquals(repo.cacheCapacity(), repo.requests.get(0).getLimit());
    }

    @Test
    void samePageSizeIsANoOp() {
        RecordingRepo repo = new RecordingRepo();
        repo.setSlotsPerPage(18 * 5);
        repo.requests.clear();

        // 容量相同（列数变了但格子总数没变）：不重新分配、不重复请求
        repo.setSlotsPerPage(18 * 5);

        assertEquals(18 * 5 * 3, repo.cacheCapacity());
        assertTrue(repo.requests.isEmpty());
    }

    @Test
    void duplicatePageRequestIsSuppressedWhileInFlight() {
        RecordingRepo repo = new RecordingRepo();
        repo.requests.clear();

        repo.onScrollChanged(0);
        assertEquals(1, repo.requests.size());

        // 同一页的请求已在途：拖动缩放引发的第二次请求应被合并掉
        repo.onScrollChanged(0);
        assertEquals(1, repo.requests.size(), "同一页在途时不应重复发包");
    }

    @Test
    void inFlightPageRetriesAfterBoundedWait() {
        RecordingRepo repo = new RecordingRepo();
        repo.requests.clear();
        repo.onScrollChanged(0);
        for (int tick = 0; tick < 39; tick++) {
            repo.tick();
        }
        assertEquals(1, repo.requests.size());
        repo.tick();
        assertEquals(2, repo.requests.size());
    }

    @Test
    void aDifferentPageIsNotSuppressedWhileAnotherIsInFlight() {
        RecordingRepo repo = new RecordingRepo();
        repo.requests.clear();

        repo.onScrollChanged(0);
        // 目标页落在缓存之外 → 以新偏移请求，不会被"同页去重"吞掉
        repo.onScrollChanged(500);

        assertEquals(2, repo.requests.size());
        assertEquals(440, repo.requests.get(1).getOffset());
        assertEquals(repo.cacheCapacity(), repo.requests.get(1).getLimit());
    }

    @Test
    void queryChangeAlwaysSendsEvenIfThePageMatchesTheInFlightOne() {
        RecordingRepo repo = new RecordingRepo();
        repo.requests.clear();

        repo.onScrollChanged(0);
        assertEquals(1, repo.requests.size());

        // 参数变了就不是"同一页"：即使 offset/limit 相同也必须发出去
        repo.onQueryParamsChanged("abc", (byte) 1, (byte) 0, (byte) 0, (byte) 0);

        assertEquals(2, repo.requests.size());
        assertEquals("abc", repo.requests.get(1).getSearchString());
        assertEquals(1, repo.requests.get(1).getSearchMode());
    }

    @Test
    void v2SplitsThreePagesIntoProtocolSizedSerialChunks() {
        RecordingV2Repo repo = new RecordingV2Repo();
        com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm container =
                org.mockito.Mockito.mock(com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm.class);
        org.mockito.Mockito.when(container.getContainerSessionId()).thenReturn(1L);
        org.mockito.Mockito.when(container.getQueryGeneration()).thenReturn(0L);
        repo.bindContainer(container);
        long initialVersion = repo.getRenderViewVersion();

        repo.onScrollChanged(0);

        assertEquals(1, repo.requests.size());
        assertEquals(0, repo.requests.get(0).getOffset());
        assertEquals(135, repo.requests.get(0).getLimit());
        repo.handlePageResult(new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResultV2(
                1L, 0L, 1L, 270, 0, java.util.Collections.emptyList()));
        assertEquals(2, repo.requests.size());
        assertEquals(135, repo.requests.get(1).getOffset());
        assertEquals(45, repo.requests.get(1).getLimit());
        assertTrue(repo.getRenderViewVersion() > initialVersion);
        long firstChunkVersion = repo.getRenderViewVersion();

        repo.handlePageResult(new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResultV2(
                1L, 0L, 2L, 270, 135, java.util.Collections.emptyList()));
        assertEquals(2, repo.requests.size());
        assertTrue(repo.getRenderViewVersion() > firstChunkVersion);
        assertTrue(repo.harnessQueryState().contains("awaiting=false cacheOffset=0"));
    }

    private static final class RecordingV2Repo extends OmniItemRepo {
        final List<com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequestV2> requests =
                new ArrayList<com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequestV2>();

        RecordingV2Repo() {
            super(new GuiScrollbar(), NO_SORT);
        }

        @Override
        protected void sendToServer(
                com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequestV2 request) {
            requests.add(request);
        }

        @Override
        protected void syncToParentView() {
        }

        @Override
        protected void setChanged(boolean value) {
        }
    }
}
