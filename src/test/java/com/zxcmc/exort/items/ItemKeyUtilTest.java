package com.zxcmc.exort.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ItemKeyUtilTest {
  @Test
  void sampleDataIsDeeplyImmutable() {
    ItemStack sample = new TestStack(3);
    byte[] bytes = {1, 2, 3};
    ItemKeyUtil.SampleData data = new ItemKeyUtil.SampleData(sample, bytes, "key");

    sample.setAmount(9);
    bytes[0] = 9;
    ItemStack returnedSample = data.sample();
    returnedSample.setAmount(7);
    byte[] returnedBytes = data.bytes();
    returnedBytes[1] = 8;

    assertEquals(1, data.sample().getAmount());
    assertArrayEquals(new byte[] {1, 2, 3}, data.bytes());
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
    public TestStack clone() {
      return new TestStack(amount);
    }
  }
}
