package com.zxcmc.exort.display.device;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class TransmitterDisplayManager extends BaseCarrierDisplayManager {
  private final Component transmitterName;
  private final String enabledModelId;
  private final String chargingModelId;
  private final Supplier<TransmitterSessionManager> transmitterSessionManager;

  public TransmitterDisplayManager(
      Plugin plugin,
      Material carrierMaterial,
      String displayModelId,
      String enabledModelId,
      String chargingModelId,
      Material displayBaseMaterial,
      double displayScale,
      double offsetX,
      double offsetY,
      double offsetZ,
      DisplayMetadataService metadataService,
      Component transmitterName,
      Supplier<TransmitterSessionManager> transmitterSessionManager) {
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
        "transmitter");
    this.transmitterName =
        transmitterName == null ? Component.text("Wireless Transmitter") : transmitterName;
    this.enabledModelId = enabledModelId == null ? displayModelId : enabledModelId;
    this.chargingModelId = chargingModelId == null ? this.enabledModelId : chargingModelId;
    this.transmitterSessionManager = transmitterSessionManager;
  }

  @Override
  protected boolean isValidBlock(Block block) {
    return Carriers.matchesCarrier(block, carrierMaterial)
        && TransmitterMarker.isTransmitter(plugin, block);
  }

  @Override
  protected void decorateMeta(ItemMeta meta, Block block) {
    meta.displayName(transmitterName.decoration(TextDecoration.ITALIC, false));
  }

  @Override
  protected String modelId(Block block) {
    TransmitterSessionManager manager =
        transmitterSessionManager == null ? null : transmitterSessionManager.get();
    if (manager == null) {
      return displayModelId;
    }
    return switch (manager.visualState(block)) {
      case CHARGING -> chargingModelId;
      case ENABLED -> enabledModelId;
      case DEFAULT -> displayModelId;
    };
  }

  @Override
  public void removeDisplay(Block block) {
    TransmitterSessionManager manager =
        transmitterSessionManager == null ? null : transmitterSessionManager.get();
    if (manager != null) {
      manager.cancelChargingRefresh(block);
    }
    super.removeDisplay(block);
  }

  @Override
  protected String localizationKey(Block block) {
    return "item.transmitter";
  }
}
