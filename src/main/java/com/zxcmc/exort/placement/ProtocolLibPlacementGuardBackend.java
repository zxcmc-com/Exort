package com.zxcmc.exort.placement;

import com.zxcmc.exort.integration.protocol.ProtocolLibEnhancements.PlacementGuardPackets;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ProtocolLibPlacementGuardBackend implements PlacementGuardBackend {
  private static final int ENTITY_ID_START = 2_000_000_000;
  private static final int ENTITY_ID_RESET = 1_900_000_000;

  private final PlacementGuardPackets packets;
  private final Consumer<String> failureSink;
  private int nextEntityId = ENTITY_ID_START;

  public ProtocolLibPlacementGuardBackend(
      PlacementGuardPackets packets, Consumer<String> failureSink) {
    this.packets = packets;
    this.failureSink = failureSink;
  }

  @Override
  public String name() {
    return "ProtocolLib fake entity";
  }

  @Override
  public boolean usesServerEntities() {
    return false;
  }

  @Override
  public PlacementGuardHandle createGuard(Player player, GuardTarget target) {
    int entityId = nextEntityId();
    UUID entityUuid = UUID.randomUUID();
    if (!packets.spawnArmorStand(player, entityId, entityUuid, target.location())) {
      markFailure();
      return null;
    }
    return new ProtocolGuardHandle(
        packets, failureSink, player.getUniqueId(), entityId, target.location());
  }

  private void markFailure() {
    failureSink.accept(packets.lastFailure());
  }

  private int nextEntityId() {
    if (nextEntityId <= ENTITY_ID_RESET) {
      nextEntityId = ENTITY_ID_START;
    }
    return nextEntityId--;
  }

  private static final class ProtocolGuardHandle implements PlacementGuardHandle {
    private final PlacementGuardPackets packets;
    private final Consumer<String> failureSink;
    private final UUID playerId;
    private final int entityId;
    private Location location;
    private boolean valid = true;

    private ProtocolGuardHandle(
        PlacementGuardPackets packets,
        Consumer<String> failureSink,
        UUID playerId,
        int entityId,
        Location location) {
      this.packets = packets;
      this.failureSink = failureSink;
      this.playerId = playerId;
      this.entityId = entityId;
      this.location = location.clone();
    }

    @Override
    public boolean isValid() {
      Player player = Bukkit.getPlayer(playerId);
      return valid && player != null && player.isOnline();
    }

    @Override
    public void move(Player player, GuardTarget target) {
      if (!valid || player == null || !player.isOnline()) {
        valid = false;
        return;
      }
      Location next = target.location();
      if (sameLocation(next)) {
        return;
      }
      if (packets.teleportEntity(player, entityId, next)) {
        location = next.clone();
      } else {
        packets.destroyEntity(player, entityId);
        failureSink.accept(packets.lastFailure());
        valid = false;
      }
    }

    @Override
    public void remove() {
      if (!valid) return;
      Player player = Bukkit.getPlayer(playerId);
      if (player != null && player.isOnline()) {
        if (!packets.destroyEntity(player, entityId)) {
          failureSink.accept(packets.lastFailure());
        }
      }
      valid = false;
    }

    @Override
    public UUID bukkitEntityUuid() {
      return null;
    }

    private boolean sameLocation(Location other) {
      return location.getWorld() == other.getWorld() && location.distanceSquared(other) < 1.0E-6;
    }
  }
}
