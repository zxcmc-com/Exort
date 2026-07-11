package com.zxcmc.exort.chunkloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Main-thread-confined quota and reference-count ledger for plugin chunk tickets. */
final class ChunkLoaderTicketLedger {
  enum Status {
    RESERVED,
    ALREADY_RESERVED,
    GLOBAL_LOADER_LIMIT,
    WORLD_LOADER_LIMIT,
    PLAYER_LOADER_LIMIT,
    GLOBAL_CHUNK_LIMIT,
    WORLD_CHUNK_LIMIT
  }

  record Ticket(UUID worldId, int chunkX, int chunkZ) {}

  record Reservation(Status status, List<Ticket> newTickets) {
    Reservation {
      newTickets = List.copyOf(newTickets);
    }

    boolean allowed() {
      return status == Status.RESERVED || status == Status.ALREADY_RESERVED;
    }
  }

  record Release(List<Ticket> removedTickets) {
    Release {
      removedTickets = List.copyOf(removedTickets);
    }
  }

  private final ChunkLoaderLimits limits;
  private final Map<UUID, LoaderReservation> byLoader = new LinkedHashMap<>();
  private final Map<Ticket, Integer> ticketReferences = new HashMap<>();
  private final Map<UUID, Integer> uniqueTicketsByWorld = new HashMap<>();

  ChunkLoaderTicketLedger(ChunkLoaderLimits limits) {
    this.limits = Objects.requireNonNull(limits, "limits");
  }

  Status check(ChunkLoaderRecord record, int radius) {
    return evaluate(record, radius, false).status();
  }

  Reservation reserve(ChunkLoaderRecord record, int radius) {
    return evaluate(record, radius, true);
  }

  private Reservation evaluate(ChunkLoaderRecord record, int radius, boolean commit) {
    Objects.requireNonNull(record, "record");
    if (byLoader.containsKey(record.id())) {
      return new Reservation(Status.ALREADY_RESERVED, List.of());
    }
    if (!record.bypassLimits()) {
      if (byLoader.size() >= limits.maxActiveLoaders()) {
        return denied(Status.GLOBAL_LOADER_LIMIT);
      }
      long worldLoaders =
          byLoader.values().stream()
              .filter(reservation -> reservation.worldId().equals(record.worldId()))
              .count();
      if (worldLoaders >= limits.maxActiveLoadersPerWorld()) {
        return denied(Status.WORLD_LOADER_LIMIT);
      }
      if (record.placedByUuid() != null) {
        long playerLoaders =
            byLoader.values().stream()
                .filter(reservation -> record.placedByUuid().equals(reservation.playerId()))
                .count();
        if (playerLoaders >= limits.maxActiveLoadersPerPlayer()) {
          return denied(Status.PLAYER_LOADER_LIMIT);
        }
      }
    }

    List<Ticket> requested =
        ChunkLoaderArea.square(record.chunkX(), record.chunkZ(), radius).stream()
            .map(chunk -> new Ticket(record.worldId(), chunk.x(), chunk.z()))
            .toList();
    List<Ticket> newTickets =
        requested.stream().filter(ticket -> !ticketReferences.containsKey(ticket)).toList();
    if (!record.bypassLimits()) {
      if ((long) ticketReferences.size() + newTickets.size() > limits.maxUniqueChunks()) {
        return denied(Status.GLOBAL_CHUNK_LIMIT);
      }
      int worldTickets = uniqueTicketsByWorld.getOrDefault(record.worldId(), 0);
      if ((long) worldTickets + newTickets.size() > limits.maxUniqueChunksPerWorld()) {
        return denied(Status.WORLD_CHUNK_LIMIT);
      }
    }

    if (!commit) {
      return new Reservation(Status.RESERVED, newTickets);
    }
    for (Ticket ticket : requested) {
      ticketReferences.merge(ticket, 1, Integer::sum);
    }
    if (!newTickets.isEmpty()) {
      uniqueTicketsByWorld.merge(record.worldId(), newTickets.size(), Integer::sum);
    }
    byLoader.put(
        record.id(),
        new LoaderReservation(record.worldId(), record.placedByUuid(), List.copyOf(requested)));
    return new Reservation(Status.RESERVED, newTickets);
  }

  Release release(UUID loaderId) {
    LoaderReservation reservation = byLoader.remove(loaderId);
    if (reservation == null) {
      return new Release(List.of());
    }
    List<Ticket> removed = new ArrayList<>();
    for (Ticket ticket : reservation.tickets()) {
      int references = ticketReferences.getOrDefault(ticket, 0);
      if (references <= 1) {
        ticketReferences.remove(ticket);
        removed.add(ticket);
      } else {
        ticketReferences.put(ticket, references - 1);
      }
    }
    if (!removed.isEmpty()) {
      uniqueTicketsByWorld.compute(
          reservation.worldId(),
          (worldId, current) -> {
            int remaining = (current == null ? 0 : current) - removed.size();
            return remaining <= 0 ? null : remaining;
          });
    }
    return new Release(removed);
  }

  boolean isReserved(UUID loaderId) {
    return byLoader.containsKey(loaderId);
  }

  int uniqueTicketCount() {
    return ticketReferences.size();
  }

  Set<Ticket> tickets() {
    return Set.copyOf(new LinkedHashSet<>(ticketReferences.keySet()));
  }

  void clear() {
    byLoader.clear();
    ticketReferences.clear();
    uniqueTicketsByWorld.clear();
  }

  private static Reservation denied(Status status) {
    return new Reservation(status, List.of());
  }

  private record LoaderReservation(UUID worldId, UUID playerId, List<Ticket> tickets) {}
}
