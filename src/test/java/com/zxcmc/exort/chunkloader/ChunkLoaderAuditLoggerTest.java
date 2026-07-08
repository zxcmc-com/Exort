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

  @Test
  void blockDestroyUsesDestroyActionAndReason() {
    RecordingWriter writer = new RecordingWriter();
    ChunkLoaderAuditConfig config =
        new ChunkLoaderAuditConfig(
            true,
            EnumSet.allOf(ChunkLoaderAuditEvent.class),
            ChunkLoaderAuditFileConfig.defaults());
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000003");

    try (ChunkLoaderAuditLogger logger =
        new ChunkLoaderAuditLogger(Logger.getLogger("test"), config, writer)) {
      logger.log(
          ChunkLoaderAuditEvent.DESTROY,
          null,
          id,
          ChunkLoaderType.DORMANT_CHUNK_LOADER,
          null,
          "creative_break");
    }

    assertEquals(1, writer.lines.size());
    String line = writer.lines.getFirst();
    assertTrue(line.contains("action=destroy"));
    assertTrue(line.contains("type=dormant_chunk_loader"));
    assertTrue(line.contains("reason=creative_break"));
    assertTrue(!line.contains("action=break"));
  }

  @Test
  void creativeInventoryDeleteUsesDestroyedItemAction() {
    RecordingWriter writer = new RecordingWriter();
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000004");

    try (ChunkLoaderAuditLogger logger = fileOnlyLogger(writer)) {
      logger.logItemDestroy(
          null, null, id, ChunkLoaderType.CHUNK_LOADER, 1, "creative_inventory", null);
    }

    assertEquals(1, writer.lines.size());
    String line = writer.lines.getFirst();
    assertTrue(line.contains("action=destroyed"));
    assertTrue(line.contains("reason=creative_inventory"));
    assertTrue(!line.contains("action=lost"));
  }

  @Test
  void physicalItemDestroyReasonsUseDestroyedItemAction() {
    RecordingWriter writer = new RecordingWriter();
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000005");

    try (ChunkLoaderAuditLogger logger = fileOnlyLogger(writer)) {
      for (String reason :
          List.of(
              "despawn",
              "out_of_world",
              "contact",
              "block_explosion",
              "kill",
              "creative_pick_replace",
              "curse_of_vanishing")) {
        logger.logItemDestroy(
            null, null, id, ChunkLoaderType.PERSONAL_CHUNK_LOADER, 1, reason, null);
      }
    }

    assertEquals(7, writer.lines.size());
    for (String line : writer.lines) {
      assertTrue(line.contains("action=destroyed"), line);
      assertTrue(!line.contains("action=lost"), line);
    }
  }

  @Test
  void pluginOrUnknownItemRemovalStaysLost() {
    RecordingWriter writer = new RecordingWriter();
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000006");

    try (ChunkLoaderAuditLogger logger = fileOnlyLogger(writer)) {
      logger.logItemDestroy(null, null, id, ChunkLoaderType.CHUNK_LOADER, 1, "plugin", null);
      logger.logItemDestroy(null, null, id, ChunkLoaderType.CHUNK_LOADER, 1, "discard", null);
    }

    assertEquals(2, writer.lines.size());
    for (String line : writer.lines) {
      assertTrue(line.contains("action=lost"), line);
      assertTrue(!line.contains("action=destroyed"), line);
    }
  }

  @Test
  void duplicateWritesDedicatedAuditAction() {
    RecordingWriter writer = new RecordingWriter();
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000007");

    try (ChunkLoaderAuditLogger logger = fileOnlyLogger(writer)) {
      logger.logDuplicate(
          null, id, ChunkLoaderType.DORMANT_CHUNK_LOADER, 1, "creative_inventory", null);
    }

    assertEquals(1, writer.lines.size());
    String line = writer.lines.getFirst();
    assertTrue(line.contains("action=duplicate"));
    assertTrue(line.contains("uuid=" + id));
    assertTrue(line.contains("type=dormant_chunk_loader"));
    assertTrue(line.contains("source=creative_inventory"));
    assertTrue(line.contains("reason=creative_inventory"));
    assertTrue(!line.contains("action=issue"));
  }

  @Test
  void consoleSourceLabelUsesHumanReadableTextButFileKeepsStableSource() {
    assertEquals("creative pick", ChunkLoaderAuditLogger.consoleSourceLabel("creative_pick"));

    RecordingWriter writer = new RecordingWriter();
    try (ChunkLoaderAuditLogger logger = fileOnlyLogger(writer)) {
      logger.logIssue(null, null, 1, ChunkLoaderType.CHUNK_LOADER, "creative_pick", null);
    }

    assertEquals(1, writer.lines.size());
    assertTrue(writer.lines.getFirst().contains("source=creative_pick"));
  }

  private static ChunkLoaderAuditLogger fileOnlyLogger(RecordingWriter writer) {
    ChunkLoaderAuditConfig config =
        new ChunkLoaderAuditConfig(
            true,
            EnumSet.noneOf(ChunkLoaderAuditEvent.class),
            ChunkLoaderAuditFileConfig.defaults());
    return new ChunkLoaderAuditLogger(null, config, writer);
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
