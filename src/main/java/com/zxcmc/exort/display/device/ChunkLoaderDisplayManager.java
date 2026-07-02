package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.items.CustomItemText;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class ChunkLoaderDisplayManager extends BaseCarrierDisplayManager {
  private final Component chunkLoaderName;
  private final Component personalChunkLoaderName;
  private final Component dormantChunkLoaderName;

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
      Component chunkLoaderName,
      Component personalChunkLoaderName,
      Component dormantChunkLoaderName) {
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
    this.chunkLoaderName = safeName(chunkLoaderName, "Chunk Loader");
    this.personalChunkLoaderName = safeName(personalChunkLoaderName, "Personal Chunk Loader");
    this.dormantChunkLoaderName = safeName(dormantChunkLoaderName, "Dormant Chunk Loader");
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial)
        && ChunkLoaderMarker.isChunkLoader(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    meta.displayName(nameFor(block).decoration(TextDecoration.ITALIC, false));
  }

  @Override
  protected String localizationKey(Block block) {
    return typeFor(block).translationKey();
  }

  @Override
  protected void applyTransform(ItemDisplay display, Block block) {
    super.applyTransform(display, block);
    display.customName(nameFor(block));
    display.setCustomNameVisible(false);
  }

  private Component nameFor(Block block) {
    return switch (typeFor(block)) {
      case PERSONAL_CHUNK_LOADER -> personalChunkLoaderName;
      case DORMANT_CHUNK_LOADER -> dormantChunkLoaderName;
      case CHUNK_LOADER -> chunkLoaderName;
    };
  }

  private ChunkLoaderType typeFor(Block block) {
    return ChunkLoaderMarker.get(plugin, block)
        .map(ChunkLoaderMarker.Data::type)
        .orElse(ChunkLoaderType.defaultType());
  }

  private static Component safeName(Component name, String fallback) {
    return CustomItemText.chunkLoaderName(name == null ? Component.text(fallback) : name)
        .decoration(TextDecoration.ITALIC, false);
  }
}
