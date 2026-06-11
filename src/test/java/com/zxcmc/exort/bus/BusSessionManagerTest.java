package com.zxcmc.exort.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class BusSessionManagerTest {
  @Test
  void humanizeMaterialFormatsRawMaterialNamesForFallbacks() {
    assertEquals("Blast Furnace", BusSessionManager.humanizeMaterial(Material.BLAST_FURNACE));
    assertEquals("Chest", BusSessionManager.humanizeMaterial(Material.CHEST));
  }
}
