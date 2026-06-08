package com.zxcmc.exort.breaking;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class CarrierBreakSoundSuppressor {
  private CarrierBreakSoundSuppressor() {}

  static void stop(Player player, Block block) {
    if (player == null || block == null) return;
    stop(player, breakSound(block));
  }

  static void stop(Player player, String soundKey) {
    if (player == null || soundKey == null || soundKey.isBlank()) return;
    player.stopSound(soundKey, SoundCategory.BLOCKS);
  }

  static void stop(Player player, Sound sound) {
    if (player == null || sound == null) return;
    player.stopSound(sound, SoundCategory.BLOCKS);
  }

  static Sound breakSound(Block block) {
    try {
      return block.getBlockData().getSoundGroup().getBreakSound();
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
