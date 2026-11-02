/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import com.github.aeddddd.ae2enhanced.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.dock.workspace.DockLayoutCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface DockLayoutPersistence {
    Optional<DockLayoutCodec.DecodedLayout> load() throws IOException;

    void save(DockWorkspace workspace) throws IOException;

    /**
     * Reports the file produced when the most recent load quarantined a corrupt document.
     *
     * <p>Persistence implementations that do not support quarantine can keep the default empty
     * result. Exposing the state at the interface boundary lets callers avoid immediately replacing
     * recovered data with defaults without depending on a concrete store implementation.</p>
     */
    default Optional<Path> lastQuarantinedPath() {
        return Optional.empty();
    }

    /**
     * 最近一次 load 是否遇到了损坏文档（含"没有文件可搬"的场景，例如物品 NBT 里的布局）。
     *
     * <p>{@code DockManager} 在没有恢复结果时禁止回写；有有效恢复源且 {@link #lastLoadNeedsRepair()}
     * 为真时，会写回恢复内容修复损坏源。默认实现与 {@link #lastQuarantinedPath()} 等价。</p>
     */
    default boolean lastLoadWasCorrupt() {
        return lastQuarantinedPath().isPresent();
    }

    default boolean lastLoadNeedsRepair() {
        return false;
    }
}
