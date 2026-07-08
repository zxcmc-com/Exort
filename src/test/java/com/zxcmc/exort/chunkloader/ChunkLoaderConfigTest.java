package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ChunkLoaderConfigTest {
  @Test
  void radiusDefaultsAndClampsToSupportedRange() {
    YamlConfiguration config = new YamlConfiguration();
    assertEquals(1, ChunkLoaderConfig.fromConfig(config, null).radius());

    config.set("chunkLoader.radius", -5);
    assertEquals(0, ChunkLoaderConfig.fromConfig(config, null).radius());

    config.set("chunkLoader.radius", 20);
    assertEquals(8, ChunkLoaderConfig.fromConfig(config, null).radius());
  }

  @Test
  void enabledDefaultsTrueAndReadsPublicKey() {
    YamlConfiguration config = new YamlConfiguration();
    assertTrue(ChunkLoaderConfig.fromConfig(config, null).enabled());

    config.set("chunkLoader.enabled", false);

    assertFalse(ChunkLoaderConfig.fromConfig(config, null).enabled());
  }

  @Test
  void auditUsesPublicCamelCaseInventoryMoveKey() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("chunkLoader.audit.events.inventoryMove", true);

    ChunkLoaderAuditConfig audit = ChunkLoaderAuditConfig.fromConfig(config);

    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.INVENTORY_MOVE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.PLACE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.DESTROY));
  }

  @Test
  void auditConsoleDefaultsLogPlayerFacingEventsExceptTicketNoise() {
    ChunkLoaderAuditConfig audit = ChunkLoaderAuditConfig.fromConfig(new YamlConfiguration());

    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.ISSUE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.DUPLICATE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.CRAFT));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.INVENTORY_MOVE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.DROP));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.PICKUP));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.PLACE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.BREAK));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.ENABLE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.DISABLE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.DESTROY));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.CLEANUP));
    assertFalse(audit.shouldLog(ChunkLoaderAuditEvent.TICKET_ACQUIRE));
    assertFalse(audit.shouldLog(ChunkLoaderAuditEvent.TICKET_RELEASE));
  }

  @Test
  void auditFileDefaultsToFullRotatedLog() {
    ChunkLoaderAuditConfig audit = ChunkLoaderAuditConfig.fromConfig(new YamlConfiguration());

    assertTrue(audit.shouldWriteFile());
    assertEquals(ChunkLoaderAuditFileConfig.DEFAULT_PATH, audit.file().path());
    assertEquals(10L * 1024L * 1024L, audit.file().maxSizeBytes());
    assertEquals(10, audit.file().maxFiles());
  }

  @Test
  void auditFileCanBeDisabledWithoutChangingConsoleEvents() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("chunkLoader.audit.file.enabled", false);

    ChunkLoaderAuditConfig audit = ChunkLoaderAuditConfig.fromConfig(config);

    assertFalse(audit.shouldWriteFile());
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.PLACE));
    assertTrue(audit.shouldLog(ChunkLoaderAuditEvent.INVENTORY_MOVE));
  }

  @Test
  void auditEnabledFalseDisablesConsoleAndFileAudit() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("chunkLoader.audit.enabled", false);
    config.set("chunkLoader.audit.file.enabled", true);
    config.set("chunkLoader.audit.events.inventoryMove", true);

    ChunkLoaderAuditConfig audit = ChunkLoaderAuditConfig.fromConfig(config);

    assertFalse(audit.enabled());
    assertFalse(audit.shouldWriteFile());
    assertFalse(audit.shouldLog(ChunkLoaderAuditEvent.PLACE));
    assertFalse(audit.shouldLog(ChunkLoaderAuditEvent.INVENTORY_MOVE));
  }

  @Test
  void auditFileKeepsTinyRotationValuesForSmokeTests() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("chunkLoader.audit.file.maxSizeBytes", 16);
    config.set("chunkLoader.audit.file.maxFiles", 2);

    ChunkLoaderAuditConfig audit = ChunkLoaderAuditConfig.fromConfig(config);

    assertEquals(16, audit.file().maxSizeBytes());
    assertEquals(2, audit.file().maxFiles());
  }

  @Test
  void auditFilePathCannotEscapeDataFolder() {
    Path dataFolder = Path.of("/tmp/exort").toAbsolutePath().normalize();

    assertEquals(
        dataFolder.resolve(ChunkLoaderAuditFileConfig.DEFAULT_PATH),
        RotatingChunkLoaderAuditFileWriter.resolveSafePath(dataFolder, "../server.log", null));
    assertEquals(
        dataFolder.resolve(ChunkLoaderAuditFileConfig.DEFAULT_PATH),
        RotatingChunkLoaderAuditFileWriter.resolveSafePath(dataFolder, "/tmp/server.log", null));
  }
}
