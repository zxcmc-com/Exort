package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.items.ItemStackCodec;
import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class StoredItemCodecTest {
  @Test
  void acceptsBlobAtPersistenceLimit() {
    StoredItemCodec codec = codecWithRoundTrip();

    StoredItemCodec.Preflight belowLimit =
        codec.preflight(null, new ByteItemStack(StoredItemCodec.MAX_BLOB_BYTES - 1));
    StoredItemCodec.Preflight result =
        codec.preflight(null, new ByteItemStack(StoredItemCodec.MAX_BLOB_BYTES));

    assertTrue(belowLimit.accepted());
    assertEquals(StoredItemCodec.MAX_BLOB_BYTES - 1, belowLimit.item().blob().length);
    assertTrue(result.accepted());
    assertEquals(StoredItemCodec.MAX_BLOB_BYTES, result.item().blob().length);
  }

  @Test
  void rejectsBlobAbovePersistenceLimit() {
    StoredItemCodec codec = codecWithRoundTrip();

    StoredItemCodec.Preflight result =
        codec.preflight(null, new ByteItemStack(StoredItemCodec.MAX_BLOB_BYTES + 1));

    assertEquals(StoredItemCodec.Failure.BLOB_TOO_LARGE, result.failure());
  }

  @Test
  void reportsEncoderAndDecoderFailuresWithoutReturningPreparedItem() {
    StoredItemCodec encodeFailure =
        new StoredItemCodec(
            new ItemStackCodec() {
              @Override
              public byte[] encode(ItemStack stack) {
                throw new IllegalStateException("encode failed");
              }

              @Override
              public ItemStack decode(byte[] bytes) {
                return new ByteItemStack(bytes.length);
              }
            });
    StoredItemCodec decodeFailure =
        new StoredItemCodec(
            new ItemStackCodec() {
              @Override
              public byte[] encode(ItemStack stack) {
                return new byte[] {1};
              }

              @Override
              public ItemStack decode(byte[] bytes) {
                throw new IllegalStateException("decode failed");
              }
            });

    assertEquals(
        StoredItemCodec.Failure.SERIALIZATION_FAILED,
        encodeFailure.preflight(null, new ByteItemStack(1)).failure());
    assertEquals(
        StoredItemCodec.Failure.DESERIALIZATION_FAILED,
        decodeFailure.preflight(null, new ByteItemStack(1)).failure());
  }

  @Test
  void rejectsExpectedKeyThatDoesNotMatchPersistedRepresentation() {
    StoredItemCodec.Preflight result =
        codecWithRoundTrip().preflight("not-the-item-key", new ByteItemStack(8));

    assertEquals(StoredItemCodec.Failure.KEY_MISMATCH, result.failure());
  }

  @Test
  void rejectsRoundTripThatChangesPersistedRepresentation() {
    StoredItemCodec codec =
        new StoredItemCodec(
            new ItemStackCodec() {
              @Override
              public byte[] encode(ItemStack stack) {
                return ((ByteItemStack) stack).bytes();
              }

              @Override
              public ItemStack decode(byte[] bytes) {
                byte[] changed = Arrays.copyOf(bytes, bytes.length + 1);
                return new ByteItemStack(changed);
              }
            });

    StoredItemCodec.Preflight result = codec.preflight(null, new ByteItemStack(4));

    assertEquals(StoredItemCodec.Failure.ROUND_TRIP_MISMATCH, result.failure());
  }

  @Test
  void persistedRepresentationCanNormalizeAcrossServerVersions() {
    StoredItemCodec codec =
        new StoredItemCodec(
            new ItemStackCodec() {
              @Override
              public byte[] encode(ItemStack stack) {
                return ((ByteItemStack) stack).bytes();
              }

              @Override
              public ItemStack decode(byte[] bytes) {
                return new ByteItemStack(
                    bytes.length == 3 ? Arrays.copyOf(bytes, bytes.length + 1) : bytes);
              }
            });
    byte[] oldBlob = new byte[] {1, 2, 3};
    String oldKey = com.zxcmc.exort.items.ItemKeyUtil.sha256Hex(oldBlob);

    StoredItemCodec.Preflight result = codec.decodePersisted(oldKey, oldBlob);

    assertTrue(result.accepted());
    assertEquals(4, result.item().blob().length);
    assertFalse(oldKey.equals(result.item().key()));
  }

  private static StoredItemCodec codecWithRoundTrip() {
    return new StoredItemCodec(
        new ItemStackCodec() {
          @Override
          public byte[] encode(ItemStack stack) {
            return ((ByteItemStack) stack).bytes();
          }

          @Override
          public ItemStack decode(byte[] bytes) {
            return new ByteItemStack(bytes);
          }
        });
  }

  static final class ByteItemStack extends ItemStack {
    private final byte[] bytes;
    private int amount = 1;

    ByteItemStack(int size) {
      this(new byte[size]);
    }

    ByteItemStack(byte[] bytes) {
      this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    byte[] bytes() {
      return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public Material getType() {
      return Material.STONE;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public boolean hasItemMeta() {
      return false;
    }

    @Override
    public ItemStack clone() {
      ByteItemStack clone = new ByteItemStack(bytes);
      clone.setAmount(amount);
      return clone;
    }
  }
}
