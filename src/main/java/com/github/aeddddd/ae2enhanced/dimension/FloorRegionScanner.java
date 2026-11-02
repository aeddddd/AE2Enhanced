package com.github.aeddddd.ae2enhanced.dimension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 后台 region location table 只读扫描；不接触 live World、Chunk 或 TileEntity。 */
public final class FloorRegionScanner {
    private FloorRegionScanner() {
    }

    public static List<Long> scan(Path regionDirectory, int regionX, int regionZ,
            int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) throws IOException {
        Path file = regionDirectory.resolve("r." + regionX + "." + regionZ + ".mca");
        if (!Files.isRegularFile(file) || Files.size(file) < 4096L) {
            return Collections.emptyList();
        }
        byte[] header = new byte[4096];
        try (InputStream input = Files.newInputStream(file)) {
            int read = 0;
            while (read < header.length) {
                int count = input.read(header, read, header.length - read);
                if (count < 0) {
                    throw new IOException("Truncated region location table: " + file);
                }
                read += count;
            }
        }
        long fileSize = Files.size(file);
        List<Long> chunks = new ArrayList<>();
        int baseX = regionX * 32;
        int baseZ = regionZ * 32;
        for (int index = 0; index < 1024; index++) {
            int offset = index * 4;
            int sectorOffset = (header[offset] & 0xff) << 16 | (header[offset + 1] & 0xff) << 8
                    | header[offset + 2] & 0xff;
            int sectorCount = header[offset + 3] & 0xff;
            if (sectorOffset == 0 || sectorCount == 0) {
                continue;
            }
            if (sectorOffset < 2 || (long) sectorOffset * 4096L >= fileSize) {
                throw new IOException("Invalid region chunk location: " + file + " index " + index);
            }
            int chunkX = baseX + index % 32;
            int chunkZ = baseZ + index / 32;
            if (chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ) {
                chunks.add(FloorRepaintTask.chunkKey(chunkX, chunkZ));
            }
        }
        return chunks;
    }
}
