package com.zxcmc.exort.items;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CustomItemRegistry {
  public static final CustomItemIdentity STORAGE_CORE =
      new CustomItemIdentity("storage_core", "item.storage_core");
  public static final CustomItemIdentity TERMINAL =
      new CustomItemIdentity("terminal", "item.terminal");
  public static final CustomItemIdentity CRAFTING_TERMINAL =
      new CustomItemIdentity("crafting_terminal", "item.crafting_terminal");
  public static final CustomItemIdentity MONITOR =
      new CustomItemIdentity("monitor", "item.monitor");
  public static final CustomItemIdentity IMPORT_BUS =
      new CustomItemIdentity("import_bus", "item.import_bus");
  public static final CustomItemIdentity EXPORT_BUS =
      new CustomItemIdentity("export_bus", "item.export_bus");
  public static final CustomItemIdentity WIRE = new CustomItemIdentity("wire", "item.wire");
  public static final CustomItemIdentity WIRELESS_TERMINAL =
      new CustomItemIdentity("wireless_terminal", "item.wireless_terminal");

  private static final List<CustomItemIdentity> FIXED_ITEMS =
      List.of(
          STORAGE_CORE,
          TERMINAL,
          CRAFTING_TERMINAL,
          MONITOR,
          IMPORT_BUS,
          EXPORT_BUS,
          WIRE,
          WIRELESS_TERMINAL);
  private static final Map<String, CustomItemIdentity> BY_ID =
      FIXED_ITEMS.stream()
          .collect(Collectors.toUnmodifiableMap(CustomItemIdentity::id, Function.identity()));

  private CustomItemRegistry() {}

  public static List<CustomItemIdentity> fixedItems() {
    return FIXED_ITEMS;
  }

  public static List<String> fixedItemIds() {
    return FIXED_ITEMS.stream().map(CustomItemIdentity::id).toList();
  }

  public static Optional<CustomItemIdentity> fixedItem(String rawId) {
    if (rawId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_ID.get(rawId.trim().toLowerCase(Locale.ROOT)));
  }
}
