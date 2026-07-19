package com.zxcmc.exort.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BusSettingsTest {
  @Test
  void filtersAreDeeplyCopiedAtTheAsyncBoundary() {
    ItemStack stack = new TestStack(3);
    ItemStack[] source = {stack};
    BusSettings settings =
        new BusSettings(new BusPos(new UUID(0L, 1L), 1, 2, 3), BusType.IMPORT, BusMode.ALL, source);

    source[0].setAmount(9);
    ItemStack[] returned = settings.filters();
    returned[0].setAmount(7);

    assertEquals(3, settings.filters()[0].getAmount());
  }

  private static final class TestStack extends ItemStack {
    private int amount;

    private TestStack(int amount) {
      this.amount = amount;
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
      return new TestStack(amount);
    }
  }
}
