package com.zxcmc.exort.display.core;

import com.zxcmc.exort.debug.PerfStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;

public final class DisplayEntityIndex {
  private final Map<UUID, Entry> byEntityId = new HashMap<>();
  private final Map<Integer, UUID> byNetworkId = new HashMap<>();
  private final Map<SectionKey, Set<UUID>> bySection = new HashMap<>();

  public void clear() {
    byEntityId.clear();
    byNetworkId.clear();
    bySection.clear();
    updateGauge();
  }

  public void register(Display display) {
    register(display, null, true);
  }

  public void register(Display display, String localizationKey) {
    register(display, localizationKey, false);
  }

  private void register(Display display, String localizationKey, boolean preserveLocalizationKey) {
    if (display == null || !display.isValid()) {
      return;
    }
    DisplayRole role = DisplayRole.fromTags(display.getScoreboardTags());
    if (role == null) {
      unregister(display.getUniqueId());
      return;
    }
    Location location = display.getLocation();
    if (location.getWorld() == null) {
      return;
    }
    Entry previous = byEntityId.get(display.getUniqueId());
    String effectiveLocalizationKey =
        preserveLocalizationKey && previous != null ? previous.localizationKey() : localizationKey;
    Entry entry =
        new Entry(
            display.getUniqueId(),
            display.getEntityId(),
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            role,
            effectiveLocalizationKey,
            SectionKey.of(location));
    previous = byEntityId.put(entry.entityUuid(), entry);
    if (previous != null && !previous.section().equals(entry.section())) {
      removeFromSection(previous);
    }
    if (previous != null && previous.entityId() != entry.entityId()) {
      byNetworkId.remove(previous.entityId());
    }
    byNetworkId.put(entry.entityId(), entry.entityUuid());
    bySection.computeIfAbsent(entry.section(), ignored -> new HashSet<>()).add(entry.entityUuid());
    updateGauge();
  }

  public void unregister(UUID entityUuid) {
    if (entityUuid == null) {
      return;
    }
    Entry previous = byEntityId.remove(entityUuid);
    if (previous != null) {
      byNetworkId.remove(previous.entityId());
      removeFromSection(previous);
      updateGauge();
    }
  }

  public Entry findByNetworkId(int entityId) {
    UUID entityUuid = byNetworkId.get(entityId);
    return entityUuid == null ? null : byEntityId.get(entityUuid);
  }

  public List<Entry> query(Location origin, double range) {
    if (origin == null || origin.getWorld() == null) {
      return List.of();
    }
    List<Entry> out = new ArrayList<>();
    queryInto(origin, range, out);
    return out;
  }

  public void queryInto(Location origin, double range, List<Entry> out) {
    if (out == null) {
      return;
    }
    out.clear();
    if (origin == null || origin.getWorld() == null) {
      return;
    }
    double safeRange = Math.max(0.0, range);
    double rangeSquared = safeRange * safeRange;
    int minX = floorSection(origin.getBlockX() - (int) Math.ceil(safeRange));
    int maxX = floorSection(origin.getBlockX() + (int) Math.ceil(safeRange));
    int minY = floorSection(origin.getBlockY() - (int) Math.ceil(safeRange));
    int maxY = floorSection(origin.getBlockY() + (int) Math.ceil(safeRange));
    int minZ = floorSection(origin.getBlockZ() - (int) Math.ceil(safeRange));
    int maxZ = floorSection(origin.getBlockZ() + (int) Math.ceil(safeRange));
    UUID worldId = origin.getWorld().getUID();
    for (int sectionX = minX; sectionX <= maxX; sectionX++) {
      for (int sectionY = minY; sectionY <= maxY; sectionY++) {
        for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
          Set<UUID> ids = bySection.get(new SectionKey(worldId, sectionX, sectionY, sectionZ));
          if (ids == null || ids.isEmpty()) {
            continue;
          }
          for (UUID id : ids) {
            Entry entry = byEntityId.get(id);
            if (entry == null || !worldId.equals(entry.worldId())) {
              continue;
            }
            if (entry.distanceSquared(origin) <= rangeSquared) {
              out.add(entry);
            }
          }
        }
      }
    }
  }

  public Display resolve(UUID entityUuid) {
    if (entityUuid == null) {
      return null;
    }
    Entity entity = Bukkit.getEntity(entityUuid);
    return entity instanceof Display display && display.isValid() ? display : null;
  }

  private void removeFromSection(Entry entry) {
    Set<UUID> ids = bySection.get(entry.section());
    if (ids == null) {
      return;
    }
    ids.remove(entry.entityUuid());
    if (ids.isEmpty()) {
      bySection.remove(entry.section());
    }
  }

  private void updateGauge() {
    PerfStats.setGauge("display.index.entities", byEntityId.size());
  }

  private static int floorSection(int blockCoord) {
    return blockCoord >> 4;
  }

  public record SectionKey(UUID worldId, int x, int y, int z) {
    static SectionKey of(Location location) {
      return new SectionKey(
          location.getWorld().getUID(),
          floorSection(location.getBlockX()),
          floorSection(location.getBlockY()),
          floorSection(location.getBlockZ()));
    }
  }

  public record Entry(
      UUID entityUuid,
      int entityId,
      UUID worldId,
      int x,
      int y,
      int z,
      DisplayRole role,
      String localizationKey,
      SectionKey section) {
    private double distanceSquared(Location origin) {
      double dx = (x + 0.5) - origin.getX();
      double dy = (y + 0.5) - origin.getY();
      double dz = (z + 0.5) - origin.getZ();
      return dx * dx + dy * dy + dz * dz;
    }
  }
}
