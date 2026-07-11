package com.zxcmc.exort.items;

import org.bukkit.inventory.ItemStack;

/** Serialization boundary for item stacks stored outside the live Bukkit inventory model. */
public interface ItemStackCodec {
  ItemStackCodec BUKKIT =
      new ItemStackCodec() {
        @Override
        public byte[] encode(ItemStack stack) {
          return stack.serializeAsBytes();
        }

        @Override
        public ItemStack decode(byte[] bytes) {
          return ItemStack.deserializeBytes(bytes);
        }
      };

  byte[] encode(ItemStack stack);

  ItemStack decode(byte[] bytes);
}
