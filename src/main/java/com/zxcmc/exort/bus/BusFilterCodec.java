package com.zxcmc.exort.bus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;

public final class BusFilterCodec {
  private BusFilterCodec() {}

  public static byte[] encode(ItemStack[] filters, int slots) {
    if (filters == null || slots <= 0) {
      return new byte[0];
    }
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos)) {
      out.writeInt(slots);
      for (int i = 0; i < slots; i++) {
        ItemStack stack = i < filters.length ? filters[i] : null;
        if (stack == null || stack.getType().isAir()) {
          out.writeInt(-1);
          continue;
        }
        byte[] data = stack.serializeAsBytes();
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
    ItemStack[] filters = new ItemStack[Math.max(0, slots)];
    if (data == null || data.length == 0 || slots <= 0) {
      return filters;
    }
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bais)) {
      int count = in.readInt();
      int limit = Math.min(count, slots);
      for (int i = 0; i < limit; i++) {
        int len = in.readInt();
        if (len <= 0) {
          filters[i] = null;
          continue;
        }
        byte[] blob = in.readNBytes(len);
        filters[i] = ItemStack.deserializeBytes(blob);
      }
    } catch (IOException ignored) {
      // Ignore malformed data and return best-effort result.
    }
    return filters;
  }
}
