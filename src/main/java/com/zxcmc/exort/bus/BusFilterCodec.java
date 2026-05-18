package com.zxcmc.exort.bus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;

public final class BusFilterCodec {
  private static final int MAX_FILTER_SLOTS = 54;
  private static final int MAX_ITEM_BLOB_BYTES = 1_048_576;

  private BusFilterCodec() {}

  public static byte[] encode(ItemStack[] filters, int slots) {
    if (filters == null || slots <= 0) {
      return new byte[0];
    }
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos)) {
      int safeSlots = Math.min(slots, MAX_FILTER_SLOTS);
      out.writeInt(safeSlots);
      for (int i = 0; i < safeSlots; i++) {
        ItemStack stack = i < filters.length ? filters[i] : null;
        if (stack == null || stack.getType().isAir()) {
          out.writeInt(-1);
          continue;
        }
        byte[] data = stack.serializeAsBytes();
        if (data.length <= 0 || data.length > MAX_ITEM_BLOB_BYTES) {
          out.writeInt(-1);
          continue;
        }
        out.writeInt(data.length);
        out.write(data);
      }
      out.flush();
      return baos.toByteArray();
    } catch (IOException e) {
      return new byte[0];
    }
  }

  public static ItemStack[] decode(byte[] data, int slots) {
    int safeSlots = Math.min(Math.max(0, slots), MAX_FILTER_SLOTS);
    ItemStack[] filters = new ItemStack[safeSlots];
    if (data == null || data.length == 0 || slots <= 0) {
      return filters;
    }
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais)) {
      int count = in.readInt();
      if (count < 0 || count > MAX_FILTER_SLOTS) {
        return filters;
      }
      int limit = Math.min(count, safeSlots);
      for (int i = 0; i < limit; i++) {
        int len = in.readInt();
        if (len <= 0) {
          filters[i] = null;
          continue;
        }
        if (len > MAX_ITEM_BLOB_BYTES || len > in.available()) {
          break;
        }
        byte[] blob = in.readNBytes(len);
        filters[i] = ItemStack.deserializeBytes(blob);
      }
    } catch (IOException | RuntimeException ignored) {
      // Ignore malformed data and return best-effort result.
    }
    return filters;
  }
}
