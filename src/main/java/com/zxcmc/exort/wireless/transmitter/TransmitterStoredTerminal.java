package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.items.ItemStackCodec;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.TransmitterMarker;
import java.util.Arrays;
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

  public static final class TakeReservation {
    private enum State {
      OPEN,
      COMMITTED,
      ROLLED_BACK
    }

    private final Plugin plugin;
    private final Block block;
    private final byte[] blob;
    private final ItemStack item;
    private State state = State.OPEN;

    private TakeReservation(Plugin plugin, Block block, byte[] blob, ItemStack item) {
      this.plugin = plugin;
      this.block = block;
      this.blob = Arrays.copyOf(blob, blob.length);
      this.item = item.clone();
    }

    public ItemStack item() {
      return item.clone();
    }

    public boolean commit() {
      if (state != State.OPEN) {
        return false;
      }
      Optional<byte[]> current =
          ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL);
      if (current.isEmpty() || !Arrays.equals(blob, current.get())) {
        return false;
      }
      try {
        clear(plugin, block);
      } catch (RuntimeException failure) {
        Optional<byte[]> afterFailure =
            ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL);
        if (afterFailure.isEmpty()) {
          state = State.COMMITTED;
          return true;
        }
        return false;
      }
      state = State.COMMITTED;
      return true;
    }

    public boolean rollback() {
      if (state != State.COMMITTED) {
        return false;
      }
      if (ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL)
          .isPresent()) {
        return false;
      }
      ChunkMarkerStore.setBytes(
          plugin,
          block,
          TransmitterMarker.SECTION,
          FIELD_TERMINAL,
          Arrays.copyOf(blob, blob.length));
      state = State.ROLLED_BACK;
      return true;
    }
  }

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

  public static Optional<byte[]> terminalBlob(Plugin plugin, Block block) {
    return ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL)
        .filter(bytes -> bytes.length > 0 && bytes.length <= MAX_ITEM_BLOB_BYTES)
        .map(bytes -> Arrays.copyOf(bytes, bytes.length));
  }

  public static void restore(
      Plugin plugin, Block block, TransmitterMode mode, byte[] terminalBlob) {
    setMode(plugin, block, mode);
    if (terminalBlob == null
        || terminalBlob.length <= 0
        || terminalBlob.length > MAX_ITEM_BLOB_BYTES) {
      clear(plugin, block);
      return;
    }
    ChunkMarkerStore.setBytes(
        plugin,
        block,
        TransmitterMarker.SECTION,
        FIELD_TERMINAL,
        Arrays.copyOf(terminalBlob, terminalBlob.length));
  }

  public static Optional<ItemStack> get(
      Plugin plugin, Block block, Predicate<ItemStack> validator, Consumer<String> warning) {
    return get(plugin, block, validator, warning, ItemStackCodec.BUKKIT);
  }

  static Optional<ItemStack> get(
      Plugin plugin,
      Block block,
      Predicate<ItemStack> validator,
      Consumer<String> warning,
      ItemStackCodec codec) {
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
      stack = codec.decode(Arrays.copyOf(bytes, bytes.length));
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
    return set(plugin, block, stack, validator, ItemStackCodec.BUKKIT);
  }

  static boolean set(
      Plugin plugin,
      Block block,
      ItemStack stack,
      Predicate<ItemStack> validator,
      ItemStackCodec codec) {
    if (isEmpty(stack) || validator == null || !validator.test(stack)) {
      return false;
    }
    byte[] bytes;
    try {
      ItemStack copy = stack.clone();
      copy.setAmount(1);
      bytes = codec.encode(copy);
      if (bytes == null || bytes.length <= 0 || bytes.length > MAX_ITEM_BLOB_BYTES) {
        return false;
      }
      ItemStack decoded = codec.decode(Arrays.copyOf(bytes, bytes.length));
      if (isEmpty(decoded) || !validator.test(decoded)) {
        return false;
      }
      decoded.setAmount(1);
      byte[] roundTrip = codec.encode(decoded);
      if (!Arrays.equals(bytes, roundTrip)) {
        return false;
      }
    } catch (RuntimeException error) {
      return false;
    }
    Optional<byte[]> previous =
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL);
    try {
      ChunkMarkerStore.setBytes(
          plugin,
          block,
          TransmitterMarker.SECTION,
          FIELD_TERMINAL,
          Arrays.copyOf(bytes, bytes.length));
      return true;
    } catch (RuntimeException failure) {
      try {
        if (previous.isPresent()) {
          ChunkMarkerStore.setBytes(
              plugin,
              block,
              TransmitterMarker.SECTION,
              FIELD_TERMINAL,
              Arrays.copyOf(previous.get(), previous.get().length));
        } else {
          clear(plugin, block);
        }
      } catch (RuntimeException rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      return false;
    }
  }

  public static Optional<TakeReservation> reserveTake(
      Plugin plugin, Block block, Predicate<ItemStack> validator, Consumer<String> warning) {
    return reserveTake(plugin, block, validator, warning, ItemStackCodec.BUKKIT);
  }

  static Optional<TakeReservation> reserveTake(
      Plugin plugin,
      Block block,
      Predicate<ItemStack> validator,
      Consumer<String> warning,
      ItemStackCodec codec) {
    Optional<byte[]> raw =
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL);
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    Optional<ItemStack> item = get(plugin, block, validator, warning, codec);
    if (item.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new TakeReservation(plugin, block, raw.get(), item.get()));
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
