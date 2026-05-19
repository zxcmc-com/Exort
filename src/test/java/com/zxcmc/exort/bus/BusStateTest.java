package com.zxcmc.exort.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class BusStateTest {
  @Test
  void settingsRevisionChangesOnlyWhenPersistentSettingsChange() {
    BusState state =
        new BusState(
            new BusPos(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1, 2, 3),
            BusType.IMPORT,
            BlockFace.NORTH,
            BusMode.DISABLED);

    long revision = state.settingsRevision();

    state.setMode(BusMode.WHITELIST);
    assertEquals(++revision, state.settingsRevision());

    state.setMode(BusMode.WHITELIST);
    assertEquals(revision, state.settingsRevision());

    state.setFacing(BlockFace.EAST);
    assertEquals(++revision, state.settingsRevision());

    state.setFacing(BlockFace.EAST);
    assertEquals(revision, state.settingsRevision());

    state.setType(BusType.EXPORT);
    assertEquals(++revision, state.settingsRevision());
  }
}
