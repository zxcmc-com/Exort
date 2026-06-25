package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class VanillaChorusRulesTest {
  @Test
  void recomputesPlantConnectionsFromNeighbors() {
    FakeWorld world = new FakeWorld();
    world.put(0, -1, 0, ChorusMaterial.END_STONE);
    world.put(1, 0, 0, ChorusMaterial.CHORUS_FLOWER);
    world.put(0, 1, 0, ChorusMaterial.CHORUS_PLANT);
    world.put(-1, 0, 0, ChorusMaterial.OTHER);

    assertEquals(
        new ChorusFaceMask(false, true, false, false, true, true),
        VanillaChorusRules.recomputePlantMask(world));
  }

  @Test
  void plantSurvivesOnEndStoneOrPlantBelow() {
    FakeWorld endStone = new FakeWorld();
    endStone.put(0, -1, 0, ChorusMaterial.END_STONE);
    assertTrue(VanillaChorusRules.canPlantSurvive(endStone));

    FakeWorld plant = new FakeWorld();
    plant.put(0, -1, 0, ChorusMaterial.CHORUS_PLANT);
    assertTrue(VanillaChorusRules.canPlantSurvive(plant));

    assertFalse(VanillaChorusRules.canPlantSurvive(new FakeWorld()));
  }

  @Test
  void horizontalPlantCanSupportOnlyWithAirAboveOrBelow() {
    FakeWorld supported = new FakeWorld();
    supported.put(1, 0, 0, ChorusMaterial.CHORUS_PLANT);
    supported.put(1, -1, 0, ChorusMaterial.END_STONE);
    supported.put(0, 1, 0, ChorusMaterial.AIR);
    supported.put(0, -1, 0, ChorusMaterial.OTHER);
    assertTrue(VanillaChorusRules.canPlantSurvive(supported));

    FakeWorld pinched = new FakeWorld();
    pinched.put(1, 0, 0, ChorusMaterial.CHORUS_PLANT);
    pinched.put(1, -1, 0, ChorusMaterial.END_STONE);
    pinched.put(0, 1, 0, ChorusMaterial.CHORUS_PLANT);
    pinched.put(0, -1, 0, ChorusMaterial.END_STONE);
    assertFalse(VanillaChorusRules.canPlantSurvive(pinched));
  }

  @Test
  void flowerSurvivesOnPlantOrEndStoneBelow() {
    FakeWorld plant = new FakeWorld();
    plant.put(0, -1, 0, ChorusMaterial.CHORUS_PLANT);
    assertTrue(VanillaChorusRules.canFlowerSurvive(plant));

    FakeWorld endStone = new FakeWorld();
    endStone.put(0, -1, 0, ChorusMaterial.END_STONE);
    assertTrue(VanillaChorusRules.canFlowerSurvive(endStone));
  }

  @Test
  void horizontalFlowerSurvivesWithExactlyOnePlantNeighborAndAirBelow() {
    FakeWorld world = new FakeWorld();
    world.put(0, -1, 0, ChorusMaterial.AIR);
    world.put(-1, 0, 0, ChorusMaterial.CHORUS_PLANT);

    assertTrue(VanillaChorusRules.canFlowerSurvive(world));
  }

  @Test
  void horizontalFlowerRequiresPlantNeighbor() {
    FakeWorld air = new FakeWorld();
    air.put(0, -1, 0, ChorusMaterial.AIR);
    assertFalse(VanillaChorusRules.canFlowerSurvive(air));
  }

  @Test
  void horizontalFlowerRejectsMultiplePlantNeighbors() {
    FakeWorld world = new FakeWorld();
    world.put(0, -1, 0, ChorusMaterial.AIR);
    world.put(-1, 0, 0, ChorusMaterial.CHORUS_PLANT);
    world.put(1, 0, 0, ChorusMaterial.CHORUS_PLANT);

    assertFalse(VanillaChorusRules.canFlowerSurvive(world));
  }

  @Test
  void horizontalFlowerRejectsNonAirHorizontalNeighbor() {
    FakeWorld world = new FakeWorld();
    world.put(0, -1, 0, ChorusMaterial.AIR);
    world.put(-1, 0, 0, ChorusMaterial.CHORUS_PLANT);
    world.put(0, 0, 1, ChorusMaterial.OTHER);

    assertFalse(VanillaChorusRules.canFlowerSurvive(world));
  }

  private static final class FakeWorld implements ChorusWorldView {
    private final Map<String, ChorusMaterial> blocks = new HashMap<>();

    void put(int dx, int dy, int dz, ChorusMaterial material) {
      blocks.put(key(dx, dy, dz), material);
    }

    @Override
    public ChorusMaterial typeAt(int dx, int dy, int dz) {
      return blocks.getOrDefault(key(dx, dy, dz), ChorusMaterial.AIR);
    }

    private static String key(int dx, int dy, int dz) {
      return dx + "," + dy + "," + dz;
    }
  }
}
