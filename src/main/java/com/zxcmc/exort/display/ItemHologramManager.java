package com.zxcmc.exort.display;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.MarkerCoords;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageTier;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ItemHologramManager implements Listener {
  public enum Kind {
    TERMINAL,
    STORAGE
  }

  public record Config(
      boolean enabled, double offsetX, double offsetY, double offsetZ, double scale) {}

  private final ExortPlugin plugin;
  private final StorageKeys keys;
  private final int wireLimit;
  private final int wireHardCap;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material disconnectedMaterial;
  private final Config terminalConfig;
  private final Config storageConfig;
  private final Map<HoloPos, Kind> holograms = new ConcurrentHashMap<>();
  private final Map<HoloPos, ItemDisplay> displays = new ConcurrentHashMap<>();
  private final Map<HoloPos, Material> cache = new ConcurrentHashMap<>();
  private volatile boolean dirtyAll = true;
  private int taskId = -1;

  public ItemHologramManager(
      ExortPlugin plugin,
      StorageKeys keys,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Config terminalConfig,
      Config storageConfig) {
    this.plugin = plugin;
    this.keys = keys;
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.terminalConfig = terminalConfig;
    this.storageConfig = storageConfig;
    this.disconnectedMaterial = resolveTestBlock();
  }

  public void start() {
    if (!terminalConfig.enabled() && !storageConfig.enabled()) return;
    if (taskId != -1) return;
    scanLoadedHolograms();
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
  }

  public void stop() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    clearAll();
  }

  public void registerTerminal(Block block) {
    if (!terminalConfig.enabled()) return;
    holograms.put(HoloPos.of(block), Kind.TERMINAL);
    dirtyAll = true;
  }

  public void unregisterTerminal(Block block) {
    HoloPos pos = HoloPos.of(block);
    if (holograms.remove(pos) != null) {
      removeDisplay(pos);
      cache.remove(pos);
    }
    dirtyAll = true;
  }

  public void registerStorage(Block block) {
    if (!storageConfig.enabled()) return;
    holograms.put(HoloPos.of(block), Kind.STORAGE);
    dirtyAll = true;
  }

  public void unregisterStorage(Block block) {
    HoloPos pos = HoloPos.of(block);
    if (holograms.remove(pos) != null) {
      removeDisplay(pos);
      cache.remove(pos);
    }
    dirtyAll = true;
  }

  public void invalidateAll() {
    dirtyAll = true;
  }

  public void clearAll() {
    displays
        .values()
        .forEach(
            display -> {
              if (display != null && !display.isDead()) display.remove();
            });
    displays.clear();
    holograms.clear();
    cache.clear();
  }

  private void removeDisplay(HoloPos pos) {
    ItemDisplay display = displays.remove(pos);
    if (display != null && !display.isDead()) {
      display.remove();
    }
  }

  private void scanLoadedHolograms() {
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        scanChunkMarkers(chunk);
      }
    }
  }

  private void tick() {
    boolean recomputeAll = dirtyAll;
    if (recomputeAll) {
      dirtyAll = false;
    }
    Iterator<Map.Entry<HoloPos, Kind>> it = holograms.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<HoloPos, Kind> entry = it.next();
      HoloPos pos = entry.getKey();
      Kind kind = entry.getValue();
      Config config = kind == Kind.TERMINAL ? terminalConfig : storageConfig;
      if (!config.enabled()) {
        removeDisplay(pos);
        it.remove();
        cache.remove(pos);
        continue;
      }
      World world = Bukkit.getWorld(pos.world());
      if (world == null) {
        removeDisplay(pos);
        it.remove();
        cache.remove(pos);
        continue;
      }
      if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
        removeDisplay(pos);
        continue;
      }
      Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
      if (!isValidBlock(block, kind)) {
        removeDisplay(pos);
        it.remove();
        cache.remove(pos);
        continue;
      }
      Material display = resolveDisplay(block, kind, recomputeAll, pos);
      if (display == null) {
        removeDisplay(pos);
        continue;
      }
      ItemDisplay entity = displays.get(pos);
      if (entity == null || entity.isDead()) {
        Location desired = targetLoc(block, kind, config);
        entity = spawnDisplay(desired, display, config.scale(), kind);
        displays.put(pos, entity);
      } else {
        applySettings(entity, config.scale(), kind);
        entity.setItemStack(new ItemStack(display));
        entity.teleport(targetLoc(block, kind, config));
      }
      orientDisplay(entity, block, kind, config.scale());
    }
  }

  private Material resolveDisplay(Block block, Kind kind, boolean recomputeAll, HoloPos pos) {
    Material cached = cache.get(pos);
    if (!recomputeAll && cached != null) return cached;
    Material resolved =
        switch (kind) {
          case TERMINAL -> resolveTerminalDisplay(block);
          case STORAGE -> resolveStorageDisplay(block);
        };
    cache.put(pos, resolved);
    return resolved;
  }

  private Material resolveTerminalDisplay(Block terminal) {
    var result =
        TerminalLinkFinder.find(
            terminal, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
    if (result.count() == 1 && result.data() != null) {
      return result.data().tier().displayMaterial();
    }
    return disconnectedMaterial;
  }

  private Material resolveStorageDisplay(Block storage) {
    return StorageMarker.get(plugin, storage)
        .map(StorageMarker.Data::tier)
        .map(StorageTier::displayMaterial)
        .orElse(null);
  }

  private ItemDisplay spawnDisplay(Location loc, Material display, double scale, Kind kind) {
    return loc.getWorld()
        .spawn(
            loc,
            ItemDisplay.class,
            item -> {
              applySettings(item, scale, kind);
              item.setItemStack(new ItemStack(display));
            });
  }

  private void applySettings(ItemDisplay item, double scale, Kind kind) {
    item.setPersistent(true);
    item.setInvulnerable(true);
    item.setSilent(true);
    item.setInvisible(true);
    if (kind == Kind.STORAGE) {
      item.setBrightness(new Display.Brightness(15, 15));
    } else {
      item.setBrightness(null);
    }
    item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
    item.setViewRange(1.0f);
    item.setDisplayWidth(0.0f);
    item.setDisplayHeight(0.0f);
    item.setTeleportDuration(0);
    item.addScoreboardTag(DisplayTags.HOLOGRAM_TAG);
    item.addScoreboardTag(DisplayTags.DISPLAY_TAG);
    Transformation t = item.getTransformation();
    t.getScale().set(new Vector3f((float) scale, (float) scale, (float) scale));
    t.getLeftRotation().identity();
    t.getRightRotation().identity();
    item.setTransformation(t);
    item.setBillboard(Display.Billboard.FIXED);
  }

  private void orientDisplay(ItemDisplay display, Block block, Kind kind, double scale) {
    BlockFace face = facingFor(block, kind);
    float yaw = yawForFacing(face);
    Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(-yaw));
    Transformation t = display.getTransformation();
    t.getLeftRotation().set(rotation);
    t.getRightRotation().identity();
    t.getScale().set(new Vector3f((float) scale, (float) scale, (float) scale));
    display.setTransformation(t);
    display.setRotation(0f, 0f);
  }

  private Location targetLoc(Block block, Kind kind, Config config) {
    BlockFace face = facingFor(block, kind);
    Vector3f rotated = rotatedOffset(face, config.offsetX(), config.offsetY(), config.offsetZ());
    return block.getLocation().add(rotated.x(), rotated.y(), rotated.z());
  }

  private boolean isValidBlock(Block block, Kind kind) {
    return switch (kind) {
      case TERMINAL ->
          Carriers.matchesCarrier(block, terminalCarrier)
              && TerminalMarker.isTerminal(plugin, block);
      case STORAGE ->
          Carriers.matchesCarrier(block, storageCarrier)
              && StorageMarker.get(plugin, block).isPresent();
    };
  }

  private BlockFace facingFor(Block block, Kind kind) {
    return switch (kind) {
      case TERMINAL -> TerminalMarker.facing(plugin, block).orElse(BlockFace.SOUTH);
      case STORAGE ->
          StorageMarker.get(plugin, block).map(StorageMarker.Data::facing).orElse(BlockFace.SOUTH);
    };
  }

  private float yawForFacing(BlockFace face) {
    return switch (face) {
      case NORTH -> 180f;
      case EAST -> -90f;
      case WEST -> 90f;
      default -> 0f; // SOUTH
    };
  }

  private Vector3f rotatedOffset(BlockFace face, double ox, double oy, double oz) {
    double localRight = ox - 0.5;
    double localForward = oz - 0.5;
    double fx = 0.0;
    double fz = 1.0;
    switch (face) {
      case NORTH -> {
        fx = 0.0;
        fz = -1.0;
      }
      case EAST -> {
        fx = 1.0;
        fz = 0.0;
      }
      case WEST -> {
        fx = -1.0;
        fz = 0.0;
      }
      default -> {
        fx = 0.0;
        fz = 1.0;
      }
    }
    // Right vector (per facing) keeps offset.x anchored to the left edge in local space.
    double rx = -fz;
    double rz = fx;
    double worldX = rx * localRight + fx * localForward;
    double worldZ = rz * localRight + fz * localForward;
    return new Vector3f((float) (worldX + 0.5), (float) oy, (float) (worldZ + 0.5));
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (TerminalMarker.isTerminal(plugin, block)) {
      unregisterTerminal(block);
    } else if (StorageMarker.get(plugin, block).isPresent()) {
      unregisterStorage(block);
    }
  }

  @EventHandler
  public void onChunkLoad(ChunkLoadEvent event) {
    scanChunkMarkers(event.getChunk());
  }

  private void scanChunkMarkers(Chunk chunk) {
    var keys = chunk.getPersistentDataContainer().getKeys();
    if (keys.isEmpty()) return;
    for (var key : keys) {
      if (!key.getNamespace().equals(plugin.getName().toLowerCase())) continue;
      String raw = key.getKey();
      if (raw.startsWith("terminal_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("terminal_".length()));
        if (xyz == null) continue;
        Block b = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
        if (Carriers.matchesCarrier(b, terminalCarrier) && TerminalMarker.isTerminal(plugin, b)) {
          registerTerminal(b);
        }
      } else if (raw.startsWith("storage_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("storage_".length()));
        if (xyz == null) continue;
        Block b = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
        if (Carriers.matchesCarrier(b, storageCarrier)
            && StorageMarker.get(plugin, b).isPresent()) {
          registerStorage(b);
        }
      }
    }
  }

  private Material resolveTestBlock() {
    try {
      return Material.valueOf("TEST_BLOCK");
    } catch (IllegalArgumentException ignored) {
      return Carriers.CARRIER_BARRIER;
    }
  }

  private record HoloPos(UUID world, int x, int y, int z) {
    static HoloPos of(Block block) {
      return new HoloPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
