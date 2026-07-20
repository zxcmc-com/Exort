package com.zxcmc.exort.bus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class BusRegistryTest {
  @Test
  void cachedStateListIsStructurallyImmutable() {
    try (Database database = new EmptyBusDatabase()) {
      BusRegistry registry = new BusRegistry(BukkitTestDoubles.plugin(), database);
      BusPos position =
          new BusPos(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1, 64, 1);
      BusState state =
          registry.getOrCreateState(
              position, new BusMarker.Data(BusType.IMPORT, BlockFace.NORTH, BusMode.ALL), null);

      List<BusState> snapshot = registry.snapshotList();

      assertEquals(1, snapshot.size());
      assertSame(state, snapshot.getFirst());
      assertThrows(UnsupportedOperationException.class, snapshot::clear);
      assertSame(snapshot, registry.snapshotList());
    }
  }

  private static final class EmptyBusDatabase extends Database {
    @Override
    public CompletableFuture<Optional<BusSettings>> loadBusSettings(BusPos pos, int slots) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
  }
}
