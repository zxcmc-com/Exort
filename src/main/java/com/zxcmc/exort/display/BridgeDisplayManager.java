package com.zxcmc.exort.display;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.marker.BridgeMarker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class BridgeDisplayManager extends BaseCarrierDisplayManager {
  private final Component bridgeName;

  public BridgeDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component bridgeName) {
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
        "bridge");
    this.bridgeName = bridgeName == null ? Component.text("Bridge") : bridgeName;
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial) && BridgeMarker.isBridge(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    meta.displayName(bridgeName.decoration(TextDecoration.ITALIC, false));
  }

  @Override
  protected String localizationKey(Block block) {
    return "item.bridge";
  }
}
