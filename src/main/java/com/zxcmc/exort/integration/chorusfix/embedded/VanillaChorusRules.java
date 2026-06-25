package com.zxcmc.exort.integration.chorusfix.embedded;

public final class VanillaChorusRules {
  private VanillaChorusRules() {}

  public static ChorusFaceMask recomputePlantMask(ChorusWorldView world) {
    return new ChorusFaceMask(
        connectsToPlantOrFlower(world.typeAt(0, 0, -1)),
        connectsToPlantOrFlower(world.typeAt(1, 0, 0)),
        connectsToPlantOrFlower(world.typeAt(0, 0, 1)),
        connectsToPlantOrFlower(world.typeAt(-1, 0, 0)),
        connectsToPlantOrFlower(world.typeAt(0, 1, 0)),
        connectsDown(world.typeAt(0, -1, 0)));
  }

  public static boolean canPlantSurvive(ChorusWorldView world) {
    ChorusMaterial above = world.typeAt(0, 1, 0);
    ChorusMaterial below = world.typeAt(0, -1, 0);
    for (HorizontalOffset horizontal : HorizontalOffset.VALUES) {
      ChorusMaterial neighbor = world.typeAt(horizontal.dx, 0, horizontal.dz);
      if (neighbor != ChorusMaterial.CHORUS_PLANT) {
        continue;
      }
      if (above != ChorusMaterial.AIR && below != ChorusMaterial.AIR) {
        return false;
      }
      ChorusMaterial neighborBelow = world.typeAt(horizontal.dx, -1, horizontal.dz);
      if (neighborBelow.supportsChorusPlant()) {
        return true;
      }
    }
    return below.supportsChorusPlant();
  }

  public static boolean canFlowerSurvive(ChorusWorldView world) {
    ChorusMaterial below = world.typeAt(0, -1, 0);
    if (below.supportsChorusFlower()) {
      return true;
    }
    if (below != ChorusMaterial.AIR) {
      return false;
    }

    boolean foundPlantNeighbor = false;
    for (HorizontalOffset horizontal : HorizontalOffset.VALUES) {
      ChorusMaterial neighbor = world.typeAt(horizontal.dx, 0, horizontal.dz);
      if (neighbor == ChorusMaterial.CHORUS_PLANT) {
        if (foundPlantNeighbor) {
          return false;
        }
        foundPlantNeighbor = true;
      } else if (neighbor != ChorusMaterial.AIR) {
        return false;
      }
    }
    return foundPlantNeighbor;
  }

  private static boolean connectsToPlantOrFlower(ChorusMaterial material) {
    return material == ChorusMaterial.CHORUS_PLANT || material == ChorusMaterial.CHORUS_FLOWER;
  }

  private static boolean connectsDown(ChorusMaterial material) {
    return connectsToPlantOrFlower(material) || material == ChorusMaterial.END_STONE;
  }

  private record HorizontalOffset(int dx, int dz) {
    private static final HorizontalOffset[] VALUES = {
      new HorizontalOffset(0, -1),
      new HorizontalOffset(1, 0),
      new HorizontalOffset(0, 1),
      new HorizontalOffset(-1, 0)
    };
  }
}
