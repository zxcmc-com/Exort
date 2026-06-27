package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.marker.RelayMarker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class RelayDisplayManager extends BaseCarrierDisplayManager {
  private final Component relayName;

  public RelayDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component relayName) {
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
  protected String localizationKey(Block block) {
    return "item.relay";
  }
}
