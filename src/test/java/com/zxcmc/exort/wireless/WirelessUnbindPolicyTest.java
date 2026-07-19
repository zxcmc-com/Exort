package com.zxcmc.exort.wireless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class WirelessUnbindPolicyTest {
  private final WirelessUnbindPolicy policy =
      new WirelessUnbindPolicy(
          stack -> stack instanceof TestStack test && test.wireless,
          stack -> stack instanceof TestStack test && test.linked,
          stack -> {
            TestStack source = (TestStack) stack;
            return new TestStack(source.material, true, false, source.charge, source.amount);
          });

  @Test
  void acceptsOneLinkedTerminalAtAnyChargeAndReturnsOne() {
    TestStack input = new TestStack(Material.SHIELD, true, true, 37, 8);

    WirelessUnbindPolicy.Plan plan = policy.plan(new ItemStack[] {null, input}).orElseThrow();

    assertSame(input, plan.input());
    assertEquals(1, plan.result().getAmount());
    assertEquals(37, ((TestStack) plan.result()).charge);
    assertFalse(((TestStack) plan.result()).linked);
  }

  @Test
  void rejectsUnlinkedMultipleAndForeignInputs() {
    TestStack linked = new TestStack(Material.SHIELD, true, true, 10, 1);
    TestStack unlinked = new TestStack(Material.SHIELD, true, false, 10, 1);
    TestStack foreign = new TestStack(Material.STONE, false, false, 0, 1);

    assertTrue(policy.plan(new ItemStack[] {unlinked}).isEmpty());
    assertTrue(policy.plan(new ItemStack[] {linked, linked}).isEmpty());
    assertTrue(policy.plan(new ItemStack[] {linked, foreign}).isEmpty());
  }

  private static final class TestStack extends ItemStack {
    private final Material material;
    private final boolean wireless;
    private final boolean linked;
    private final int charge;
    private int amount;

    private TestStack(Material material, boolean wireless, boolean linked, int charge, int amount) {
      this.material = material;
      this.wireless = wireless;
      this.linked = linked;
      this.charge = charge;
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
  }
}
