package com.zxcmc.exort.display;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
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
  public StorageDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ) {
    super(
        plugin,
        carrierMaterial,
        displayModelId,
        displayBaseMaterial,
        displayScale,
        offsetX,
        offsetY,
        offsetZ,
        "storage");
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial)
        && (StorageMarker.get(plugin, block).isPresent()
            || StorageCoreMarker.isCore(plugin, block));
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    StorageMarker.get(plugin, block)
        .ifPresentOrElse(
            data ->
                meta.displayName(
                    Component.text(data.tier().displayName())
                        .decoration(TextDecoration.ITALIC, false)),
            () -> {
              if (StorageCoreMarker.isCore(plugin, block)) {
                meta.displayName(
                    Component.text(coreName()).decoration(TextDecoration.ITALIC, false));
              }
            });
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
      display.customName(Component.text(data.tier().displayName()));
      display.setCustomNameVisible(false);
    } else if (StorageCoreMarker.isCore(plugin, block)) {
      t.getLeftRotation().set(new Quaternionf());
      t.getRightRotation().identity();
      display.customName(Component.text(coreName()));
      display.setCustomNameVisible(false);
    } else {
      t.getLeftRotation().set(new Quaternionf());
      t.getRightRotation().identity();
      display.customName(null);
    }
    display.setTransformation(t);
  }

  private String coreName() {
    if (plugin instanceof ExortPlugin exort) {
      return exort.getLang().tr("item.storage_core");
    }
    return "Storage Core";
  }
}
