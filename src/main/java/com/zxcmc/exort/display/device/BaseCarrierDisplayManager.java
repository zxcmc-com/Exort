package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.display.core.DisplayTags;
import com.zxcmc.exort.items.ItemModelUtil;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.DisplayMarker;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public abstract class BaseCarrierDisplayManager {
  protected final Plugin plugin;
  protected final Material carrierMaterial;
  protected final String displayModelId;
  protected final Material displayBaseMaterial;
  protected final double displayScale;
  protected final double offsetX;
  protected final double offsetY;
  protected final double offsetZ;
  protected final DisplayMetadataService metadataService;
  private final String markerType;

  protected BaseCarrierDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      String markerType) {
    this.plugin = plugin;
    this.carrierMaterial = carrierMaterial;
    this.displayModelId = displayModelId;
    this.displayBaseMaterial = displayBaseMaterial;
    this.displayScale = displayScale;
    this.offsetX = offsetX;
    this.offsetY = offsetY;
    this.offsetZ = offsetZ;
    this.metadataService = metadataService;
    this.markerType = markerType;
  }

  public void scanLoadedChunks() {
    for (World world : Bukkit.getWorlds()) {
      for (var chunk : world.getLoadedChunks()) {
        scanChunk(chunk);
      }
    }
  }

  public void refreshChunk(Chunk chunk) {
    scanChunk(chunk);
  }

  private void scanChunk(Chunk chunk) {
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) return;
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          if (!Carriers.matchesCarrier(block, carrierMaterial)) return;
          if (!hasRefreshMarker(block)) return;
          refresh(block);
        });
  }

  protected boolean hasRefreshMarker(Block block) {
    return ChunkMarkerStore.hasSection(plugin, block, markerType);
  }

  public void refresh(Block block) {
    if (!isValidBlock(block)) {
      removeDisplay(block);
      return;
    }
    UUID existingId = DisplayMarker.get(plugin, markerType, block).orElse(null);
    var existing = existingId != null ? Bukkit.getEntity(existingId) : null;
    ItemDisplay display = existing instanceof ItemDisplay itemDisplay ? itemDisplay : null;
    if (existingId != null && existing != null && display == null) {
      DisplayMarker.clear(plugin, markerType, block);
    }
    if (display == null || display.isDead()) {
      display = spawn(block);
      if (display != null) {
        DisplayMarker.set(plugin, markerType, block, display.getUniqueId());
      }
    } else {
      display.teleport(target(block));
      apply(display, block);
    }
  }

  public void removeDisplay(Block block) {
    UUID existingId = DisplayMarker.get(plugin, markerType, block).orElse(null);
    if (existingId != null) {
      var ent = Bukkit.getEntity(existingId);
      if (ent instanceof ItemDisplay d && !d.isDead()) removeManagedDisplay(d);
    }
    DisplayMarker.clear(plugin, markerType, block);
    cleanupNearby(block);
  }

  private ItemDisplay spawn(Block block) {
    return block
        .getWorld()
        .spawn(
            target(block),
            ItemDisplay.class,
            item -> {
              item.setPersistent(true);
              item.setInvulnerable(true);
              item.setSilent(true);
              item.setInvisible(true);
              item.addScoreboardTag(DisplayTags.DISPLAY_TAG);
              item.addScoreboardTag(markerType);
              item.setBillboard(Display.Billboard.FIXED);
              item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
              metadataService.normalize(item);
              apply(item, block);
            });
  }

  private void apply(ItemDisplay display, Block block) {
    ItemStack stack = new ItemStack(displayBaseMaterial);
    var meta = stack.getItemMeta();
    ItemModelUtil.applyItemModel(meta, modelId(block));
    if (meta != null) {
      decorateMeta(meta, block);
      stack.setItemMeta(meta);
    }
    display.setItemStack(stack);
    metadataService.normalize(display, localizationKey(block));
    applyTransform(display, block);
  }

  protected String modelId(Block block) {
    return displayModelId;
  }

  protected Location target(Block block) {
    return block.getLocation().add(offsetX, offsetY, offsetZ);
  }

  protected void applyTransform(ItemDisplay display, Block block) {
    Transformation t = display.getTransformation();
    t.getScale()
        .set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
    display.setTransformation(t);
  }

  protected void cleanupNearby(Block block) {
    var loc = target(block);
    double radius = cleanupRadius();
    for (var ent : block.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
      if (ent instanceof ItemDisplay display
          && display.getScoreboardTags().contains(DisplayTags.DISPLAY_TAG)) {
        removeManagedDisplay(display);
      }
    }
  }

  protected void removeManagedDisplay(Display display) {
    if (display == null || display.isDead()) {
      return;
    }
    metadataService.unregister(display);
    display.remove();
  }

  protected abstract boolean isValidBlock(Block block);

  protected abstract void decorateMeta(ItemMeta meta, Block block);

  protected String localizationKey(Block block) {
    return null;
  }

  protected double cleanupRadius() {
    return 0.5;
  }

  // optionally overridden by subclasses
}
