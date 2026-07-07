package com.zxcmc.exort.wireless.transmitter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class TransmitterStoredTerminalTest {
  @Test
  void modeDefaultsToChargeAndPersists() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-mode", 501);

    assertEquals(TransmitterMode.CHARGE_ONLY, TransmitterStoredTerminal.mode(plugin, block));

    TransmitterStoredTerminal.setMode(plugin, block, TransmitterMode.BIND);

    assertEquals(TransmitterMode.BIND, TransmitterStoredTerminal.mode(plugin, block));
  }

  @Test
  void oldChargeModeIdReadsAsChargeOnly() {
    assertEquals(TransmitterMode.CHARGE_ONLY, TransmitterMode.fromString("charge"));
  }

  @Test
  void setterWritesSingleItemBlobWithoutMutatingSourceAmount() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-write", 502);
    ItemStack source = new TestItemStack(Material.ENDER_PEARL, 17);

    assertTrue(TransmitterStoredTerminal.set(plugin, block, source, this::acceptsEnderPearl));

    assertArrayEquals(
        new byte[] {1},
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal")
            .orElseThrow());
    assertEquals(17, source.getAmount());
  }

  @Test
  void setterRejectsNonWirelessTerminalWithoutWritingBlob() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-reject-set", 503);

    assertFalse(
        TransmitterStoredTerminal.set(
            plugin, block, new TestItemStack(Material.DIRT, 1), stack -> false));

    assertTrue(
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal").isEmpty());
  }

  @Test
  void getterClearsCorruptTerminalBlob() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-corrupt", 505);
    List<String> warnings = new ArrayList<>();
    ChunkMarkerStore.setBytes(
        plugin, block, TransmitterMarker.SECTION, "terminal", new byte[] {1, 2, 3});

    assertTrue(
        TransmitterStoredTerminal.get(plugin, block, stack -> true, warnings::add).isEmpty());

    assertFalse(warnings.isEmpty());
    assertTrue(
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal").isEmpty());
  }

  @Test
  void getterClearsOversizedTerminalBlob() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-oversized", 506);
    List<String> warnings = new ArrayList<>();
    ChunkMarkerStore.setBytes(
        plugin,
        block,
        TransmitterMarker.SECTION,
        "terminal",
        new byte[TransmitterStoredTerminal.MAX_ITEM_BLOB_BYTES + 1]);

    assertTrue(
        TransmitterStoredTerminal.get(plugin, block, stack -> true, warnings::add).isEmpty());

    assertFalse(warnings.isEmpty());
    assertTrue(
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal").isEmpty());
  }

  private boolean acceptsEnderPearl(ItemStack stack) {
    return stack != null && stack.getType() == Material.ENDER_PEARL;
  }

  private static Block transmitterBlock(String name, long id) {
    var world = BukkitTestDoubles.world(name, new UUID(0L, id));
    return world.block(4, 64, 4, Material.BARRIER);
  }

  private static final class TestItemStack extends ItemStack {
    private final Material type;
    private int amount;

    TestItemStack(Material type, int amount) {
      this.type = type;
      this.amount = amount;
    }

    @Override
    public Material getType() {
      return type;
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
    public byte[] serializeAsBytes() {
      return new byte[] {(byte) amount};
    }

    @Override
    public ItemStack clone() {
      return new TestItemStack(type, amount);
    }
  }
}
