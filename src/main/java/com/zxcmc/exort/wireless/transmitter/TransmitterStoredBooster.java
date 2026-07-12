package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.items.ItemStackCodec;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class TransmitterStoredBooster {
  private static final String FIELD_BOOSTER = "booster";

  private TransmitterStoredBooster() {}

  public static Optional<byte[]> boosterBlob(Plugin plugin, Block block) {
    return TransmitterStoredTerminal.storedItemBlob(plugin, block, FIELD_BOOSTER);
  }

  public static void restore(Plugin plugin, Block block, byte[] boosterBlob) {
    TransmitterStoredTerminal.restoreStoredItem(plugin, block, FIELD_BOOSTER, boosterBlob);
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
    return TransmitterStoredTerminal.getStoredItem(
        plugin,
        block,
        FIELD_BOOSTER,
        "booster",
        "Wireless Signal Booster",
        validator,
        warning,
        codec);
  }

  static TransmitterStoredTerminal.WriteResult setDetailed(
      Plugin plugin, Block block, ItemStack stack, Predicate<ItemStack> validator) {
    return setDetailed(plugin, block, stack, validator, ItemStackCodec.BUKKIT);
  }

  static TransmitterStoredTerminal.WriteResult setDetailed(
      Plugin plugin,
      Block block,
      ItemStack stack,
      Predicate<ItemStack> validator,
      ItemStackCodec codec) {
    return TransmitterStoredTerminal.setStoredItemDetailed(
        plugin, block, FIELD_BOOSTER, "Wireless Signal Booster", stack, validator, codec);
  }

  public static Optional<TransmitterStoredTerminal.TakeReservation> reserveTake(
      Plugin plugin, Block block, Predicate<ItemStack> validator, Consumer<String> warning) {
    return TransmitterStoredTerminal.reserveStoredItem(
        plugin,
        block,
        FIELD_BOOSTER,
        "booster",
        "Wireless Signal Booster",
        validator,
        warning,
        ItemStackCodec.BUKKIT);
  }

  public static void clear(Plugin plugin, Block block) {
    TransmitterStoredTerminal.clearStoredItem(plugin, block, FIELD_BOOSTER);
  }
}
