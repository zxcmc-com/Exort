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
  private final String personalDisplayModelId;
  private final String dormantDisplayModelId;
  private final String disabledDisplayModelId;

  public ChunkLoaderDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      String personalDisplayModelId,
      String dormantDisplayModelId,
      String disabledDisplayModelId,
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
    this.chunkLoaderName = safeName(ChunkLoaderType.CHUNK_LOADER, chunkLoaderName, "Chunk Loader");
    this.personalChunkLoaderName =
        safeName(
            ChunkLoaderType.PERSONAL_CHUNK_LOADER,
            personalChunkLoaderName,
            "Personal Chunk Loader");
    this.dormantChunkLoaderName =
        safeName(
            ChunkLoaderType.DORMANT_CHUNK_LOADER, dormantChunkLoaderName, "Dormant Chunk Loader");
    this.personalDisplayModelId = personalDisplayModelId;
    this.dormantDisplayModelId = dormantDisplayModelId;
    this.disabledDisplayModelId = disabledDisplayModelId;
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
  protected String modelId(Block block) {
    ChunkLoaderMarker.Data data = dataFor(block);
    if (data != null && !data.enabled()) {
      return disabledDisplayModelId;
    }
    return switch (data == null ? ChunkLoaderType.defaultType() : data.type()) {
      case PERSONAL_CHUNK_LOADER -> personalDisplayModelId;
      case DORMANT_CHUNK_LOADER -> dormantDisplayModelId;
      case CHUNK_LOADER -> displayModelId;
    };
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
    ChunkLoaderMarker.Data data = dataFor(block);
    return data == null ? ChunkLoaderType.defaultType() : data.type();
  }

  private ChunkLoaderMarker.Data dataFor(Block block) {
    return ChunkLoaderMarker.get(plugin, block).orElse(null);
  }

  private static Component safeName(ChunkLoaderType type, Component name, String fallback) {
    return CustomItemText.chunkLoaderName(type, name == null ? Component.text(fallback) : name)
        .decoration(TextDecoration.ITALIC, false);
  }
}
