package com.zxcmc.exort.wireless.transmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.runtime.RuntimeItemModelConfig;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransmitterSessionManagerTest {
  @BeforeEach
  void loadStorageTier() {
    YamlConfiguration tiers = new YamlConfiguration();
    tiers.set("common.maxItems", "1p");
    tiers.set("common.material", "CHEST");
    tiers.set("common.name", "Common");
    StorageTier.loadFromConfig(tiers, Logger.getLogger("test"));
  }

  @Test
  void transmitterWithoutStorageUsesDefaultVisual() {
    Harness harness = harness("transmitter-no-storage", 601);

    assertEquals(
        TransmitterSessionManager.VisualState.DEFAULT,
        harness.manager.visualState(harness.transmitter));
  }

  @Test
  void activeTransmitterWithoutStoredTerminalUsesEnabledVisual() {
    Harness harness = harness("transmitter-active", 602);
    placeStorage(harness);

    assertEquals(
        TransmitterSessionManager.VisualState.ENABLED,
        harness.manager.visualState(harness.transmitter));
  }

  @Test
  void disabledModeUsesDefaultVisualEvenWithStorage() {
    Harness harness = harness("transmitter-disabled", 603);
    placeStorage(harness);
    TransmitterStoredTerminal.setMode(
        harness.plugin, harness.transmitter, TransmitterMode.DISABLED);

    assertEquals(
        TransmitterSessionManager.VisualState.DEFAULT,
        harness.manager.visualState(harness.transmitter));
  }

  private static Harness harness(String worldName, long worldId) {
    Plugin plugin = BukkitTestDoubles.plugin();
    var world = BukkitTestDoubles.world(worldName, new UUID(0L, worldId));
    Block transmitter = world.block(0, 64, 0, Material.BARRIER);
    TransmitterMarker.set(plugin, transmitter);
    StorageKeys keys = new StorageKeys(plugin);
    Lang lang = new Lang(null);
    RuntimeItemModelConfig itemModels = RuntimeItemModelConfig.forMode(true);
    CustomItems customItems =
        new CustomItems(
            keys,
            lang,
            itemModels.wireItemModel(),
            itemModels.storageItemModel(),
            itemModels.terminalItemModel(),
            itemModels.craftingTerminalItemModel(),
            itemModels.monitorItemModel(),
            itemModels.importBusItemModel(),
            itemModels.exportBusItemModel(),
            itemModels.relayItemModel(),
            itemModels.transmitterItemModel(),
            itemModels.chunkLoaderItemModel(),
            itemModels.personalChunkLoaderItemModel(),
            itemModels.dormantChunkLoaderItemModel(),
            itemModels.wirelessItemModel(),
            itemModels.wirelessDisabledModel(),
            "minecraft:target",
            true);
    WirelessTerminalService wirelessService =
        new WirelessTerminalService(lang, keys, customItems, true, 48);
    WirelessTransmitterService transmitterService =
        new WirelessTransmitterService(
            plugin,
            keys,
            true,
            48,
            256,
            4096,
            3,
            Material.BARRIER,
            Material.BARRIER,
            Material.BARRIER,
            Material.BARRIER,
            () -> null);
    transmitterService.register(transmitter);
    TransmitterSessionManager manager =
        new TransmitterSessionManager(
            plugin,
            transmitterService,
            wirelessService,
            lang,
            new PlayerFeedback(lang),
            RegionProtection.allowAll(),
            () -> false,
            GuiOverlayConfig::defaults,
            block -> {});
    return new Harness(plugin, transmitter, manager);
  }

  private static void placeStorage(Harness harness) {
    Block storage = harness.transmitter.getRelative(org.bukkit.block.BlockFace.EAST);
    storage.setType(Material.BARRIER);
    StorageMarker.setRaw(
        harness.plugin, storage, "storage-" + storage.getX(), "common", 45L * 64L, null);
  }

  private record Harness(Plugin plugin, Block transmitter, TransmitterSessionManager manager) {}
}
