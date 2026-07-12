package com.zxcmc.exort.wireless.transmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class TransmitterSessionInventoryDestinationTest {
  @Test
  void prefersCompatibleStackBeforeEmptySlot() {
    ItemStack[] contents = new ItemStack[36];
    contents[0] = new TestStack(Material.STONE, 64);
    contents[4] = new TestStack(Material.PAPER, 12);
    ItemStack output = new TestStack(Material.PAPER, 1);

    TransmitterSession.InventoryDestination destination =
        TransmitterSession.inventoryDestination(contents, 64, output);

    assertEquals(4, destination.slot());
    assertEquals(13, destination.stack().getAmount());
    assertEquals(12, contents[4].getAmount());
  }

  @Test
  void usesFirstEmptyStorageSlotWithoutMutatingOutput() {
    ItemStack[] contents = new ItemStack[36];
    contents[0] = new TestStack(Material.STONE, 64);
    ItemStack output = new TestStack(Material.PAPER, 1);

    TransmitterSession.InventoryDestination destination =
        TransmitterSession.inventoryDestination(contents, 64, output);

    assertEquals(1, destination.slot());
    assertEquals(Material.PAPER, destination.stack().getType());
    assertEquals(1, output.getAmount());
  }

  @Test
  void rejectsFullInventory() {
    ItemStack[] contents = new ItemStack[36];
    for (int slot = 0; slot < contents.length; slot++) {
      contents[slot] = new TestStack(Material.STONE, 64);
    }

    assertNull(
        TransmitterSession.inventoryDestination(contents, 64, new TestStack(Material.PAPER, 1)));
  }

  private static final class TestStack extends ItemStack {
    private final Material material;
    private int amount;

    private TestStack(Material material, int amount) {
      this.material = material;
      this.amount = amount;
    }

    @Override
    public Material getType() {
      return material;
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
    public int getMaxStackSize() {
      return 64;
    }

    @Override
    public boolean isSimilar(ItemStack stack) {
      return stack != null && material == stack.getType();
    }

    @Override
    public TestStack clone() {
      return new TestStack(material, amount);
    }
  }
}
