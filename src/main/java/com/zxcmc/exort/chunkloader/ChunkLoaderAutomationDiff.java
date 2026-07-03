package com.zxcmc.exort.chunkloader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ChunkLoaderAutomationDiff {
  private ChunkLoaderAutomationDiff() {}

  static List<Result> diff(
      ChunkLoaderItemSnapshot moving,
      ChunkLoaderItemSnapshot sourceBefore,
      ChunkLoaderItemSnapshot destinationBefore,
      ChunkLoaderItemSnapshot sourceAfter,
      ChunkLoaderItemSnapshot destinationAfter) {
    if (moving == null || moving.isEmpty()) {
      return List.of();
    }
    ChunkLoaderItemSnapshot safeSourceBefore =
        sourceBefore == null ? ChunkLoaderItemSnapshot.empty() : sourceBefore;
    ChunkLoaderItemSnapshot safeDestinationBefore =
        destinationBefore == null ? ChunkLoaderItemSnapshot.empty() : destinationBefore;
    ChunkLoaderItemSnapshot safeSourceAfter =
        sourceAfter == null ? ChunkLoaderItemSnapshot.empty() : sourceAfter;
    ChunkLoaderItemSnapshot safeDestinationAfter =
        destinationAfter == null ? ChunkLoaderItemSnapshot.empty() : destinationAfter;

    List<Result> result = new ArrayList<>();
    for (ChunkLoaderItemSnapshot.Key key : moving.keys().stream().sorted(keyOrder()).toList()) {
      int remaining = Math.max(0, moving.count(key));
      int destinationGain =
          Math.max(0, safeDestinationAfter.count(key) - safeDestinationBefore.count(key));
      int sourceLoss = Math.max(0, safeSourceBefore.count(key) - safeSourceAfter.count(key));
      int moved =
          sourceLoss > 0
              ? Math.min(Math.min(remaining, sourceLoss), destinationGain)
              : Math.min(remaining, destinationGain);
      if (moved > 0) {
        result.add(new Result(Action.MOVED, key.id(), key.type(), moved));
      }
      if (sourceLoss > 0) {
        int lost = Math.min(remaining, sourceLoss) - moved;
        if (lost > 0) {
          result.add(new Result(Action.LOST, key.id(), key.type(), lost));
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

  enum Action {
    MOVED,
    LOST
  }

  record Result(Action action, java.util.UUID id, ChunkLoaderType type, int amount) {}
}
