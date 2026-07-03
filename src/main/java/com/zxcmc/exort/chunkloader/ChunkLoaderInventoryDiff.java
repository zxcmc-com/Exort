package com.zxcmc.exort.chunkloader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ChunkLoaderInventoryDiff {
  private ChunkLoaderInventoryDiff() {}

  static List<Transfer> diff(
      ChunkLoaderItemSnapshot externalBefore,
      ChunkLoaderItemSnapshot playerBefore,
      ChunkLoaderItemSnapshot externalAfter,
      ChunkLoaderItemSnapshot playerAfter) {
    ChunkLoaderItemSnapshot safeExternalBefore =
        externalBefore == null ? ChunkLoaderItemSnapshot.empty() : externalBefore;
    ChunkLoaderItemSnapshot safePlayerBefore =
        playerBefore == null ? ChunkLoaderItemSnapshot.empty() : playerBefore;
    ChunkLoaderItemSnapshot safeExternalAfter =
        externalAfter == null ? ChunkLoaderItemSnapshot.empty() : externalAfter;
    ChunkLoaderItemSnapshot safePlayerAfter =
        playerAfter == null ? ChunkLoaderItemSnapshot.empty() : playerAfter;

    Set<ChunkLoaderItemSnapshot.Key> keys = new LinkedHashSet<>();
    keys.addAll(safeExternalBefore.unionKeys(safeExternalAfter));
    keys.addAll(safePlayerBefore.unionKeys(safePlayerAfter));

    List<Transfer> result = new ArrayList<>();
    for (ChunkLoaderItemSnapshot.Key key : keys.stream().sorted(keyOrder()).toList()) {
      int externalDelta = safeExternalAfter.count(key) - safeExternalBefore.count(key);
      int playerDelta = safePlayerAfter.count(key) - safePlayerBefore.count(key);
      if (externalDelta > 0 && playerDelta < 0) {
        result.add(
            new Transfer(
                Direction.INTO_EXTERNAL,
                key.id(),
                key.type(),
                Math.min(externalDelta, -playerDelta)));
      } else if (externalDelta < 0) {
        int externalLoss = -externalDelta;
        int taken = playerDelta > 0 ? Math.min(externalLoss, playerDelta) : 0;
        if (taken > 0) {
          result.add(new Transfer(Direction.INTO_PLAYER, key.id(), key.type(), taken));
        }
        int lost = externalLoss - taken;
        if (lost > 0) {
          result.add(new Transfer(Direction.LOST_FROM_EXTERNAL, key.id(), key.type(), lost));
        }
      }
    }
    return List.copyOf(result);
  }

  private static Comparator<ChunkLoaderItemSnapshot.Key> keyOrder() {
    return Comparator.comparing(
            ChunkLoaderItemSnapshot.Key::id, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.type().id());
  }

  enum Direction {
    INTO_EXTERNAL,
    INTO_PLAYER,
    LOST_FROM_EXTERNAL
  }

  record Transfer(Direction direction, java.util.UUID id, ChunkLoaderType type, int amount) {}
}
