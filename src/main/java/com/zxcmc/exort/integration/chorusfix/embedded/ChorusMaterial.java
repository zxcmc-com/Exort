package com.zxcmc.exort.integration.chorusfix.embedded;

import org.bukkit.Material;

public enum ChorusMaterial {
  AIR,
  END_STONE,
  CHORUS_PLANT,
  CHORUS_FLOWER,
  OTHER;

  public static ChorusMaterial fromMaterial(Material material) {
    if (material == null) {
      return OTHER;
    }
    if (material == Material.AIR
        || material == Material.CAVE_AIR
        || material == Material.VOID_AIR) {
      return AIR;
    }
    return switch (material) {
      case END_STONE -> END_STONE;
      case CHORUS_PLANT -> CHORUS_PLANT;
      case CHORUS_FLOWER -> CHORUS_FLOWER;
      default -> OTHER;
    };
  }

  public boolean isChorusBlock() {
    return this == CHORUS_PLANT || this == CHORUS_FLOWER;
  }

  public boolean isPlantOrFlower() {
    return isChorusBlock();
  }

  public boolean supportsChorusPlant() {
    return this == CHORUS_PLANT || this == END_STONE;
  }

  public boolean supportsChorusFlower() {
    return this == CHORUS_PLANT || this == END_STONE;
  }
}
