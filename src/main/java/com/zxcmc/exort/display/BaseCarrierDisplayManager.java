package com.zxcmc.exort.display;

import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.ItemModelUtil;
import com.zxcmc.exort.core.marker.DisplayMarker;
import com.zxcmc.exort.core.marker.MarkerCoords;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.UUID;

public abstract class BaseCarrierDisplayManager {
    protected final Plugin plugin;
    protected final Material carrierMaterial;
    protected final String displayModelId;
    protected final Material displayBaseMaterial;
    protected final double displayScale;
    protected final double offsetX;
    protected final double offsetY;
    protected final double offsetZ;
    private final String markerType;

    protected BaseCarrierDisplayManager(Plugin plugin,
                                        Material carrierMaterial,
                                        String displayModelId,
                                        Material displayBaseMaterial,
                                        double displayScale,
                                        double offsetX,
                                        double offsetY,
                                        double offsetZ,
                                        String markerType) {
        this.plugin = plugin;
        this.carrierMaterial = carrierMaterial;
        this.displayModelId = displayModelId;
        this.displayBaseMaterial = displayBaseMaterial;
        this.displayScale = displayScale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.markerType = markerType;
    }

    public void scanLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    public void refreshChunk(org.bukkit.Chunk chunk) {
        scanChunk(chunk);
    }

    private void scanChunk(org.bukkit.Chunk chunk) {
        var keys = chunk.getPersistentDataContainer().getKeys();
        if (keys.isEmpty()) return;
        for (NamespacedKey k : keys) {
            if (!k.getNamespace().equals(plugin.getName().toLowerCase())) continue;
            String key = k.getKey();
            if (!key.startsWith(markerType + "_")) continue;
            int[] xyz = MarkerCoords.parseXYZ(key.substring(markerType.length() + 1));
            if (xyz == null) continue;
            var block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
            if (!Carriers.matchesCarrier(block, carrierMaterial)) continue;
            refresh(block);
        }
    }

    public void refresh(Block block) {
        if (!isValidBlock(block)) {
            removeDisplay(block);
            return;
        }
        UUID existingId = DisplayMarker.get(plugin, markerType, block).orElse(null);
        ItemDisplay display = existingId != null ? (ItemDisplay) Bukkit.getEntity(existingId) : null;
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
            if (ent instanceof ItemDisplay d && !d.isDead()) d.remove();
        }
        DisplayMarker.clear(plugin, markerType, block);
        cleanupNearby(block);
    }

    private ItemDisplay spawn(Block block) {
        return block.getWorld().spawn(target(block), ItemDisplay.class, item -> {
            item.setPersistent(true);
            item.setInvulnerable(true);
            item.setSilent(true);
            item.setInvisible(true);
            item.addScoreboardTag(DisplayTags.DISPLAY_TAG);
            item.addScoreboardTag(markerType);
            item.setBillboard(Display.Billboard.FIXED);
            item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            item.setDisplayWidth(0.0f);
            item.setDisplayHeight(0.0f);
            item.setViewRange(1.0f);
            item.setTeleportDuration(0);
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
        t.getScale().set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
        display.setTransformation(t);
    }

    protected void cleanupNearby(Block block) {
        var loc = target(block);
        double radius = cleanupRadius();
        for (var ent : block.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (ent instanceof ItemDisplay display && display.getScoreboardTags().contains(DisplayTags.DISPLAY_TAG)) {
                display.remove();
            }
        }
    }

    protected abstract boolean isValidBlock(Block block);

    protected abstract void decorateMeta(org.bukkit.inventory.meta.ItemMeta meta, Block block);

    protected double cleanupRadius() {
        return 0.5;
    }

    // optionally overridden by subclasses
}
