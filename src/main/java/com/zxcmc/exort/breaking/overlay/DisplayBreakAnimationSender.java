package com.zxcmc.exort.breaking.overlay;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.breaking.BreakAnimationStages;
import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.display.core.DisplayMetadataNormalizer;
import com.zxcmc.exort.display.core.DisplayTags;
import com.zxcmc.exort.items.ItemModelUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public final class DisplayBreakAnimationSender implements BreakAnimationSender {
  private static final double CLEANUP_RADIUS = 0.85;

  private final Plugin plugin;
  private final Material displayBaseMaterial;
  private final String modelRoot;
  private final BreakingOverlayModelResolver modelResolver;
  private final float displayScale;
  private final Map<BlockKey, UUID> overlays = new HashMap<>();
  private final Set<String> warnedModels = new HashSet<>();

  public DisplayBreakAnimationSender(
      Plugin plugin,
      Material displayBaseMaterial,
      String namespace,
      String modelRoot,
      double displayScale,
      BreakingOverlayModelResolver modelResolver) {
    this.plugin = plugin;
    this.displayBaseMaterial =
        displayBaseMaterial == null || !displayBaseMaterial.isItem()
            ? Material.PAPER
            : displayBaseMaterial;
    this.modelRoot = normalizeRoot(namespace, modelRoot);
    this.displayScale = (float) Math.max(0.01, displayScale);
    this.modelResolver = modelResolver;
  }

  public static void clearStaleOverlays() {
    for (World world : Bukkit.getWorlds()) {
      for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
        if (display.getScoreboardTags().contains(DisplayTags.BREAK_OVERLAY_TAG)) {
          display.remove();
        }
      }
    }
  }

  @Override
  public void show(Block block, BreakType type, double progress) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    String modelKey = modelResolver == null ? null : modelResolver.modelKey(block, type);
    if (modelKey == null || modelKey.isBlank()) {
      clear(block);
      return;
    }
    BlockKey key = BlockKey.of(block);
    ItemDisplay display = findOverlay(key);
    if (display == null) {
      display = spawn(block);
      if (display == null) {
        return;
      }
      overlays.put(key, display.getUniqueId());
    }
    display.teleport(target(block));
    apply(display, modelKey, type, BreakAnimationStages.stageForProgress(progress));
  }

  @Override
  public void clear(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    BlockKey key = BlockKey.of(block);
    UUID id = overlays.remove(key);
    if (id != null) {
      var entity = Bukkit.getEntity(id);
      if (entity instanceof ItemDisplay display
          && display.getScoreboardTags().contains(DisplayTags.BREAK_OVERLAY_TAG)) {
        display.remove();
      }
    }
    cleanupNearby(block);
  }

  private ItemDisplay findOverlay(BlockKey key) {
    UUID id = overlays.get(key);
    if (id == null) {
      return null;
    }
    var entity = Bukkit.getEntity(id);
    if (entity instanceof ItemDisplay display
        && !display.isDead()
        && display.getScoreboardTags().contains(DisplayTags.BREAK_OVERLAY_TAG)) {
      return display;
    }
    overlays.remove(key);
    return null;
  }

  private ItemDisplay spawn(Block block) {
    return block
        .getWorld()
        .spawn(
            target(block),
            ItemDisplay.class,
            item -> {
              item.setPersistent(false);
              item.setInvulnerable(true);
              item.setSilent(true);
              item.setInvisible(true);
              item.addScoreboardTag(DisplayTags.BREAK_OVERLAY_TAG);
              item.setBillboard(Display.Billboard.FIXED);
              item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
              DisplayMetadataNormalizer.normalize(
                  item, new DisplayMetadataNormalizer.DisplayDimensions(1.0f, 1.0f));
              item.customName(null);
              item.setCustomNameVisible(false);
            });
  }

  private void apply(ItemDisplay display, String modelKey, BreakType type, int stage) {
    ItemStack stack = new ItemStack(displayBaseMaterial);
    var meta = stack.getItemMeta();
    String modelId = modelRoot + modelKey + "/stage_" + modelStageFor(type, stage);
    ItemModelUtil.ApplyResult result = ItemModelUtil.applyItemModelDetailed(meta, modelId);
    if (meta != null) {
      stack.setItemMeta(meta);
    }
    if (!result.ok() && warnedModels.add(modelId)) {
      plugin
          .getLogger()
          .warning("Failed to apply break overlay item_model '" + modelId + "': " + result.error());
    }
    display.setItemStack(stack);

    Transformation t = display.getTransformation();
    t.getLeftRotation().identity();
    t.getRightRotation().identity();
    t.getScale().set(new Vector3f(displayScale, displayScale, displayScale));
    display.setTransformation(t);
    display.setRotation(0f, 0f);
  }

  static int modelStageFor(BreakType type, int stage) {
    if (type != BreakType.WIRE) {
      return stage;
    }
    if (stage <= 4) {
      return 0;
    }
    if (stage <= 7) {
      return 1;
    }
    return 2;
  }

  private void cleanupNearby(Block block) {
    Location loc = target(block);
    for (var entity :
        block.getWorld().getNearbyEntities(loc, CLEANUP_RADIUS, CLEANUP_RADIUS, CLEANUP_RADIUS)) {
      if (entity instanceof ItemDisplay display
          && display.getScoreboardTags().contains(DisplayTags.BREAK_OVERLAY_TAG)) {
        display.remove();
      }
    }
  }

  private Location target(Block block) {
    return block.getLocation().add(0.5, 0.5, 0.5);
  }

  private static String normalizeRoot(String namespace, String rawRoot) {
    String root = rawRoot == null || rawRoot.isBlank() ? "breaking/" : rawRoot.trim();
    root = root.toLowerCase(Locale.ROOT);
    if (!root.endsWith("/")) {
      root = root + "/";
    }
    if (root.indexOf(':') >= 0) {
      return root;
    }
    String ns = namespace == null || namespace.isBlank() ? "exort" : namespace.trim();
    return ns.toLowerCase(Locale.ROOT) + ":" + root;
  }

  private record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
