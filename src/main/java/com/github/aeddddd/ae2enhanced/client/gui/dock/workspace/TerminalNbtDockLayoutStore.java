package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt;

/**
 * 阶段 4a 的布局持久化：<strong>物品 NBT 权威 + 本地文件兜底</strong>。
 *
 * <h3>读（零同步）</h3>
 * <p>打开终端时直接读玩家手里那份<strong>客户端副本</strong>物品栈的 {@code omni_layout}，
 * 不发任何包、不等服务端。读不到（新物品/旧终端写的物品）→ 交回默认布局 + reconcile。
 * 读到了但解码失败 → <strong>标记为损坏并返回默认布局</strong>，由
 * {@code DockManager.persistenceBlockedAfterLoadFailure} 保证这份坏数据不会被默认布局就地覆盖
 * （与文件实现"隔离损坏文件"的语义对齐，只是 NBT 场景没有文件可搬，所以只作标记）。</p>
 *
 * <h3>写（双写）</h3>
 * <ol>
 *   <li>本地文件 {@code config/ae2enhanced/spliced_terminal/layout.json}：客户端崩溃时的保险，
 *       也是调试时肉眼可见的一份；</li>
 *   <li>{@code PacketOmniLayout} → 服务端校验后写进终端物品 NBT（权威，跟着物品走）。</li>
 * </ol>
 *
 * <p>物品 NBT 与文件同时存在时<strong>以物品 NBT 为准</strong>：布局跟随终端物品（复制/赠送带走布局
 * 是预期行为），文件只是本机兜底。</p>
 */
public final class TerminalNbtDockLayoutStore implements DockLayoutPersistence {

    /** 上传通道：把布局 JSON 交给网络层（注入以便无头单测）。 */
    public interface LayoutUploader {
        void upload(String layoutJson);
    }

    private final DockLayoutCodec codec;
    private final Supplier<ItemStack> terminalStack;
    private final LayoutUploader uploader;
    /** 本地兜底文件（可为 null：纯物品 NBT 模式）。 */
    private final AtomicFileDockLayoutStore fileBackup;

    private boolean lastLoadCorrupt;
    private Path lastQuarantinedPath;

    public TerminalNbtDockLayoutStore(DockLayoutCodec codec, Supplier<ItemStack> terminalStack,
            LayoutUploader uploader, AtomicFileDockLayoutStore fileBackup) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.terminalStack = Objects.requireNonNull(terminalStack, "terminalStack");
        this.uploader = Objects.requireNonNull(uploader, "uploader");
        this.fileBackup = fileBackup;
    }

    @Override
    public Optional<DockLayoutCodec.DecodedLayout> load() throws IOException {
        this.lastLoadCorrupt = false;
        this.lastQuarantinedPath = null;

        String json = OmniTerminalLayoutNbt.read(stack());
        if (json != null) {
            try {
                return Optional.of(codec.decode(json));
            } catch (DockLayoutFormatException | RuntimeException e) {
                // 物品上的布局坏了：不搬文件、不清 NBT，只标记"本轮加载失败"
                lastLoadCorrupt = true;
                return Optional.empty();
            }
        }
        if (fileBackup != null) {
            Optional<DockLayoutCodec.DecodedLayout> fromFile = fileBackup.load();
            this.lastQuarantinedPath = fileBackup.lastQuarantinedPath().orElse(null);
            this.lastLoadCorrupt = fileBackup.lastLoadWasCorrupt();
            return fromFile;
        }
        return Optional.empty();
    }

    @Override
    public void save(DockWorkspace workspace) throws IOException {
        String json = codec.encode(workspace);
        // 1) 本地兜底（失败不阻断上传：物品 NBT 才是权威）
        if (fileBackup != null) {
            try {
                fileBackup.saveEncoded(json);
            } catch (IOException | RuntimeException e) {
                org.apache.logging.log4j.LogManager.getLogger(TerminalNbtDockLayoutStore.class)
                        .warn("写入可拼接终端布局兜底文件失败(不阻断上传)", e);
            }
        }
        // 2) 上传给服务端写物品 NBT
        uploader.upload(json);
    }

    @Override
    public Optional<Path> lastQuarantinedPath() {
        return Optional.ofNullable(lastQuarantinedPath);
    }

    @Override
    public boolean lastLoadWasCorrupt() {
        return lastLoadCorrupt;
    }

    private ItemStack stack() {
        ItemStack stack = terminalStack.get();
        return stack == null ? ItemStack.EMPTY : stack;
    }
}
