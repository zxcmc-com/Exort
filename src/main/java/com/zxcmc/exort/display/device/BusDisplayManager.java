package com.zxcmc.exort.display.device;

import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.display.core.DisplayRotation;
import com.zxcmc.exort.display.core.DisplayTags;
import com.zxcmc.exort.marker.BusMarker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/** RESOURCE/VANILLA display manager for import/export buses. */
public class BusDisplayManager extends BaseCarrierDisplayManager {
  private final String importModelId;
  private final String exportModelId;
  private final Component importName;
  private final Component exportName;

  public BusDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String importModelId,
      String exportModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component importName,
      Component exportName) {
    super(
        plugin,
        carrierMaterial,
        importModelId,
        displayBaseMaterial,
        displayScale,
        offsetX,
        offsetY,
        offsetZ,
        metadataService,
        DisplayTags.BUS_TAG);
    this.importModelId = importModelId == null ? "" : importModelId;
    this.exportModelId = exportModelId == null ? "" : exportModelId;
    this.importName = importName;
    this.exportName = exportName;
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial) && BusMarker.isBus(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    BusMarker.get(plugin, block)
        .ifPresent(
            data -> {
              Component name = data.type() == BusType.EXPORT ? exportName : importName;
              if (name != null) {
                meta.displayName(name.decoration(TextDecoration.ITALIC, false));
              }
            });
  }

  @Override
  protected String modelId(Block block) {
    return BusMarker.get(plugin, block)
        .map(data -> data.type() == BusType.EXPORT ? exportModelId : importModelId)
        .orElse(importModelId);
  }

  @Override
  protected String localizationKey(Block block) {
    return BusMarker.get(plugin, block)
        .map(data -> data.type() == BusType.EXPORT ? "item.export_bus" : "item.import_bus")
        .orElse(null);
  }

  @Override
  protected void applyTransform(ItemDisplay display, Block block) {
    Transformation t = display.getTransformation();
    t.getScale()
        .set(new Vector3f((float) displayScale, (float) displayScale, (float) displayScale));
    BusMarker.get(plugin, block)
        .ifPresentOrElse(
            data -> {
              t.getLeftRotation().set(DisplayRotation.rotationForFacingFull(data.facing()));
              t.getRightRotation().identity();
              Component name = data.type() == BusType.EXPORT ? exportName : importName;
              if (name != null) {
                display.customName(name);
                display.setCustomNameVisible(false);
              } else {
                display.customName(null);
              }
            },
            () -> {
              t.getLeftRotation().identity();
              t.getRightRotation().identity();
            });
    display.setTransformation(t);
  }
}
