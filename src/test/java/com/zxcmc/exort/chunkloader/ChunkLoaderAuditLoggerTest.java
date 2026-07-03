package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.items.CustomItemText;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
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
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

    try (ChunkLoaderAuditLogger logger =
        new ChunkLoaderAuditLogger(Logger.getLogger("test"), config, writer)) {
      logger.logInventoryTake(null, id, 1, "CHEST", null);
    }

    assertEquals(1, writer.lines.size());
    String line = writer.lines.getFirst();
    assertTrue(line.contains("action=inventory_take"));
    assertTrue(line.contains("uuid=" + id));
    assertTrue(line.contains("type=chunk_loader"));
    assertTrue(line.contains("name=\"Chunk Loader\""));
    assertTrue(line.contains("source=CHEST"));
    assertTrue(line.contains("destination=player_inventory"));
  }

  @Test
  void fileAuditIncludesTypedChunkLoaderNamesWhenConsoleEventIsFiltered() {
    RecordingWriter writer = new RecordingWriter();
    ChunkLoaderAuditConfig config =
        new ChunkLoaderAuditConfig(
            true,
            EnumSet.complementOf(EnumSet.of(ChunkLoaderAuditEvent.INVENTORY_MOVE)),
            ChunkLoaderAuditFileConfig.defaults());
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");

    try (ChunkLoaderAuditLogger logger =
        new ChunkLoaderAuditLogger(Logger.getLogger("test"), config, writer)) {
      logger.logInventoryTake(null, id, ChunkLoaderType.PERSONAL_CHUNK_LOADER, 2, "CHEST", null);
    }

    assertEquals(1, writer.lines.size());
    String line = writer.lines.getFirst();
    assertTrue(line.contains("action=inventory_take"));
    assertTrue(line.contains("uuid=" + id));
    assertTrue(line.contains("type=personal_chunk_loader"));
    assertTrue(line.contains("name=\"Personal Chunk Loader\""));
    assertTrue(line.contains("amount=2"));
  }

  @Test
  void ansiFallbackKeepsHexChunkLoaderColor() {
    String rendered =
        ChunkLoaderAuditLogger.ansiSerialize(
            Component.text(
                "Personal Chunk Loader",
                CustomItemText.chunkLoaderNameColor(ChunkLoaderType.PERSONAL_CHUNK_LOADER)));

    assertTrue(rendered.contains("\u001B[38;2;136;71;255mPersonal Chunk Loader"));
    assertTrue(rendered.endsWith("\u001B[0m"));
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
