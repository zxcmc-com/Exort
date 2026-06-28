package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class ChunkLoaderDisplayManager extends BaseCarrierDisplayManager {
  private final Component name;

  public ChunkLoaderDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component name) {
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
        "chunk_loader");
    this.name = name;
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial)
        && ChunkLoaderMarker.isChunkLoader(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    if (name != null) {
      meta.displayName(name.decoration(TextDecoration.ITALIC, false));
    }
  }

  @Override
  protected String localizationKey(Block block) {
    return "item.chunk_loader";
  }
}
