package com.zxcmc.exort.display;

import com.zxcmc.exort.core.breaking.BreakAnimationSender;
import com.zxcmc.exort.core.breaking.BreakAnimationStages;
import com.zxcmc.exort.core.breaking.BreakType;
import com.zxcmc.exort.core.items.ItemModelUtil;
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
  private final String modelPrefix;
  private final float displayScale;
  private final Map<BlockKey, UUID> overlays = new HashMap<>();
  private final Set<String> warnedModels = new HashSet<>();

  public DisplayBreakAnimationSender(
      Plugin plugin,
      Material displayBaseMaterial,
      String namespace,
      String modelPrefix,
      double displayScale) {
    this.plugin = plugin;
    this.displayBaseMaterial =
        displayBaseMaterial == null || !displayBaseMaterial.isItem()
            ? Material.PAPER
            : displayBaseMaterial;
    this.modelPrefix = normalizePrefix(namespace, modelPrefix);
    this.displayScale = (float) Math.max(0.01, displayScale);
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
    apply(display, BreakAnimationStages.stageForProgress(progress));
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
              item.setDisplayWidth(0.0f);
              item.setDisplayHeight(0.0f);
              item.setViewRange(1.0f);
              item.setTeleportDuration(0);
              item.customName(null);
              item.setCustomNameVisible(false);
            });
  }

  private void apply(ItemDisplay display, int stage) {
    ItemStack stack = new ItemStack(displayBaseMaterial);
    var meta = stack.getItemMeta();
    String modelId = modelPrefix + stage;
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

  private static String normalizePrefix(String namespace, String rawPrefix) {
    String prefix = rawPrefix == null || rawPrefix.isBlank() ? "breaking/stage_" : rawPrefix.trim();
    prefix = prefix.toLowerCase(Locale.ROOT);
    if (prefix.indexOf(':') >= 0) {
      return prefix;
    }
    String ns = namespace == null || namespace.isBlank() ? "exort" : namespace.trim();
    return ns.toLowerCase(Locale.ROOT) + ":" + prefix;
  }

  private record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
