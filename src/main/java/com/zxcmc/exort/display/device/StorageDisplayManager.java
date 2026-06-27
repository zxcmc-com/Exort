package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.display.core.DisplayRotation;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** RESOURCE/VANILLA display manager for storage blocks (carrier + ItemDisplay model). */
public class StorageDisplayManager extends BaseCarrierDisplayManager {
  private final Component storageName;
  private final Component coreName;

  public StorageDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component storageName,
      Component coreName) {
    super(
        plugin,
        carrierMaterial,
        displayModelId,
        displayBaseMaterial,
        displayScale,
        offsetX,
        offsetY,
        offsetZ,
        metadataService,
        "storage");
    this.storageName = storageName == null ? Component.text("Storage") : storageName;
    this.coreName = coreName == null ? Component.text("Storage Core") : coreName;
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial)
        && (StorageMarker.get(plugin, block).isPresent()
            || StorageCoreMarker.isCore(plugin, block));
  }

  @Override
  protected boolean hasRefreshMarker(Block block) {
    return StorageMarker.get(plugin, block).isPresent() || StorageCoreMarker.isCore(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    StorageMarker.get(plugin, block)
        .ifPresentOrElse(
            data -> meta.displayName(storageName(data.tier(), data.displayName())),
            () -> {
              if (StorageCoreMarker.isCore(plugin, block)) {
                meta.displayName(coreName().decoration(TextDecoration.ITALIC, false));
              }
            });
  }

  @Override
  protected String localizationKey(Block block) {
    return StorageCoreMarker.isCore(plugin, block) ? "item.storage_core" : null;
  }

  @Override
  protected void applyTransform(ItemDisplay display, Block block) {
    Transformation t = display.getTransformation();
    t.getScale()
        .set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
    var dataOpt = StorageMarker.get(plugin, block);
    if (dataOpt.isPresent()) {
      var data = dataOpt.get();
      t.getLeftRotation().set(DisplayRotation.rotationForFacing(data.facing()));
      t.getRightRotation().identity();
      display.customName(storageName(data.tier(), data.displayName()));
      display.setCustomNameVisible(false);
    } else if (StorageCoreMarker.isCore(plugin, block)) {
      t.getLeftRotation().set(new Quaternionf());
      t.getRightRotation().identity();
      display.customName(coreName());
      display.setCustomNameVisible(false);
    } else {
      t.getLeftRotation().set(new Quaternionf());
      t.getRightRotation().identity();
      display.customName(null);
    }
    display.setTransformation(t);
  }

  private Component coreName() {
    return coreName;
  }

  private Component storageName(StorageTier tier, String displayName) {
    return StorageDisplayName.displayComponent(storageName, tier, displayName);
  }
}
