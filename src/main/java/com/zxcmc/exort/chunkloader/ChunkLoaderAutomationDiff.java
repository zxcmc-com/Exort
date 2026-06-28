package com.zxcmc.exort.chunkloader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
    for (UUID id :
        moving.ids().stream().sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList()) {
      int remaining = Math.max(0, moving.count(id));
      int destinationGain =
          Math.max(0, safeDestinationAfter.count(id) - safeDestinationBefore.count(id));
      int sourceLoss = Math.max(0, safeSourceBefore.count(id) - safeSourceAfter.count(id));
      int moved =
          sourceLoss > 0
              ? Math.min(Math.min(remaining, sourceLoss), destinationGain)
              : Math.min(remaining, destinationGain);
      if (moved > 0) {
        result.add(new Result(Action.MOVED, id, moved));
      }
      if (sourceLoss > 0) {
        int lost = Math.min(remaining, sourceLoss) - moved;
        if (lost > 0) {
          result.add(new Result(Action.LOST, id, lost));
        }
      }
    }
    return List.copyOf(result);
  }

  enum Action {
    MOVED,
    LOST
  }

  record Result(Action action, UUID id, int amount) {}
}
