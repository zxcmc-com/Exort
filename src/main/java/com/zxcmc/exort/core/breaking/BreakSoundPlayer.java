package com.zxcmc.exort.core.breaking;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class BreakSoundPlayer {
  private static final double DEFAULT_RANGE = 16.0;
  private static final java.lang.reflect.Method PLAY_SOUND_KEY;

  static {
    java.lang.reflect.Method method = null;
    try {
      method =
          Player.class.getMethod(
              "playSound",
              Location.class,
              String.class,
              SoundCategory.class,
              float.class,
              float.class);
    } catch (NoSuchMethodException ignored) {
      // not available on this API
    }
    PLAY_SOUND_KEY = method;
  }

  private BreakSoundPlayer() {}

  public static boolean available() {
    return PLAY_SOUND_KEY != null;
  }

  public static void play(
      World world, Location location, String key, double range, float volume, float pitch) {
    if (world == null || key == null || PLAY_SOUND_KEY == null) return;
    double effectiveRange = range > 0 ? range : DEFAULT_RANGE;
    double rangeSq = effectiveRange * effectiveRange;
    for (Player player : world.getPlayers()) {
      if (player.getLocation().distanceSquared(location) <= rangeSq) {
        try {
          PLAY_SOUND_KEY.invoke(player, location, key, SoundCategory.BLOCKS, volume, pitch);
        } catch (Exception ignored) {
          // ignore if sound cannot be played
        }
      }
    }
  }
}
