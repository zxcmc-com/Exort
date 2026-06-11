package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class PacketItemLocalizerTest {
  @Test
  void localizeSlotReturnsOriginalWhenItemIsUnchanged() {
    ItemStack item = new TestItemStack(1);

    ItemStack localized = PacketItemLocalizer.localizeSlot(null, item, (player, stack) -> stack);

    assertSame(item, localized);
  }

  @Test
  void localizeItemsReturnsOriginalListWhenNoItemsChange() {
    List<ItemStack> items = List.of(new TestItemStack(1), new TestItemStack(2));

    List<ItemStack> localized =
        PacketItemLocalizer.localizeItems(null, items, (player, stack) -> stack);

    assertSame(items, localized);
  }

  @Test
  void localizeItemsCopiesListOnlyWhenAnItemChanges() {
    TestItemStack first = new TestItemStack(1);
    TestItemStack second = new TestItemStack(2);
    List<ItemStack> items = new ArrayList<>(List.of(first, second));

    List<ItemStack> localized =
        PacketItemLocalizer.localizeItems(
            null, items, (player, stack) -> stack == second ? new TestItemStack(20) : stack);

    assertNotSame(items, localized);
    assertSame(first, localized.get(0));
    assertNotSame(second, localized.get(1));
    assertEquals(2, second.getAmount());
    assertEquals(20, localized.get(1).getAmount());
  }

  private static final class TestItemStack extends ItemStack {
    private int amount;

    private TestItemStack(int amount) {
      this.amount = amount;
    }

    @Override
    public Material getType() {
      return Material.STONE;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public ItemStack clone() {
      return new TestItemStack(amount);
    }
  }
}
