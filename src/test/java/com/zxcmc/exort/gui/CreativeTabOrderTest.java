package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CreativeTabOrderTest {
  @Test
  void freezesSourceDataAndReturnedPositions() {
    List<String> entries = new java.util.ArrayList<>(List.of("minecraft:stone"));
    Map<String, List<String>> tabs = new LinkedHashMap<>();
    tabs.put("minecraft:building_blocks", entries);
    CreativeTabOrder order = CreativeTabOrder.fromTabs(tabs);

    entries.add("minecraft:dirt");
    List<CreativeTabOrder.Position> positions = order.positionsFor(new TestStack(Material.STONE));

    assertEquals(List.of(new CreativeTabOrder.Position(0, 0)), positions);
    assertThrows(
        UnsupportedOperationException.class,
        () -> positions.add(new CreativeTabOrder.Position(1, 1)));
  }

  @Test
  void customCategoryUsesInjectedExortClassifierOnly() {
    ItemStack exort = new TestStack(Material.PAPER);
    ItemStack foreign = new TestStack(Material.DIAMOND);
    CreativeTabOrder order =
        CreativeTabOrder.fromTabs(
            Map.of("minecraft:ingredients", List.of("minecraft:paper")), stack -> stack == exort);

    assertEquals(order.customTabIndex(), order.positionFor(exort).tabIndex());
    assertEquals(order.unknownTabIndex(), order.positionFor(foreign).tabIndex());
  }

  private static final class TestStack extends ItemStack {
    private final Material material;

    private TestStack(Material material) {
      this.material = material;
    }

    @Override
    public Material getType() {
      return material;
    }
  }
}
