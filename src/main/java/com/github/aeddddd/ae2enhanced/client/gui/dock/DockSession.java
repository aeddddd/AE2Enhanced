package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.io.IOException;

import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutPersistence;
import com.github.aeddddd.ae2enhanced.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.dock.workspace.DockLayoutDto;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class DockSession {
    private static final Logger LOGGER = LogManager.getLogger(DockSession.class);

    private DockLayoutPersistence persistence;
    private DockWorkspace workspace;
    private long revision;
    private long persistedRevision = -1L;
    private boolean persistenceBlocked;

    DockWorkspace workspace() {
        return workspace;
    }

    void replace(DockWorkspace next) {
        this.workspace = next;
    }

    void attach(DockLayoutPersistence persistence) {
        this.persistence = persistence;
    }

    boolean isPersistenceBlocked() {
        return persistenceBlocked;
    }

    void setPersistenceBlocked(boolean blocked) {
        this.persistenceBlocked = blocked;
    }

    void initializeRevision(long revision, boolean persisted) {
        this.revision = revision;
        this.persistedRevision = persisted ? revision : -1L;
    }

    void allowPersistence() {
        this.persistenceBlocked = false;
    }

    void markUserEdit() {
        this.persistenceBlocked = false;
        this.revision++;
    }

    void save(DockWorkspace workspace, boolean gestureActive) {
        if (persistence == null || workspace == null || persistedRevision == revision
                || persistenceBlocked || gestureActive) {
            return;
        }
        try {
            persistence.save(workspace);
            persistedRevision = revision;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to write dock layout v{}", DockLayoutDto.CURRENT_VERSION, e);
        }
    }
}
