package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.TransmitterMarker;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class TransmitterStoredTerminal {
  static final int MAX_ITEM_BLOB_BYTES = 1_048_576;
  private static final String FIELD_MODE = "mode";
  private static final String FIELD_TERMINAL = "terminal";

  private TransmitterStoredTerminal() {}

  public static TransmitterMode mode(Plugin plugin, Block block) {
    return ChunkMarkerStore.getString(plugin, block, TransmitterMarker.SECTION, FIELD_MODE)
        .map(TransmitterMode::fromString)
        .orElse(TransmitterMode.CHARGE_ONLY);
  }

  public static void setMode(Plugin plugin, Block block, TransmitterMode mode) {
    ChunkMarkerStore.setString(
        plugin,
        block,
        TransmitterMarker.SECTION,
        FIELD_MODE,
        mode == null ? TransmitterMode.CHARGE_ONLY.id() : mode.id());
  }

  public static Optional<ItemStack> get(
      Plugin plugin, Block block, Predicate<ItemStack> validator, Consumer<String> warning) {
    Optional<byte[]> raw =
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL);
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    byte[] bytes = raw.get();
    if (bytes.length <= 0 || bytes.length > MAX_ITEM_BLOB_BYTES) {
      warn(warning, "Invalid Wireless Transmitter stored terminal blob length: " + bytes.length);
      clear(plugin, block);
      return Optional.empty();
    }
    ItemStack stack;
    try {
      stack = ItemStack.deserializeBytes(bytes);
    } catch (RuntimeException error) {
      warn(warning, "Invalid Wireless Transmitter stored terminal blob: " + error.getMessage());
      clear(plugin, block);
      return Optional.empty();
    }
    if (isEmpty(stack) || validator == null || !validator.test(stack)) {
      warn(warning, "Wireless Transmitter stored terminal is not a Wireless Terminal.");
      clear(plugin, block);
      return Optional.empty();
    }
    stack.setAmount(1);
    return Optional.of(stack);
  }

  public static boolean set(
      Plugin plugin, Block block, ItemStack stack, Predicate<ItemStack> validator) {
    if (isEmpty(stack) || validator == null || !validator.test(stack)) {
      return false;
    }
    ItemStack copy = stack.clone();
    copy.setAmount(1);
    byte[] bytes = copy.serializeAsBytes();
    if (bytes.length <= 0 || bytes.length > MAX_ITEM_BLOB_BYTES) {
      return false;
    }
    ChunkMarkerStore.setBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL, bytes);
    return true;
  }

  public static Optional<ItemStack> take(
      Plugin plugin, Block block, Predicate<ItemStack> validator, Consumer<String> warning) {
    Optional<ItemStack> item = get(plugin, block, validator, warning);
    clear(plugin, block);
    return item;
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.removeField(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
  }

  private static void warn(Consumer<String> warning, String message) {
    if (warning != null) {
      warning.accept(message);
    }
  }
}
