package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
