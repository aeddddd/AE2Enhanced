package com.github.aeddddd.ae2enhanced.dimension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FloorRegionScannerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsOnlyLocationTableAndFiltersTaskBounds() throws Exception {
        byte[] region = new byte[3 * 4096];
        region[0] = 0;
        region[1] = 0;
        region[2] = 2;
        region[3] = 1;
        int outside = 4;
        region[outside * 4 + 2] = 2;
        region[outside * 4 + 3] = 1;
        Files.write(temporaryDirectory.resolve("r.0.0.mca"), region);

        List<Long> chunks = FloorRegionScanner.scan(temporaryDirectory, 0, 0, 0, 0, 0, 0);

        assertThat(chunks).containsExactly(FloorRepaintTask.chunkKey(0, 0));
    }

    @Test
    void missingRegionProducesNoChunks() throws Exception {
        assertThat(FloorRegionScanner.scan(temporaryDirectory, 4, 4, 0, 256, 0, 256)).isEmpty();
    }
}
