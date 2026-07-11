package com.zxcmc.exort.storage;

import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.items.ItemStackCodec;
import java.util.Arrays;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Validates the complete persistent representation before an item enters a storage cache. */
public final class StoredItemCodec {
  public static final int MAX_BLOB_BYTES = 1_048_576;

  public enum Failure {
    NONE,
    INVALID_AMOUNT,
    EMPTY_ITEM,
    SERIALIZATION_FAILED,
    EMPTY_BLOB,
    BLOB_TOO_LARGE,
    DESERIALIZATION_FAILED,
    ROUND_TRIP_MISMATCH,
    KEY_MISMATCH,
    INVALID_WEIGHT,
    COUNT_OVERFLOW,
    READ_ONLY,
    STALE_PREFLIGHT
  }

  public static final class PreparedItem {
    private final String key;
    private final ItemStack sample;
    private final byte[] blob;

    private PreparedItem(String key, ItemStack sample, byte[] blob) {
      this.key = Objects.requireNonNull(key, "key");
      this.sample = Objects.requireNonNull(sample, "sample");
      this.blob = Arrays.copyOf(blob, blob.length);
    }

    public String key() {
      return key;
    }

    public ItemStack sample() {
      return sample.clone();
    }

    ItemStack internalSample() {
      return sample;
    }

    public byte[] blob() {
      return Arrays.copyOf(blob, blob.length);
    }

    byte[] internalBlob() {
      return blob;
    }
  }

  public record Preflight(PreparedItem item, Failure failure, String detail) {
    public Preflight {
      Objects.requireNonNull(failure, "failure");
      detail = detail == null ? "" : detail;
    }

    public boolean accepted() {
      return item != null && failure == Failure.NONE;
    }

    static Preflight accepted(PreparedItem item) {
      return new Preflight(Objects.requireNonNull(item, "item"), Failure.NONE, "");
    }

    static Preflight rejected(Failure failure, String detail) {
      if (failure == Failure.NONE) {
        throw new IllegalArgumentException("A rejected item requires a failure reason");
      }
      return new Preflight(null, failure, detail);
    }
  }

  private final ItemStackCodec itemStackCodec;

  public StoredItemCodec() {
    this(ItemStackCodec.BUKKIT);
  }

  public StoredItemCodec(ItemStackCodec itemStackCodec) {
    this.itemStackCodec = Objects.requireNonNull(itemStackCodec, "itemStackCodec");
  }

  public Preflight preflight(String expectedKey, ItemStack stack) {
    if (isEmpty(stack)) {
      return Preflight.rejected(Failure.EMPTY_ITEM, "item is empty");
    }
    ItemStack sample;
    byte[] blob;
    try {
      sample = stack.clone();
      sample.setAmount(1);
      blob = itemStackCodec.encode(sample);
    } catch (RuntimeException error) {
      return Preflight.rejected(Failure.SERIALIZATION_FAILED, failureDetail(error));
    }
    if (blob == null || blob.length == 0) {
      return Preflight.rejected(Failure.EMPTY_BLOB, "serialized item blob is empty");
    }
    if (blob.length > MAX_BLOB_BYTES) {
      return Preflight.rejected(
          Failure.BLOB_TOO_LARGE,
          "serialized item blob has " + blob.length + " bytes; limit is " + MAX_BLOB_BYTES);
    }

    String key = ItemKeyUtil.sha256Hex(blob);
    if (expectedKey != null && !expectedKey.equals(key)) {
      return Preflight.rejected(Failure.KEY_MISMATCH, "item key does not match serialized blob");
    }

    ItemStack decoded;
    byte[] roundTrip;
    try {
      decoded = itemStackCodec.decode(Arrays.copyOf(blob, blob.length));
      if (isEmpty(decoded)) {
        return Preflight.rejected(Failure.DESERIALIZATION_FAILED, "decoded item is empty");
      }
      decoded.setAmount(1);
      roundTrip = itemStackCodec.encode(decoded);
    } catch (RuntimeException error) {
      return Preflight.rejected(Failure.DESERIALIZATION_FAILED, failureDetail(error));
    }
    if (roundTrip == null
        || roundTrip.length == 0
        || roundTrip.length > MAX_BLOB_BYTES
        || !key.equals(ItemKeyUtil.sha256Hex(roundTrip))) {
      return Preflight.rejected(
          Failure.ROUND_TRIP_MISMATCH, "item representation changed after persistence round-trip");
    }
    return Preflight.accepted(new PreparedItem(key, sample, blob));
  }

  /** Validates and decodes an existing database representation without mutating it. */
  public Preflight decodePersisted(String expectedKey, byte[] blob) {
    if (blob == null || blob.length == 0) {
      return Preflight.rejected(Failure.EMPTY_BLOB, "serialized item blob is empty");
    }
    if (blob.length > MAX_BLOB_BYTES) {
      return Preflight.rejected(
          Failure.BLOB_TOO_LARGE,
          "serialized item blob has " + blob.length + " bytes; limit is " + MAX_BLOB_BYTES);
    }
    String key = ItemKeyUtil.sha256Hex(blob);
    if (expectedKey == null || expectedKey.isBlank() || !expectedKey.equals(key)) {
      return Preflight.rejected(Failure.KEY_MISMATCH, "item key does not match serialized blob");
    }
    ItemStack decoded;
    byte[] roundTrip;
    try {
      decoded = itemStackCodec.decode(Arrays.copyOf(blob, blob.length));
      if (isEmpty(decoded)) {
        return Preflight.rejected(Failure.DESERIALIZATION_FAILED, "decoded item is empty");
      }
      decoded.setAmount(1);
      roundTrip = itemStackCodec.encode(decoded);
    } catch (RuntimeException error) {
      return Preflight.rejected(Failure.DESERIALIZATION_FAILED, failureDetail(error));
    }
    if (roundTrip == null || roundTrip.length == 0 || roundTrip.length > MAX_BLOB_BYTES) {
      return Preflight.rejected(
          Failure.ROUND_TRIP_MISMATCH, "item representation changed after persistence round-trip");
    }
    String normalizedKey = ItemKeyUtil.sha256Hex(roundTrip);
    try {
      ItemStack normalized = itemStackCodec.decode(Arrays.copyOf(roundTrip, roundTrip.length));
      if (isEmpty(normalized)) {
        return Preflight.rejected(
            Failure.ROUND_TRIP_MISMATCH, "normalized item representation decodes as empty");
      }
      normalized.setAmount(1);
      byte[] stableRoundTrip = itemStackCodec.encode(normalized);
      if (stableRoundTrip == null
          || stableRoundTrip.length == 0
          || stableRoundTrip.length > MAX_BLOB_BYTES
          || !normalizedKey.equals(ItemKeyUtil.sha256Hex(stableRoundTrip))) {
        return Preflight.rejected(
            Failure.ROUND_TRIP_MISMATCH,
            "item representation does not converge after persistence normalization");
      }
    } catch (RuntimeException error) {
      return Preflight.rejected(Failure.ROUND_TRIP_MISMATCH, failureDetail(error));
    }
    return Preflight.accepted(new PreparedItem(normalizedKey, decoded, roundTrip));
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null
        || stack.getAmount() <= 0
        || stack.getType() == Material.AIR
        || stack.getType() == Material.CAVE_AIR
        || stack.getType() == Material.VOID_AIR;
  }

  private static String failureDetail(RuntimeException error) {
    String message = error.getMessage();
    return error.getClass().getSimpleName()
        + (message == null || message.isBlank() ? "" : ": " + message);
  }
}
