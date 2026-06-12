package com.zxcmc.exort.command;

import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import java.util.function.Supplier;

public final class CommandRuntimeAccess {
  private final Supplier<CustomItems> customItems;
  private final Supplier<WirelessTerminalService> wirelessService;

  public CommandRuntimeAccess(
      Supplier<CustomItems> customItems, Supplier<WirelessTerminalService> wirelessService) {
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    this.wirelessService = Objects.requireNonNull(wirelessService, "wirelessService");
  }

  public CustomItems customItems() {
    return current(customItems, "customItems");
  }

  public WirelessTerminalService wirelessService() {
    return current(wirelessService, "wirelessService");
  }

  private static <T> T current(Supplier<T> supplier, String name) {
    return Objects.requireNonNull(supplier.get(), name);
  }
}
