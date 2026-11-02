package com.github.aeddddd.ae2enhanced.pathing;

import appeng.me.GridNode;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IGridNodeAccessor;
import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GridValidationBatcherTest {

    private static GridNode detachedNode() {
        GridNode node = mock(GridNode.class, org.mockito.Mockito.withSettings().extraInterfaces(IGridNodeAccessor.class));
        when(node.getGrid()).thenReturn(null);
        return node;
    }

    @Test
    void nestedOperationsFlushOnceAndClearEveryDeferredNode() {
        GridValidationBatcher batcher = new GridValidationBatcher();
        GridNode first = detachedNode();
        GridNode second = detachedNode();

        batcher.beginBatch();
        batcher.deferNode(first);
        batcher.beginBatch();
        batcher.deferNode(second);
        batcher.endBatch(null);

        assertThat(batcher.batchDepth()).isEqualTo(1);
        assertThat(batcher.pendingNodeCount()).isEqualTo(2);
        verify((IGridNodeAccessor) first, never()).ae2e$validateGrid();
        verify((IGridNodeAccessor) second, never()).ae2e$validateGrid();

        batcher.endBatch(null);

        verify((IGridNodeAccessor) first).ae2e$validateGrid();
        verify((IGridNodeAccessor) second).ae2e$validateGrid();
        assertThat(batcher.batchDepth()).isZero();
        assertThat(batcher.pendingNodeCount()).isZero();
    }

    @Test
    void flushFailureClearsOperationBeforeTheNextOne() {
        GridValidationBatcher batcher = new GridValidationBatcher();
        GridNode failing = mock(GridNode.class, org.mockito.Mockito.withSettings().extraInterfaces(IGridNodeAccessor.class));
        when(failing.getGrid()).thenThrow(new IllegalStateException("interrupted"));
        batcher.beginBatch();
        batcher.deferNode(failing);

        assertThatThrownBy(() -> batcher.endBatch(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("interrupted");
        assertThat(batcher.batchDepth()).isZero();
        assertThat(batcher.pendingNodeCount()).isZero();

        GridNode next = detachedNode();
        batcher.beginBatch();
        batcher.deferNode(next);
        batcher.endBatch(null);
        verify((IGridNodeAccessor) next).ae2e$validateGrid();
        verify((IGridNodeAccessor) failing, never()).ae2e$validateGrid();
    }

    @Test
    void serverScopesIsolateNetworksAndABAReopenDropsOldNodes() {
        ServerScope firstScope = new ServerScope();
        GridValidationBatcher first = firstScope.service(
                GridValidationBatcher.class, GridValidationBatcher::new);
        GridNode oldNode = detachedNode();
        first.beginBatch();
        first.deferNode(oldNode);

        ServerScope secondScope = new ServerScope();
        GridValidationBatcher second = secondScope.service(
                GridValidationBatcher.class, GridValidationBatcher::new);
        assertThat(second).isNotSameAs(first);
        assertThat(second.batchDepth()).isZero();
        assertThat(second.pendingNodeCount()).isZero();

        firstScope.close();
        assertThat(first.batchDepth()).isZero();
        assertThat(first.pendingNodeCount()).isZero();
        ServerScope reopenedScope = new ServerScope();
        GridValidationBatcher reopened = reopenedScope.service(
                GridValidationBatcher.class, GridValidationBatcher::new);
        assertThat(reopened.pendingNodeCount()).isZero();
        reopened.beginBatch();
        reopened.deferNode(detachedNode());
        reopened.endBatch(null);

        verify((IGridNodeAccessor) oldNode, never()).ae2e$validateGrid();
        secondScope.close();
        reopenedScope.close();
    }

    @Test
    void mismatchedEndCannotLeaveAnOrphanPendingSet() {
        GridValidationBatcher batcher = new GridValidationBatcher();

        assertThatThrownBy(() -> batcher.endBatch(null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(batcher.batchDepth()).isZero();
        assertThat(batcher.pendingNodeCount()).isZero();
        assertThatThrownBy(() -> batcher.deferNode(detachedNode()))
                .isInstanceOf(IllegalStateException.class);
    }
}
