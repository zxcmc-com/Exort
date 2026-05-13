package com.zxcmc.exort.core.resourcepack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexoPackBridge {
  private static final String NEXO_PLUGIN = "Nexo";
  private static final String PACK_NAME = "zxcmc_exort.zip";

  private NexoPackBridge() {}

  public static boolean isNexoEnabled(JavaPlugin plugin) {
    Plugin nexo = plugin.getServer().getPluginManager().getPlugin(NEXO_PLUGIN);
    return nexo != null && nexo.isEnabled();
  }

  public static boolean copyIfPresent(JavaPlugin plugin, File source) {
    Plugin nexo = plugin.getServer().getPluginManager().getPlugin(NEXO_PLUGIN);
    if (nexo == null || !nexo.isEnabled()) return false;
    if (source == null || !source.isFile()) return false;
    File targetDir = new File(new File(nexo.getDataFolder(), "pack"), "external_packs");
    if (!targetDir.exists()) {
      targetDir.mkdirs();
    }
    File target = new File(targetDir, PACK_NAME);
    try {
      if (target.isFile()) {
        long mismatch = Files.mismatch(source.toPath(), target.toPath());
        if (mismatch == -1L) {
          return true;
        }
      }
      Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to copy resource pack to Nexo: " + e.getMessage());
      return false;
    }
  }

  public static void removeIfPresent(JavaPlugin plugin) {
    Plugin nexo = plugin.getServer().getPluginManager().getPlugin(NEXO_PLUGIN);
    if (nexo == null || !nexo.isEnabled()) return;
    File target =
        new File(new File(new File(nexo.getDataFolder(), "pack"), "external_packs"), PACK_NAME);
    if (!target.isFile()) return;
    try {
      Files.delete(target.toPath());
    } catch (IOException e) {
      plugin
          .getLogger()
          .warning("Failed to remove Exort resource pack from Nexo: " + e.getMessage());
    }
  }
}
