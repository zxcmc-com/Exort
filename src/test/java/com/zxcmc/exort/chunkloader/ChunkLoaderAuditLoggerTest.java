package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ChunkLoaderAuditLoggerTest {
  @Test
  void fileAuditIgnoresConsoleEventFilters() {
    RecordingWriter writer = new RecordingWriter();
    ChunkLoaderAuditConfig config =
        new ChunkLoaderAuditConfig(
            true,
            EnumSet.complementOf(EnumSet.of(ChunkLoaderAuditEvent.INVENTORY_MOVE)),
            ChunkLoaderAuditFileConfig.defaults());
    ChunkLoaderAuditLogger logger =
        new ChunkLoaderAuditLogger(Logger.getLogger("test"), config, writer);
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

    logger.logInventoryTake(null, id, 1, "CHEST", null);

    assertEquals(1, writer.lines.size());
    String line = writer.lines.getFirst();
    assertTrue(line.contains("action=inventory_take"));
    assertTrue(line.contains("uuid=" + id));
    assertTrue(line.contains("source=CHEST"));
    assertTrue(line.contains("destination=player_inventory"));
  }

  private static final class RecordingWriter implements ChunkLoaderAuditFileWriter {
    private final List<String> lines = new ArrayList<>();

    @Override
    public void write(String line) {
      lines.add(line);
    }

    @Override
    public void close() {}
  }
}
