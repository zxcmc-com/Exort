package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.relay.RelayVisualStateResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class RelayDisplayManager extends BaseCarrierDisplayManager {
  private final Component relayName;
  private final RelayVisualStateResolver visualStateResolver;
  private final String greenModelId;
  private final String blueModelId;
  private final String redModelId;

  public RelayDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      String greenModelId,
      String blueModelId,
      String redModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component relayName,
      RelayVisualStateResolver visualStateResolver) {
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
        "relay");
    this.relayName = relayName == null ? Component.text("Network Relay") : relayName;
    this.greenModelId = greenModelId;
    this.blueModelId = blueModelId;
    this.redModelId = redModelId;
    this.visualStateResolver = visualStateResolver;
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial) && RelayMarker.isRelay(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    meta.displayName(relayName.decoration(TextDecoration.ITALIC, false));
  }

  @Override
  protected String modelId(Block block) {
    if (visualStateResolver == null) {
      return displayModelId;
    }
    return switch (visualStateResolver.resolve(block)) {
      case GREEN -> greenModelId;
      case BLUE -> blueModelId;
      case RED -> redModelId;
      case BLACK -> displayModelId;
    };
  }

  @Override
  protected String localizationKey(Block block) {
    return "item.relay";
  }
}
