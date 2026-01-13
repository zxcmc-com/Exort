package com.zxcmc.exort.core.resourcepack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexoPackBridge {
  private static final String NEXO_PLUGIN = "Nexo";
  private static final String PACK_NAME = "exort.zip";

  private NexoPackBridge() {}

  public static void copyIfPresent(JavaPlugin plugin) {
    Plugin nexo = plugin.getServer().getPluginManager().getPlugin(NEXO_PLUGIN);
    if (nexo == null || !nexo.isEnabled()) {
      return;
    }
    File source = new File(new File(plugin.getDataFolder(), "pack"), PACK_NAME);
    if (!source.isFile()) {
      return;
    }
    File targetDir = new File(new File(nexo.getDataFolder(), "pack"), "external_packs");
    if (!targetDir.exists()) {
      targetDir.mkdirs();
    }
    File target = new File(targetDir, PACK_NAME);
    try {
      if (target.isFile()) {
        long mismatch = Files.mismatch(source.toPath(), target.toPath());
        if (mismatch == -1L) {
          return;
        }
      }
      Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to copy resource pack to Nexo: " + e.getMessage());
    }
  }
}
