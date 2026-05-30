package com.zxcmc.exort.display;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.items.ItemModelUtil;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.DisplayMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Wire rendering: one ItemDisplay per wire block with a baked connection-mask model. */
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
  // Minecraft's ItemDisplayRenderer applies an implicit Y+180 to item models. This compensates
  // that renderer transform so RESOURCE wire model axes match block-space axes.
  private static final Quaternionf MIRROR_Y_180 = new Quaternionf().rotateY((float) Math.PI);

  private final Plugin plugin;
  private final boolean enabled;
  private final Material wireCarrierMaterial;
  private final Material terminalMaterial;
  private final Material storageCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final String displayNamespace;
  private final String wireModel;
  private final boolean resourceMode;
  private final Material displayBaseMaterial;
  private final double displayScale;
  private final double offsetX;
  private final double offsetY;
  private final double offsetZ;
  private final String entityName;
  private final DisplayMetadataService metadataService;
  private final Set<String> warnedItemModels = Collections.synchronizedSet(new HashSet<>());
  private final Set<String> warnedModelIds = Collections.synchronizedSet(new HashSet<>());

  public WireDisplayManager(
      Plugin plugin,
      boolean enabled,
      Material wireCarrierMaterial,
      Material terminalMaterial,
      Material storageCarrier,
      Material monitorCarrier,
      Material busCarrier,
      String displayNamespace,
      String wireModel,
      boolean resourceMode,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      String entityName,
      DisplayMetadataService metadataService) {
    this.plugin = plugin;
    this.enabled = enabled;
    this.wireCarrierMaterial = wireCarrierMaterial;
    this.terminalMaterial = terminalMaterial;
    this.storageCarrier = storageCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.displayNamespace = displayNamespace == null ? "" : displayNamespace.trim();
    this.wireModel = wireModel == null ? "" : wireModel.trim();
    this.resourceMode = resourceMode;
    this.displayBaseMaterial = displayBaseMaterial;
    this.displayScale = displayScale;
    this.offsetX = offsetX;
    this.offsetY = offsetY;
    this.offsetZ = offsetZ;
    this.entityName = entityName == null ? "" : entityName;
    this.metadataService = metadataService;
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
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) return;
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          if (!WireMarker.isWire(plugin, block)) return;
          if (!Carriers.matchesCarrier(block, wireCarrierMaterial)) return;
          updateWireAndNeighbors(block);
        });
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
    updateWireCompact(wire);
  }

  private void updateWireCompact(Block wire) {
    WireRender render = renderFor(wire);
    if (render == null) {
      removeDisplay(wire);
      return;
    }

    UUID existingId = DisplayMarker.get(plugin, "wire", wire).orElse(null);
    var existing = existingId != null ? Bukkit.getEntity(existingId) : null;
    ItemDisplay display = existing instanceof ItemDisplay itemDisplay ? itemDisplay : null;
    if (existingId != null && existing != null && display == null) {
      DisplayMarker.clear(plugin, "wire", wire);
    }
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
      display.addScoreboardTag(DisplayTags.WIRE_COMPACT_TAG);
      applySettings(display);
      applyModel(display, render.modelId());
      display.teleport(targetLoc(wire));
      applyOrientation(display, render.rotation());
      metadataService.normalize(display);
    }
  }

  private void removeDisplay(Block wire) {
    removeCompactDisplay(wire);
    // Fallback: remove any stray displays in the block space with our tag
    var loc = targetLoc(wire);
    for (var ent : wire.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
      if (ent instanceof ItemDisplay display
          && display.getScoreboardTags().contains(DisplayTags.DISPLAY_TAG)) {
        removeManagedDisplay(display);
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
              item.addScoreboardTag(DisplayTags.WIRE_COMPACT_TAG);
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

  private void removeCompactDisplay(Block wire) {
    UUID existingId = DisplayMarker.get(plugin, "wire", wire).orElse(null);
    if (existingId != null) {
      var ent = Bukkit.getEntity(existingId);
      if (ent instanceof ItemDisplay display && !display.isDead()) {
        removeManagedDisplay(display);
      }
      DisplayMarker.clear(plugin, "wire", wire);
    }
    var loc = targetLoc(wire);
    for (var ent : wire.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
      if (!(ent instanceof ItemDisplay display)) continue;
      var tags = display.getScoreboardTags();
      if (!tags.contains(DisplayTags.DISPLAY_TAG)) continue;
      if (!tags.contains(DisplayTags.WIRE_COMPACT_TAG)) continue;
      if (!display.getLocation().getBlock().equals(wire)) continue;
      removeManagedDisplay(display);
    }
  }

  private void removeManagedDisplay(Display display) {
    if (display == null || display.isDead()) {
      return;
    }
    metadataService.unregister(display);
    display.remove();
  }

  private void applySettings(ItemDisplay item) {
    item.setPersistent(true);
    item.setInvulnerable(true);
    item.setSilent(true);
    item.setInvisible(true);
    item.setBillboard(Display.Billboard.FIXED);
    item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
    item.addScoreboardTag(DisplayTags.DISPLAY_TAG);
    metadataService.normalize(item);
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
    if (!resourceMode) {
      if (wireModel.isBlank()) return null;
      return new WireRender(wireModel, new Quaternionf(MIRROR_Y_180));
    }
    if (mask == 0) {
      if (wireModel.isBlank()) return null;
      return new WireRender(wireModel, new Quaternionf(MIRROR_Y_180));
    }

    return new WireRender(compactModelId(suffix(mask)), new Quaternionf(MIRROR_Y_180));
  }

  private String compactModelId(String key) {
    String normalized = key == null ? "" : key.trim();
    if (normalized.isBlank()) {
      return "";
    }
    if (normalized.contains(":")) {
      return normalized;
    }
    return displayNamespace + ":wire/" + normalized;
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

  static String compactModelKeyForMask(int mask) {
    return suffix(mask);
  }

  private record WireRender(String modelId, Quaternionf rotation) {}
}
