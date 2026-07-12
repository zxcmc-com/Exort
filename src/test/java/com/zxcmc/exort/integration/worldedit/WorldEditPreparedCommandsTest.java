package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldEditPreparedCommandsTest {
  @Test
  void commandSignatureNormalizesSlashNamespaceAndWhitespace() {
    assertEquals(
        WorldEditCommandParser.commandSignature("//move   5   east"),
        WorldEditCommandParser.commandSignature("worldedit:move 5 east"));
    assertEquals("redo 2", WorldEditCommandParser.commandSignature("/worldedit:redo  2"));
  }

  @Test
  void preparedCommandIsConsumedOnceByTheSameActor() {
    WorldEditPreparedCommands commands = new WorldEditPreparedCommands(5_000L);
    UUID actor = new UUID(1L, 2L);

    commands.remember(actor, "//copy", 1_000L);

    assertTrue(commands.consume(actor, "worldedit:copy", 1_001L));
    assertFalse(commands.consume(actor, "//copy", 1_002L));
  }

  @Test
  void actorsAndCommandsDoNotConsumeEachOthersPreparation() {
    WorldEditPreparedCommands commands = new WorldEditPreparedCommands(5_000L);
    UUID console = new UUID(3L, 4L);
    UUID player = new UUID(5L, 6L);

    commands.remember(console, "//cut", 1_000L);
    commands.remember(player, "//copy", 1_000L);

    assertFalse(commands.consume(console, "//copy", 1_001L));
    assertTrue(commands.consume(console, "//cut", 1_001L));
    assertTrue(commands.consume(player, "//copy", 1_001L));
  }

  @Test
  void expiredPreparationIsDiscarded() {
    WorldEditPreparedCommands commands = new WorldEditPreparedCommands(100L);
    UUID actor = new UUID(7L, 8L);

    commands.remember(actor, "//paste", 1_000L);

    assertFalse(commands.consume(actor, "//paste", 1_101L));
    assertEquals(0, commands.size());
  }

  @Test
  void preparationMapRemainsBounded() {
    WorldEditPreparedCommands commands = new WorldEditPreparedCommands(5_000L);
    for (int index = 0; index < 300; index++) {
      commands.remember(new UUID(0L, index), "//set stone", 1_000L);
    }

    assertEquals(256, commands.size());
    assertFalse(commands.consume(new UUID(0L, 0L), "//set stone", 1_001L));
    assertTrue(commands.consume(new UUID(0L, 299L), "//set stone", 1_001L));
  }
}
