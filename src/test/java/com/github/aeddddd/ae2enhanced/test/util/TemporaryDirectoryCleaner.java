package com.github.aeddddd.ae2enhanced.test.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Windows 文件索引/扫描短暂占用目录时，保留失败并在清理前作有限重试。 */
public final class TemporaryDirectoryCleaner {
    private TemporaryDirectoryCleaner() {
    }

    public static void clearContents(Path directory) throws IOException, InterruptedException {
        IOException failure = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(directory))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        });
                return;
            } catch (UncheckedIOException | DirectoryNotEmptyException exception) {
                failure = exception instanceof UncheckedIOException
                        ? ((UncheckedIOException) exception).getCause()
                        : (DirectoryNotEmptyException) exception;
                if (attempt < 19) {
                    Thread.sleep(25L);
                }
            }
        }
        if (failure != null) throw failure;
    }
}
