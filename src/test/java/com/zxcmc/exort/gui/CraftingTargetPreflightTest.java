package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CraftingTargetPreflightTest {
  @Test
  void mergeIncludesMainOutputAndRemainders() {
    ItemStack stone = new MaterialStack(Material.STONE);
    ItemStack bucket = new MaterialStack(Material.BUCKET);

    var merged =
        CraftingTargetPreflight.merge(
            java.util.List.of(new OutputStack("minecraft:stone", stone, 8)),
            java.util.List.of(new OutputStack("minecraft:bucket", bucket, 2)));

    assertEquals(2, merged.size());
    assertEquals(8, amount(merged, "minecraft:stone"));
    assertEquals(2, amount(merged, "minecraft:bucket"));
  }

  @Test
  void mergeSumsMatchingOutputAndRemainderKeys() {
    ItemStack bucket = new MaterialStack(Material.BUCKET);

    var merged =
        CraftingTargetPreflight.merge(
            java.util.List.of(new OutputStack("minecraft:bucket", bucket, 1)),
            java.util.List.of(new OutputStack("minecraft:bucket", bucket, 3)));

    assertEquals(1, merged.size());
    assertEquals(4, amount(merged, "minecraft:bucket"));
  }

  private static long amount(java.util.List<OutputStack> stacks, String key) {
    return stacks.stream()
        .filter(stack -> stack.key().equals(key))
        .mapToLong(OutputStack::amount)
        .findFirst()
        .orElse(0);
  }

  private static final class MaterialStack extends ItemStack {
    private final Material type;

    private MaterialStack(Material type) {
      this.type = type;
    }

    @Override
    public Material getType() {
      return type;
    }
  }
}
