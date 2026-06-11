package com.zxcmc.exort.integration.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class PacketDisplayLocalizer {
  private static final int ENTITY_CUSTOM_NAME_METADATA_INDEX = 2;

  @FunctionalInterface
  interface DisplayLocalizer {
    String localize(Player player, int entityId);
  }

  interface MetadataValueAdapter<T> {
    int index(T value);

    Object value(T value);

    T withValue(T value, Object replacement);

    default ItemStack itemStackFromValue(Object previousValue) {
      return previousValue instanceof ItemStack stack ? stack : null;
    }

    default Object itemStackValue(Object previousValue, ItemStack localizedStack) {
      return localizedStack;
    }

    Object customNameValue(Object previousValue, String localizedName);

    default Object customNameValue(T metadataValue, Object previousValue, String localizedName) {
      return customNameValue(previousValue, localizedName);
    }
  }

  private PacketDisplayLocalizer() {}

  static <T> List<T> localizeValues(
      Player player,
      int entityId,
      List<T> values,
      MetadataValueAdapter<T> adapter,
      DisplayLocalizer localizer) {
    if (values == null || values.isEmpty() || adapter == null || localizer == null) {
      return values;
    }
    String localizedName = localizer.localize(player, entityId);
    if (localizedName == null || localizedName.isBlank()) {
      return values;
    }
    List<T> localized = null;
    for (int i = 0; i < values.size(); i++) {
      T original = values.get(i);
      Object replacement = localizedValue(original, adapter, localizedName);
      if (replacement == null) {
        continue;
      }
      if (localized == null) {
        localized = new ArrayList<>(values);
      }
      localized.set(i, adapter.withValue(original, replacement));
    }
    return localized == null ? values : localized;
  }

  private static <T> Object localizedValue(
      T metadataValue, MetadataValueAdapter<T> adapter, String localizedName) {
    Object raw = adapter.value(metadataValue);
    ItemStack stack = adapter.itemStackFromValue(raw);
    if (stack != null) {
      ItemStack localized = localizedStack(stack, localizedName);
      return localized == null ? null : adapter.itemStackValue(raw, localized);
    }
    if (adapter.index(metadataValue) == ENTITY_CUSTOM_NAME_METADATA_INDEX
        && raw instanceof Optional<?>) {
      return adapter.customNameValue(metadataValue, raw, localizedName);
    }
    return null;
  }

  private static ItemStack localizedStack(ItemStack source, String localizedName) {
    ItemStack localized = source.clone();
    ItemMeta meta = localized.getItemMeta();
    if (meta == null) {
      return null;
    }
    meta.displayName(Component.text(localizedName).decoration(TextDecoration.ITALIC, false));
    localized.setItemMeta(meta);
    return localized;
  }
}
