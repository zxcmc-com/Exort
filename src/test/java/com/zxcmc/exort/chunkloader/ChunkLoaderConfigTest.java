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
  void limitsUseSafeDefaultsAndClampOperationalValues() {
    YamlConfiguration config = new YamlConfiguration();

    ChunkLoaderLimits defaults = ChunkLoaderConfig.fromConfig(config, null).limits();

    assertEquals(ChunkLoaderLimits.DEFAULT_MAX_ACTIVE_LOADERS, defaults.maxActiveLoaders());
    assertEquals(
        ChunkLoaderLimits.DEFAULT_MAX_ACTIVE_LOADERS_PER_WORLD,
        defaults.maxActiveLoadersPerWorld());
    assertEquals(
        ChunkLoaderLimits.DEFAULT_MAX_ACTIVE_LOADERS_PER_PLAYER,
        defaults.maxActiveLoadersPerPlayer());
    assertEquals(ChunkLoaderLimits.DEFAULT_MAX_UNIQUE_CHUNKS, defaults.maxUniqueChunks());
    assertEquals(
        ChunkLoaderLimits.DEFAULT_MAX_UNIQUE_CHUNKS_PER_WORLD, defaults.maxUniqueChunksPerWorld());

    config.set("chunkLoader.limits.maxActiveLoaders", Integer.MAX_VALUE);
    config.set("chunkLoader.limits.maxActiveLoadersPerWorld", -1);
    config.set("chunkLoader.limits.maxActiveLoadersPerPlayer", 0);
    config.set("chunkLoader.limits.maxUniqueChunks", Integer.MAX_VALUE);
    config.set("chunkLoader.limits.maxUniqueChunksPerWorld", -100);

    ChunkLoaderLimits clamped = ChunkLoaderConfig.fromConfig(config, null).limits();

    assertEquals(ChunkLoaderLimits.MAX_ACTIVE_LOADERS, clamped.maxActiveLoaders());
    assertEquals(ChunkLoaderLimits.MIN_ACTIVE_LOADERS, clamped.maxActiveLoadersPerWorld());
    assertEquals(ChunkLoaderLimits.MIN_ACTIVE_LOADERS, clamped.maxActiveLoadersPerPlayer());
    assertEquals(ChunkLoaderLimits.MAX_UNIQUE_CHUNKS, clamped.maxUniqueChunks());
    assertEquals(ChunkLoaderLimits.MIN_UNIQUE_CHUNKS, clamped.maxUniqueChunksPerWorld());
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
  void auditFileRotationValuesHaveDiskUsageBounds() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("chunkLoader.audit.file.maxSizeBytes", Long.MAX_VALUE);
    config.set("chunkLoader.audit.file.maxFiles", Integer.MAX_VALUE);

    ChunkLoaderAuditConfig audit = ChunkLoaderAuditConfig.fromConfig(config);

    assertEquals(ChunkLoaderAuditFileConfig.MAX_SIZE_BYTES, audit.file().maxSizeBytes());
    assertEquals(ChunkLoaderAuditFileConfig.MAX_FILES, audit.file().maxFiles());
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
    assertEquals(
        dataFolder.resolve(ChunkLoaderAuditFileConfig.DEFAULT_PATH),
        RotatingChunkLoaderAuditFileWriter.resolveSafePath(dataFolder, "bad\0path", null));
  }
}
