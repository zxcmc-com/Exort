package com.zxcmc.exort.display;

import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.ItemModelUtil;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.DisplayMarker;
import com.zxcmc.exort.core.marker.MarkerCoords;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Wire rendering: - VANILLA: single ItemDisplay (legacy model selection) - RESOURCE: center +
 * per-face connection ItemDisplays
 */
public class WireDisplayManager {
  private static final BlockFace[] FACES =
      new BlockFace[] {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
      };
  private static final int ALL_MASK = 0b11_1111;
  private static final Quaternionf MIRROR_Y_180 = new Quaternionf().rotateY((float) Math.PI);
  private static final List<Rotation> ROTATIONS = generateRotations();
  private static final String TAG_WIRE_CENTER = "exort_wire_center";
  private static final String TAG_WIRE_CONN_PREFIX = "exort_wire_conn_";

  private final Plugin plugin;
  private final boolean enabled;
  private final Material wireCarrierMaterial;
  private final Material terminalMaterial;
  private final Material storageCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final String displayNamespace;
  private final String displayCenter;
  private final String displayConnection;
  private final boolean useConnectionModels;
  private final Material displayBaseMaterial;
  private final double displayScale;
  private final double offsetX;
  private final double offsetY;
  private final double offsetZ;
  private final String entityName;
  private final Set<String> warnedItemModels = Collections.synchronizedSet(new HashSet<>());
  private final Set<String> warnedModelIds = Collections.synchronizedSet(new HashSet<>());
  private final Map<BlockFace, Quaternionf> connectionRotations = new EnumMap<>(BlockFace.class);

  public WireDisplayManager(
      Plugin plugin,
      boolean enabled,
      Material wireCarrierMaterial,
      Material terminalMaterial,
      Material storageCarrier,
      Material monitorCarrier,
      Material busCarrier,
      String displayNamespace,
      String displayCenter,
      String displayConnection,
      boolean useConnectionModels,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      String entityName) {
    this.plugin = plugin;
    this.enabled = enabled;
    this.wireCarrierMaterial = wireCarrierMaterial;
    this.terminalMaterial = terminalMaterial;
    this.storageCarrier = storageCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.displayNamespace = displayNamespace == null ? "" : displayNamespace.trim();
    this.displayCenter = displayCenter == null ? "" : displayCenter.trim();
    this.displayConnection = displayConnection == null ? "" : displayConnection.trim();
    this.useConnectionModels = useConnectionModels;
    this.displayBaseMaterial = displayBaseMaterial;
    this.displayScale = displayScale;
    this.offsetX = offsetX;
    this.offsetY = offsetY;
    this.offsetZ = offsetZ;
    this.entityName = entityName == null ? "" : entityName;
    if (useConnectionModels) {
      int baseMask = bit(BlockFace.NORTH);
      for (BlockFace face : FACES) {
        Rotation rot = findRotation(baseMask, bit(face));
        Quaternionf q = rot == null ? new Quaternionf() : new Quaternionf(rot.quat());
        q.mul(MIRROR_Y_180);
        connectionRotations.put(face, q);
      }
    }
  }

  public boolean isEnabled() {
    return enabled && !displayNamespace.isBlank();
  }

  public void scanLoadedChunks() {
    if (!isEnabled()) return;
    for (World world : Bukkit.getWorlds()) {
      for (var chunk : world.getLoadedChunks()) {
        scanChunk(chunk);
      }
    }
  }

  public void refreshChunk(Chunk chunk) {
    if (!isEnabled()) return;
    scanChunk(chunk);
  }

  private void scanChunk(Chunk chunk) {
    Set<NamespacedKey> keys = chunk.getPersistentDataContainer().getKeys();
    if (keys.isEmpty()) return;
    for (NamespacedKey k : keys) {
      if (!k.getNamespace().equals(plugin.getName().toLowerCase())) continue;
      String key = k.getKey();
      if (!key.startsWith("wire_")) continue;
      // Skip our own display UUID markers: wire_display_x_y_z (stored as STRING)
      if (key.startsWith("wire_display_")) continue;

      int[] xyz = MarkerCoords.parseXYZ(key.substring("wire_".length()));
      if (xyz == null) continue;
      if (!chunk.getPersistentDataContainer().has(k, PersistentDataType.BYTE)) continue;
      Byte marker = chunk.getPersistentDataContainer().get(k, PersistentDataType.BYTE);
      if (marker == null || marker != (byte) 1) continue;
      Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
      if (!Carriers.matchesCarrier(block, wireCarrierMaterial)) continue;
      updateWireAndNeighbors(block);
    }
  }

  public void updateWireAndNeighbors(Block wire) {
    if (!isEnabled()) return;
    if (!isWire(wire)) return;
    updateWire(wire);
    for (BlockFace face : FACES) {
      Block other = wire.getRelative(face);
      if (Carriers.matchesCarrier(other, wireCarrierMaterial) && WireMarker.isWire(plugin, other)) {
        updateWire(other);
      }
    }
  }

  public void removeWire(Block wire) {
    if (!isEnabled()) return;
    removeDisplay(wire);
    for (BlockFace face : FACES) {
      Block other = wire.getRelative(face);
      if (Carriers.matchesCarrier(other, wireCarrierMaterial) && WireMarker.isWire(plugin, other)) {
        updateWire(other);
      }
    }
  }

  private boolean isWire(Block block) {
    return block != null
        && Carriers.matchesCarrier(block, wireCarrierMaterial)
        && WireMarker.isWire(plugin, block);
  }

  private void updateWire(Block wire) {
    if (useConnectionModels) {
      updateWireMulti(wire);
      return;
    }
    WireRender render = renderFor(wire);
    if (render == null) {
      removeDisplay(wire);
      return;
    }

    UUID existingId = DisplayMarker.get(plugin, "wire", wire).orElse(null);
    ItemDisplay display = existingId != null ? (ItemDisplay) Bukkit.getEntity(existingId) : null;
    if (display == null || display.isDead()) {
      display = findNearbyDisplay(wire);
      if (display != null) {
        DisplayMarker.set(plugin, "wire", wire, display.getUniqueId());
      }
    }
    if (display == null || display.isDead()) {
      display = spawnDisplay(wire, render);
      if (display == null) return;
      DisplayMarker.set(plugin, "wire", wire, display.getUniqueId());
    } else {
      applySettings(display);
      applyModel(display, render.modelId());
      display.teleport(targetLoc(wire));
      applyOrientation(display, render.rotation());
    }
  }

  private void removeDisplay(Block wire) {
    if (useConnectionModels) {
      removeMultiDisplays(wire);
      return;
    }
    UUID existingId = DisplayMarker.get(plugin, "wire", wire).orElse(null);
    if (existingId != null) {
      var ent = Bukkit.getEntity(existingId);
      if (ent instanceof ItemDisplay display && !display.isDead()) {
        display.remove();
      }
      DisplayMarker.clear(plugin, "wire", wire);
    }
    // Fallback: remove any stray displays in the block space with our tag
    var loc = targetLoc(wire);
    for (var ent : wire.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
      if (ent instanceof ItemDisplay display
          && display.getScoreboardTags().contains(DisplayTags.DISPLAY_TAG)) {
        display.remove();
      }
    }
  }

  private ItemDisplay findNearbyDisplay(Block wire) {
    var loc = targetLoc(wire);
    for (var ent : wire.getWorld().getNearbyEntities(loc, 0.35, 0.35, 0.35)) {
      if (!(ent instanceof ItemDisplay display)) continue;
      if (!display.getScoreboardTags().contains(DisplayTags.DISPLAY_TAG)) continue;
      if (!display.getLocation().getBlock().equals(wire)) continue;
      return display;
    }
    return null;
  }

  private ItemDisplay spawnDisplay(Block wire, WireRender render) {
    Location loc = targetLoc(wire);
    return loc.getWorld()
        .spawn(
            loc,
            ItemDisplay.class,
            item -> {
              applySettings(item);

              Transformation t = item.getTransformation();
              t.getScale()
                  .set(
                      new Vector3f(
                          (float) displayScale, (float) displayScale, (float) displayScale));
              item.setTransformation(t);

              applyModel(item, render.modelId());
              applyOrientation(item, render.rotation());
            });
  }

  private void updateWireMulti(Block wire) {
    if (displayCenter.isBlank()) {
      removeMultiDisplays(wire);
      return;
    }
    int mask = connectionsMask(wire);

    ItemDisplay center = findTaggedDisplay(wire, TAG_WIRE_CENTER);
    if (center == null || center.isDead()) {
      center = spawnTaggedDisplay(wire, displayCenter, MIRROR_Y_180, TAG_WIRE_CENTER);
    } else {
      applySettings(center);
      applyModel(center, displayCenter);
      center.teleport(targetLoc(wire));
      applyOrientation(center, MIRROR_Y_180);
    }

    for (BlockFace face : FACES) {
      boolean connected = (mask & bit(face)) != 0;
      String tag = TAG_WIRE_CONN_PREFIX + face.name().toLowerCase();
      ItemDisplay display = findTaggedDisplay(wire, tag);
      if (!connected) {
        if (display != null && !display.isDead()) {
          display.remove();
        }
        continue;
      }
      if (display == null || display.isDead()) {
        display = spawnTaggedDisplay(wire, displayConnection, connectionRotations.get(face), tag);
      } else {
        applySettings(display);
        applyModel(display, displayConnection);
        display.teleport(targetLoc(wire));
        applyOrientation(display, connectionRotations.get(face));
      }
    }
  }

  private void removeMultiDisplays(Block wire) {
    DisplayMarker.clear(plugin, "wire", wire);
    var loc = targetLoc(wire);
    for (var ent : wire.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
      if (!(ent instanceof ItemDisplay display)) continue;
      var tags = display.getScoreboardTags();
      if (!tags.contains(DisplayTags.DISPLAY_TAG)) continue;
      if (!display.getLocation().getBlock().equals(wire)) continue;
      if (tags.contains(TAG_WIRE_CENTER)
          || tags.stream().anyMatch(tag -> tag.startsWith(TAG_WIRE_CONN_PREFIX))) {
        display.remove();
      }
    }
  }

  private ItemDisplay findTaggedDisplay(Block wire, String tag) {
    var loc = targetLoc(wire);
    for (var ent : wire.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
      if (!(ent instanceof ItemDisplay display)) continue;
      var tags = display.getScoreboardTags();
      if (!tags.contains(DisplayTags.DISPLAY_TAG)) continue;
      if (!tags.contains(tag)) continue;
      if (!display.getLocation().getBlock().equals(wire)) continue;
      return display;
    }
    return null;
  }

  private ItemDisplay spawnTaggedDisplay(
      Block wire, String modelId, Quaternionf rotation, String tag) {
    if (modelId == null || modelId.isBlank()) return null;
    Location loc = targetLoc(wire);
    return loc.getWorld()
        .spawn(
            loc,
            ItemDisplay.class,
            item -> {
              applySettings(item);
              applyModel(item, modelId);
              applyOrientation(item, rotation == null ? new Quaternionf() : rotation);
              item.addScoreboardTag(tag);
            });
  }

  private void applySettings(ItemDisplay item) {
    item.setPersistent(true);
    item.setInvulnerable(true);
    item.setSilent(true);
    item.setInvisible(true);
    item.setBillboard(Display.Billboard.FIXED);
    item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
    item.setDisplayWidth(0.0f);
    item.setDisplayHeight(0.0f);
    item.setViewRange(1.0f);
    item.setTeleportDuration(0);
    item.addScoreboardTag(DisplayTags.DISPLAY_TAG);
    if (!entityName.isBlank()) {
      item.customName(Component.text(entityName));
    } else {
      item.customName(null);
    }
    item.setCustomNameVisible(false);
  }

  private void applyModel(ItemDisplay display, String modelId) {
    if (warnedModelIds.add(modelId)) {
      if (NamespacedKey.fromString(modelId) == null) {
        plugin.getLogger().warning("Invalid wire display model id: '" + modelId + "'");
      }
    }
    ItemStack stack = new ItemStack(displayBaseMaterial);
    var meta = stack.getItemMeta();
    ItemModelUtil.ApplyResult res = ItemModelUtil.applyItemModelDetailed(meta, modelId);
    if (meta != null && !entityName.isBlank()) {
      meta.displayName(Component.text(entityName).decoration(TextDecoration.ITALIC, false));
    }
    if (meta != null) {
      stack.setItemMeta(meta);
    }
    if (!res.ok() && warnedItemModels.add(modelId)) {
      plugin
          .getLogger()
          .warning(
              "Failed to apply item_model '" + modelId + "' to wire display item: " + res.error());
    }
    display.setItemStack(stack);
  }

  private void applyOrientation(ItemDisplay display, Quaternionf rotation) {
    Transformation t = display.getTransformation();
    t.getLeftRotation().set(rotation);
    t.getScale()
        .set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
    t.getRightRotation().identity();
    display.setTransformation(t);
    display.setRotation(0f, 0f);
  }

  private Location targetLoc(Block wire) {
    return wire.getLocation().add(offsetX, offsetY, offsetZ);
  }

  private WireRender renderFor(Block wire) {
    int mask = connectionsMask(wire);
    if (!useConnectionModels) {
      if (displayCenter.isBlank()) return null;
      return new WireRender(displayCenter, new Quaternionf(MIRROR_Y_180));
    }
    if (mask == 0) {
      if (displayCenter.isBlank()) return null;
      return new WireRender(displayCenter, new Quaternionf(MIRROR_Y_180));
    }

    ModelBase base = baseForMask(mask);
    if (base == null) {
      return new WireRender(displayNamespace + ":" + suffix(mask), new Quaternionf(MIRROR_Y_180));
    }

    Rotation rot = findRotation(base.baseMask(), mask);
    Quaternionf q = rot == null ? new Quaternionf() : new Quaternionf(rot.quat());
    q.mul(MIRROR_Y_180);
    return new WireRender(displayNamespace + ":" + base.modelKey(), q);
  }

  private int connectionsMask(Block wire) {
    int mask = 0;
    if (isConnected(wire, BlockFace.UP)) mask |= bit(BlockFace.UP);
    if (isConnected(wire, BlockFace.DOWN)) mask |= bit(BlockFace.DOWN);
    if (isConnected(wire, BlockFace.NORTH)) mask |= bit(BlockFace.NORTH);
    if (isConnected(wire, BlockFace.SOUTH)) mask |= bit(BlockFace.SOUTH);
    if (isConnected(wire, BlockFace.EAST)) mask |= bit(BlockFace.EAST);
    if (isConnected(wire, BlockFace.WEST)) mask |= bit(BlockFace.WEST);
    return mask;
  }

  private boolean isConnected(Block wire, BlockFace face) {
    Block neighbor = wire.getRelative(face);
    if (neighbor == null) return false;
    if (Carriers.matchesCarrier(neighbor, wireCarrierMaterial)
        && WireMarker.isWire(plugin, neighbor)) return true;
    if (isTerminal(neighbor)) return !isFrontFace(neighbor, face.getOppositeFace());
    if (isStorage(neighbor)) return true;
    if (isMonitor(neighbor)) return !isFrontFace(neighbor, face.getOppositeFace());
    if (isBus(neighbor)) return !isFrontFace(neighbor, face.getOppositeFace());
    return false;
  }

  private boolean isTerminal(Block block) {
    return Carriers.matchesCarrier(block, terminalMaterial)
        && TerminalMarker.isTerminal(plugin, block);
  }

  private boolean isStorage(Block block) {
    return Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent();
  }

  private boolean isMonitor(Block block) {
    return Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
  }

  private boolean isBus(Block block) {
    return Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
  }

  private boolean isFrontFace(Block block, BlockFace towardWire) {
    if (towardWire == null) return false;
    if (isTerminal(block)) {
      return TerminalMarker.facing(plugin, block).map(towardWire::equals).orElse(false);
    }
    if (isMonitor(block)) {
      return MonitorMarker.facing(plugin, block).map(towardWire::equals).orElse(false);
    }
    if (isBus(block)) {
      return BusMarker.get(plugin, block)
          .map(BusMarker.Data::facing)
          .map(towardWire::equals)
          .orElse(false);
    }
    return false;
  }

  private static int bit(BlockFace face) {
    return 1 << faceIndex(face);
  }

  private static int faceIndex(BlockFace face) {
    return switch (face) {
      case UP -> 0;
      case DOWN -> 1;
      case NORTH -> 2;
      case SOUTH -> 3;
      case EAST -> 4;
      case WEST -> 5;
      default -> throw new IllegalArgumentException("Unsupported face: " + face);
    };
  }

  private static BlockFace indexFace(int index) {
    return switch (index) {
      case 0 -> BlockFace.UP;
      case 1 -> BlockFace.DOWN;
      case 2 -> BlockFace.NORTH;
      case 3 -> BlockFace.SOUTH;
      case 4 -> BlockFace.EAST;
      case 5 -> BlockFace.WEST;
      default -> throw new IllegalArgumentException("Bad face index: " + index);
    };
  }

  private static String suffix(int mask) {
    StringBuilder sb = new StringBuilder(6);
    if ((mask & bit(BlockFace.UP)) != 0) sb.append('u');
    if ((mask & bit(BlockFace.DOWN)) != 0) sb.append('d');
    if ((mask & bit(BlockFace.NORTH)) != 0) sb.append('n');
    if ((mask & bit(BlockFace.SOUTH)) != 0) sb.append('s');
    if ((mask & bit(BlockFace.EAST)) != 0) sb.append('e');
    if ((mask & bit(BlockFace.WEST)) != 0) sb.append('w');
    return sb.toString();
  }

  private static boolean isOppositePairMask(int mask) {
    return mask == (bit(BlockFace.UP) | bit(BlockFace.DOWN))
        || mask == (bit(BlockFace.NORTH) | bit(BlockFace.SOUTH))
        || mask == (bit(BlockFace.EAST) | bit(BlockFace.WEST));
  }

  private static boolean hasOppositePair(int mask) {
    return (mask & (bit(BlockFace.UP) | bit(BlockFace.DOWN)))
            == (bit(BlockFace.UP) | bit(BlockFace.DOWN))
        || (mask & (bit(BlockFace.NORTH) | bit(BlockFace.SOUTH)))
            == (bit(BlockFace.NORTH) | bit(BlockFace.SOUTH))
        || (mask & (bit(BlockFace.EAST) | bit(BlockFace.WEST)))
            == (bit(BlockFace.EAST) | bit(BlockFace.WEST));
  }

  private static ModelBase baseForMask(int mask) {
    int count = Integer.bitCount(mask);
    return switch (count) {
      case 1 -> new ModelBase("n", bit(BlockFace.NORTH));
      case 2 ->
          isOppositePairMask(mask)
              ? new ModelBase("ns", bit(BlockFace.NORTH) | bit(BlockFace.SOUTH))
              : new ModelBase("ne", bit(BlockFace.NORTH) | bit(BlockFace.EAST));
      case 3 ->
          hasOppositePair(mask)
              ? new ModelBase(
                  "nse", bit(BlockFace.NORTH) | bit(BlockFace.SOUTH) | bit(BlockFace.EAST))
              : new ModelBase(
                  "une", bit(BlockFace.UP) | bit(BlockFace.NORTH) | bit(BlockFace.EAST));
      case 4 -> {
        int missing = ALL_MASK ^ mask;
        if (isOppositePairMask(missing)) {
          yield new ModelBase(
              "udns",
              bit(BlockFace.UP)
                  | bit(BlockFace.DOWN)
                  | bit(BlockFace.NORTH)
                  | bit(BlockFace.SOUTH));
        }
        yield new ModelBase(
            "unse",
            bit(BlockFace.UP) | bit(BlockFace.NORTH) | bit(BlockFace.SOUTH) | bit(BlockFace.EAST));
      }
      case 5 ->
          new ModelBase(
              "dnsew",
              bit(BlockFace.DOWN)
                  | bit(BlockFace.NORTH)
                  | bit(BlockFace.SOUTH)
                  | bit(BlockFace.EAST)
                  | bit(BlockFace.WEST));
      case 6 -> new ModelBase("udnsew", ALL_MASK);
      default -> null;
    };
  }

  private static Rotation findRotation(int baseMask, int targetMask) {
    for (Rotation r : ROTATIONS) {
      if (r.applyMask(baseMask) == targetMask) return r;
    }
    return null;
  }

  private static List<Rotation> generateRotations() {
    Quaternionf rx = new Quaternionf().rotateX((float) (Math.PI / 2.0));
    Quaternionf ry = new Quaternionf().rotateY((float) (Math.PI / 2.0));
    Quaternionf rz = new Quaternionf().rotateZ((float) (Math.PI / 2.0));

    Queue<Quaternionf> q = new ArrayDeque<>();
    q.add(new Quaternionf()); // identity

    Map<String, Rotation> unique = new LinkedHashMap<>();
    while (!q.isEmpty() && unique.size() < 24) {
      Quaternionf cur = q.poll();
      Rotation rot = Rotation.from(cur);
      if (unique.containsKey(rot.key())) continue;
      unique.put(rot.key(), rot);

      q.add(new Quaternionf(cur).mul(rx));
      q.add(new Quaternionf(cur).mul(ry));
      q.add(new Quaternionf(cur).mul(rz));
    }
    return List.copyOf(unique.values());
  }

  private record WireRender(String modelId, Quaternionf rotation) {}

  private record ModelBase(String modelKey, int baseMask) {}

  private record Rotation(String key, Quaternionf quat, int[] faceMap) {
    int applyMask(int mask) {
      int out = 0;
      for (int i = 0; i < 6; i++) {
        if ((mask & (1 << i)) == 0) continue;
        out |= 1 << faceMap[i];
      }
      return out;
    }

    static Rotation from(Quaternionf q) {
      int[] map = new int[6];
      StringBuilder sb = new StringBuilder(12);
      for (int i = 0; i < 6; i++) {
        BlockFace src = indexFace(i);
        BlockFace dst = rotateFace(q, src);
        int di = faceIndex(dst);
        map[i] = di;
        sb.append(i).append("->").append(di).append(';');
      }
      return new Rotation(sb.toString(), new Quaternionf(q), map);
    }

    private static BlockFace rotateFace(Quaternionf q, BlockFace face) {
      Vector3f v = faceVector(face);
      q.transform(v);
      int x = Math.round(v.x);
      int y = Math.round(v.y);
      int z = Math.round(v.z);
      if (x == 1 && y == 0 && z == 0) return BlockFace.EAST;
      if (x == -1 && y == 0 && z == 0) return BlockFace.WEST;
      if (x == 0 && y == 1 && z == 0) return BlockFace.UP;
      if (x == 0 && y == -1 && z == 0) return BlockFace.DOWN;
      if (x == 0 && y == 0 && z == 1) return BlockFace.SOUTH;
      if (x == 0 && y == 0 && z == -1) return BlockFace.NORTH;
      return face;
    }

    private static Vector3f faceVector(BlockFace face) {
      return switch (face) {
        case EAST -> new Vector3f(1, 0, 0);
        case WEST -> new Vector3f(-1, 0, 0);
        case UP -> new Vector3f(0, 1, 0);
        case DOWN -> new Vector3f(0, -1, 0);
        case SOUTH -> new Vector3f(0, 0, 1);
        case NORTH -> new Vector3f(0, 0, -1);
        default -> new Vector3f(0, 0, 0);
      };
    }
  }
}
