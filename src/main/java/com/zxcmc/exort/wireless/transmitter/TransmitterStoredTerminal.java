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

  enum WriteFailure {
    NONE,
    INVALID_ITEM,
    SERIALIZATION_FAILED,
    EMPTY_BLOB,
    BLOB_TOO_LARGE,
    DESERIALIZATION_FAILED,
    ROUND_TRIP_MISMATCH,
    WRITE_FAILED,
    ROLLBACK_FAILED
  }

  record WriteResult(boolean success, WriteFailure failure, String detail) {
    WriteResult {
      failure = failure == null ? WriteFailure.WRITE_FAILED : failure;
      detail = detail == null ? "" : detail;
    }

    static WriteResult accepted() {
      return new WriteResult(true, WriteFailure.NONE, "");
    }

    static WriteResult rejected(WriteFailure failure, String detail) {
      return new WriteResult(false, failure, detail);
    }
  }

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
    return setDetailed(plugin, block, stack, validator).success();
  }

  static WriteResult setDetailed(
      Plugin plugin, Block block, ItemStack stack, Predicate<ItemStack> validator) {
    return setDetailed(plugin, block, stack, validator, ItemStackCodec.BUKKIT);
  }

  static boolean set(
      Plugin plugin,
      Block block,
      ItemStack stack,
      Predicate<ItemStack> validator,
      ItemStackCodec codec) {
    return setDetailed(plugin, block, stack, validator, codec).success();
  }

  static WriteResult setDetailed(
      Plugin plugin,
      Block block,
      ItemStack stack,
      Predicate<ItemStack> validator,
      ItemStackCodec codec) {
    if (isEmpty(stack) || validator == null || !validator.test(stack)) {
      return WriteResult.rejected(WriteFailure.INVALID_ITEM, "item is not a Wireless Terminal");
    }
    byte[] bytes;
    try {
      ItemStack copy = stack.clone();
      copy.setAmount(1);
      bytes = codec.encode(copy);
    } catch (RuntimeException error) {
      return WriteResult.rejected(WriteFailure.SERIALIZATION_FAILED, failureDetail(error));
    }
    WriteResult blobValidation = validateBlob(bytes);
    if (!blobValidation.success()) {
      return blobValidation;
    }

    byte[] normalized;
    ItemStack normalizedItem;
    try {
      ItemStack decoded = codec.decode(Arrays.copyOf(bytes, bytes.length));
      if (isEmpty(decoded) || !validator.test(decoded)) {
        return WriteResult.rejected(
            WriteFailure.DESERIALIZATION_FAILED, "decoded item is not a Wireless Terminal");
      }
      decoded.setAmount(1);
      normalizedItem = decoded;
      normalized = codec.encode(decoded);
    } catch (RuntimeException error) {
      return WriteResult.rejected(WriteFailure.DESERIALIZATION_FAILED, failureDetail(error));
    }
    blobValidation = validateBlob(normalized);
    if (!blobValidation.success()) {
      return blobValidation;
    }

    try {
      ItemStack stable = codec.decode(Arrays.copyOf(normalized, normalized.length));
      if (isEmpty(stable) || !validator.test(stable)) {
        return WriteResult.rejected(
            WriteFailure.ROUND_TRIP_MISMATCH, "normalized item is not a Wireless Terminal");
      }
      stable.setAmount(1);
      byte[] stableRoundTrip = codec.encode(stable);
      WriteResult stableValidation = validateBlob(stableRoundTrip);
      if (!stableValidation.success()) {
        return WriteResult.rejected(WriteFailure.ROUND_TRIP_MISMATCH, stableValidation.detail());
      }
      if (!Arrays.equals(normalized, stableRoundTrip) && !normalizedItem.isSimilar(stable)) {
        return WriteResult.rejected(
            WriteFailure.ROUND_TRIP_MISMATCH,
            "item changed semantically after persistence normalization");
      }
    } catch (RuntimeException error) {
      return WriteResult.rejected(WriteFailure.ROUND_TRIP_MISMATCH, failureDetail(error));
    }
    Optional<byte[]> previous =
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, FIELD_TERMINAL);
    try {
      ChunkMarkerStore.setBytes(
          plugin,
          block,
          TransmitterMarker.SECTION,
          FIELD_TERMINAL,
          Arrays.copyOf(normalized, normalized.length));
      return WriteResult.accepted();
    } catch (RuntimeException failure) {
      boolean rollbackFailed = false;
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
        rollbackFailed = true;
        failure.addSuppressed(rollbackFailure);
      }
      return WriteResult.rejected(
          rollbackFailed ? WriteFailure.ROLLBACK_FAILED : WriteFailure.WRITE_FAILED,
          failureDetail(failure));
    }
  }

  private static WriteResult validateBlob(byte[] bytes) {
    if (bytes == null || bytes.length <= 0) {
      return WriteResult.rejected(WriteFailure.EMPTY_BLOB, "serialized item blob is empty");
    }
    if (bytes.length > MAX_ITEM_BLOB_BYTES) {
      return WriteResult.rejected(
          WriteFailure.BLOB_TOO_LARGE,
          "serialized item blob has " + bytes.length + " bytes; limit is " + MAX_ITEM_BLOB_BYTES);
    }
    return WriteResult.accepted();
  }

  private static String failureDetail(RuntimeException error) {
    String detail = error.getClass().getSimpleName();
    if (error.getSuppressed().length > 0) {
      detail += "; rollback=" + error.getSuppressed()[0].getClass().getSimpleName();
    }
    return detail;
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
