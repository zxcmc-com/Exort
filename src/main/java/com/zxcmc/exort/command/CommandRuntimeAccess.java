package com.zxcmc.exort.command;

import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import java.util.function.Supplier;

public final class CommandRuntimeAccess {
  private final Supplier<CustomItems> customItems;
  private final Supplier<WirelessTerminalService> wirelessService;
  private final Supplier<ChunkLoaderService> chunkLoaderService;

  public CommandRuntimeAccess(
      Supplier<CustomItems> customItems,
      Supplier<WirelessTerminalService> wirelessService,
      Supplier<ChunkLoaderService> chunkLoaderService) {
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    this.wirelessService = Objects.requireNonNull(wirelessService, "wirelessService");
    this.chunkLoaderService = Objects.requireNonNull(chunkLoaderService, "chunkLoaderService");
  }

  public CustomItems customItems() {
    return current(customItems, "customItems");
  }

  public WirelessTerminalService wirelessService() {
    return current(wirelessService, "wirelessService");
  }

  public ChunkLoaderService chunkLoaderService() {
    return current(chunkLoaderService, "chunkLoaderService");
  }

  private static <T> T current(Supplier<T> supplier, String name) {
    return Objects.requireNonNull(supplier.get(), name);
  }
}
