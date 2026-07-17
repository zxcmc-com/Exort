package com.zxcmc.exort.display.core;

import com.zxcmc.exort.debug.PerfStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;

public final class DisplayEntityIndex {
  private final Map<UUID, Entry> byEntityId = new ConcurrentHashMap<>();
  private final Map<Integer, UUID> byNetworkId = new ConcurrentHashMap<>();
  private final Map<SectionKey, Set<UUID>> bySection = new ConcurrentHashMap<>();

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
      byNetworkId.remove(previous.entityId(), previous.entityUuid());
    }
    byNetworkId.put(entry.entityId(), entry.entityUuid());
    bySection
        .computeIfAbsent(entry.section(), ignored -> ConcurrentHashMap.newKeySet())
        .add(entry.entityUuid());
    updateGauge();
  }

  public void unregister(UUID entityUuid) {
    if (entityUuid == null) {
      return;
    }
    Entry previous = byEntityId.remove(entityUuid);
    if (previous != null) {
      byNetworkId.remove(previous.entityId(), previous.entityUuid());
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
    QueryCursor cursor = openQuery(origin, range);
    while (!cursor.complete()) {
      cursor.advance(4096, out::add);
    }
  }

  /** Opens a main-thread-confined, resumable query over the concurrent spatial index. */
  public QueryCursor openQuery(Location origin, double range) {
    if (origin == null || origin.getWorld() == null) {
      return QueryCursor.empty(this);
    }
    double safeRange = Math.max(0.0, range);
    int roundedRange = (int) Math.min(Integer.MAX_VALUE, Math.ceil(safeRange));
    int minX = floorSection(saturatingAdd(origin.getBlockX(), -roundedRange));
    int maxX = floorSection(saturatingAdd(origin.getBlockX(), roundedRange));
    int minY = floorSection(saturatingAdd(origin.getBlockY(), -roundedRange));
    int maxY = floorSection(saturatingAdd(origin.getBlockY(), roundedRange));
    int minZ = floorSection(saturatingAdd(origin.getBlockZ(), -roundedRange));
    int maxZ = floorSection(saturatingAdd(origin.getBlockZ(), roundedRange));
    return new QueryCursor(
        this,
        origin.getWorld().getUID(),
        origin.getX(),
        origin.getY(),
        origin.getZ(),
        safeRange * safeRange,
        minX,
        maxX,
        minY,
        maxY,
        minZ,
        maxZ);
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
      bySection.remove(entry.section(), ids);
    }
  }

  private void updateGauge() {
    PerfStats.setGauge("display.index.entities", byEntityId.size());
  }

  private static int floorSection(int blockCoord) {
    return blockCoord >> 4;
  }

  private static int saturatingAdd(int value, int delta) {
    long result = (long) value + delta;
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
  }

  public record QueryStep(int examined, int matched, boolean complete) {}

  /**
   * Main-thread-confined state for one bounded query. Concurrent index updates are tolerated
   * through weakly consistent iterators; callers must not share one cursor between threads.
   */
  public static final class QueryCursor {
    private final DisplayEntityIndex index;
    private final UUID worldId;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final double rangeSquared;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;
    private int sectionX;
    private int sectionY;
    private int sectionZ;
    private Iterator<UUID> currentIds = Collections.emptyIterator();
    private boolean sectionPending;
    private boolean complete;

    private QueryCursor(
        DisplayEntityIndex index,
        UUID worldId,
        double originX,
        double originY,
        double originZ,
        double rangeSquared,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ) {
      this.index = index;
      this.worldId = worldId;
      this.originX = originX;
      this.originY = originY;
      this.originZ = originZ;
      this.rangeSquared = rangeSquared;
      this.maxX = maxX;
      this.minY = minY;
      this.maxY = maxY;
      this.minZ = minZ;
      this.maxZ = maxZ;
      sectionX = minX;
      sectionY = minY;
      sectionZ = minZ;
      sectionPending = true;
    }

    private static QueryCursor empty(DisplayEntityIndex index) {
      QueryCursor cursor = new QueryCursor(index, null, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0);
      cursor.complete = true;
      cursor.sectionPending = false;
      return cursor;
    }

    public QueryStep advance(int maxWork, Consumer<Entry> consumer) {
      if (complete || maxWork <= 0) {
        return new QueryStep(0, 0, complete);
      }
      int examined = 0;
      int matched = 0;
      while (!complete && examined < maxWork) {
        if (currentIds.hasNext()) {
          UUID id = currentIds.next();
          examined++;
          Entry entry = index.byEntityId.get(id);
          if (entry != null
              && worldId.equals(entry.worldId())
              && entry.distanceSquared(originX, originY, originZ) <= rangeSquared) {
            consumer.accept(entry);
            matched++;
          }
          continue;
        }
        if (!sectionPending) {
          advanceSection();
          continue;
        }
        examined++;
        Set<UUID> ids = index.bySection.get(new SectionKey(worldId, sectionX, sectionY, sectionZ));
        currentIds = ids == null ? Collections.emptyIterator() : ids.iterator();
        sectionPending = false;
      }
      if (!complete && !currentIds.hasNext() && !sectionPending) {
        advanceSection();
      }
      return new QueryStep(examined, matched, complete);
    }

    public boolean complete() {
      return complete;
    }

    private void advanceSection() {
      if (sectionZ < maxZ) {
        sectionZ++;
      } else if (sectionY < maxY) {
        sectionY++;
        sectionZ = minZ;
      } else if (sectionX < maxX) {
        sectionX++;
        sectionY = minY;
        sectionZ = minZ;
      } else {
        complete = true;
        return;
      }
      sectionPending = true;
    }
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
    private double distanceSquared(double originX, double originY, double originZ) {
      double dx = (x + 0.5) - originX;
      double dy = (y + 0.5) - originY;
      double dz = (z + 0.5) - originZ;
      return dx * dx + dy * dy + dz * dz;
    }
  }
}
