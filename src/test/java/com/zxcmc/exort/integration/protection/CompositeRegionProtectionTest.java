package com.zxcmc.exort.integration.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CompositeRegionProtectionTest {
  @Test
  void denyFromAnyAdapterDeniesAction() {
    CompositeRegionProtection protection =
        new CompositeRegionProtection(
            List.of(adapter("allow", fixed(true)), adapter("deny", fixed(false))), null, false);

    assertFalse(protection.canBuild(null, null, null));
    assertFalse(protection.canBreak(null, null));
    assertFalse(protection.canInteract(null, null));
    assertFalse(protection.canUse(null, null));
  }

  @Test
  void checkFailureAllowsWhenFailOpen() {
    CompositeRegionProtection protection =
        new CompositeRegionProtection(List.of(adapter("throwing", throwing())), null, false);

    assertTrue(protection.canBuild(null, null, null));
    assertEquals(List.of("throwing:build"), protection.runtimeFailureKeys());
  }

  @Test
  void checkFailureDeniesWhenFailClosed() {
    CompositeRegionProtection protection =
        new CompositeRegionProtection(List.of(adapter("throwing", throwing())), null, true);

    assertFalse(protection.canBuild(null, null, null));
  }

  private static CompositeRegionProtection.Adapter adapter(
      String name, RegionProtection protection) {
    return new CompositeRegionProtection.Adapter(name, protection);
  }

  private static RegionProtection fixed(boolean allowed) {
    return new RegionProtection() {
      @Override
      public boolean canBuild(Player player, Location location, Material material) {
        return allowed;
      }

      @Override
      public boolean canBreak(Player player, Block block) {
        return allowed;
      }

      @Override
      public boolean canInteract(Player player, Block block) {
        return allowed;
      }

      @Override
      public boolean canUse(Player player, Block block) {
        return allowed;
      }
    };
  }

  private static RegionProtection throwing() {
    return new RegionProtection() {
      @Override
      public boolean canBuild(Player player, Location location, Material material) {
        throw new IllegalStateException("boom");
      }

      @Override
      public boolean canBreak(Player player, Block block) {
        throw new IllegalStateException("boom");
      }

      @Override
      public boolean canInteract(Player player, Block block) {
        throw new IllegalStateException("boom");
      }

      @Override
      public boolean canUse(Player player, Block block) {
        throw new IllegalStateException("boom");
      }
    };
  }
}
