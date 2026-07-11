package com.zxcmc.exort.breaking;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class BreakSoundPlayer {
  private static final double DEFAULT_RANGE = 16.0;

  private BreakSoundPlayer() {}

  public static boolean available() {
    return true;
  }

  public static void play(
      World world, Location location, String key, double range, float volume, float pitch) {
    if (world == null || key == null) return;
    double effectiveRange = range > 0 ? range : DEFAULT_RANGE;
    double rangeSq = effectiveRange * effectiveRange;
    for (Player player : world.getPlayers()) {
      if (player.getLocation().distanceSquared(location) <= rangeSq) {
        try {
          player.playSound(location, key, SoundCategory.BLOCKS, volume, pitch);
        } catch (RuntimeException ignored) {
          // ignore if sound cannot be played
        }
      }
    }
  }
}
