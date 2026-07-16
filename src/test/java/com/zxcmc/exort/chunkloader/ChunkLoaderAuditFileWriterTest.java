package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkLoaderAuditFileWriterTest {
  @TempDir Path tempDir;

  @Test
  void rotatesCurrentFileAndKeepsConfiguredArchiveCount() throws Exception {
    ChunkLoaderAuditFileWriter writer =
        RotatingChunkLoaderAuditFileWriter.create(
            tempDir,
            new ChunkLoaderAuditFileConfig(true, "logs/chunkloaders.log", 40, 2),
            Logger.getLogger("test"));

    writer.write("first line that is long enough to rotate");
    writer.write("second line that is long enough to rotate");
    writer.write("third line that is long enough to rotate");
    writer.write("fourth line that is long enough to rotate");
    writer.close();

    Path log = tempDir.resolve("logs/chunkloaders.log");
    assertTrue(Files.exists(log));
    assertTrue(Files.exists(log.resolveSibling("chunkloaders.log.1")));
    assertTrue(Files.exists(log.resolveSibling("chunkloaders.log.2")));
    assertFalse(Files.exists(log.resolveSibling("chunkloaders.log.3")));
  }

  @Test
  void unwritableParentWarnsOnceAndLeavesExistingFileUntouched() throws Exception {
    Path occupied = tempDir.resolve("occupied");
    Files.writeString(occupied, "keep-me");
    AtomicInteger warnings = new AtomicInteger();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            warnings.incrementAndGet();
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        });
    ChunkLoaderAuditFileWriter writer =
        RotatingChunkLoaderAuditFileWriter.create(
            tempDir,
            new ChunkLoaderAuditFileConfig(true, "occupied/chunkloaders.log", 40, 2),
            logger);

    writer.write("first");
    writer.write("second");
    writer.close();

    assertEquals(1, warnings.get());
    assertEquals("keep-me", Files.readString(occupied));
  }
}
