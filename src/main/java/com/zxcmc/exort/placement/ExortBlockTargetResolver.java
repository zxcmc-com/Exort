package com.zxcmc.exort.placement;

import com.zxcmc.exort.block.ExortBlockClassifier;
import com.zxcmc.exort.runtime.RuntimeMaterials;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class ExortBlockTargetResolver {
  private final ExortBlockClassifier classifier;

  public ExortBlockTargetResolver(
      Plugin plugin,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier,
      Material relayCarrier) {
    this.classifier =
        new ExortBlockClassifier(
            plugin,
            new RuntimeMaterials(
                wireMaterial,
                storageCarrier,
                terminalCarrier,
                monitorCarrier,
                busCarrier,
                relayCarrier));
  }

  public boolean isExortBlock(Block block) {
    return classifier.isExortBlock(block);
  }
}
