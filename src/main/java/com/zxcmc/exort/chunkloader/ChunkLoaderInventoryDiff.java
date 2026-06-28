package com.zxcmc.exort.chunkloader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    Set<UUID> ids = new LinkedHashSet<>();
    ids.addAll(safeExternalBefore.unionIds(safeExternalAfter));
    ids.addAll(safePlayerBefore.unionIds(safePlayerAfter));

    List<Transfer> result = new ArrayList<>();
    for (UUID id : ids.stream().sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList()) {
      int externalDelta = safeExternalAfter.count(id) - safeExternalBefore.count(id);
      int playerDelta = safePlayerAfter.count(id) - safePlayerBefore.count(id);
      if (externalDelta > 0 && playerDelta < 0) {
        result.add(
            new Transfer(Direction.INTO_EXTERNAL, id, Math.min(externalDelta, -playerDelta)));
      } else if (externalDelta < 0) {
        int externalLoss = -externalDelta;
        int taken = playerDelta > 0 ? Math.min(externalLoss, playerDelta) : 0;
        if (taken > 0) {
          result.add(new Transfer(Direction.INTO_PLAYER, id, taken));
        }
        int lost = externalLoss - taken;
        if (lost > 0) {
          result.add(new Transfer(Direction.LOST_FROM_EXTERNAL, id, lost));
        }
      }
    }
    return List.copyOf(result);
  }

  enum Direction {
    INTO_EXTERNAL,
    INTO_PLAYER,
    LOST_FROM_EXTERNAL
  }

  record Transfer(Direction direction, UUID id, int amount) {}
}
