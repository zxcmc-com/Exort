package com.zxcmc.exort.wireless.transmitter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.items.ItemStackCodec;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class TransmitterStoredTerminalTest {
  private static final ItemStackCodec TEST_CODEC =
      new ItemStackCodec() {
        @Override
        public byte[] encode(ItemStack stack) {
          return stack.serializeAsBytes();
        }

        @Override
        public ItemStack decode(byte[] bytes) {
          if (bytes.length != 1) {
            throw new IllegalArgumentException("invalid test item blob");
          }
          return new TestItemStack(Material.ENDER_PEARL, Byte.toUnsignedInt(bytes[0]));
        }
      };

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
    assertEquals(TransmitterMode.CHARGE_ONLY, TransmitterMode.fromString("charge_only"));
  }

  @Test
  void setterWritesSingleItemBlobWithoutMutatingSourceAmount() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-write", 502);
    ItemStack source = new TestItemStack(Material.ENDER_PEARL, 17);

    assertTrue(
        TransmitterStoredTerminal.set(plugin, block, source, this::acceptsEnderPearl, TEST_CODEC));

    assertArrayEquals(
        new byte[] {1},
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal")
            .orElseThrow());
    assertEquals(17, source.getAmount());
  }

  @Test
  void setterStoresCanonicalRepresentationWhenFirstRoundTripNormalizes() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-canonical", 510);
    AtomicInteger encodes = new AtomicInteger();
    ItemStackCodec normalizingCodec =
        new ItemStackCodec() {
          @Override
          public byte[] encode(ItemStack stack) {
            return encodes.getAndIncrement() == 0 ? new byte[] {9, 1} : new byte[] {1};
          }

          @Override
          public ItemStack decode(byte[] bytes) {
            Material material =
                Byte.toUnsignedInt(bytes[0]) % 2 == 0 ? Material.DIRT : Material.ENDER_PEARL;
            return new TestItemStack(material, 1);
          }
        };

    TransmitterStoredTerminal.WriteResult result =
        TransmitterStoredTerminal.setDetailed(
            plugin,
            block,
            new TestItemStack(Material.ENDER_PEARL, 8),
            this::acceptsEnderPearl,
            normalizingCodec);

    assertTrue(result.success());
    assertArrayEquals(
        new byte[] {1},
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal")
            .orElseThrow());
  }

  @Test
  void setterRejectsRepresentationThatDoesNotStabilize() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-unstable", 511);
    AtomicInteger encodes = new AtomicInteger();
    ItemStackCodec unstableCodec =
        new ItemStackCodec() {
          @Override
          public byte[] encode(ItemStack stack) {
            return new byte[] {(byte) encodes.incrementAndGet()};
          }

          @Override
          public ItemStack decode(byte[] bytes) {
            Material material =
                Byte.toUnsignedInt(bytes[0]) % 2 == 0 ? Material.DIRT : Material.ENDER_PEARL;
            return new TestItemStack(material, 1);
          }
        };

    TransmitterStoredTerminal.WriteResult result =
        TransmitterStoredTerminal.setDetailed(
            plugin,
            block,
            new TestItemStack(Material.ENDER_PEARL, 1),
            stack -> true,
            unstableCodec);

    assertFalse(result.success());
    assertEquals(TransmitterStoredTerminal.WriteFailure.ROUND_TRIP_MISMATCH, result.failure());
    assertTrue(
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal").isEmpty());
  }

  @Test
  void setterRejectsNonWirelessTerminalWithoutWritingBlob() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-reject-set", 503);

    assertFalse(
        TransmitterStoredTerminal.set(
            plugin, block, new TestItemStack(Material.DIRT, 1), stack -> false, TEST_CODEC));

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
        TransmitterStoredTerminal.get(plugin, block, stack -> true, warnings::add, TEST_CODEC)
            .isEmpty());

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
        TransmitterStoredTerminal.get(plugin, block, stack -> true, warnings::add, TEST_CODEC)
            .isEmpty());

    assertFalse(warnings.isEmpty());
    assertTrue(
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal").isEmpty());
  }

  @Test
  void failedReplacementKeepsPreviouslyStoredTerminal() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-replacement", 507);
    ItemStack original = new TestItemStack(Material.ENDER_PEARL, 1);
    assertTrue(
        TransmitterStoredTerminal.set(
            plugin, block, original, this::acceptsEnderPearl, TEST_CODEC));
    byte[] originalBlob =
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal")
            .orElseThrow();
    ItemStackCodec failingCodec =
        new ItemStackCodec() {
          @Override
          public byte[] encode(ItemStack stack) {
            throw new IllegalStateException("encode failed");
          }

          @Override
          public ItemStack decode(byte[] bytes) {
            throw new AssertionError("decode must not run");
          }
        };

    assertFalse(
        TransmitterStoredTerminal.set(
            plugin,
            block,
            new TestItemStack(Material.ENDER_PEARL, 1),
            this::acceptsEnderPearl,
            failingCodec));

    assertArrayEquals(
        originalBlob,
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal")
            .orElseThrow());
  }

  @Test
  void committedTakeCanBeRolledBackWithoutDuplicatingTerminal() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-take", 508);
    assertTrue(
        TransmitterStoredTerminal.set(
            plugin,
            block,
            new TestItemStack(Material.ENDER_PEARL, 1),
            this::acceptsEnderPearl,
            TEST_CODEC));

    TransmitterStoredTerminal.TakeReservation reservation =
        TransmitterStoredTerminal.reserveTake(
                plugin, block, this::acceptsEnderPearl, message -> {}, TEST_CODEC)
            .orElseThrow();

    assertTrue(reservation.commit());
    assertFalse(reservation.commit());
    assertTrue(
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal").isEmpty());
    assertTrue(reservation.rollback());
    assertFalse(reservation.rollback());
    assertTrue(
        TransmitterStoredTerminal.get(
                plugin, block, this::acceptsEnderPearl, message -> {}, TEST_CODEC)
            .isPresent());
  }

  @Test
  void takeCommitFailsIfStoredTerminalChangedAfterReservation() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block = transmitterBlock("stored-terminal-stale-take", 509);
    assertTrue(
        TransmitterStoredTerminal.set(
            plugin,
            block,
            new TestItemStack(Material.ENDER_PEARL, 1),
            this::acceptsEnderPearl,
            TEST_CODEC));
    TransmitterStoredTerminal.TakeReservation reservation =
        TransmitterStoredTerminal.reserveTake(
                plugin, block, this::acceptsEnderPearl, message -> {}, TEST_CODEC)
            .orElseThrow();
    ChunkMarkerStore.setBytes(plugin, block, TransmitterMarker.SECTION, "terminal", new byte[] {2});

    assertFalse(reservation.commit());
    assertArrayEquals(
        new byte[] {2},
        ChunkMarkerStore.getBytes(plugin, block, TransmitterMarker.SECTION, "terminal")
            .orElseThrow());
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
