package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DebugCommandTeleportTest {
  @Test
  void teleportCommandChangesDimensionBeforeMovingPlayer() {
    assertEquals(
        "/minecraft:execute in minecraft:the_nether run minecraft:tp @s 10.5 65.0 -2.5",
        DebugCommand.teleportCommand("minecraft:the_nether", 10.5D, 65.0D, -2.5D));
  }

  @Test
  void invalidDimensionKeyCannotInjectCommand() {
    assertEquals(
        "/minecraft:tp 10.5 65.0 -2.5",
        DebugCommand.teleportCommand("minecraft:overworld run say injected", 10.5D, 65.0D, -2.5D));
  }
}
