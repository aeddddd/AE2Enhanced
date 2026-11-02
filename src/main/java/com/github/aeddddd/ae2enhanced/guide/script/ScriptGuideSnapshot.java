package com.github.aeddddd.ae2enhanced.guide.script;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 登录同步使用的不可变全量快照及其有界二进制编码。 */
public final class ScriptGuideSnapshot {
    private final long revision;
    private final List<ScriptGuidePage> pages;

    public ScriptGuideSnapshot(long revision, List<ScriptGuidePage> pages) {
        this.revision = revision;
        this.pages = Collections.unmodifiableList(new ArrayList<>(pages));
    }

    public static ScriptGuideSnapshot decode(long revision, byte[] encoded) throws IOException {
        if (encoded == null || encoded.length > ScriptGuideRepository.MAX_TOTAL_BYTES
                + ScriptGuideRepository.MAX_PAGES * 128) {
            throw new IOException("Invalid script guide snapshot size");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int count = in.readInt();
            if (count < 0 || count > ScriptGuideRepository.MAX_PAGES * 32) {
                throw new IOException("Invalid script guide entry count");
            }
            List<ScriptGuidePage> pages = new ArrayList<>();
            int totalContent = 0;
            for (int i = 0; i < count; i++) {
                String pageId = in.readUTF();
                String language = in.readUTF();
                int length = in.readInt();
                if (length < 0 || length > ScriptGuideRepository.MAX_PAGE_BYTES
                        || totalContent > ScriptGuideRepository.MAX_TOTAL_BYTES - length) {
                    throw new IOException("Invalid script guide content length");
                }
                byte[] content = new byte[length];
                in.readFully(content);
                totalContent += length;
                String text = ScriptGuideService.decodeUtf8(content);
                ScriptGuideService.validatePageId(pageId);
                ScriptGuideService.validateLanguage(language, true);
                pages.add(new ScriptGuidePage(pageId, language, text));
            }
            if (in.read() != -1) {
                throw new IOException("Trailing script guide data");
            }
            return new ScriptGuideSnapshot(revision, Collections.unmodifiableList(pages));
        } catch (EOFException exception) {
            throw new IOException("Truncated script guide snapshot", exception);
        }
    }

    public long getRevision() {
        return revision;
    }

    public List<ScriptGuidePage> getPages() {
        return pages;
    }

    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(pages.size());
            for (ScriptGuidePage page : pages) {
                out.writeUTF(page.getPageId());
                out.writeUTF(page.getLanguage());
                byte[] content = page.getContent().getBytes(StandardCharsets.UTF_8);
                out.writeInt(content.length);
                out.write(content);
            }
        }
        if (bytes.size() > ScriptGuideRepository.MAX_TOTAL_BYTES + ScriptGuideRepository.MAX_PAGES * 128) {
            throw new IOException("Script guide snapshot overhead exceeds budget");
        }
        return bytes.toByteArray();
    }
}
