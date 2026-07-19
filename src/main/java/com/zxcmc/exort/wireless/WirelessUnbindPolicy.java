package com.zxcmc.exort.wireless;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Shared policy for the one-item Wireless Terminal unbind recipe. */
public final class WirelessUnbindPolicy {
  public record Plan(ItemStack input, ItemStack result) {}

  private final Predicate<ItemStack> wirelessTerminal;
  private final Predicate<ItemStack> linkedTerminal;
  private final UnaryOperator<ItemStack> unbind;

  public WirelessUnbindPolicy(WirelessTerminalService service) {
    this(service::isWireless, service::isLinked, service::resetLinkViaCraft);
  }

  WirelessUnbindPolicy(
      Predicate<ItemStack> wirelessTerminal,
      Predicate<ItemStack> linkedTerminal,
      UnaryOperator<ItemStack> unbind) {
    this.wirelessTerminal = Objects.requireNonNull(wirelessTerminal, "wirelessTerminal");
    this.linkedTerminal = Objects.requireNonNull(linkedTerminal, "linkedTerminal");
    this.unbind = Objects.requireNonNull(unbind, "unbind");
  }

  public Optional<Plan> plan(ItemStack[] inputs) {
    if (inputs == null || inputs.length == 0) {
      return Optional.empty();
    }
    ItemStack found = null;
    for (ItemStack input : inputs) {
      if (input == null || isAir(input.getType())) {
        continue;
      }
      if (found != null || !wirelessTerminal.test(input)) {
        return Optional.empty();
      }
      found = input;
    }
    if (found == null || !linkedTerminal.test(found)) {
      return Optional.empty();
    }
    ItemStack result = unbind.apply(found);
    if (result == null || isAir(result.getType())) {
      return Optional.empty();
    }
    result.setAmount(1);
    return Optional.of(new Plan(found, result));
  }

  private static boolean isAir(Material material) {
    return material == Material.AIR
        || material == Material.CAVE_AIR
        || material == Material.VOID_AIR;
  }
}
