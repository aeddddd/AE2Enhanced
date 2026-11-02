package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.UnaryOperator;

/** 每个服务器作用域一份的智能样板配方权威；disabledMask 不参与共享 revision。 */
public final class SmartPatternRepository implements AutoCloseable {
    private final SmartPatternStorageFile.Adapter storage;
    private final Map<UUID, SmartPatternStorageFile.Stored> cache = new HashMap<>();
    private final Map<UUID, List<ListenerEntry>> listeners = new HashMap<>();
    private boolean closed;

    public SmartPatternRepository(@Nonnull SmartPatternStorageFile.Adapter storage) {
        this.storage = storage;
    }

    @Nonnull
    public static SmartPatternRepository get(@Nullable World world) {
        ServerScope scope = RuntimeScopes.requireServer();
        return scope.service(SmartPatternRepository.class,
                () -> new SmartPatternRepository(SmartPatternStorageFile.adapter(world)));
    }

    @Nullable
    private static Snapshot snapshot(@Nullable SmartPatternStorageFile.Stored stored) {
        return stored == null ? null : new Snapshot(stored.data().copy(), stored.revision());
    }

    @Nullable
    public Snapshot get(@Nonnull UUID id) {
        SmartPatternStorageFile.Stored stored;
        synchronized (this) {
            if (closed) return null;
            stored = load(id);
        }
        return snapshot(stored);
    }

    /** 旧内嵌数据仅在 expectedRevision 仍匹配时迁移；已有更新 revision 不会被旧 Tile 覆盖。 */
    @Nonnull
    public UpdateResult migrate(@Nonnull SmartPatternData data, long expectedRevision) {
        return compareAndSet(data.getPatternDataId(), expectedRevision, current -> data);
    }

    @Nonnull
    public UpdateResult edit(@Nonnull UUID id, long expectedRevision,
                             @Nonnull UnaryOperator<SmartPatternData> editor) {
        return compareAndSet(id, expectedRevision, current -> current == null ? null : editor.apply(current));
    }

    @Nonnull
    private UpdateResult compareAndSet(@Nonnull UUID id, long expectedRevision,
                                       @Nonnull UnaryOperator<SmartPatternData> editor) {
        List<ListenerEntry> notify;
        SmartPatternStorageFile.Stored committed;
        synchronized (this) {
            if (closed) return UpdateResult.failed(Status.CLOSED, null);
            SmartPatternStorageFile.Stored current = load(id);
            long currentRevision = current == null ? 0L : current.revision();
            if (expectedRevision < 0L || currentRevision != expectedRevision) {
                return UpdateResult.failed(Status.STALE, snapshot(current));
            }
            if (currentRevision == Long.MAX_VALUE) {
                return UpdateResult.failed(Status.REVISION_EXHAUSTED, snapshot(current));
            }
            SmartPatternData currentData = current == null ? null : current.data().copy();
            SmartPatternData changed = editor.apply(currentData);
            if (changed == null) {
                return UpdateResult.failed(Status.MISSING, snapshot(current));
            }
            if (!id.equals(changed.getPatternDataId())) {
                throw new IllegalArgumentException("SmartPattern UUID changed during edit");
            }
            changed = changed.copy();
            if (current != null) {
                changed.setDisabledMask(current.data().getDisabledMask());
            } else {
                changed.setDisabledMask(new java.util.BitSet());
            }
            long nextRevision = currentRevision + 1L;
            SmartPatternStorageFile.Stored next = new SmartPatternStorageFile.Stored(changed, nextRevision);
            if (!storage.save(id, next)) {
                return UpdateResult.failed(Status.SAVE_FAILED, snapshot(current));
            }
            committed = next;
            cache.put(id, next);
            notify = liveListeners(id);
        }
        Snapshot snapshot = snapshot(committed);
        for (ListenerEntry entry : notify) {
            SmartPatternListener listener = entry.listener.get();
            if (listener != null) {
                try {
                    listener.onPatternChanged(id, snapshot.revision());
                } catch (RuntimeException exception) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error(
                            "[AE2E] SmartPattern deployed listener failed for {}", id, exception);
                }
            }
        }
        return UpdateResult.committed(snapshot);
    }

    /** 返回的注册句柄保持 listener 存活；repository 只弱引用 listener，关闭或失效后可回收。 */
    @Nonnull
    public ListenerRegistration listen(@Nonnull UUID id, @Nonnull SmartPatternListener listener) {
        ListenerEntry entry = new ListenerEntry(listener);
        synchronized (this) {
            if (closed) return ListenerRegistration.closed();
            listeners.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entry);
            liveListeners(id);
        }
        return new ListenerRegistration(this, id, entry, listener);
    }

    @Nonnull
    private SmartPatternStorageFile.Stored load(@Nonnull UUID id) {
        SmartPatternStorageFile.Stored stored = cache.get(id);
        if (stored == null) {
            stored = storage.load(id);
            if (stored != null) {
                cache.put(id, stored);
            }
        }
        return stored;
    }

    @Nonnull
    private List<ListenerEntry> liveListeners(@Nonnull UUID id) {
        List<ListenerEntry> entries = listeners.get(id);
        if (entries == null) return java.util.Collections.emptyList();
        List<ListenerEntry> result = new ArrayList<>(entries.size());
        Iterator<ListenerEntry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            ListenerEntry entry = iterator.next();
            if (entry.closed || entry.listener.get() == null) {
                iterator.remove();
            } else {
                result.add(entry);
            }
        }
        if (entries.isEmpty()) listeners.remove(id);
        return result;
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            cache.clear();
            listeners.clear();
        }
    }

    public enum Status {
        COMMITTED, STALE, MISSING, SAVE_FAILED, REVISION_EXHAUSTED, CLOSED
    }

    public interface SmartPatternListener {
        void onPatternChanged(@Nonnull UUID id, long revision);
    }

    private static final class ListenerEntry {
        private final java.lang.ref.WeakReference<SmartPatternListener> listener;
        private boolean closed;

        private ListenerEntry(SmartPatternListener listener) {
            this.listener = new java.lang.ref.WeakReference<>(listener);
        }
    }

    public static final class ListenerRegistration implements AutoCloseable {
        private final SmartPatternRepository repository;
        private final UUID id;
        private final ListenerEntry entry;
        @SuppressWarnings("unused")
        private final SmartPatternListener listener;

        private ListenerRegistration(SmartPatternRepository repository, UUID id,
                                     ListenerEntry entry, SmartPatternListener listener) {
            this.repository = repository;
            this.id = id;
            this.entry = entry;
            this.listener = listener;
        }

        private static ListenerRegistration closed() {
            return new ListenerRegistration(null, new UUID(0L, 0L), new ListenerEntry(null), null);
        }

        @Override
        public void close() {
            if (repository == null) return;
            synchronized (repository) {
                entry.closed = true;
                repository.liveListeners(id);
            }
        }
    }

    public static final class Snapshot {
        private final SmartPatternData data;
        private final long revision;

        private Snapshot(SmartPatternData data, long revision) {
            this.data = data;
            this.revision = revision;
        }

        @Nonnull
        public SmartPatternData data() {
            return data.copy();
        }

        public long revision() {
            return revision;
        }
    }

    public static final class UpdateResult {
        private final Status status;
        private final Snapshot snapshot;

        private UpdateResult(Status status, Snapshot snapshot) {
            this.status = status;
            this.snapshot = snapshot;
        }

        private static UpdateResult committed(Snapshot snapshot) {
            return new UpdateResult(Status.COMMITTED, snapshot);
        }

        private static UpdateResult failed(Status status, Snapshot snapshot) {
            return new UpdateResult(status, snapshot);
        }

        @Nonnull
        public Status status() {
            return status;
        }

        public boolean committed() {
            return status == Status.COMMITTED;
        }

        @Nullable
        public Snapshot snapshot() {
            return snapshot;
        }
    }
}
